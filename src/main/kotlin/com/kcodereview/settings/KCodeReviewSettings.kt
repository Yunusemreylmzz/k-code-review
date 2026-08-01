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
import com.intellij.util.SlowOperations

@Service(Service.Level.APP)
@State(name = "KCodeReviewSettings", storages = [Storage("kCodeReview.xml")])
class KCodeReviewSettings : PersistentStateComponent<KCodeReviewSettings.State> {

    data class State(
        var geminiModel: String = DEFAULT_MODEL,
        var customPrompt: String = "",
        var maxFilesPerReview: Int = 20,
        var maxCharsPerFile: Int = 40_000,
        var includePatchContext: Boolean = true,
        var preCommitReviewEnabled: Boolean = true,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var geminiModel: String
        get() = state.geminiModel.ifBlank { DEFAULT_MODEL }
        set(value) {
            state.geminiModel = value.ifBlank { DEFAULT_MODEL }
        }

    var customPrompt: String
        get() = state.customPrompt
        set(value) {
            state.customPrompt = value
        }

    var maxFilesPerReview: Int
        get() = state.maxFilesPerReview.coerceIn(1, 100)
        set(value) {
            state.maxFilesPerReview = value.coerceIn(1, 100)
        }

    var maxCharsPerFile: Int
        get() = state.maxCharsPerFile.coerceIn(2_000, 200_000)
        set(value) {
            state.maxCharsPerFile = value.coerceIn(2_000, 200_000)
        }

    var includePatchContext: Boolean
        get() = state.includePatchContext
        set(value) {
            state.includePatchContext = value
        }

    var preCommitReviewEnabled: Boolean
        get() = state.preCommitReviewEnabled
        set(value) {
            state.preCommitReviewEnabled = value
        }

    fun getApiKey(): String = SlowOperations.knownIssue("KCR-1").use {
        PasswordSafe.instance.getPassword(credentialAttributes()).orEmpty()
    }

    fun setApiKey(apiKey: String) {
        SlowOperations.knownIssue("KCR-1").use {
            val credentials = if (apiKey.isBlank()) null else Credentials("gemini", apiKey.trim())
            PasswordSafe.instance.set(credentialAttributes(), credentials)
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.0-flash"

        fun getInstance(): KCodeReviewSettings = service()

        private fun credentialAttributes(): CredentialAttributes =
            CredentialAttributes(generateServiceName("K Code Review", "Gemini API Key"))
    }
}
