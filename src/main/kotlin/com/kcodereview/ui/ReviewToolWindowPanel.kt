package com.kcodereview.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.kcodereview.model.Finding
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity
import com.kcodereview.service.CodeReviewService
import com.kcodereview.settings.KCodeReviewConfigurable
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Code Analysis — always 2 columns:
 * LEFT 40% warnings list | RIGHT 60% detail inspector.
 *
 * Single-click updates the right pane.
 * Double-click navigates to source (so the detail pane is not "lost" on first click).
 */
class ReviewToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val log = Logger.getInstance(ReviewToolWindowPanel::class.java)
    private val service = project.getService(CodeReviewService::class.java)
    private val listModel = DefaultListModel<Finding>()
    private val warningsList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = 52
        cellRenderer = WarningListCellRenderer()
        emptyText.text = "No warnings"
    }

    private val detailPanel = FindingDetailPanel()
    private val summaryLabel = JBLabel("Code Analysis")
    private val blockerStat = MetricCard("BLOCKER", FindingDetailPanel.severityColor(Severity.BLOCKER))
    private val criticalStat = MetricCard("CRITICAL", FindingDetailPanel.severityColor(Severity.CRITICAL))
    private val majorStat = MetricCard("MAJOR", FindingDetailPanel.severityColor(Severity.MAJOR))
    private val minorStat = MetricCard("MINOR", FindingDetailPanel.severityColor(Severity.MINOR))
    private val infoStat = MetricCard("INFO", FindingDetailPanel.severityColor(Severity.INFO))

    private val listener: (ReviewResult?) -> Unit = { result ->
        ApplicationManager.getApplication().invokeLater {
            render(result)
            if (result != null) ReviewToolWindowFactory.show(project)
        }
    }

    init {
        border = JBUI.Borders.empty(8)
        minimumSize = Dimension(700, 280)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            add(JButton("Load Demo UI", AllIcons.Actions.Preview).also { btn ->
                btn.addActionListener {
                    service.publishResult(DemoFindings.reviewResult())
                }
            })
            add(JButton("Review Latest Commit", AllIcons.Actions.Execute).also { btn ->
                btn.addActionListener { runLatest() }
            })
            add(JButton("Settings", AllIcons.General.Settings).also { btn ->
                btn.addActionListener {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, KCodeReviewConfigurable::class.java)
                }
            })
        }

        val metrics = JPanel(GridLayout(1, 5, 8, 0)).apply {
            border = JBUI.Borders.empty(4, 0, 8, 0)
            add(blockerStat); add(criticalStat); add(majorStat); add(minorStat); add(infoStat)
        }

        val north = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(summaryLabel.apply { border = JBUI.Borders.empty(4, 2) }, BorderLayout.CENTER)
            add(metrics, BorderLayout.SOUTH)
        }

        val listPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.border()),
                    "Warnings (click = details, double-click = go to code)",
                ),
                JBUI.Borders.empty(4),
            )
            background = JBColor.background()
            isOpaque = true
            add(JBScrollPane(warningsList), BorderLayout.CENTER)
        }

        // GridBagLayout: weights guarantee both columns always get space.
        val body = JPanel(GridBagLayout()).apply {
            val left = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 0.40
                weighty = 1.0
                fill = GridBagConstraints.BOTH
                insets = Insets(0, 0, 0, 6)
            }
            val right = GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                weightx = 0.60
                weighty = 1.0
                fill = GridBagConstraints.BOTH
                insets = Insets(0, 6, 0, 0)
            }
            add(listPanel, left)
            add(detailPanel, right)
        }

        add(north, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)

        warningsList.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            val finding = warningsList.selectedValue
            if (finding != null) {
                log.info("Warning selected: ${finding.title}")
                detailPanel.showFinding(finding)
            }
        }
        warningsList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = warningsList.locationToIndex(e.point)
                if (index < 0) return
                warningsList.selectedIndex = index
                val finding = listModel.getElementAt(index)
                detailPanel.showFinding(finding)
                if (e.clickCount >= 2) {
                    navigateToFinding(finding)
                }
            }
        })

        service.addListener(listener)
        val existing = service.lastResult
        if (existing == null) {
            // First open: show demo so the 2-column layout is immediately visible.
            service.publishResult(DemoFindings.reviewResult())
        } else {
            render(existing)
        }

        SwingUtilities.invokeLater {
            revalidate()
            repaint()
        }
    }

    private fun runLatest() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "K Code Review", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Reviewing latest commit…"
                try {
                    service.reviewLatestCommit()
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, ex.message ?: "Review failed", "K Code Review")
                    }
                }
            }
        })
    }

    private fun render(result: ReviewResult?) {
        listModel.clear()
        if (result == null) {
            summaryLabel.text = "Code Analysis — empty"
            updateMetrics(emptyMap())
            detailPanel.showFinding(null)
            return
        }
        result.findings.forEach { listModel.addElement(it) }
        updateMetrics(result.countBySeverity())
        summaryLabel.text =
            "Code Analysis — ${result.commitHash.take(8)} · ${result.totalFindings} warnings · ${result.commitMessage.lineSequence().firstOrNull()}"
        if (result.findings.isNotEmpty()) {
            warningsList.selectedIndex = 0
            detailPanel.showFinding(result.findings.first())
        } else {
            detailPanel.showEmptyReviewSummaries(
                result.fileReviews.joinToString("\n\n") { "${it.filePath}: ${it.summary}" }
                    .ifBlank { "No findings." },
            )
        }
        revalidate()
        repaint()
    }

    private fun updateMetrics(counts: Map<Severity, Int>) {
        blockerStat.setCount(counts[Severity.BLOCKER] ?: 0)
        criticalStat.setCount(counts[Severity.CRITICAL] ?: 0)
        majorStat.setCount(counts[Severity.MAJOR] ?: 0)
        minorStat.setCount(counts[Severity.MINOR] ?: 0)
        infoStat.setCount(counts[Severity.INFO] ?: 0)
    }

    private fun navigateToFinding(finding: Finding) {
        val base = project.basePath ?: return
        val vf = LocalFileSystem.getInstance().findFileByPath("$base/${finding.filePath}")
            ?: LocalFileSystem.getInstance().findFileByPath(finding.filePath)
            ?: return
        val line = ((finding.line ?: 1) - 1).coerceAtLeast(0)
        OpenFileDescriptor(project, vf, line, 0).navigate(true)
    }

    private class MetricCard(title: String, accent: Color) : JPanel(BorderLayout()) {
        private val countLabel = JBLabel("0", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 20f)
            foreground = accent
        }

        init {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(6),
            )
            add(JBLabel(title, SwingConstants.CENTER).apply {
                foreground = accent
                font = font.deriveFont(Font.BOLD, 11f)
            }, BorderLayout.NORTH)
            add(countLabel, BorderLayout.CENTER)
        }

        fun setCount(count: Int) {
            countLabel.text = count.toString()
        }
    }

    private class WarningListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val finding = value as? Finding ?: return label
            label.text = "<html><b>[${finding.severity.displayName}]</b> ${escape(finding.title)}<br>" +
                "<font color='gray'>${escape(finding.locationLabel())}</font></html>"
            label.icon = when (finding.severity) {
                Severity.BLOCKER, Severity.CRITICAL -> AllIcons.General.Error
                Severity.MAJOR -> AllIcons.General.Warning
                else -> AllIcons.General.Information
            }
            label.border = JBUI.Borders.empty(6, 8)
            if (!isSelected) label.foreground = JBColor.foreground()
            return label
        }

        private fun escape(text: String): String =
            text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
