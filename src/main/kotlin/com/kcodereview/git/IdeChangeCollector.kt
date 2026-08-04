package com.kcodereview.git

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.kcodereview.model.ChangeType
import com.kcodereview.model.ChangedFile

/**
 * Collects every dirty (modified/added) source file known to the IDE VCS layer.
 * This is more reliable than git CLI alone: unstaged files always appear here.
 */
object IdeChangeCollector {

    fun collect(project: Project): List<ChangedFile> {
        val basePath = project.basePath ?: return emptyList()
        val changes = ChangeListManager.getInstance(project).allChanges
        val byPath = linkedMapOf<String, ChangedFile>()

        for (change in changes) {
            if (change.type == Change.Type.DELETED) continue
            val filePath = ChangesUtil.getFilePath(change)
            val absolute = filePath.path
            val relative = toRelativePath(basePath, absolute) ?: continue
            if (!isReviewable(relative)) continue

            val content = readContent(change, absolute)
            if (content.isBlank()) continue

            byPath[ChangeSetMerger.normalize(relative)] = ChangedFile(
                path = ChangeSetMerger.normalize(relative),
                content = content,
                patch = null,
                changeType = mapChangeType(change.type),
            )
        }
        return byPath.values.toList()
    }

    fun toRelativePath(basePath: String, absolutePath: String): String? {
        val rel = FileUtil.getRelativePath(
            FileUtil.toSystemIndependentName(basePath),
            FileUtil.toSystemIndependentName(absolutePath),
            '/',
        ) ?: return null
        if (rel.startsWith("../")) return null
        return ChangeSetMerger.normalize(rel)
    }

    fun isReviewable(path: String): Boolean {
        val lower = path.lowercase()
        val blockedExt = listOf(
            ".jar", ".class", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico",
            ".pdf", ".zip", ".gz", ".lock", ".woff", ".woff2", ".ttf", ".eot",
        )
        if (blockedExt.any { lower.endsWith(it) }) return false
        val name = path.substringAfterLast('/')
        return name !in setOf("package-lock.json", "yarn.lock", "pnpm-lock.yaml")
    }

    private fun readContent(change: Change, absolutePath: String): String {
        val vf = change.virtualFile
            ?: LocalFileSystem.getInstance().findFileByPath(absolutePath)
        if (vf != null && vf.isValid && !vf.isDirectory) {
            val fromVf = ReadAction.compute<String, RuntimeException> {
                runCatching { VfsUtil.loadText(vf) }.getOrDefault("")
            }
            if (fromVf.isNotBlank()) return fromVf
        }
        return runCatching { change.afterRevision?.content.orEmpty() }.getOrDefault("")
    }

    private fun mapChangeType(type: Change.Type): ChangeType = when (type) {
        Change.Type.NEW -> ChangeType.ADDED
        Change.Type.DELETED -> ChangeType.DELETED
        Change.Type.MOVED -> ChangeType.RENAMED
        Change.Type.MODIFICATION -> ChangeType.MODIFIED
        else -> ChangeType.UNKNOWN
    }
}
