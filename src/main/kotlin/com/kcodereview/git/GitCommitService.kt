package com.kcodereview.git

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.kcodereview.model.ChangeType
import com.kcodereview.model.ChangedFile
import com.kcodereview.model.CommitSnapshot
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

@Service(Service.Level.PROJECT)
class GitCommitService(private val project: Project) {

    private val log = Logger.getInstance(GitCommitService::class.java)

    fun listRecentCommits(limit: Int = 30): List<CommitListItem> {
        val repo = requireRepository()
        val commits = GitHistoryUtils.history(project, repo.root, "--max-count=$limit")
        return commits.map { commit ->
            CommitListItem(
                hash = commit.id.asString(),
                shortHash = commit.id.toShortString(),
                message = commit.fullMessage.trim(),
                author = commit.author?.name.orEmpty(),
            )
        }
    }

    fun loadLatestCommit(): CommitSnapshot {
        val items = listRecentCommits(1)
        require(items.isNotEmpty()) { "No commits found in this repository." }
        return loadCommit(items.first().hash)
    }

    /**
     * Loads currently staged (index) changes — used by pre-commit review.
     */
    fun loadStagedChanges(commitMessage: String = "Pre-commit staged changes"): CommitSnapshot {
        val repo = requireRepository()
        val nameStatus = runCatching {
            runGit(repo, GitCommand.DIFF, listOf("--cached", "--name-status", "--find-renames", "--"))
        }.getOrDefault("")

        val files = nameStatus.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> parseStagedNameStatusLine(repo, line) }
            .toList()

