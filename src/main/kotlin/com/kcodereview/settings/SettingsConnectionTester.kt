package com.kcodereview.settings

import com.kcodereview.ai.LlmClientFactory
import com.kcodereview.ai.LlmProvider
import com.kcodereview.log.ReviewLogClient
import com.kcodereview.log.ReviewLogPayloadBuilder

/**
 * Settings → Test Connection: validates LLM API key, optional prompt URL, optional log API URL.
 */
object SettingsConnectionTester {

    data class Request(
        val apiKey: String,
        val selectedModelName: String,
        val customUrl: String,
        val customModelId: String,
        val customProviderFormat: LlmProvider,
        val promptFileUrl: String,
        val logApiUrl: String,
    )

    data class CheckResult(
        val label: String,
        val ok: Boolean,
        val detail: String,
    )

    fun run(
        request: Request,
        settings: KCodeReviewSettings,
        onProgress: (String) -> Unit = {},
    ): List<CheckResult> {
        val results = mutableListOf<CheckResult>()
        results += testApiKey(request, settings, onProgress)
        if (request.promptFileUrl.isNotBlank()) {
            results += testPromptUrl(request.promptFileUrl, onProgress)
        }
        if (request.logApiUrl.isNotBlank()) {
            results += testLogApiUrl(request.logApiUrl, onProgress)
        }
        return results
    }

    fun formatSuccess(results: List<CheckResult>): String {
        val labels = results.filter { it.ok }.joinToString(" · ") { it.label }
        return "✅ OK — $labels"
    }

    fun formatFailure(failed: CheckResult): String =
        "❌ ${failed.label}: ${failed.detail}"

    fun validateHttpUrl(url: String, fieldLabel: String) {
        val trimmed = url.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "$fieldLabel must start with http:// or https://"
        }
    }

    private fun testApiKey(
        request: Request,
        settings: KCodeReviewSettings,
        onProgress: (String) -> Unit,
    ): CheckResult {
        onProgress("Testing LLM API key…")
        return runCatching {
            require(request.apiKey.isNotBlank()) {
                "Enter your LLM API key above (or Apply if already saved)."
            }
            val snapshot = settings.snapshotLlmConfig()
            try {
                settings.selectedModelName = request.selectedModelName
                settings.customUrl = request.customUrl
                settings.customModelId = request.customModelId
                settings.customProviderFormat = request.customProviderFormat
                val client = LlmClientFactory.create(settings)
                val reply = client.generate(
                    "Reply with JSON only: {\"ok\":true}",
                    "ping",
                )
                require(reply.isNotBlank()) { "LLM returned an empty response." }
                "responded (${reply.length} chars)"
            } finally {
                settings.restoreLlmConfig(snapshot)
            }
        }.fold(
            onSuccess = { detail -> CheckResult("LLM API key", true, detail) },
            onFailure = { ex ->
                CheckResult(
                    "LLM API key",
                    false,
                    ex.message?.take(300) ?: ex.javaClass.simpleName,
                )
            },
        )
    }

    private fun testPromptUrl(promptUrl: String, onProgress: (String) -> Unit): CheckResult {
        onProgress("Testing prompt file URL…")
        return runCatching {
            validateHttpUrl(promptUrl, "Prompt file URL")
            val body = RemoteConfigFetcher.fetch(promptUrl)
            require(body.isNotBlank()) { "Prompt file returned an empty body." }
            "fetched (${body.length} chars)"
        }.fold(
            onSuccess = { detail -> CheckResult("Prompt URL", true, detail) },
            onFailure = { ex ->
                CheckResult(
                    "Prompt URL",
                    false,
                    ex.message?.take(300) ?: ex.javaClass.simpleName,
                )
            },
        )
    }

    private fun testLogApiUrl(logApiUrl: String, onProgress: (String) -> Unit): CheckResult {
        onProgress("Testing Log API URL…")
        return runCatching {
            validateHttpUrl(logApiUrl, "Log API URL")
            val status = ReviewLogClient.probe(
                apiUrl = logApiUrl,
                jsonBody = ReviewLogPayloadBuilder.exampleTemplateJson(),
            )
            "HTTP $status"
        }.fold(
            onSuccess = { detail -> CheckResult("Log API URL", true, detail) },
            onFailure = { ex ->
                CheckResult(
                    "Log API URL",
                    false,
                    ex.message?.take(300) ?: ex.javaClass.simpleName,
                )
            },
        )
    }

    private data class LlmConfigSnapshot(
        val selectedModelName: String,
        val customUrl: String,
        val customModelId: String,
        val customProviderFormat: LlmProvider,
    )

    private fun KCodeReviewSettings.snapshotLlmConfig(): LlmConfigSnapshot =
        LlmConfigSnapshot(
            selectedModelName = selectedModelName,
            customUrl = customUrl,
            customModelId = customModelId,
            customProviderFormat = customProviderFormat,
        )

    private fun KCodeReviewSettings.restoreLlmConfig(snapshot: LlmConfigSnapshot) {
        selectedModelName = snapshot.selectedModelName
        customUrl = snapshot.customUrl
        customModelId = snapshot.customModelId
        customProviderFormat = snapshot.customProviderFormat
    }
}
