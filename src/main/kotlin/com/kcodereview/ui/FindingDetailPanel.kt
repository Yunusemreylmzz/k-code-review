package com.kcodereview.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.kcodereview.model.Finding
import com.kcodereview.model.Severity
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder

/**
 * RIGHT column of Code Analysis — always visible.
 */
class FindingDetailPanel : JPanel(BorderLayout()) {

    private val titleLabel = JBLabel("Select a warning on the left").apply {
        font = font.deriveFont(Font.BOLD, 16f)
    }
    private val priorityLabel = JBLabel(" ").apply {
        font = font.deriveFont(Font.BOLD, 12f)
        border = JBUI.Borders.empty(4, 10)
        isOpaque = true
    }
    private val metaLabel = JBLabel(" ").apply { foreground = JBColor.GRAY }

    private val explanationArea = textArea(wrap = true, rows = 6)
    private val howToFixArea = textArea(
        wrap = true,
        rows = 5,
        background = JBColor(Color(245, 248, 240), Color(40, 48, 40)),
    )
    private val fixedCodeArea = textArea(
        wrap = false,
        rows = 7,
        background = JBColor(Color(246, 248, 250), Color(30, 32, 36)),
        mono = true,
    )

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JBColor(Color(70, 130, 180), Color(100, 160, 210)), 2),
                "Warning details",
                TitledBorder.LEFT,
                TitledBorder.TOP,
            ),
            EmptyBorder(8, 10, 8, 10),
        )
        background = JBColor(Color(250, 251, 252), Color(43, 45, 48))
        isOpaque = true
        minimumSize = Dimension(280, 200)
        preferredSize = Dimension(480, 400)

        val header = JPanel(BorderLayout(8, 6)).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.NORTH)
            add(JPanel(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(priorityLabel, BorderLayout.WEST)
                add(metaLabel, BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }

        val sections = JPanel(GridLayout(3, 1, 0, 8)).apply {
            isOpaque = false
            add(titled("1. AI detailed explanation", explanationArea))
            add(titled("2. What to do / How to fix", howToFixArea))
            add(titled("3. AI suggested fixed code", fixedCodeArea))
        }

        add(header, BorderLayout.NORTH)
        add(sections, BorderLayout.CENTER)
        showFinding(null)
    }

    fun showFinding(finding: Finding?) {
        if (finding == null) {
            titleLabel.text = "Select a warning on the left"
            priorityLabel.text = "  —  "
            priorityLabel.isOpaque = true
            priorityLabel.background = JBColor.GRAY
            priorityLabel.foreground = Color.WHITE
            metaLabel.text = "Left list → click a row to fill this panel"
            explanationArea.text = "Waiting for selection…"
            howToFixArea.text = ""
            fixedCodeArea.text = ""
            revalidate()
            repaint()
            return
        }

        val content = FindingDetailContent.from(finding)
        titleLabel.text = content.title
        metaLabel.text = "${content.location}  ·  ${finding.category.displayName}" +
            (finding.ruleKey?.let { "  ·  $it" } ?: "")
        priorityLabel.text = "  PRIORITY: ${content.priority}  "
        priorityLabel.isOpaque = true
        priorityLabel.background = severityColor(finding.severity)
        priorityLabel.foreground = when (finding.severity) {
            Severity.MAJOR, Severity.INFO -> JBColor(Color(25, 25, 25), Color(20, 20, 20))
            else -> Color.WHITE
        }
        explanationArea.text = content.explanation.ifBlank { "No detailed explanation provided." }
        howToFixArea.text = content.howToFix.ifBlank { "No remediation steps provided." }
        fixedCodeArea.text = content.fixedCode.ifBlank { "// No fixed-code sample for this finding." }
        explanationArea.caretPosition = 0
        howToFixArea.caretPosition = 0
        fixedCodeArea.caretPosition = 0
        revalidate()
        repaint()
    }

    fun showEmptyReviewSummaries(text: String) {
        titleLabel.text = "No warnings"
        priorityLabel.text = "  OK  "
        priorityLabel.isOpaque = true
        priorityLabel.background = JBColor(Color(46, 125, 50), Color(76, 175, 80))
        priorityLabel.foreground = Color.WHITE
        metaLabel.text = "Review completed without findings"
        explanationArea.text = text
        howToFixArea.text = "Nothing to fix."
        fixedCodeArea.text = ""
        revalidate()
        repaint()
    }

    private fun titled(title: String, area: JTextArea): JPanel =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
            )
            add(JBScrollPane(area), BorderLayout.CENTER)
        }

    private fun textArea(
        wrap: Boolean,
        rows: Int,
        background: Color = JBColor.background(),
        mono: Boolean = false,
    ): JBTextArea = JBTextArea(rows, 40).apply {
        isEditable = false
        lineWrap = wrap
        wrapStyleWord = wrap
        border = JBUI.Borders.empty(6)
        this.background = background
        if (mono) font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    companion object {
        fun severityColor(severity: Severity): Color = when (severity) {
            Severity.BLOCKER -> JBColor(Color(180, 35, 24), Color(198, 40, 40))
            Severity.CRITICAL -> JBColor(Color(210, 90, 20), Color(230, 126, 34))
            Severity.MAJOR -> JBColor(Color(180, 140, 20), Color(241, 196, 15))
            Severity.MINOR -> JBColor(Color(40, 120, 200), Color(52, 152, 219))
            Severity.INFO -> JBColor(Color(100, 100, 100), Color(149, 165, 166))
        }
    }
}
