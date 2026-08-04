package com.kcodereview.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.kcodereview.model.Finding

/**
 * Applies a finding's [Finding.fixedCode] into the related source file at the finding line.
 */
object ApplyFixService {

    fun apply(project: Project, finding: Finding): Boolean {
        val code = finding.fixedCode?.trim().orEmpty()
        if (code.isBlank() || code == "// No fixed-code sample for this finding.") {
            Messages.showInfoMessage(project, "No suggested fix code for this finding.", "K Code Review")
            return false
        }

        val vf = resolveFile(project, finding.filePath)
        if (vf == null) {
            Messages.showErrorDialog(
                project,
                "Could not find file:\n${finding.filePath}",
                "K Code Review — Apply Fix",
            )
            return false
        }

        val document = FileDocumentManager.getInstance().getDocument(vf)
        if (document == null) {
            Messages.showErrorDialog(project, "File is not editable: ${finding.filePath}", "K Code Review")
            return false
        }

        WriteCommandAction.runWriteCommandAction(project, "K Code Review: Apply Suggested Fix", "K Code Review", {
            val lineIndex = ((finding.line ?: 1) - 1).coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
            val start = document.getLineStartOffset(lineIndex)
            val end = document.getLineEndOffset(lineIndex)
            val replacement = code.trimEnd()
            document.replaceString(start, end, replacement)
        })

        ApplicationManager.getApplication().invokeLater {
            val navLine = ((finding.line ?: 1) - 1).coerceAtLeast(0)
            OpenFileDescriptor(project, vf, navLine, 0).navigate(true)
        }
        return true
    }

    fun resolveFile(project: Project, filePath: String) =
        project.basePath?.let { LocalFileSystem.getInstance().findFileByPath("$it/$filePath") }
            ?: LocalFileSystem.getInstance().findFileByPath(filePath)
}
