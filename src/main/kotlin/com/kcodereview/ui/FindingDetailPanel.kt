package com.kcodereview.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.kcodereview.model.Finding
import com.kcodereview.model.Severity
import com.kcodereview.service.ApplyFixService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants

/**
 * RIGHT column — Why / How to fix (stacked steps) / Suggested Fix + Apply.
 * Uses a single vertical scroll column so content never collapses to zero height.
 */
class FindingDetailPanel(
    private val project: Project? = null,
) : JPanel(BorderLayout()) {

    private var currentFinding: Finding? = null

    private val titleLabel = JBLabel("Select a warning on the left").apply {
        font = font.deriveFont(Font.BOLD, 14f)
    }
    private val metaLabel = JBLabel(" ").apply {
        foreground = JBColor.GRAY
    }
    private val explanationArea = JBTextArea(4, 20).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = JBColor.namedColor("TextArea.background", JBColor.background())
        border = JBUI.Borders.empty(8)
        font = font.deriveFont(12f)
    }
    private val stepsContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
    }
    private val codeArea = JBTextArea(8, 20).apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        background = JBColor(Color(0x2B2D30), Color(0x2B2D30))
        foreground = JBColor(Color(0xA9B7C6), Color(0xA9B7C6))
        border = JBUI.Borders.empty(8)
        lineWrap = false
    }
    private val applyButton = JButton("Apply", AllIcons.Actions.Execute).apply {
        toolTipText = "Replace the finding line with the suggested fix in the source file"
        isEnabled = false
    }
    private val suggestedPanel = JPanel(BorderLayout(0, 6)).apply {
        isOpaque = false
        isVisible = false
        border = JBUI.Borders.emptyTop(12)
    }

    private val contentColumn = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(4)
    }

    init {
        applyButton.addActionListener {
            val finding = currentFinding ?: return@addActionListener
            val proj = project ?: return@addActionListener
            if (ApplyFixService.apply(proj, finding)) {
                applyButton.text = "Applied"
                applyButton.isEnabled = false
            }
        }

        border = JBUI.Borders.empty(8)
        background = JBColor.background()
        isOpaque = true
        minimumSize = Dimension(320, 240)
        preferredSize = Dimension(520, 420)

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 0, 10, 0)
            add(titleLabel, BorderLayout.NORTH)
            add(metaLabel, BorderLayout.SOUTH)
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 64)
        }

        val whyTitle = sectionTitle("Why is this an issue?", AllIcons.General.BalloonInformation)
        val whyScroll = JBScrollPane(explanationArea).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1)
            alignmentX = LEFT_ALIGNMENT
            preferredSize = Dimension(480, 100)
            maximumSize = Dimension(Int.MAX_VALUE, 160)
        }

        val howTitle = sectionTitle("How to fix", AllIcons.Actions.IntentionBulb)
        val stepsScroll = JBScrollPane(stepsContainer).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1)
            alignmentX = LEFT_ALIGNMENT
            preferredSize = Dimension(480, 140)
            maximumSize = Dimension(Int.MAX_VALUE, 220)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        val suggestedHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JLabel("Suggested Fix", AllIcons.Actions.Edit, SwingConstants.LEFT).apply {
                font = font.deriveFont(Font.BOLD, 12f)
            }, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
                add(applyButton)
            }, BorderLayout.EAST)
        }
        suggestedPanel.add(suggestedHeader, BorderLayout.NORTH)
        suggestedPanel.add(JBScrollPane(codeArea).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1)
            preferredSize = Dimension(480, 160)
        }, BorderLayout.CENTER)
        suggestedPanel.alignmentX = LEFT_ALIGNMENT
        suggestedPanel.maximumSize = Dimension(Int.MAX_VALUE, 260)

        contentColumn.add(header)
        contentColumn.add(whyTitle)
        contentColumn.add(Box.createVerticalStrut(4))
        contentColumn.add(whyScroll)
        contentColumn.add(Box.createVerticalStrut(12))
        contentColumn.add(howTitle)
        contentColumn.add(Box.createVerticalStrut(4))
        contentColumn.add(stepsScroll)
        contentColumn.add(suggestedPanel)
        contentColumn.add(Box.createVerticalGlue())

        add(JBScrollPane(contentColumn).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)

        showFinding(null)
    }

    fun showFinding(finding: Finding?) {
        currentFinding = finding
        applyButton.text = "Apply"
        if (finding == null) {
            titleLabel.icon = null
            titleLabel.text = "Select a warning on the left"
            metaLabel.text = " "
            explanationArea.text = ""
            stepsContainer.removeAll()
            suggestedPanel.isVisible = false
            applyButton.isEnabled = false
            refreshUi()
            return
        }

        val content = FindingDetailContent.from(finding)
        titleLabel.icon = CommitBlockedDialog.severityIcon(finding.severity)
        titleLabel.text = " ${content.title}"
        val (_, fg) = CommitBlockedDialog.chipColors(finding.severity)
        metaLabel.text = "${content.priority}  ·  ${finding.category.displayName}  ·  ${content.location}" +
            (finding.ruleKey?.let { "  ·  $it" } ?: "")
        metaLabel.foreground = fg

        explanationArea.text = content.explanation
        renderSteps(content.howToFixSteps)

        val hasFix = content.fixedCode.isNotBlank() &&
            content.fixedCode != "// No fixed-code sample for this finding."
        suggestedPanel.isVisible = hasFix
        if (hasFix) {
            codeArea.text = content.fixedCode
            applyButton.isEnabled = project != null
        } else {
            codeArea.text = ""
            applyButton.isEnabled = false
        }
        refreshUi()
    }

    fun showEmptyReviewSummaries(text: String) {
        currentFinding = null
        titleLabel.icon = AllIcons.General.InspectionsOK
        titleLabel.text = " No warnings"
        metaLabel.text = " "
        explanationArea.text = text
        stepsContainer.removeAll()
        suggestedPanel.isVisible = false
        applyButton.isEnabled = false
        refreshUi()
    }

    private fun refreshUi() {
        stepsContainer.revalidate()
        contentColumn.revalidate()
        revalidate()
        repaint()
    }

    private fun renderSteps(steps: List<String>) {
        stepsContainer.removeAll()
        if (steps.isEmpty()) {
            stepsContainer.add(JBLabel("  No remediation steps provided.").apply {
                foreground = JBColor.GRAY
                border = JBUI.Borders.empty(8)
            })
            return
        }
        steps.forEachIndexed { index, step ->
            stepsContainer.add(stepRow(index + 1, step))
            stepsContainer.add(Box.createVerticalStrut(8))
        }
        stepsContainer.add(Box.createVerticalGlue())
    }

    private fun stepRow(number: Int, text: String): JComponent {
        val (bg, fg) = CommitBlockedDialog.chipColors(Severity.MINOR)
        val badge = JLabel("$number").apply {
            horizontalAlignment = SwingConstants.CENTER
            preferredSize = Dimension(24, 24)
            minimumSize = Dimension(24, 24)
            isOpaque = true
            background = bg
            foreground = fg
            font = font.deriveFont(Font.BOLD, 11f)
            border = BorderFactory.createLineBorder(fg, 1)
        }
        val label = JBLabel("<html><body style='width:320px'>${escapeHtml(text)}</body></html>").apply {
            border = JBUI.Borders.empty(2, 0)
        }
        return JPanel(GridBagLayout()).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 80)
            isOpaque = false
            border = JBUI.Borders.empty(4, 8)
            add(badge, GridBagConstraints().apply {
                gridx = 0; gridy = 0
                insets = Insets(0, 0, 0, 10)
                anchor = GridBagConstraints.NORTH
            })
            add(label, GridBagConstraints().apply {
                gridx = 1; gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
            })
        }
    }

    private fun sectionTitle(title: String, icon: javax.swing.Icon): JComponent =
        JLabel(title, icon, SwingConstants.LEFT).apply {
            font = font.deriveFont(Font.BOLD, 12f)
            alignmentX = LEFT_ALIGNMENT
            border = JBUI.Borders.empty(0, 0, 2, 0)
            maximumSize = Dimension(Int.MAX_VALUE, 24)
        }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
