package com.kcodereview.ai

/**
 * Describes a single LLM model entry in the settings dropdown.
 *
 * Each preset knows its own endpoint URL, model ID, and request format (via [LlmProvider]).
 * The special [CUSTOM] sentinel causes the UI to reveal extra URL / format fields.
 */
data class LlmModel(
    /** Human-readable label shown in the dropdown. */
    val displayName: String,
    /** Which API wire format to use for this model. */
    val provider: LlmProvider,
    /** Model identifier sent in the request body (empty for Gemini — model is in the URL). */
    val modelId: String,
    /** Full API endpoint URL. */
    val endpointUrl: String,
    val isCustom: Boolean = false,
) {
    override fun toString() = displayName   // makes JComboBox render correctly

    companion object {

        val CUSTOM = LlmModel(
            displayName = "⚙ Custom Model…",
            provider    = LlmProvider.OPENAI,
            modelId     = "",
            endpointUrl = "",
            isCustom    = true,
        )

        private fun gemini(displayName: String, modelId: String) = LlmModel(
            displayName = displayName,
            provider    = LlmProvider.GEMINI,
            modelId     = modelId,
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent",
        )

        private fun qwen(displayName: String, modelId: String) = LlmModel(
            displayName = displayName,
            provider    = LlmProvider.OPENAI_COMPATIBLE,
            modelId     = modelId,
            // International DashScope OpenAI-compatible endpoint (Qwen / "Gwen").
            endpointUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions",
        )

        /**
         * Ordered list of all preset models.
         * [CUSTOM] is always last.
         */
        val ALL: List<LlmModel> = listOf(

            // ── Google Gemini (working for new AQ. free-tier keys first) ───────
            gemini("Gemini 3.6 Flash", "gemini-3.6-flash"),
            gemini("Gemini 3.5 Flash", "gemini-3.5-flash"),
            gemini("Gemini 3.5 Flash Lite", "gemini-3.5-flash-lite"),
            gemini("Gemini 3.1 Flash Lite", "gemini-3.1-flash-lite"),
            gemini("Gemini Flash (latest)", "gemini-flash-latest"),
            gemini("Gemini Flash Lite (latest)", "gemini-flash-lite-latest"),
            gemini("Gemini 3 Flash Preview", "gemini-3-flash-preview"),
            gemini("Gemini 3 Pro Preview", "gemini-3-pro-preview"),
            gemini("Gemini 3.1 Pro Preview", "gemini-3.1-pro-preview"),
            gemini("Gemini Pro (latest)", "gemini-pro-latest"),
            gemini("Gemini 2.0 Flash", "gemini-2.0-flash"),
            gemini("Gemini 2.0 Flash Lite", "gemini-2.0-flash-lite"),
            // Legacy — often 404 / free-tier quota 0 for new AI Studio keys:
            gemini("Gemini 2.5 Flash (legacy)", "gemini-2.5-flash"),
            gemini("Gemini 2.5 Pro (legacy)", "gemini-2.5-pro"),
            gemini("Gemini 2.5 Flash Lite (legacy)", "gemini-2.5-flash-lite"),

            // ── Qwen (Alibaba DashScope; often written “Gwen”) ────────────────
            qwen("Qwen Plus", "qwen-plus"),
            qwen("Qwen Max", "qwen-max"),
            qwen("Qwen Turbo", "qwen-turbo"),
            qwen("Qwen 2.5 Coder", "qwen2.5-coder-32b-instruct"),
            qwen("Qwen 3 Coder", "qwen3-coder-plus"),

            // ── Anthropic ──────────────────────────────────────────────────────
            LlmModel(
                "Claude Opus 4",
                LlmProvider.ANTHROPIC, "claude-opus-4-0",
                "https://api.anthropic.com/v1/messages",
            ),
            LlmModel(
                "Claude Sonnet 4.5",
                LlmProvider.ANTHROPIC, "claude-sonnet-4-5",
                "https://api.anthropic.com/v1/messages",
            ),
            LlmModel(
                "Claude Sonnet 3.5",
                LlmProvider.ANTHROPIC, "claude-3-5-sonnet-20241022",
                "https://api.anthropic.com/v1/messages",
            ),

            // ── OpenAI ────────────────────────────────────────────────────────
            LlmModel(
                "GPT-4o",
                LlmProvider.OPENAI, "gpt-4o",
                "https://api.openai.com/v1/chat/completions",
            ),
            LlmModel(
                "GPT-4.1",
                LlmProvider.OPENAI, "gpt-4.1",
                "https://api.openai.com/v1/chat/completions",
            ),
            LlmModel(
                "o3",
                LlmProvider.OPENAI, "o3",
                "https://api.openai.com/v1/chat/completions",
            ),
            LlmModel(
                "o4-mini",
                LlmProvider.OPENAI, "o4-mini",
                "https://api.openai.com/v1/chat/completions",
            ),

            // ── Moonshot / Kimi ───────────────────────────────────────────────
            LlmModel(
                "Kimi K2 Code",
                LlmProvider.OPENAI_COMPATIBLE, "kimi-k2",
                "https://api.moonshot.cn/v1/chat/completions",
            ),

            // ── Custom ────────────────────────────────────────────────────────
            CUSTOM,
        )

        /** Old saved display names → current replacements. */
        private val MIGRATIONS: Map<String, String> = mapOf(
            "Gemini 2.5 Pro" to "Gemini 3.5 Flash",
            "Gemini 2.5 Flash" to "Gemini 3.5 Flash",
            "Gemini 2.0 Flash" to "Gemini 3.5 Flash",
        )

        fun normalizeDisplayName(name: String): String =
            MIGRATIONS[name] ?: name

        fun findByDisplayName(name: String): LlmModel {
            val normalized = normalizeDisplayName(name)
            return ALL.firstOrNull { it.displayName == normalized }
                ?: ALL.firstOrNull { it.displayName == name }
                ?: ALL.first { it.displayName == "Gemini 3.5 Flash" }
        }
    }
}
