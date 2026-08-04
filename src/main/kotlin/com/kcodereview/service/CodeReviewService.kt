package com.kcodereview.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.kcodereview.ai.LlmClientFactory
import com.kcodereview.ai.PromptBuilder
import com.kcodereview.ai.ReviewParser
import com.kcodereview.git.GitCommitService
import com.kcodereview.model.CommitSnapshot
import com.kcodereview.model.FileReview
import com.kcodereview.model.ReviewResult
import com.kcodereview.settings.KCodeReviewSettings
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class CodeReviewService(private val project: Project) {

    private val log = Logger.getInstance(CodeReviewService::class.java)
    private val listeners = CopyOnWriteArrayList<(ReviewResult?) -> Unit>()

    @Volatile
    var lastResult: ReviewResult? = null
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    fun addListener(listener: (ReviewResult?) -> Unit) {
        listeners += listener
        lastResult?.let(listener)
    }

    fun removeListener(listener: (ReviewResult?) -> Unit) {
        listeners -= listener
    }

    fun reviewLatestCommit(): ReviewResult {
        val snapshot = project.getService(GitCommitService::class.java).loadLatestCommit()
        return reviewCommit(snapshot.hash)
    }

    fun reviewCommit(hash: String): ReviewResult {
        val git = project.getService(GitCommitService::class.java)
        return reviewSnapshot(git.loadCommit(hash))
    }

    /**
     * Reviews currently staged index changes (pre-commit gate).
     * Returns null when there is nothing reviewable staged.
     */
    fun reviewStagedChanges(commitMessage: String): ReviewResult? {
        val snapshot = project.getService(GitCommitService::class.java)
            .loadStagedChanges(commitMessage)
        val reviewable = snapshot.files.filter { it.content.isNotBlank() }
        if (reviewable.isEmpty()) return null
        return reviewSnapshot(snapshot.copy(files = reviewable))
    }

    /**
     * Reviews staged ∪ unstaged local source changes so every edited class is analyzed.
     */
    fun reviewLocalChanges(commitMessage: String = "Local working tree changes"): ReviewResult? {
        val snapshot = project.getService(GitCommitService::class.java)
            .loadLocalChanges(commitMessage)
        val reviewable = snapshot.files.filter { it.content.isNotBlank() }
        if (reviewable.isEmpty()) return null
        return reviewSnapshot(snapshot.copy(files = reviewable))
    }

    private fun reviewSnapshot(snapshot: CommitSnapshot): ReviewResult {
        check(!isRunning) { "A review is already running." }
        isRunning = true
        val started = System.currentTimeMillis()
        try {
            val settings = KCodeReviewSettings.getInstance()
            val llmClient = LlmClientFactory.create(settings)

            val files = snapshot.files
                .filter { it.content.isNotBlank() }
                .take(settings.maxFilesPerReview)

            require(files.isNotEmpty()) {
                "No reviewable source files for ${snapshot.shortHash}."
            }

            log.info(
                "Starting review of ${files.size} file(s) in ${snapshot.shortHash}: " +
                    files.joinToString { it.path.substringAfterLast('/') },
            )

            val system = PromptBuilder.systemPrompt()
            val fileReviews = mutableListOf<FileReview>()
            val indicator = ProgressManager.getInstance().progressIndicator
            var hardFailures = 0

            for ((index, file) in files.withIndex()) {
                indicator?.checkCanceled()
                indicator?.text = "Reviewing ${file.path} (${index + 1}/${files.size})…"
                indicator?.fraction = index.toDouble() / files.size.toDouble()

                log.info("Reviewing ${file.path} (${index + 1}/${files.size}) in ${snapshot.shortHash}")
                val fileStarted = System.currentTimeMillis()
                val user = PromptBuilder.userPrompt(snapshot, file)
                try {
                    val raw = llmClient.generate(system, user)
                    log.info(
                        "Reviewed ${file.path} in ${System.currentTimeMillis() - fileStarted}ms " +
                            "(prompt≈${user.length} chars)",
                    )
                    val parsed = ReviewParser.parse(file.path, raw)
                    fileReviews += FileReview(
                        filePath = file.path,
                        summary = parsed.summary,
                        findings = parsed.findings.sorted(),
                    )
                } catch (ex: Exception) {
                    hardFailures++
                    log.warn("LLM failed for ${file.path}: ${ex.message}", ex)
                    // Continue other classes — do not abort the whole multi-file review.
                    fileReviews += FileReview(
                        filePath = file.path,
                        summary = "Review failed for this class: ${ex.message?.take(240).orEmpty()}",
                        findings = emptyList(),
                    )
                }
            }

            if (fileReviews.isEmpty() || (hardFailures == files.size && files.isNotEmpty())) {
                val msg = fileReviews.firstOrNull()?.summary
                    ?: "All file reviews failed."
                error(msg)
            }

            val result = ReviewResult(
                commitHash = snapshot.hash,
                commitMessage = snapshot.message,
                reviewedAtEpochMs = System.currentTimeMillis(),
                fileReviews = fileReviews,
            )
            log.info(
                "Review complete: ${result.totalFindings} findings in " +
                    "${System.currentTimeMillis() - started}ms " +
                    "(${files.size} file(s), ${result.fileReviews.count { it.findings.isNotEmpty() }} with issues)",
            )
            lastResult = result
            listeners.forEach { it(result) }
            return result
        } finally {
            isRunning = false
        }
    }

    /** Publishes a result to the tool window without calling Gemini (demo / tests). */
    fun publishResult(result: ReviewResult) {
        lastResult = result
        listeners.forEach { it(result) }
    }
}
