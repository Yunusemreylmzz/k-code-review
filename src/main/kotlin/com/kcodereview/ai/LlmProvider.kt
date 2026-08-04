package com.kcodereview.ai

/**
 * Supported LLM providers.
 * Each entry carries its display name, default endpoint URL, and a sensible default model name.
 */
enum class LlmProvider(
    val displayName: String,
    val defaultUrl: String,
    val defaultModel: String,
) {
    GEMINI(
        displayName  = "Google Gemini",
        defaultUrl   = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
        defaultModel = "gemini-3.5-flash",
    ),
    OPENAI(
        displayName  = "OpenAI",
        defaultUrl   = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o",
    ),
    ANTHROPIC(
        displayName  = "Anthropic Claude",
        defaultUrl   = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-3-5-sonnet-20241022",
    ),
    OPENAI_COMPATIBLE(
        displayName  = "OpenAI-Compatible (Ollama, Azure…)",
        defaultUrl   = "http://localhost:11434/v1/chat/completions",
        defaultModel = "llama3",
    ),
    ;

    companion object {
        fun fromName(name: String): LlmProvider =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: GEMINI
    }
}
