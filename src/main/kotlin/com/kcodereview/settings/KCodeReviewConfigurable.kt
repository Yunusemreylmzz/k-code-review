package com.kcodereview.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ui.JBColor
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.kcodereview.ai.LlmModel
import com.kcodereview.ai.LlmProvider
import com.kcodereview.log.ReviewLogPayloadBuilder
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Settings page: IDE → Settings → Tools → K Code Review
 */
class KCodeReviewConfigurable : Configurable {

    private var panel: JPanel? = null
    private var hasStoredApiKey: Boolean = false

    private val modelBox = JComboBox(
        DefaultComboBoxModel(LlmModel.ALL.toTypedArray()),
    )
    private val apiKeyField = JBPasswordField()
    private val promptUrlField = JBTextField()
    private val logApiUrlField = JBTextField()
    private val logExampleArea = JBTextArea().apply {
        isEditable = false
        isOpaque = true
        lineWrap = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        rows = 16
        caret.isVisible = false
        caret.isSelectionVisible = true
        // Guidance only — never treat as an input field.
        toolTipText = "Read-only example of the JSON POSTed to Log API URL"
    }
    private val logExampleScroll = JBScrollPane(logExampleArea).apply {
        preferredSize = Dimension(520, 240)
        minimumSize = Dimension(320, 160)
    }
    private val copyExampleButton = JButton("Copy", AllIcons.Actions.Copy).apply {
        toolTipText = "Copy example request body"
        isFocusable = false
        addActionListener { copyExampleBody() }
    }
    private val logHintLabel = JBLabel(
        "Optional: POST slim review JSON to your API (git user, repo, severity counts).",
    ).apply {
        foreground = JBColor.GRAY
    }

    private val maxFilesSpinner = JSpinner(SpinnerNumberModel(8, 1, 100, 1))
    private val maxCharsSpinner = JSpinner(SpinnerNumberModel(12_000, 2_000, 200_000, 1_000))
    private val includePatchCheck = JBCheckBox("Prefer git diff in the AI prompt (faster, commit-scoped)")
    private val preCommitCheck = JBCheckBox(
        "Pre-commit review: block once on any finding; second Commit click proceeds",
    )
    private val testStatusLabel = JBLabel(" ").apply { foreground = Color(0x888888) }
    private val testButton = JButton("Test Connection").apply {
        toolTipText =
            "Tests LLM API key, prompt file URL (if set), and Log API URL (if set)."
        addActionListener { runConnectionTest() }
    }

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

    private lateinit var customSection: JPanel

    private val inactiveFg = JBColor(Color(0x9E9E9E), Color(0x777777))
    private val inactiveBg = JBColor(Color(0xF5F5F5), Color(0x3C3F41))
    private val activeFg = JBColor(Color(0x212121), Color(0xE8E8E8))
    private val activeBg = JBColor(Color(0xFFFFFF), Color(0x2B2D30))
    private val inactiveBorder = JBColor(Color(0xD0D0D0), Color(0x555555))
    private val activeBorder = JBColor(Color(0x1976D2), Color(0x64B5F6))
    private val guideFg = JBColor(Color(0x616161), Color(0x9E9E9E))
    private val guideBg = JBColor(Color(0xFAFAFA), Color(0x2B2D30))

    override fun getDisplayName(): String = "K Code Review"

