package com.kcodereview.ai

import com.kcodereview.settings.KCodeReviewSettings

/**
 * Creates the correct [LlmClient] implementation from the current [KCodeReviewSettings].
 *
 * For preset models, all connection details come from [LlmModel].
 * For the "Custom Model…" entry, URL / model-ID / format come from the custom fields in settings.
 *
 * Call [create] at review time (never cache the result) so that settings changes
 * are always reflected without restarting the IDE.
 */
object LlmClientFactory {

    fun create(settings: KCodeReviewSettings = KCodeReviewSettings.getInstance()): LlmClient {
        val apiKey = settings.getApiKey()
        require(apiKey.isNotBlank()) {
            "LLM API key is not configured. Open Settings → Tools → K Code Review."
        }

        val model = LlmModel.findByDisplayName(settings.selectedModelName)

        val url      = if (model.isCustom) settings.customUrl     else model.endpointUrl
        val modelId  = if (model.isCustom) settings.customModelId else model.modelId
        val provider = if (model.isCustom) settings.customProviderFormat else model.provider

        require(url.isNotBlank()) {
            "LLM endpoint URL is not configured. Open Settings → Tools → K Code Review."
        }

        return when (provider) {
            LlmProvider.GEMINI            -> GeminiClient(apiKey, url)
            LlmProvider.OPENAI            -> OpenAiLlmClient(apiKey, url, modelId)
            LlmProvider.ANTHROPIC         -> AnthropicLlmClient(apiKey, url, modelId)
            LlmProvider.OPENAI_COMPATIBLE -> OpenAiLlmClient(apiKey, url, modelId)
        }
    }
}
