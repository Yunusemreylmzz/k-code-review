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
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.components.JBCheckBox
import com.kcodereview.git.GitCommitService
import com.kcodereview.model.ChangedFile
import com.kcodereview.model.ReviewResult
import com.kcodereview.service.CodeReviewService
import com.kcodereview.service.CommitGate
import com.kcodereview.service.PreCommitOverrideService
import com.kcodereview.settings.KCodeReviewSettings
import com.kcodereview.ui.ReviewToolWindowFactory
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

/**
 * Registers the pre-commit handler so IntelliJ shows a
 * **"Perform K Code Review"** checkbox in the Git commit options panel —
 * exactly like SonarQube for IDE.
 *
 * When the checkbox is ticked and the user clicks Commit:
 *  1. All staged / changed files are reviewed (only the diff, no unrelated files).
 *  2. Findings are published to the K Code Review tool window.
 *  3. If any findings exist the commit is blocked once; a second attempt with the
 *     same staged fingerprint proceeds (override gate).
 */
class KCodeReviewCheckinHandlerFactory : CheckinHandlerFactory() {
    override fun createHandler(
        panel: CheckinProjectPanel,
        commitContext: CommitContext,
    ): CheckinHandler = KCodeReviewCheckinHandler(panel.project, panel)
}

// ─────────────────────────────────────────────────────────────────────────────
// Implementation
// ─────────────────────────────────────────────────────────────────────────────

