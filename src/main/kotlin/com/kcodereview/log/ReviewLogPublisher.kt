package com.kcodereview.log

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.kcodereview.git.GitCommitService
import com.kcodereview.model.ReviewResult
import com.kcodereview.settings.KCodeReviewSettings

/**
 * Orchestrates fire-and-forget log publish after a successful review.
 * Depends on abstractions: [ReviewLogPayloadBuilder] (schema) + [ReviewLogClient] (transport).
 */
object ReviewLogPublisher {

    private val log = Logger.getInstance(ReviewLogPublisher::class.java)

    fun publishAsync(project: Project, result: ReviewResult, reviewAuthor: String = "") {
        val url = KCodeReviewSettings.getInstance().logApiUrl
        if (url.isBlank()) return

        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching {
                val settings = KCodeReviewSettings.getInstance()
                val git = project.getService(GitCommitService::class.java).loadGitProjectInfo()
                val body = ReviewLogPayloadBuilder.build(
                    result = result,
                    git = git,
                    projectName = project.name,
                    projectBasePath = project.basePath.orEmpty(),
                    modelName = settings.selectedModelName,
                    pluginVersion = pluginVersion(),
                    reviewAuthor = reviewAuthor.ifBlank { git.username },
                )
                ReviewLogClient.post(url, body)
            }.onFailure {
                log.warn("ReviewLogPublisher failed: ${it.message}")
            }
        }
    }

    fun pluginVersion(): String =
        runCatching {
            PluginManagerCore.getPlugin(PluginId.getId("com.kcodereview.plugin"))?.version
        }.getOrNull().orEmpty().ifBlank { "unknown" }
}