    override fun createComponent(): JComponent {
        customUrlField.emptyText.text = "https://api.example.com/v1/chat/completions"
        customModelIdField.emptyText.text = "e.g. llama3, mistral, my-model"
        promptUrlField.emptyText.text =
            "https://github.com/user/repo/blob/main/prompt.txt  (optional)"
        logApiUrlField.emptyText.text =
            "https://api.example.com/v1/code-review/logs  (optional)"

        refreshExampleBody()

        modelBox.addActionListener { refreshCustomVisibility() }
        logApiUrlField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = refreshLogFieldsStyle()
            override fun removeUpdate(e: DocumentEvent?) = refreshLogFieldsStyle()
            override fun changedUpdate(e: DocumentEvent?) = refreshLogFieldsStyle()
        })

        val customSectionBuilder = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Custom Model Settings"), 1)
            .addLabeledComponent(JBLabel("Endpoint URL:"), customUrlField, 1, false)
            .addLabeledComponent(JBLabel("Model ID:"), customModelIdField, 1, false)
            .addLabeledComponent(JBLabel("API format:"), customFormatBox, 1, false)
        customSection = customSectionBuilder.panel

        val exampleHeader = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(4)
            add(JBLabel("Example request body (read-only guidance):"), BorderLayout.WEST)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                    isOpaque = false
                    add(copyExampleButton)
                },
                BorderLayout.EAST,
            )
        }

        val metricLogsContent = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Log API URL (optional):"), logApiUrlField, 1, false)
            .addComponent(logHintLabel, 1)
            .addComponent(exampleHeader, 1)
            .addComponent(logExampleScroll, 1)
            .panel
            .also { p ->
                // Collapsed by default: keep it in layout but invisible.
                p.isVisible = false
                p.border = JBUI.Borders.empty(2, 0, 0, 0)
            }

        var metricLogsExpanded = false
        val metricLogsToggle = JButton("Metric & Logs").apply {
            isFocusable = false
            foreground = JBColor.GRAY
            isOpaque = false
            border = JBUI.Borders.empty(6, 0)
            icon = AllIcons.Actions.Expandall
            addActionListener {
                metricLogsExpanded = !metricLogsExpanded
                metricLogsContent.isVisible = metricLogsExpanded
                icon = if (metricLogsExpanded) AllIcons.Actions.Collapseall else AllIcons.Actions.Expandall
                panel?.revalidate()
                panel?.repaint()
            }
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Model:"), modelBox, 1, false)
            .addLabeledComponent(JBLabel("LLM API key:"), apiKeyField, 1, false)
            .addComponent(customSection, 1)
            .addLabeledComponent(JBLabel("Prompt file URL (optional):"), promptUrlField, 1, false)
            .addComponent(metricLogsToggle, 1)
            .addComponent(metricLogsContent, 1)
            .addLabeledComponent(JBLabel("Max files / review:"), maxFilesSpinner, 1, false)
            .addLabeledComponent(JBLabel("Max chars / file:"), maxCharsSpinner, 1, false)
            .addComponent(includePatchCheck, 1)
            .addComponent(preCommitCheck, 1)
            .addComponent(testButton, 1)
            .addComponent(testStatusLabel, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .also { it.border = JBUI.Borders.empty(10) }

        refreshLogFieldsStyle()
        // IDE usually calls reset() after createComponent; load key state eagerly anyway.
        hasStoredApiKey = KCodeReviewSettings.getInstance().hasApiKey()
        refreshApiKeyField()
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = KCodeReviewSettings.getInstance()
        val typedKey = String(apiKeyField.password)
        return (modelBox.selectedItem as? LlmModel)?.displayName != s.selectedModelName ||
            ApiKeyFieldState.isModified(typedKey) ||
            customUrlField.text.trim() != s.customUrl ||
            customModelIdField.text.trim() != s.customModelId ||
            customFormatBox.selectedItem != s.customProviderFormat ||
            promptUrlField.text.trim() != s.promptFileUrl ||
            logApiUrlField.text.trim() != s.logApiUrl ||
            (maxFilesSpinner.value as Int) != s.maxFilesPerReview ||
            (maxCharsSpinner.value as Int) != s.maxCharsPerFile ||
            includePatchCheck.isSelected != s.includePatchContext ||
            preCommitCheck.isSelected != s.preCommitReviewEnabled
    }

    override fun apply() {
        val s = KCodeReviewSettings.getInstance()

        val typedKey = String(apiKeyField.password)
        if (ApiKeyFieldState.shouldPersist(typedKey)) {
            s.setApiKey(typedKey)
        }
        hasStoredApiKey = s.hasApiKey()
        refreshApiKeyField()

        val prevPromptUrl = s.promptFileUrl
        s.selectedModelName = (modelBox.selectedItem as LlmModel).displayName
        s.customUrl = customUrlField.text.trim()
        s.customModelId = customModelIdField.text.trim()
        s.customProviderFormat = customFormatBox.selectedItem as LlmProvider
        s.promptFileUrl = promptUrlField.text.trim()
        s.logApiUrl = logApiUrlField.text.trim()
        s.maxFilesPerReview = maxFilesSpinner.value as Int
        s.maxCharsPerFile = maxCharsSpinner.value as Int
        s.includePatchContext = includePatchCheck.isSelected
        s.preCommitReviewEnabled = preCommitCheck.isSelected

        if (s.promptFileUrl != prevPromptUrl) RemoteConfigFetcher.invalidate(prevPromptUrl)
        refreshExampleBody()
        refreshLogFieldsStyle()
    }

    override fun reset() {
        val s = KCodeReviewSettings.getInstance()
        hasStoredApiKey = s.hasApiKey()
        modelBox.selectedItem = LlmModel.findByDisplayName(s.selectedModelName)
        refreshApiKeyField()
        customUrlField.text = s.customUrl
        customModelIdField.text = s.customModelId
        customFormatBox.selectedItem = s.customProviderFormat
        promptUrlField.text = s.promptFileUrl
        logApiUrlField.text = s.logApiUrl
        maxFilesSpinner.value = s.maxFilesPerReview
        maxCharsSpinner.value = s.maxCharsPerFile
        includePatchCheck.isSelected = s.includePatchContext
        preCommitCheck.isSelected = s.preCommitReviewEnabled
        refreshExampleBody()
        refreshCustomVisibility()
        refreshLogFieldsStyle()
        setTestStatus(
            if (hasStoredApiKey) "API key is saved." else " ",
            if (hasStoredApiKey) Color(0x2E7D32) else Color(0x888888),
        )
    }

    override fun disposeUIResources() { panel = null }

    private fun refreshApiKeyField() {
        apiKeyField.text = ""
        apiKeyField.emptyText.text = ApiKeyFieldState.emptyHint(hasStoredApiKey)
    }

    private fun refreshExampleBody() {
        logExampleArea.text = ReviewLogPayloadBuilder.exampleTemplateJson()
        logExampleArea.caretPosition = 0
        logExampleArea.isEditable = false
        logExampleArea.caret.isVisible = false
    }

    private fun copyExampleBody() {
        val text = logExampleArea.text.ifBlank { ReviewLogPayloadBuilder.exampleTemplateJson() }
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        val previous = copyExampleButton.text
        copyExampleButton.text = "Copied"
        copyExampleButton.icon = AllIcons.Actions.Checked
        javax.swing.Timer(1500) {
            copyExampleButton.text = previous
            copyExampleButton.icon = AllIcons.Actions.Copy
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun refreshCustomVisibility() {
        val isCustom = (modelBox.selectedItem as? LlmModel)?.isCustom == true
        customSection.isVisible = isCustom
        panel?.revalidate()
        panel?.repaint()
    }

    /** Gray when empty; active border/colors when a log URL is present. Example stays guide-styled. */
    private fun refreshLogFieldsStyle() {
        val active = logApiUrlField.text.trim().isNotEmpty()
        val fg = if (active) activeFg else inactiveFg
        val bg = if (active) activeBg else inactiveBg
        val borderColor = if (active) activeBorder else inactiveBorder

        logApiUrlField.foreground = fg
        logApiUrlField.background = bg
        logApiUrlField.border = JBUI.Borders.compound(
            LineBorder(borderColor, if (active) 2 else 1),
            JBUI.Borders.empty(4, 6),
        )

        // Always guidance look — never looks like a writable form field.
        logExampleArea.isEditable = false
        logExampleArea.foreground = guideFg
        logExampleArea.background = guideBg
        logExampleScroll.border = LineBorder(inactiveBorder, 1)
        logHintLabel.foreground = if (active) activeBorder else JBColor.GRAY
        logHintLabel.text = if (active) {
            "Log API active — each review POSTs this slim JSON to the URL (fire-and-forget)."
        } else {
            "Optional: POST slim review JSON to your API (git user, repo, severity counts)."
        }
    }

    private fun runConnectionTest() {
        val keyTyped = String(apiKeyField.password)
        val promptUrl = promptUrlField.text.trim()
        val logUrl = logApiUrlField.text.trim()
        val selectedModel = (modelBox.selectedItem as? LlmModel)?.displayName
            ?: KCodeReviewSettings.DEFAULT_MODEL_NAME

        setTestStatus("Testing…", Color(0x888888))
        testButton.isEnabled = false

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(null, "K Code Review: testing connection…", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    val settings = KCodeReviewSettings.getInstance()
                    val result = runCatching {
                        if (ApiKeyFieldState.shouldPersist(keyTyped)) {
                            settings.setApiKey(keyTyped)
                        }
                        val apiKey = settings.getApiKey()
                        val checks = SettingsConnectionTester.run(
                            request = SettingsConnectionTester.Request(
                                apiKey = apiKey,
                                selectedModelName = selectedModel,
                                customUrl = customUrlField.text.trim(),
                                customModelId = customModelIdField.text.trim(),
                                customProviderFormat = customFormatBox.selectedItem as LlmProvider,
                                promptFileUrl = promptUrl,
                                logApiUrl = logUrl,
                            ),
                            settings = settings,
                            onProgress = { indicator.text = it },
                        )
                        val failed = checks.firstOrNull { !it.ok }
                        if (failed != null) {
                            error(SettingsConnectionTester.formatFailure(failed))
                        }
                        SettingsConnectionTester.formatSuccess(checks)
                    }
                    SwingUtilities.invokeLater {
                        testButton.isEnabled = true
                        hasStoredApiKey = settings.hasApiKey()
                        refreshApiKeyField()
                        if (result.isSuccess) {
                            setTestStatus(result.getOrThrow(), Color(0x2E7D32))
                        } else {
                            setTestStatus(
                                result.exceptionOrNull()?.message ?: "❌ Unknown error",
                                Color(0xC62828),
                            )
                        }
                    }
                }
            },
        )
    }

    private fun setTestStatus(text: String, color: Color) {
        testStatusLabel.text = text
        testStatusLabel.foreground = color
    }
}
