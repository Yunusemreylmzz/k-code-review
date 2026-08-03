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
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
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
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

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
    
    private val rootNode = DefaultMutableTreeNode("No analysis results")
    private val treeModel = DefaultTreeModel(rootNode)
    private val warningsTree = Tree(treeModel).apply {
        isRootVisible = true
        showsRootHandles = true
        cellRenderer = WarningTreeCellRenderer()
        emptyText.text = "No warnings"
    }

    private val detailPanel = FindingDetailPanel()

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
            add(JButton("Review Staged Changes", AllIcons.Actions.RunAll).also { btn ->
                btn.toolTipText = "Run K Code Review on currently staged (changed) files — same as the pre-commit check"
                btn.addActionListener { runStagedChanges() }
            })
            add(JButton("Review Latest Commit", AllIcons.Actions.Execute).also { btn ->
                btn.addActionListener { runLatest() }
            })
            add(JButton("Load Demo", AllIcons.Actions.Preview).also { btn ->
                btn.addActionListener {
                    service.publishResult(DemoFindings.reviewResult())
                }
            })
            add(JButton("Settings", AllIcons.General.Settings).also { btn ->
                btn.addActionListener {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, KCodeReviewConfigurable::class.java)
                }
            })
        }

        val north = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
        }

        val listPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1), // Only right border
                JBUI.Borders.empty()
            )
            background = JBColor.background()
            isOpaque = true
            add(JBScrollPane(warningsTree).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
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

        warningsTree.addTreeSelectionListener { e ->
            val node = warningsTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val finding = node.userObject as? Finding
            if (finding != null) {
                log.info("Warning selected: ${finding.title}")
                detailPanel.showFinding(finding)
            }
        }
        warningsTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val path = warningsTree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val finding = node.userObject as? Finding ?: return
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
        runReview("Reviewing latest commit…") { service.reviewLatestCommit() }
    }

    private fun runStagedChanges() {
        runReview("Reviewing staged (changed) files…") {
            service.reviewStagedChanges("Manual review")
                ?: throw IllegalStateException("No staged source files to review. Stage some changes first.")
        }
    }

    private fun runReview(progressText: String, block: () -> Any?) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "K Code Review", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = progressText
                indicator.isIndeterminate = true
                runCatching { block() }.onFailure { ex ->
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, ex.message ?: "Review failed", "K Code Review")
                    }
                }
            }
        })
    }

    private fun render(result: ReviewResult?) {
        rootNode.removeAllChildren()
        
        if (result == null) {
            rootNode.userObject = "No analysis results."
            treeModel.reload()
            detailPanel.showFinding(null)
            return
        }
        
        rootNode.userObject = "Found ${result.totalFindings} issues"
        
        result.findings.forEach { 
            rootNode.add(DefaultMutableTreeNode(it))
        }
        
        treeModel.reload()
        
        if (result.findings.isNotEmpty()) {
            warningsTree.expandPath(TreePath(rootNode.path))
            warningsTree.selectionPath = TreePath((rootNode.firstChild as DefaultMutableTreeNode).path)
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

    private fun navigateToFinding(finding: Finding) {
        val base = project.basePath ?: return
        val vf = LocalFileSystem.getInstance().findFileByPath("$base/${finding.filePath}")
            ?: LocalFileSystem.getInstance().findFileByPath(finding.filePath)
            ?: return
        val line = ((finding.line ?: 1) - 1).coerceAtLeast(0)
        OpenFileDescriptor(project, vf, line, 0).navigate(true)
    }

    // MetricCard removed

    private class WarningTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            val userObject = node.userObject
            
            if (userObject is String) {
                append(userObject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            } else if (userObject is Finding) {
                icon = when (userObject.severity) {
                    Severity.BLOCKER, Severity.CRITICAL -> AllIcons.General.Error
                    Severity.MAJOR -> AllIcons.General.Warning
                    else -> AllIcons.General.Information
                }
                
                val lineStr = userObject.line?.let { "$it" } ?: "-"
                append("($lineStr) ", SimpleTextAttributes.GRAY_ATTRIBUTES)
                append("${userObject.title}. ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                
                if (userObject.ruleKey != null) {
                    append(userObject.ruleKey, SimpleTextAttributes.GRAY_ATTRIBUTES)
                }
            }
        }
    }
}
