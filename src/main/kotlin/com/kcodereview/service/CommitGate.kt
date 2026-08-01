package com.kcodereview.service

import com.kcodereview.model.Finding
import com.kcodereview.model.ReviewResult

/**
 * Pre-commit policy: any finding blocks the first commit attempt.
 * A second attempt for the same staged fingerprint may proceed (override).
 */
object CommitGate {

    fun shouldBlock(result: ReviewResult): Boolean =
        result.findings.isNotEmpty()

    fun findingsForDisplay(result: ReviewResult): List<Finding> =
        result.findings

    fun blockSummary(result: ReviewResult): String {
        val bySeverity = result.findings.groupingBy { it.severity }.eachCount()
        val parts = bySeverity.entries
            .sortedBy { it.key.rank }
            .joinToString(", ") { "${it.value} ${it.key.name}" }
        return buildString {
            append("K Code Review blocked this commit once ($parts). ")
            append("Review findings in the K Code Review tool window. ")
            append("Click Commit again to proceed anyway with the same staged changes.")
        }
    }
}
