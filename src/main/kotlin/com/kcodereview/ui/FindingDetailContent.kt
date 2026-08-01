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
    val fixedCode: String,
) {
    companion object {
        fun from(finding: Finding) = FindingDetailContent(
            title = finding.title,
            priority = finding.severity.displayName.uppercase(),
            location = finding.locationLabel(),
            explanation = finding.message,
            howToFix = finding.howToFix,
            fixedCode = finding.fixedCode.orEmpty(),
        )
    }
}
