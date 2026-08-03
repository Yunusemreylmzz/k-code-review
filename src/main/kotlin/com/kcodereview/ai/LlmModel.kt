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

        /**
         * Ordered list of all preset models.
         * [CUSTOM] is always last.
         */
        val ALL: List<LlmModel> = listOf(

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

            // ── Google Gemini ─────────────────────────────────────────────────
            LlmModel(
                "Gemini 2.5 Pro",
                LlmProvider.GEMINI, "gemini-2.5-pro",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent",
            ),
            LlmModel(
                "Gemini 2.5 Flash",
                LlmProvider.GEMINI, "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            ),
            LlmModel(
                "Gemini 2.0 Flash",
                LlmProvider.GEMINI, "gemini-2.0-flash",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
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

        fun findByDisplayName(name: String): LlmModel =
            ALL.firstOrNull { it.displayName == name } ?: CUSTOM
    }
}