private class KCodeReviewCheckinHandler(
    private val project: Project,
    private val panel: CheckinProjectPanel,
) : CheckinHandler() {

    /**
     * The checkbox that appears in the Git commit options section
     * ("Before Commit" area), mirroring how SonarQube for IDE works.
     *
     * Default state is driven by [KCodeReviewSettings.preCommitReviewEnabled]
     * so the user's preference persists across sessions.
     */
    private val performReviewCheckBox = JBCheckBox(
        "Perform K Code Review",
        KCodeReviewSettings.getInstance().preCommitReviewEnabled,
    )

    // ── CheckinHandler API ──────────────────────────────────────────────────

    /**
     * Returns the checkbox component that IntelliJ renders in the
     * "Before Commit" section of the commit dialog.
     */
    override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent =
        object : RefreshableOnComponent {
            override fun getComponent(): JComponent = performReviewCheckBox
            override fun refresh() {}
            override fun saveState() {
                KCodeReviewSettings.getInstance().preCommitReviewEnabled = performReviewCheckBox.isSelected
            }
            override fun restoreState() {
                performReviewCheckBox.isSelected = KCodeReviewSettings.getInstance().preCommitReviewEnabled
            }
        }

    override fun beforeCheckin(): ReturnResult {
        if (!performReviewCheckBox.isSelected) return ReturnResult.COMMIT

        val settings = KCodeReviewSettings.getInstance()
        if (settings.getApiKey().isBlank()) {
            val choice = Messages.showYesNoDialog(
                project,
                "K Code Review is enabled, but no LLM API key is configured.\n\n" +
                    "Open Settings now, or commit without AI review?",
                "K Code Review",
                "Cancel Commit",
                "Commit Without Review",
                Messages.getWarningIcon(),
            )
            return if (choice == Messages.YES) ReturnResult.CANCEL else ReturnResult.COMMIT
        }

        val commitMessage = panel.commitMessage.orEmpty()
        val outcomeRef = AtomicReference<PreCommitOutcome>(PreCommitOutcome.Allow())

        ProgressManager.getInstance().run(object : Task.Modal(
            project,
            "K Code Review — Analyzing staged changes…",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                outcomeRef.set(
                    runCatching { analyzeOffEdt(commitMessage, indicator) }
                        .getOrElse { ex -> PreCommitOutcome.Failed(ex as Exception) },
                )
            }
        })

        return handleOutcome(outcomeRef.get())
    }

    // ── Analysis (off EDT) ──────────────────────────────────────────────────

    private fun analyzeOffEdt(
        commitMessage: String,
        indicator: ProgressIndicator,
    ): PreCommitOutcome {
        val overrideService = project.getService(PreCommitOverrideService::class.java)
        val git = project.getService(GitCommitService::class.java)

        indicator.text = "Reading local changes (staged + unstaged)…"
        val local = git.loadLocalChanges(commitMessage)

        // Review every dirty source file so multi-class issues are never skipped.
        val reviewable: List<ChangedFile> = local.files.filter { it.content.isNotBlank() }
        if (reviewable.isEmpty()) {
            overrideService.clear()
            return PreCommitOutcome.Allow()
        }

        // If the user already acknowledged findings for these exact local files → allow.
        val fingerprint = PreCommitOverrideService.fingerprint(reviewable)
        if (overrideService.consume(fingerprint)) {
            return PreCommitOutcome.AllowAfterOverride
        }

        indicator.text = "K Code Review — Reviewing ${reviewable.size} class(es)…"
        val result = project.getService(CodeReviewService::class.java)
            .reviewLocalChanges(commitMessage)

        // Tool window + popup are opened on EDT in handleOutcome (not from this BGT thread).
        return when {
            result == null || !CommitGate.shouldBlock(result) -> {
                overrideService.clear()
                PreCommitOutcome.Allow(result)
            }
            else -> PreCommitOutcome.Block(result, fingerprint)
        }
    }

    // ── Outcome handling (back on EDT) ──────────────────────────────────────

    private fun handleOutcome(outcome: PreCommitOutcome): ReturnResult = when (outcome) {

        is PreCommitOutcome.Allow -> {
            outcome.result?.let { ReviewToolWindowFactory.show(project) }
            ReturnResult.COMMIT
        }

        is PreCommitOutcome.AllowAfterOverride -> {
            Messages.showInfoMessage(
                project,
                "K Code Review: proceeding — you already acknowledged findings for these staged changes.",
                "K Code Review",
            )
            ReturnResult.COMMIT
        }

        is PreCommitOutcome.Failed -> {
            val msg = outcome.error.message.orEmpty()
            val transient = msg.contains("HTTP 503") || msg.contains("HTTP 502") ||
                msg.contains("HTTP 504") || msg.contains("temporarily unavailable") ||
                msg.contains("high demand")
            val choice = Messages.showYesNoDialog(
                project,
                buildString {
                    append("K Code Review failed:\n")
                    append(msg.take(600))
                    append("\n\n")
                    if (transient) {
                        append("This looks temporary (Gemini overloaded). Retry in a minute, ")
                        append("or switch model in Settings → Tools → K Code Review.\n\n")
                    }
                    append("Commit anyway?")
                },
                "K Code Review",
                "Cancel Commit",
                "Commit Anyway",
                Messages.getErrorIcon(),
            )
            if (choice == Messages.YES) ReturnResult.CANCEL else ReturnResult.COMMIT
        }

        is PreCommitOutcome.Block -> {
            project.getService(PreCommitOverrideService::class.java).arm(outcome.fingerprint)
            ReviewToolWindowFactory.show(project)
            com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("K Code Review")
                .createNotification(
                    "Commit paused once",
                    "${outcome.result.totalFindings} finding(s) — review the K Code Review panel. " +
                        "Click Commit again to proceed with the same changes.",
                    com.intellij.notification.NotificationType.WARNING,
                )
                .notify(project)
            ReturnResult.CANCEL
        }
    }

    // ── Internal outcome model ──────────────────────────────────────────────

    private sealed class PreCommitOutcome {
        data class Allow(val result: ReviewResult? = null) : PreCommitOutcome()
        data object AllowAfterOverride : PreCommitOutcome()
        data class Block(val result: ReviewResult, val fingerprint: String) : PreCommitOutcome()
        data class Failed(val error: Exception) : PreCommitOutcome()
    }
}
