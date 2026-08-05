package com.kcodereview.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.kcodereview.ai.LlmModel
import com.kcodereview.ai.LlmProvider

@Service(Service.Level.APP)
@State(name = "KCodeReviewSettings", storages = [Storage("kCodeReview.xml")])
class KCodeReviewSettings : PersistentStateComponent<KCodeReviewSettings.State> {

    data class State(
        /** Display name of the selected model (key into [LlmModel.ALL]). */
        var selectedModelName: String = DEFAULT_MODEL_NAME,

        // ── Custom-model fields (used only when selectedModelName == LlmModel.CUSTOM.displayName)
        var customUrl: String = "",
        var customModelId: String = "",
        /** Serialised name of the [LlmProvider] enum — the wire format for the custom model. */
        var customProviderFormat: String = LlmProvider.OPENAI.name,

        /** GitHub (or raw) URL for the system prompt file (optional). */
        var promptFileUrl: String = "",

        /** Optional HTTP endpoint that receives review log JSON (POST). */
        var logApiUrl: String = "",

        /**
         * LLM API key backup in IDE config (not project VCS).
         * PasswordSafe is preferred; this survives when PasswordSafe is memory-only / unavailable.
         */
        var storedApiKey: String = "",

        var maxFilesPerReview: Int = 8,
        var maxCharsPerFile: Int = 12_000,
        var includePatchContext: Boolean = true,
        var preCommitReviewEnabled: Boolean = true,
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state = state
        // Migrate legacy Gemini 2.5* selections that fail on new AQ. free-tier keys.
        this.state.selectedModelName = LlmModel.normalizeDisplayName(this.state.selectedModelName)
        // Old defaults made reviews hang on large files — migrate once.
        if (this.state.maxCharsPerFile == 40_000) {
            this.state.maxCharsPerFile = 12_000
        }
        if (this.state.maxFilesPerReview == 20) {
            this.state.maxFilesPerReview = 8
        }
        // Drop accidental placeholder persisted by older builds.
        if (this.state.storedApiKey.trim() == ApiKeyFieldState.SAVED_MASK) {
            this.state.storedApiKey = ""
        }
    }

    // -------------------------------------------------------------------------
    // Persisted properties
    // -------------------------------------------------------------------------

    var selectedModelName: String
        get() = LlmModel.normalizeDisplayName(state.selectedModelName)
        set(value) { state.selectedModelName = LlmModel.normalizeDisplayName(value) }

    var customUrl: String
        get() = state.customUrl
        set(value) { state.customUrl = value.trim() }

    var customModelId: String
        get() = state.customModelId
        set(value) { state.customModelId = value.trim() }

    var customProviderFormat: LlmProvider
        get() = LlmProvider.fromName(state.customProviderFormat)
        set(value) { state.customProviderFormat = value.name }

    var promptFileUrl: String
        get() = state.promptFileUrl
        set(value) { state.promptFileUrl = value.trim() }

    var logApiUrl: String
        get() = state.logApiUrl
        set(value) { state.logApiUrl = value.trim() }

    var maxFilesPerReview: Int
        get() = state.maxFilesPerReview.coerceIn(1, 100)
        set(value) { state.maxFilesPerReview = value.coerceIn(1, 100) }

    var maxCharsPerFile: Int
        get() = state.maxCharsPerFile.coerceIn(2_000, 200_000)
        set(value) { state.maxCharsPerFile = value.coerceIn(2_000, 200_000) }

    var includePatchContext: Boolean
        get() = state.includePatchContext
        set(value) { state.includePatchContext = value }

    var preCommitReviewEnabled: Boolean
        get() = state.preCommitReviewEnabled
        set(value) { state.preCommitReviewEnabled = value }

    // -------------------------------------------------------------------------
    // API key — PasswordSafe + durable IDE-config backup
    // -------------------------------------------------------------------------

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    fun getApiKey(): String {
        val fromSafe = readPasswordSafe()
        if (fromSafe.isNotBlank()) {
            if (state.storedApiKey != fromSafe) {
                state.storedApiKey = fromSafe
            }
            return fromSafe
        }
        return cleanKey(state.storedApiKey)
    }

    fun setApiKey(apiKey: String) {
        val cleaned = cleanKey(apiKey)
        if (cleaned == ApiKeyFieldState.SAVED_MASK) return

        state.storedApiKey = cleaned
        writePasswordSafe(cleaned)
    }

    private fun readPasswordSafe(): String =
        runCatching {
            PasswordSafe.instance.getPassword(credentialAttributes()).orEmpty()
        }.onFailure {
            log.warn("PasswordSafe get failed: ${it.message}")
        }.getOrDefault("").let(::cleanKey)

    private fun writePasswordSafe(cleaned: String) {
        runCatching {
            val creds = if (cleaned.isBlank()) null else Credentials("k-code-review", cleaned)
            PasswordSafe.instance.set(credentialAttributes(), creds)
        }.onFailure {
            log.warn("PasswordSafe set failed (using IDE config backup): ${it.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Remote prompt (fetched from URL, cached 5 min)
    // -------------------------------------------------------------------------

    fun getCustomPrompt(): String {
        if (promptFileUrl.isBlank()) return ""
        // Soft fetch: never block review on remote prompt failure (5s timeout + negative cache).
        return RemoteConfigFetcher.fetchOrEmpty(promptFileUrl)
    }

    companion object {
        const val DEFAULT_MODEL_NAME = "Gemini 3.5 Flash"

        private val log = Logger.getInstance(KCodeReviewSettings::class.java)

        fun getInstance(): KCodeReviewSettings = service()

        private fun credentialAttributes(): CredentialAttributes =
            CredentialAttributes(generateServiceName("K Code Review", "LLM API Key"))

        fun cleanKey(raw: String): String =
            raw.trim().replace(Regex("\\s+"), "")
    }
}