        return CommitSnapshot(
            hash = "STAGED",
            shortHash = "staged",
            message = commitMessage.ifBlank { "Pre-commit staged changes" },
            author = System.getProperty("user.name").orEmpty(),
            files = files,
        )
    }

    /**
     * Loads unstaged working-tree changes (`git diff`). Never throws — returns empty on failure.
     */
    fun loadUnstagedChanges(): List<ChangedFile> {
        val repo = requireRepository()
        val nameStatus = runCatching {
            runGit(repo, GitCommand.DIFF, listOf("--name-status", "--find-renames"))
        }.onFailure {
            log.warn("loadUnstagedChanges failed: ${it.message}")
        }.getOrDefault("")

        return nameStatus.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> parseUnstagedNameStatusLine(repo, line) }
            .toList()
    }

    /**
     * Every dirty source file the user is editing: IDE change lists ∪ git staged ∪ git unstaged.
     * This is the default review scope so multi-class edits are never missed.
     */
    fun loadLocalChanges(commitMessage: String = "Local working tree changes"): CommitSnapshot {
        val ideFiles = runCatching { IdeChangeCollector.collect(project) }
            .onFailure { log.warn("IdeChangeCollector failed: ${it.message}") }
            .getOrDefault(emptyList())

        val staged = runCatching { loadStagedChanges(commitMessage).files }.getOrDefault(emptyList())
        val unstaged = loadUnstagedChanges()
        val gitMerged = ChangeSetMerger.mergeByPath(staged, unstaged) { it.path }

        // Prefer IDE content (live buffer); keep git patch when available for faster prompts.
        val patchByPath = gitMerged.associate { ChangeSetMerger.normalize(it.path) to it.patch }
        val byPath = linkedMapOf<String, ChangedFile>()

        for (f in ideFiles) {
            val key = ChangeSetMerger.normalize(f.path)
            byPath[key] = f.copy(patch = f.patch ?: patchByPath[key])
        }
        for (f in gitMerged) {
            val key = ChangeSetMerger.normalize(f.path)
            if (key !in byPath) {
                byPath[key] = f
            } else if (byPath.getValue(key).patch.isNullOrBlank() && !f.patch.isNullOrBlank()) {
                byPath[key] = byPath.getValue(key).copy(patch = f.patch)
            }
        }

        val files = byPath.values.toList()
        log.info(
            "loadLocalChanges: ${files.size} file(s) — " +
                files.joinToString { it.path.substringAfterLast('/') },
        )

        return CommitSnapshot(
            hash = "LOCAL",
            shortHash = "local",
            message = commitMessage.ifBlank { "Local working tree changes" },
            author = System.getProperty("user.name").orEmpty(),
            files = files,
        )
    }

    fun loadCommit(hash: String): CommitSnapshot {
        val repo = requireRepository()
        val commits = GitHistoryUtils.history(project, repo.root, hash, "--max-count=1")
        val commit = commits.firstOrNull()
            ?: throw IllegalStateException("Commit not found: $hash")

        val nameStatus = runCatching {
            runGit(repo, GitCommand.DIFF, listOf("--name-status", "--find-renames", "$hash^!", "--"))
        }.getOrElse {
            // Root / orphan commit: no parent → list files in the tree
            runGit(repo, GitCommand.SHOW, listOf("--name-status", "--pretty=format:", "--find-renames", hash))
        }
        val files = nameStatus.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> parseNameStatusLine(repo, hash, line) }
            .toList()

        return CommitSnapshot(
            hash = commit.id.asString(),
            shortHash = commit.id.toShortString(),
            message = commit.fullMessage.trim(),
            author = commit.author?.name.orEmpty(),
            files = files,
        )
    }

    private fun parseNameStatusLine(repo: GitRepository, hash: String, line: String): ChangedFile? {
        val parts = line.split('\t')
        if (parts.isEmpty()) return null
        val status = parts[0].firstOrNull() ?: return null
        val path = when {
            parts.size >= 3 && (status == 'R' || status == 'C') -> parts[2]
            parts.size >= 2 -> parts[1]
            else -> return null
        }
        if (!isReviewable(path)) return null

        val changeType = changeTypeFromStatus(status)
        if (changeType == ChangeType.DELETED) {
            return ChangedFile(path, "", null, changeType)
        }

        val content = readFileAtCommit(repo, hash, path)
        if (content.isBlank()) return null
        val patch = runCatching {
            runGit(repo, GitCommand.DIFF, listOf("$hash^!", "--", path))
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return ChangedFile(path = path, content = content, patch = patch, changeType = changeType)
    }

    private fun parseStagedNameStatusLine(repo: GitRepository, line: String): ChangedFile? {
        val parts = line.split('\t')
        if (parts.isEmpty()) return null
        val status = parts[0].firstOrNull() ?: return null
        val path = when {
            parts.size >= 3 && (status == 'R' || status == 'C') -> parts[2]
            parts.size >= 2 -> parts[1]
            else -> return null
        }
        if (!isReviewable(path)) return null

        val changeType = changeTypeFromStatus(status)
        if (changeType == ChangeType.DELETED) {
            return ChangedFile(path, "", null, changeType)
        }

        val content = readStagedFile(repo, path)
        if (content.isBlank()) return null
        val patch = runCatching {
            runGit(repo, GitCommand.DIFF, listOf("--cached", "--", path))
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return ChangedFile(path = path, content = content, patch = patch, changeType = changeType)
    }

    private fun parseUnstagedNameStatusLine(repo: GitRepository, line: String): ChangedFile? {
        val parts = line.split('\t')
        if (parts.isEmpty()) return null
        val status = parts[0].firstOrNull() ?: return null
        val path = when {
            parts.size >= 3 && (status == 'R' || status == 'C') -> parts[2]
            parts.size >= 2 -> parts[1]
            else -> return null
        }
        if (!isReviewable(path)) return null

        val changeType = changeTypeFromStatus(status)
        if (changeType == ChangeType.DELETED) {
            return ChangedFile(path, "", null, changeType)
        }

        val content = readWorkingTreeFile(repo, path)
        if (content.isBlank()) return null
        val patch = runCatching {
            runGit(repo, GitCommand.DIFF, listOf("--", path))
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return ChangedFile(path = path, content = content, patch = patch, changeType = changeType)
    }

    private fun changeTypeFromStatus(status: Char): ChangeType = when (status) {
        'A' -> ChangeType.ADDED
        'M' -> ChangeType.MODIFIED
        'D' -> ChangeType.DELETED
        'R' -> ChangeType.RENAMED
        'C' -> ChangeType.COPIED
        else -> ChangeType.UNKNOWN
    }

    private fun readStagedFile(repo: GitRepository, relativePath: String): String {
        val fromIndex = runCatching {
            runGit(repo, GitCommand.SHOW, listOf(":$relativePath"))
        }.getOrNull()
        if (!fromIndex.isNullOrBlank()) return fromIndex

        return readWorkingTreeFile(repo, relativePath)
    }

    private fun readWorkingTreeFile(repo: GitRepository, relativePath: String): String {
        val vf = VfsUtil.findRelativeFile(relativePath, repo.root) ?: return ""
        return runCatching { VfsUtil.loadText(vf) }.getOrDefault("")
    }

    private fun readFileAtCommit(repo: GitRepository, hash: String, relativePath: String): String {
        val fromGit = runCatching {
            runGit(repo, GitCommand.SHOW, listOf("$hash:$relativePath"))
        }.getOrNull()
        if (!fromGit.isNullOrBlank()) return fromGit

        val vf = VfsUtil.findRelativeFile(relativePath, repo.root) ?: return ""
        return runCatching { VfsUtil.loadText(vf) }.getOrDefault("")
    }

    private fun runGit(repo: GitRepository, command: GitCommand, parameters: List<String>): String {
        val handler = GitLineHandler(project, repo.root, command)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.addParameters(parameters)
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            throw IllegalStateException(result.errorOutputAsJoinedString.ifBlank { "Git command failed: $command" })
        }
        return result.outputAsJoinedString
    }

    private fun isReviewable(path: String): Boolean {
        val lower = path.lowercase()
        val blockedExt = listOf(
            ".jar", ".class", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico",
            ".pdf", ".zip", ".gz", ".lock", ".woff", ".woff2", ".ttf", ".eot",
        )
        if (blockedExt.any { lower.endsWith(it) }) return false
        val name = path.substringAfterLast('/')
        return name !in setOf("package-lock.json", "yarn.lock", "pnpm-lock.yaml")
    }

    private fun requireRepository(): GitRepository {
        val manager = GitRepositoryManager.getInstance(project)
        val repos = manager.repositories
        require(repos.isNotEmpty()) { "Open a Git project to run K Code Review." }
        val basePath = project.basePath
        if (basePath != null) {
            val baseVf = LocalFileSystem.getInstance().findFileByPath(basePath)
            if (baseVf != null) {
                manager.getRepositoryForFile(baseVf)?.let { return it }
            }
        }
        return repos.first()
    }

    data class CommitListItem(
        val hash: String,
        val shortHash: String,
        val message: String,
        val author: String,
    ) {
        override fun toString(): String = "$shortHash — ${message.lineSequence().firstOrNull().orEmpty()}"
    }
}
