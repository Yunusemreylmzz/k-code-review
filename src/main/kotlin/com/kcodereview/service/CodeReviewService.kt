package com.kcodereview.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.kcodereview.ai.GeminiClient
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
    private val gemini = GeminiClient()
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

    private fun reviewSnapshot(snapshot: CommitSnapshot): ReviewResult {
        check(!isRunning) { "A review is already running." }
        isRunning = true
        try {
            val settings = KCodeReviewSettings.getInstance()
            require(settings.getApiKey().isNotBlank()) {
                "Gemini API key is not configured. Open Settings → Tools → K Code Review."
            }

            val files = snapshot.files
                .filter { it.content.isNotBlank() }
                .take(settings.maxFilesPerReview)

            require(files.isNotEmpty()) {
                "No reviewable source files for ${snapshot.shortHash}."
            }

            val system = PromptBuilder.systemPrompt()
            val fileReviews = mutableListOf<FileReview>()

            for ((index, file) in files.withIndex()) {
                log.info("Reviewing ${file.path} (${index + 1}/${files.size}) in ${snapshot.shortHash}")
                val user = PromptBuilder.userPrompt(snapshot, file)
                val raw = gemini.generate(system, user)
                val parsed = ReviewParser.parse(file.path, raw)
                fileReviews += FileReview(
                    filePath = file.path,
                    summary = parsed.summary,
                    findings = parsed.findings,
                )
            }

            val result = ReviewResult(
                commitHash = snapshot.hash,
                commitMessage = snapshot.message,
                reviewedAtEpochMs = System.currentTimeMillis(),
                fileReviews = fileReviews,
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
