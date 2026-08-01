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
import com.kcodereview.service.CodeReviewService
import com.kcodereview.ui.ReviewToolWindowFactory

class ReviewLatestCommitAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.getService(CodeReviewService::class.java)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "K Code Review: analyzing commit", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Reviewing latest commit with Gemini…"
                try {
                    val result = service.reviewLatestCommit()
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
