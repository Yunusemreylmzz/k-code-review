package com.kcodereview.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.kcodereview.ai.LlmModel
import com.kcodereview.ai.LlmProvider
import java.awt.Color
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities

/**
 * Settings page: IDE → Settings → Tools → K Code Review
 *
 * Layout:
 *   Model          [dropdown with preset models + "Custom Model…"]
 *   LLM API key    [password field]
 *   ── shown only when "Custom Model…" is selected ──
 *   Endpoint URL   [text field]
 *   Model ID       [text field]
 *   API format     [dropdown: Gemini / OpenAI / Anthropic / OpenAI-Compatible]
 *   ─────────────────────────────────────────────────
 *   Prompt file URL (optional)
 *   Max files / review
 *   Max chars / file
 *   [checkboxes]
 *   [Test Connection]
 */
class KCodeReviewConfigurable : Configurable {

    private var panel: JPanel? = null

    // ── always-visible fields ────────────────────────────────────────────────
    private val modelBox = JComboBox(
        DefaultComboBoxModel(LlmModel.ALL.toTypedArray()),
    )
    private val apiKeyField = JBPasswordField()
    private val promptUrlField = JBTextField()
    private val maxFilesSpinner = JSpinner(SpinnerNumberModel(20, 1, 100, 1))
    private val maxCharsSpinner = JSpinner(SpinnerNumberModel(40_000, 2_000, 200_000, 1_000))
    private val includePatchCheck = JBCheckBox("Include git patch context in the AI prompt")
    private val preCommitCheck = JBCheckBox(
        "Pre-commit review: block once on any finding; second Commit click proceeds",
    )
    private val testStatusLabel = JBLabel(" ").apply { foreground = Color(0x888888) }
    private val testButton = JButton("Test Connection").apply {
        toolTipText = "Validates the API key and optional prompt URL."
        addActionListener { runConnectionTest() }
    }

    // ── custom-model-only fields ─────────────────────────────────────────────
    private val customUrlField = JBTextField()
    private val customModelIdField = JBTextField()
    private val customFormatBox = JComboBox(
        DefaultComboBoxModel(LlmProvider.entries.toTypedArray()),
    ).apply {
        renderer = javax.swing.DefaultListCellRenderer().also { r ->
            setRenderer { list, value, index, sel, focus ->
                r.getListCellRendererComponent(list, (value as LlmProvider).displayName, index, sel, focus)
            }
        }
    }
    /** Label + field pairs shown only for "Custom Model…". */
    private val customComponents: List<JComponent> = listOf(
        customUrlField, customModelIdField, customFormatBox,
    )

    // ── rows added to form programmatically ──────────────────────────────────
    private lateinit var customSection: JPanel

    // -------------------------------------------------------------------------
    // Configurable
    // -------------------------------------------------------------------------

    override fun getDisplayName(): String = "K Code Review"

