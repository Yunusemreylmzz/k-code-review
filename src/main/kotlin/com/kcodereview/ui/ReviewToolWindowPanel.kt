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
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.kcodereview.model.Finding
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity
import com.kcodereview.service.CodeReviewService
import com.kcodereview.settings.KCodeReviewConfigurable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Code Analysis — left: class-grouped findings tree | right: detail inspector.
 */
class ReviewToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val log = Logger.getInstance(ReviewToolWindowPanel::class.java)
    private val service = project.getService(CodeReviewService::class.java)

    private val rootNode = DefaultMutableTreeNode(FindingsTreeBuilder.Node.Root("No analysis results", 0, 0))
    private val treeModel = DefaultTreeModel(rootNode)
    private val warningsTree = Tree(treeModel).apply {
        isRootVisible = true
        showsRootHandles = true
        cellRenderer = FindingsTreeCellRenderer()
        emptyText.text = "No warnings"
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    private val detailPanel = FindingDetailPanel(project)

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
            add(JButton("Review All Changes", AllIcons.Actions.RunAll).also { btn ->
                btn.toolTipText =
                    "Review EVERY dirty class (staged + unstaged). Use this for multi-class reviews."
                btn.addActionListener { runLocalChanges() }
            })
            add(JButton("Review Latest Commit", AllIcons.Actions.Execute).also { btn ->
                btn.addActionListener { runLatest() }
            })
            add(JButton("Expand All", AllIcons.Actions.Expandall).also { btn ->
                btn.addActionListener { expandAll() }
            })
            add(JButton("Collapse Classes", AllIcons.Actions.Collapseall).also { btn ->
                btn.addActionListener { collapseClasses() }
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

        val listPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1),
                JBUI.Borders.empty(),
            )
            background = JBColor.background()
            isOpaque = true
            minimumSize = Dimension(240, 200)
            add(JBScrollPane(warningsTree).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        }

        detailPanel.minimumSize = Dimension(320, 200)

        val body = JBSplitter(false, 0.42f).apply {
            firstComponent = listPanel
            secondComponent = detailPanel
            setHonorComponentsMinimumSize(true)
            dividerWidth = 3
        }

        add(JPanel(BorderLayout()).apply { add(toolbar, BorderLayout.NORTH) }, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)

        warningsTree.addTreeSelectionListener {
            when (val payload = selectedPayload()) {
                is FindingsTreeBuilder.Node.FindingLeaf -> {
                    log.info("Warning selected: ${payload.finding.title}")
                    detailPanel.showFinding(payload.finding)
                }
                is FindingsTreeBuilder.Node.ClassGroup -> {
                    detailPanel.showEmptyReviewSummaries(
                        buildString {
                            appendLine("${payload.className} — ${payload.count} issue(s)")
                            if (payload.packagePath.isNotBlank()) appendLine(payload.packagePath)
                            appendLine(payload.filePath)
                            appendLine()
                            if (payload.findings.isEmpty()) {
                                appendLine(payload.reviewSummary.ifBlank { "No findings in this class." })
                                appendLine()
                                append("If you expected issues here, confirm the file is included via Review Local Changes (staged + unstaged).")
                            } else {
                                payload.severityCounts().entries
                                    .sortedBy { it.key.rank }
                                    .forEach { (sev, n) -> appendLine("• $n ${sev.displayName}") }
                                appendLine()
                                append("Select a finding under this class (sorted by severity, then line).")
                            }
                        },
                    )
                }
                else -> Unit
            }
        }
        warningsTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount < 2) return
                val finding = (selectedPayload() as? FindingsTreeBuilder.Node.FindingLeaf)?.finding
                    ?: return
                navigateToFinding(finding)
            }
        })

        service.addListener(listener)
        val existing = service.lastResult
        if (existing == null) {
            service.publishResult(DemoFindings.reviewResult())
        } else {
            render(existing)
        }

        SwingUtilities.invokeLater {
            revalidate()
            repaint()
        }
    }

    private fun selectedPayload(): Any? =
        (warningsTree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject

    private fun runLatest() {
        runReview("Reviewing latest commit…") { service.reviewLatestCommit() }
    }

    private fun runLocalChanges() {
        runReview("Reviewing all dirty classes (staged + unstaged)…") {
            service.reviewLocalChanges("Manual local review")
                ?: throw IllegalStateException(
                    "No local source changes to review. Edit or stage some files first.",
                )
        }
    }

    private fun runReview(progressText: String, block: () -> ReviewResult?) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "K Code Review", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = progressText
                indicator.isIndeterminate = true
                try {
                    val result = block()
                    ApplicationManager.getApplication().invokeLater {
                        ReviewToolWindowFactory.show(project)
                        if (result != null) {
                            val classes = result.fileReviews.joinToString {
                                FindingsTreeBuilder.classNameOf(it.filePath)
                            }
                            com.intellij.notification.NotificationGroupManager.getInstance()
                                .getNotificationGroup("K Code Review")
                                .createNotification(
                                    "Review complete",
                                    "${result.totalFindings} finding(s) · ${result.fileReviews.size} class(es): $classes",
                                    com.intellij.notification.NotificationType.INFORMATION,
                                )
                                .notify(project)
                        }
                    }
                } catch (ex: Exception) {
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
            rootNode.userObject = FindingsTreeBuilder.Node.Root("No analysis results.", 0, 0)
            treeModel.reload()
            detailPanel.showFinding(null)
            return
        }

        val model = FindingsTreeBuilder.build(result)
        rootNode.userObject = model.root

        for (group in model.groups) {
            val classNode = DefaultMutableTreeNode(group)
            group.findings.forEach { finding ->
                classNode.add(DefaultMutableTreeNode(FindingsTreeBuilder.Node.FindingLeaf(finding)))
            }
            rootNode.add(classNode)
        }

        // Files with a summary but zero findings still appear as empty groups? Skip — no noise.
        treeModel.reload()
        expandAll()

        val first = model.firstFinding
        if (first != null) {
            selectFinding(first)
            detailPanel.showFinding(first)
        } else {
            detailPanel.showEmptyReviewSummaries(
                result.fileReviews.joinToString("\n\n") { "${FindingsTreeBuilder.classNameOf(it.filePath)}: ${it.summary}" }
                    .ifBlank { "No findings." },
            )
        }
        revalidate()
        repaint()
    }

    private fun selectFinding(finding: Finding) {
        val root = rootNode
        for (i in 0 until root.childCount) {
            val classNode = root.getChildAt(i) as DefaultMutableTreeNode
            for (j in 0 until classNode.childCount) {
                val leaf = classNode.getChildAt(j) as DefaultMutableTreeNode
                val payload = leaf.userObject as? FindingsTreeBuilder.Node.FindingLeaf ?: continue
                if (payload.finding.id == finding.id || payload.finding === finding) {
                    warningsTree.selectionPath = TreePath(leaf.path)
                    warningsTree.scrollPathToVisible(TreePath(leaf.path))
                    return
                }
            }
        }
    }

    private fun expandAll() {
        warningsTree.expandPath(TreePath(rootNode.path))
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as DefaultMutableTreeNode
            warningsTree.expandPath(TreePath(child.path))
        }
    }

    private fun collapseClasses() {
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as DefaultMutableTreeNode
            warningsTree.collapsePath(TreePath(child.path))
        }
        warningsTree.expandPath(TreePath(rootNode.path))
    }

    private fun navigateToFinding(finding: Finding) {
        val base = project.basePath ?: return
        val vf = LocalFileSystem.getInstance().findFileByPath("$base/${finding.filePath}")
            ?: LocalFileSystem.getInstance().findFileByPath(finding.filePath)
            ?: return
        val line = ((finding.line ?: 1) - 1).coerceAtLeast(0)
        OpenFileDescriptor(project, vf, line, 0).navigate(true)
    }

    private class FindingsTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            when (val payload = node.userObject) {
                is FindingsTreeBuilder.Node.Root -> {
                    icon = AllIcons.Toolwindows.Problems
                    append(payload.label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                is FindingsTreeBuilder.Node.ClassGroup -> {
                    icon = AllIcons.Nodes.Class
                    append(payload.className, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ", SimpleTextAttributes.GRAY_ATTRIBUTES)
                    if (payload.findings.isEmpty()) {
                        val tag = when {
                            payload.reviewSummary.contains("failed", ignoreCase = true) -> "failed"
                            else -> "clean"
                        }
                        append("($tag)", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
                    } else {
                        append("(${payload.count})", SimpleTextAttributes.GRAY_ATTRIBUTES)
                    }
                    if (payload.packagePath.isNotBlank()) {
                        append("  ", SimpleTextAttributes.GRAY_ATTRIBUTES)
                        append(payload.packagePath, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
                    }
                    toolTipText = buildString {
                        append(payload.filePath)
                        if (payload.reviewSummary.isNotBlank()) {
                            append(" — ")
                            append(payload.reviewSummary.take(200))
                        }
                    }
                }
                is FindingsTreeBuilder.Node.FindingLeaf -> {
                    val finding = payload.finding
                    icon = severityIcon(finding.severity)
                    val lineStr = finding.line?.toString() ?: "—"
                    append("($lineStr) ", SimpleTextAttributes.GRAY_ATTRIBUTES)
                    append(finding.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    if (!finding.ruleKey.isNullOrBlank()) {
                        append("  ", SimpleTextAttributes.GRAY_ATTRIBUTES)
                        append(finding.ruleKey!!, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY))
                    }
                    toolTipText = "${finding.severity.displayName} · ${finding.category.displayName}"
                }
                is String -> append(payload, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                else -> append(payload?.toString().orEmpty(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            font = font.deriveFont(Font.PLAIN, 12f)
        }

        private fun severityIcon(severity: Severity) = when (severity) {
            Severity.BLOCKER, Severity.CRITICAL -> AllIcons.General.Error
            Severity.MAJOR -> AllIcons.General.Warning
            else -> AllIcons.General.Information
        }
    }
}
