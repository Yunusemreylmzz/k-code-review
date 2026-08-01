package com.kcodereview.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class KCodeReviewConfigurable : Configurable {

    private var panel: JPanel? = null
    private val apiKeyField = JBPasswordField()
    private val modelField = JBTextField()
    private val promptArea = JBTextArea(12, 60).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val maxFilesSpinner = JSpinner(SpinnerNumberModel(20, 1, 100, 1))
    private val maxCharsSpinner = JSpinner(SpinnerNumberModel(40_000, 2_000, 200_000, 1_000))
    private val includePatchCheck = JBCheckBox("Include git patch context in the AI prompt")
    private val preCommitCheck = JBCheckBox(
        "Pre-commit review: block once on any finding; second Commit click proceeds",
    )

    override fun getDisplayName(): String = "K Code Review"

    override fun createComponent(): JComponent {
        apiKeyField.emptyText.text = "Stored securely in PasswordSafe"
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Gemini API key:"), apiKeyField, 1, false)
            .addLabeledComponent(JBLabel("Gemini model:"), modelField, 1, false)
            .addLabeledComponent(JBLabel("Max files / review:"), maxFilesSpinner, 1, false)
            .addLabeledComponent(JBLabel("Max chars / file:"), maxCharsSpinner, 1, false)
            .addComponent(includePatchCheck, 1)
            .addComponent(preCommitCheck, 1)
            .addLabeledComponent(
                JBLabel("Custom review prompt (optional, overrides default):"),
                JBScrollPane(promptArea),
                1,
                true,
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .also { it.border = JBUI.Borders.empty(10) }
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = KCodeReviewSettings.getInstance()
        val currentKey = String(apiKeyField.password)
        // Avoid PasswordSafe (slow) on EDT unless the user typed a new key.
        val keyChanged = currentKey.isNotEmpty()
        return keyChanged ||
            modelField.text != settings.geminiModel ||
            promptArea.text != settings.customPrompt ||
            (maxFilesSpinner.value as Int) != settings.maxFilesPerReview ||
            (maxCharsSpinner.value as Int) != settings.maxCharsPerFile ||
            includePatchCheck.isSelected != settings.includePatchContext ||
            preCommitCheck.isSelected != settings.preCommitReviewEnabled
    }

    override fun apply() {
        val settings = KCodeReviewSettings.getInstance()
        val typedKey = String(apiKeyField.password).trim()
        if (typedKey.isNotEmpty()) {
            settings.setApiKey(typedKey)
            apiKeyField.text = ""
        }
        settings.geminiModel = modelField.text.trim()
        settings.customPrompt = promptArea.text
        settings.maxFilesPerReview = maxFilesSpinner.value as Int
        settings.maxCharsPerFile = maxCharsSpinner.value as Int
        settings.includePatchContext = includePatchCheck.isSelected
        settings.preCommitReviewEnabled = preCommitCheck.isSelected
    }

    override fun reset() {
        val settings = KCodeReviewSettings.getInstance()
        apiKeyField.text = ""
        modelField.text = settings.geminiModel
        promptArea.text = settings.customPrompt
        maxFilesSpinner.value = settings.maxFilesPerReview
        maxCharsSpinner.value = settings.maxCharsPerFile
        includePatchCheck.isSelected = settings.includePatchContext
        preCommitCheck.isSelected = settings.preCommitReviewEnabled
    }

    override fun disposeUIResources() {
        panel = null
    }
}
