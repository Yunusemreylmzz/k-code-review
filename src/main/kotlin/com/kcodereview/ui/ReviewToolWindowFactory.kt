package com.kcodereview.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class ReviewToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ReviewToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Code Analysis", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    companion object {
        const val TOOL_WINDOW_ID = "K Code Review"

        fun show(project: Project) {
            ApplicationManager.getApplication().invokeLater {
                ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.apply {
                    show()
                    activate(null)
                }
            }
        }
    }
}
