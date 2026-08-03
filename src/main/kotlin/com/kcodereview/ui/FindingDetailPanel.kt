package com.kcodereview.ui

import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.kcodereview.model.Finding
import com.kcodereview.model.Severity
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.text.html.HTMLEditorKit

/**
 * RIGHT column of Code Analysis — always visible.
 * Styled to look like SonarQube for IDE.
 */
class FindingDetailPanel : JPanel(BorderLayout()) {

    private val editorPane = JEditorPane("text/html", "").apply {
        isEditable = false
        isOpaque = false
        addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
        val htmlKit = UIUtil.getHTMLEditorKit()
        val sheet = htmlKit.styleSheet
        sheet.addRule("h2 { font-size: 110%; font-weight: bold; margin-bottom: 4px; font-style: italic; }")
        sheet.addRule("h3 { font-size: 100%; font-weight: bold; margin-top: 12px; margin-bottom: 4px; }")
        sheet.addRule(".hr { border-bottom: 1px solid #555555; margin-bottom: 8px; }")
        sheet.addRule(".tags { margin-bottom: 12px; font-size: 90%; color: #888888; }")
        sheet.addRule(".code { font-family: monospace; font-size: 95%; background-color: #2b2d30; color: #a9b7c6; padding: 8px; margin-top: 8px; white-space: pre; }")
        editorKit = htmlKit
    }

    init {
        border = JBUI.Borders.empty()
        background = JBColor.background()
        isOpaque = true
        minimumSize = Dimension(280, 200)
        preferredSize = Dimension(480, 400)
        add(JBScrollPane(editorPane).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        showFinding(null)
    }

    fun showFinding(finding: Finding?) {
        if (finding == null) {
            editorPane.text = "<html><body><h3 style='color:gray'>Select a warning on the left</h3></body></html>"
            return
        }

        val content = FindingDetailContent.from(finding)
        val priorityColor = getHtmlColor(severityColor(finding.severity))
        
        val ruleKeyHtml = if (finding.ruleKey != null) " &nbsp;&nbsp; <span>${finding.ruleKey}</span>" else ""
        
        val html = buildString {
            append("<html><body>")
            append("<h2>${escape(content.title)}</h2>")
            append("<div class='tags'>")
            append("<b><font color='$priorityColor'>${content.priority}</font></b> &nbsp;&nbsp;|&nbsp;&nbsp; ")
            append("<span>${finding.category.displayName}</span>")
            append(ruleKeyHtml)
            append("</div>")
            
            append("<h3>Why is this an issue?</h3>")
            append("<div class='hr'></div>")
            append("<p>${escape(content.explanation).replace("\n", "<br>")}</p>")
            
            append("<h3>How to fix</h3>")
            append("<p>${escape(content.howToFix).replace("\n", "<br>")}</p>")
            
            if (content.fixedCode.isNotBlank() && content.fixedCode != "// No fixed-code sample for this finding.") {
                append("<h3>Suggested Fix</h3>")
                append("<div class='code'>${escape(content.fixedCode)}</div>")
            }
            append("</body></html>")
        }
        
        editorPane.text = html
        editorPane.caretPosition = 0
    }

    fun showEmptyReviewSummaries(text: String) {
        editorPane.text = "<html><body><h2>No warnings</h2><p>${escape(text).replace("\n", "<br>")}</p></body></html>"
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun getHtmlColor(color: Color): String =
        String.format("#%02x%02x%02x", color.red, color.green, color.blue)

    companion object {
        fun severityColor(severity: Severity): Color = when (severity) {
            Severity.BLOCKER -> JBColor(Color(180, 35, 24), Color(255, 82, 82))
            Severity.CRITICAL -> JBColor(Color(210, 90, 20), Color(255, 138, 101))
            Severity.MAJOR -> JBColor(Color(180, 140, 20), Color(255, 213, 79))
            Severity.MINOR -> JBColor(Color(40, 120, 200), Color(100, 181, 246))
            Severity.INFO -> JBColor(Color(100, 100, 100), Color(158, 158, 158))
        }
    }
}
