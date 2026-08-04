package com.kcodereview.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.kcodereview.model.Finding
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity
import com.kcodereview.service.CommitGate
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Findings summary popup — used after commit gate (block once) and after manual Tools reviews.
 */
class CommitBlockedDialog(
    private val dialogProject: Project,
    private val result: ReviewResult,
    private val mode: Mode = Mode.COMMIT_BLOCKED,
) : DialogWrapper(dialogProject, true) {

    enum class Mode {
        COMMIT_BLOCKED,
        REVIEW_COMPLETE,
    }

    init {
        title = when (mode) {
            Mode.COMMIT_BLOCKED -> "Commit Blocked Once — K Code Review"
            Mode.REVIEW_COMPLETE -> "Review Findings — K Code Review"
        }
        isModal = true
        setOKButtonText("Open Analysis Panel")
        setCancelButtonText(if (mode == Mode.COMMIT_BLOCKED) "Close" else "Dismiss")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val findings = CommitGate.findingsForDisplay(result)
        val bySeverity = findings.groupingBy { it.severity }.eachCount()

        val root = JPanel(BorderLayout(0, 12)).apply {
            preferredSize = Dimension(560, 420)
            border = JBUI.Borders.empty(8, 4, 4, 4)
        }

        val headerTitle = when (mode) {
            Mode.COMMIT_BLOCKED -> "Commit paused for review"
            Mode.REVIEW_COMPLETE -> "Review complete — ${findings.size} issue(s) in ${
                    FindingsTreeBuilder.groupByClass(findings).size
                } class(es)"
        }
        val headerBody = when (mode) {
            Mode.COMMIT_BLOCKED ->
                "<html>K Code Review found issues in your staged changes.<br>" +
                    "Fix them in the analysis panel, or click <b>Commit</b> again to proceed anyway.</html>"
            Mode.REVIEW_COMPLETE ->
                "<html>Select a finding below (or in the tool window) to see <b>Why</b>, " +
                    "<b>How to fix</b>, and <b>Suggested Fix</b> on the right.</html>"
        }

        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel(headerTitle).apply {
                font = font.deriveFont(Font.BOLD, 16f)
                icon = AllIcons.General.Warning
                iconTextGap = 8
            })
            add(Box.createVerticalStrut(6))
            add(JBLabel(headerBody).apply {
                foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
            })
            add(Box.createVerticalStrut(10))
            add(severityChips(bySeverity))
        }

        val listPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4, 0)
            val groups = FindingsTreeBuilder.groupByClass(findings)
            if (groups.isEmpty()) {
                add(JBLabel("No findings in this review.").apply {
                    foreground = JBColor.GRAY
                })
            } else {
                var shown = 0
                val maxFindings = 12
                for (group in groups) {
                    if (shown >= maxFindings) break
                    add(classHeader(group))
                    for (finding in group.findings) {
                        if (shown >= maxFindings) break
                        add(findingRow(finding))
                        shown++
                    }
                }
                val remaining = findings.size - shown
                if (remaining > 0) {
                    add(Box.createVerticalStrut(6))
                    add(JBLabel("…and $remaining more across ${groups.size} classes in the analysis panel").apply {
                        foreground = JBColor.GRAY
                    })
                }
            }
        }

        val footer = JBLabel(
            when (mode) {
                Mode.COMMIT_BLOCKED ->
                    "<html><b>Next step:</b> Review details → How to fix → Suggested Fix (Apply). " +
                        "Same staged changes: second Commit continues.</html>"
                Mode.REVIEW_COMPLETE ->
                    "<html><b>Tip:</b> Use the right-hand panel in <b>K Code Review</b> for full " +
                        "explanation, numbered steps, and Apply.</html>"
            },
        ).apply {
            border = JBUI.Borders.emptyTop(4)
        }

        root.add(header, BorderLayout.NORTH)
        root.add(JBScrollPane(listPanel).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(6),
            )
        }, BorderLayout.CENTER)
        root.add(footer, BorderLayout.SOUTH)
        return root
    }

    override fun doOKAction() {
        super.doOKAction()
        ReviewToolWindowFactory.show(dialogProject)
    }

    private fun severityChips(counts: Map<Severity, Int>): JComponent {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        Severity.entries.forEach { severity ->
            val count = counts[severity] ?: return@forEach
            row.add(chip(severity, count))
        }
        if (counts.isEmpty()) {
            row.add(JBLabel("No findings"))
        }
        return row
    }

    private fun chip(severity: Severity, count: Int): JComponent {
        val (bg, fg) = chipColors(severity)
        return JLabel(" $count  ${severity.displayName} ").apply {
            icon = severityIcon(severity)
            iconTextGap = 4
            isOpaque = true
            background = bg
            foreground = fg
            font = font.deriveFont(Font.BOLD, 11f)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg.brighter(), 1),
                JBUI.Borders.empty(3, 8),
            )
        }
    }

    private fun classHeader(group: FindingsTreeBuilder.Node.ClassGroup): JComponent =
        JBLabel("${group.className}  (${group.count})").apply {
            icon = AllIcons.Nodes.Class
            iconTextGap = 6
            font = font.deriveFont(Font.BOLD, 12f)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(8, 0, 4, 0)
            toolTipText = group.filePath
        }

    private fun findingRow(finding: Finding): JComponent {
        val (bg, fg) = chipColors(finding.severity)
        val panel = JPanel(GridBagLayout()).apply {
            maximumSize = Dimension(Int.MAX_VALUE, 52)
            alignmentX = Component.LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.empty(0, 0, 6, 0),
                BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, fg),
                    JBUI.Borders.empty(6, 8),
                ),
            )
            background = JBColor(
                Color(
                    (bg.red * 0.15 + 245).toInt().coerceAtMost(255),
                    (bg.green * 0.15 + 245).toInt().coerceAtMost(255),
                    (bg.blue * 0.15 + 245).toInt().coerceAtMost(255),
                ),
                Color(bg.red / 6, bg.green / 6, bg.blue / 6),
            )
            isOpaque = true
        }

        val iconLabel = JLabel(severityIcon(finding.severity))
        val severityLabel = JBLabel(finding.severity.displayName.uppercase()).apply {
            foreground = fg
            font = font.deriveFont(Font.BOLD, 10f)
        }
        val titleLabel = JBLabel(finding.title).apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
        val locationLabel = JBLabel(finding.locationLabel()).apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(11f)
        }
        val categoryLabel = JBLabel(finding.category.displayName).apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(Font.ITALIC, 11f)
        }

        panel.add(iconLabel, GridBagConstraints().apply {
            gridx = 0; gridy = 0; gridheight = 2
            insets = Insets(0, 0, 0, 8)
            anchor = GridBagConstraints.NORTH
        })
        panel.add(severityLabel, GridBagConstraints().apply {
            gridx = 1; gridy = 0
            insets = Insets(0, 0, 2, 8)
            anchor = GridBagConstraints.WEST
        })
        panel.add(titleLabel, GridBagConstraints().apply {
            gridx = 2; gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        })
        panel.add(locationLabel, GridBagConstraints().apply {
            gridx = 1; gridy = 1
            insets = Insets(0, 0, 0, 8)
            anchor = GridBagConstraints.WEST
        })
        panel.add(categoryLabel, GridBagConstraints().apply {
            gridx = 2; gridy = 1
            anchor = GridBagConstraints.WEST
        })
        return panel
    }

    companion object {
        fun severityIcon(severity: Severity): Icon = when (severity) {
            Severity.BLOCKER, Severity.CRITICAL -> AllIcons.General.Error
            Severity.MAJOR -> AllIcons.General.Warning
            Severity.MINOR, Severity.INFO -> AllIcons.General.Information
        }

        fun chipColors(severity: Severity): Pair<Color, Color> = when (severity) {
            Severity.BLOCKER -> JBColor(Color(255, 235, 238), Color(60, 20, 24)) to
                JBColor(Color(183, 28, 28), Color(255, 138, 128))
            Severity.CRITICAL -> JBColor(Color(255, 243, 224), Color(55, 30, 10)) to
                JBColor(Color(230, 81, 0), Color(255, 171, 64))
            Severity.MAJOR -> JBColor(Color(255, 253, 231), Color(50, 45, 10)) to
                JBColor(Color(249, 168, 37), Color(255, 213, 79))
            Severity.MINOR -> JBColor(Color(227, 242, 253), Color(15, 35, 55)) to
                JBColor(Color(25, 118, 210), Color(100, 181, 246))
            Severity.INFO -> JBColor(Color(245, 245, 245), Color(40, 40, 40)) to
                JBColor(Color(97, 97, 97), Color(189, 189, 189))
        }

        fun show(project: Project, result: ReviewResult, mode: Mode = Mode.COMMIT_BLOCKED) {
            if (result.findings.isEmpty() && mode == Mode.REVIEW_COMPLETE) return
            CommitBlockedDialog(project, result, mode).show()
        }

        fun showReviewComplete(project: Project, result: ReviewResult) {
            show(project, result, Mode.REVIEW_COMPLETE)
        }
    }
}
