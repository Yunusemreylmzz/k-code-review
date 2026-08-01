package com.kcodereview.commit

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.kcodereview.git.GitCommitService
import com.kcodereview.model.ChangedFile
import com.kcodereview.model.ReviewResult
import com.kcodereview.service.CodeReviewService
import com.kcodereview.service.CommitGate
import com.kcodereview.service.PreCommitOverrideService
import com.kcodereview.settings.KCodeReviewSettings
import com.kcodereview.ui.ReviewToolWindowFactory
import java.util.concurrent.atomic.AtomicReference

class KCodeReviewCheckinHandlerFactory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return KCodeReviewCheckinHandler(panel.project, panel)
    }
}

private class KCodeReviewCheckinHandler(
    private val project: Project,
    private val panel: CheckinProjectPanel,
) : CheckinHandler() {

    private sealed class PreCommitOutcome {
        data object Allow : PreCommitOutcome()
        data object AllowAfterOverride : PreCommitOutcome()
        data class Block(val result: ReviewResult, val fingerprint: String) : PreCommitOutcome()
        data class Failed(val error: Exception) : PreCommitOutcome()
    }

    override fun beforeCheckin(): ReturnResult {
        val settings = KCodeReviewSettings.getInstance()
        if (!settings.preCommitReviewEnabled) {
            return ReturnResult.COMMIT
        }

        if (settings.getApiKey().isBlank()) {
            val choice = Messages.showYesNoDialog(
                project,
                "K Code Review pre-commit is enabled, but Gemini API key is missing.\n\n" +
                    "Configure the key now in Settings, or commit without AI review?",
                "K Code Review",
                "Cancel Commit",
                "Commit Without Review",
                Messages.getWarningIcon(),
            )
            return if (choice == Messages.YES) ReturnResult.CANCEL else ReturnResult.COMMIT
        }

        // Read UI state on EDT, then do all VCS/AI work off the EDT.
        val commitMessage = panel.commitMessage.orEmpty()
        val outcomeHolder = AtomicReference<PreCommitOutcome>(PreCommitOutcome.Allow)

        ProgressManager.getInstance().run(object : Task.Modal(
            project,
            "K Code Review: analyzing staged changes",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    if (settings.getApiKey().isBlank()) {
                        outcomeHolder.set(PreCommitOutcome.Failed(
                            IllegalStateException("Gemini API key is not configured."),
                        ))
                        return
                    }
                    outcomeHolder.set(analyzeOffEdt(commitMessage, indicator))
                } catch (ex: Exception) {
                    outcomeHolder.set(PreCommitOutcome.Failed(ex))
                }
            }
        })

        return when (val outcome = outcomeHolder.get()) {
            is PreCommitOutcome.Allow -> ReturnResult.COMMIT

            is PreCommitOutcome.AllowAfterOverride -> {
                Messages.showInfoMessage(
                    project,
                    "Proceeding with commit. You previously acknowledged K Code Review findings for these staged changes.",
                    "K Code Review",
                )
                ReturnResult.COMMIT
            }

            is PreCommitOutcome.Failed -> {
                val choice = Messages.showYesNoDialog(
                    project,
                    "Pre-commit review failed:\n${outcome.error.message}\n\nCommit anyway?",
                    "K Code Review",
                    "Cancel Commit",
                    "Commit Anyway",
                    Messages.getErrorIcon(),
                )
                if (choice == Messages.YES) ReturnResult.CANCEL else ReturnResult.COMMIT
            }

            is PreCommitOutcome.Block -> {
                ReviewToolWindowFactory.show(project)
                project.getService(PreCommitOverrideService::class.java).arm(outcome.fingerprint)

                val findings = CommitGate.findingsForDisplay(outcome.result)
                val details = findings.take(8).joinToString("\n") { finding ->
                    "• [${finding.severity.displayName}] ${finding.filePath}" +
                        (finding.line?.let { ":$it" } ?: "") +
                        " — ${finding.title}"
                }
                val more = if (findings.size > 8) "\n…and ${findings.size - 8} more" else ""

                Messages.showWarningDialog(
                    project,
                    CommitGate.blockSummary(outcome.result) + "\n\n$details$more",
                    "Commit Blocked Once by K Code Review",
                )
                ReturnResult.CANCEL
            }
        }
    }

    private fun analyzeOffEdt(commitMessage: String, indicator: ProgressIndicator): PreCommitOutcome {
        val overrideService = project.getService(PreCommitOverrideService::class.java)
        val git = project.getService(GitCommitService::class.java)

        indicator.text = "Reading staged changes…"
        val staged = git.loadStagedChanges(commitMessage)
        val reviewable: List<ChangedFile> = staged.files.filter { it.content.isNotBlank() }
        if (reviewable.isEmpty()) {
            overrideService.clear()
            return PreCommitOutcome.Allow
        }

        val fingerprint = PreCommitOverrideService.fingerprint(reviewable)
        if (overrideService.consume(fingerprint)) {
            return PreCommitOutcome.AllowAfterOverride
        }

        indicator.text = "Gemini is reviewing staged files before commit…"
        val result = project.getService(CodeReviewService::class.java)
            .reviewStagedChanges(commitMessage)

        if (result == null || !CommitGate.shouldBlock(result)) {
            overrideService.clear()
            return PreCommitOutcome.Allow
        }

        return PreCommitOutcome.Block(result, fingerprint)
    }
}
