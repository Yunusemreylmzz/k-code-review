package com.kcodereview.service

import com.kcodereview.model.FileReview
import com.kcodereview.model.Finding
import com.kcodereview.model.FindingCategory
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommitGateTest {

    @Test
    fun `blocks on any severity including INFO`() {
        val result = review(finding(Severity.INFO))
        assertTrue(CommitGate.shouldBlock(result))
        assertEquals(1, CommitGate.findingsForDisplay(result).size)
    }

    @Test
    fun `blocks on MAJOR MINOR and CRITICAL`() {
        val result = review(
            finding(Severity.MAJOR),
            finding(Severity.MINOR),
            finding(Severity.CRITICAL),
        )
        assertTrue(CommitGate.shouldBlock(result))
        assertEquals(3, CommitGate.findingsForDisplay(result).size)
    }

    @Test
    fun `allows empty findings`() {
        assertFalse(CommitGate.shouldBlock(review()))
    }

    @Test
    fun `block summary mentions second commit`() {
        val summary = CommitGate.blockSummary(review(finding(Severity.MINOR)))
        assertTrue(summary.contains("Click Commit again", ignoreCase = true))
    }

    private fun finding(severity: Severity) = Finding(
        id = severity.name,
        filePath = "A.kt",
        severity = severity,
        category = FindingCategory.BUG,
        title = severity.name,
        message = "m",
        howToFix = "f",
        line = 1,
    )

    private fun review(vararg findings: Finding) = ReviewResult(
        commitHash = "STAGED",
        commitMessage = "msg",
        reviewedAtEpochMs = 1L,
        fileReviews = listOf(FileReview("A.kt", "s", findings.toList())),
    )
}
