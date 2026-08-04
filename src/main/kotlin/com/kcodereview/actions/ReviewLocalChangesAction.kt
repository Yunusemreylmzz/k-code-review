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
import com.kcodereview.ui.FindingsTreeBuilder
import com.kcodereview.ui.ReviewToolWindowFactory

/**
 * Default review action: every dirty class (staged + unstaged / IDE change lists).
 */
class ReviewLocalChangesAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.getService(CodeReviewService::class.java)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "K Code Review: analyzing changes", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Reviewing all local changed classes…"
                try {
                    val result = service.reviewLocalChanges("Tools menu review")
                        ?: throw IllegalStateException(
                            "No local source changes found. Edit files or stage changes first.",
                        )
                    ApplicationManager.getApplication().invokeLater {
                        ReviewToolWindowFactory.show(project)
                        val classes = FindingsTreeBuilder.build(result).groups
                            .joinToString { it.className }
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("K Code Review")
                            .createNotification(
                                "Review complete",
                                "${result.totalFindings} finding(s) across " +
                                    "${result.fileReviews.size} class(es): $classes",
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