    override fun createComponent(): JComponent {
        apiKeyField.emptyText.text    = "Stored securely in PasswordSafe"
        customUrlField.emptyText.text = "https://api.example.com/v1/chat/completions"
        customModelIdField.emptyText.text = "e.g. llama3, mistral, my-model"
        promptUrlField.emptyText.text =
            "https://github.com/user/repo/blob/main/prompt.txt  (optional)"

        modelBox.addActionListener { refreshCustomVisibility() }

        val customSectionBuilder = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Custom Model Settings"), 1)
            .addLabeledComponent(JBLabel("Endpoint URL:"), customUrlField, 1, false)
            .addLabeledComponent(JBLabel("Model ID:"), customModelIdField, 1, false)
            .addLabeledComponent(JBLabel("API format:"), customFormatBox, 1, false)
        customSection = customSectionBuilder.panel

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Model:"), modelBox, 1, false)
            .addLabeledComponent(JBLabel("LLM API key:"), apiKeyField, 1, false)
            .addComponent(customSection, 1)
            .addLabeledComponent(JBLabel("Prompt file URL (optional):"), promptUrlField, 1, false)
            .addLabeledComponent(JBLabel("Max files / review:"), maxFilesSpinner, 1, false)
            .addLabeledComponent(JBLabel("Max chars / file:"), maxCharsSpinner, 1, false)
            .addComponent(includePatchCheck, 1)
            .addComponent(preCommitCheck, 1)
            .addComponent(testButton, 1)
            .addComponent(testStatusLabel, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .also { it.border = JBUI.Borders.empty(10) }

        return panel!!
    }

    override fun isModified(): Boolean {
        val s = KCodeReviewSettings.getInstance()
        return (modelBox.selectedItem as? LlmModel)?.displayName != s.selectedModelName ||
            apiKeyField.password.isNotEmpty() ||
            customUrlField.text.trim() != s.customUrl ||
            customModelIdField.text.trim() != s.customModelId ||
            customFormatBox.selectedItem != s.customProviderFormat ||
            promptUrlField.text.trim() != s.promptFileUrl ||
            (maxFilesSpinner.value as Int) != s.maxFilesPerReview ||
            (maxCharsSpinner.value as Int) != s.maxCharsPerFile ||
            includePatchCheck.isSelected != s.includePatchContext ||
            preCommitCheck.isSelected != s.preCommitReviewEnabled
    }

    override fun apply() {
        val s = KCodeReviewSettings.getInstance()

        val typedKey = String(apiKeyField.password).trim()
        if (typedKey.isNotEmpty()) {
            s.setApiKey(typedKey)
            apiKeyField.text = ""
        }

        val prevPromptUrl = s.promptFileUrl
        s.selectedModelName   = (modelBox.selectedItem as LlmModel).displayName
        s.customUrl           = customUrlField.text.trim()
        s.customModelId       = customModelIdField.text.trim()
        s.customProviderFormat = customFormatBox.selectedItem as LlmProvider
        s.promptFileUrl       = promptUrlField.text.trim()
        s.maxFilesPerReview   = maxFilesSpinner.value as Int
        s.maxCharsPerFile     = maxCharsSpinner.value as Int
        s.includePatchContext  = includePatchCheck.isSelected
        s.preCommitReviewEnabled = preCommitCheck.isSelected

        if (s.promptFileUrl != prevPromptUrl) RemoteConfigFetcher.invalidate(prevPromptUrl)
    }

    override fun reset() {
        val s = KCodeReviewSettings.getInstance()
        modelBox.selectedItem         = LlmModel.findByDisplayName(s.selectedModelName)
        apiKeyField.text              = ""
        customUrlField.text           = s.customUrl
        customModelIdField.text       = s.customModelId
        customFormatBox.selectedItem  = s.customProviderFormat
        promptUrlField.text           = s.promptFileUrl
        maxFilesSpinner.value         = s.maxFilesPerReview
        maxCharsSpinner.value         = s.maxCharsPerFile
        includePatchCheck.isSelected  = s.includePatchContext
        preCommitCheck.isSelected     = s.preCommitReviewEnabled
        refreshCustomVisibility()
        setTestStatus(" ", Color(0x888888))
    }

    override fun disposeUIResources() { panel = null }

    // -------------------------------------------------------------------------
    // Custom section visibility
    // -------------------------------------------------------------------------

    private fun refreshCustomVisibility() {
        val isCustom = (modelBox.selectedItem as? LlmModel)?.isCustom == true
        customSection.isVisible = isCustom
        panel?.revalidate()
        panel?.repaint()
    }

    // -------------------------------------------------------------------------
    // Test Connection
    // -------------------------------------------------------------------------

    private fun runConnectionTest() {
        val keyTyped  = String(apiKeyField.password).trim()
        val promptUrl = promptUrlField.text.trim()

        setTestStatus("Testing…", Color(0x888888))
        testButton.isEnabled = false

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(null, "K Code Review: testing connection…", false) {
                override fun run(indicator: ProgressIndicator) {
                    val result = runCatching {
                        val key = keyTyped.ifBlank { KCodeReviewSettings.getInstance().getApiKey() }
                        require(key.isNotBlank()) { "LLM API key is empty. Enter your API key above." }
                        if (promptUrl.isNotBlank()) {
                            val prompt = RemoteConfigFetcher.fetch(promptUrl)
                            require(prompt.isNotBlank()) { "Prompt file returned an empty body." }
                        }
                        "✅ OK — API key present${if (promptUrl.isNotBlank()) ", prompt fetched" else ""}."
                    }
                    SwingUtilities.invokeLater {
                        testButton.isEnabled = true
                        if (result.isSuccess) {
                            setTestStatus(result.getOrThrow(), Color(0x2E7D32))
                        } else {
                            setTestStatus(
                                "❌ ${result.exceptionOrNull()?.message ?: "Unknown error"}",
                                Color(0xC62828),
                            )
                        }
                    }
                }
            },
        )
    }

    private fun setTestStatus(text: String, color: Color) {
        testStatusLabel.text       = text
        testStatusLabel.foreground = color
    }
}
