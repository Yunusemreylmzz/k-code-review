package com.kcodereview.ui

import com.kcodereview.model.Finding

/**
 * Pure mapping used by the right-hand detail pane.
 */
data class FindingDetailContent(
    val title: String,
    val priority: String,
    val location: String,
    val explanation: String,
    val howToFix: String,
    val howToFixSteps: List<String>,
    val fixedCode: String,
) {
    companion object {
        fun from(finding: Finding) = FindingDetailContent(
            title = finding.title,
            priority = finding.severity.displayName.uppercase(),
            location = finding.locationLabel(),
            explanation = finding.message,
            howToFix = finding.howToFix,
            howToFixSteps = parseHowToFixSteps(finding.howToFix),
            fixedCode = finding.fixedCode.orEmpty(),
        )

        /**
         * Splits how-to-fix text into stacked actionable steps.
         * Accepts `1)`, `1.`, `-`, `•`, `*` prefixes.
         */
        fun parseHowToFixSteps(raw: String): List<String> {
            val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) return emptyList()

            val stepped = lines.map { line ->
                line.replace(Regex("""^(\d+[.)]\s*|[-•*]\s+)"""), "").trim()
            }.filter { it.isNotBlank() }

            return stepped.ifEmpty { listOf(raw.trim()) }
        }
    }
}
