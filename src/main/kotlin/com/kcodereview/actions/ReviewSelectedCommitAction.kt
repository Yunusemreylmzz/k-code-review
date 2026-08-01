package com.kcodereview.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.kcodereview.git.GitCommitService
import com.kcodereview.service.CodeReviewService
import com.kcodereview.ui.ReviewToolWindowFactory

class ReviewSelectedCommitAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val git = project.getService(GitCommitService::class.java)
        val commits = try {
            git.listRecentCommits(25)
        } catch (ex: Exception) {
            Messages.showErrorDialog(project, ex.message ?: "Git error", "K Code Review")
            return
        }
        if (commits.isEmpty()) {
            Messages.showInfoMessage(project, "No commits found.", "K Code Review")
            return
        }

        val labels = commits.map { it.toString() }.toTypedArray()
        val choice = Messages.showEditableChooseDialog(
            "Select a commit to review",
            "K Code Review",
            Messages.getQuestionIcon(),
            labels,
            labels.first(),
            null,
        ) ?: return

        val selected = commits.firstOrNull { it.toString() == choice } ?: commits.first()
        val service = project.getService(CodeReviewService::class.java)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "K Code Review: analyzing commit", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Reviewing ${selected.shortHash} with Gemini…"
                try {
                    val result = service.reviewCommit(selected.hash)
                    ApplicationManager.getApplication().invokeLater {
                        ReviewToolWindowFactory.show(project)
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("K Code Review")
                            .createNotification(
                                "Review complete",
                                "${result.totalFindings} findings in ${result.commitHash.take(8)}",
                                NotificationType.INFORMATION,
                            )
                            .notify(project)
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("K Code Review")
                            .createNotification(
                                "Review failed",
                                ex.message ?: "Unknown error",
                                NotificationType.ERROR,
                            )
                            .notify(project)
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
