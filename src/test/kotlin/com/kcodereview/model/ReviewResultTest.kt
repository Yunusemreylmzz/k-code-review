package com.kcodereview.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReviewResultTest {

    @Test
    fun `aggregates severity counts and sorts findings`() {
        val findings = listOf(
            Finding("1", "a.kt", Severity.MINOR, FindingCategory.CODE_SMELL, "m", "msg", "fix", line = 10),
            Finding("2", "b.kt", Severity.BLOCKER, FindingCategory.BUG, "b", "msg", "fix", line = 2),
            Finding("3", "a.kt", Severity.CRITICAL, FindingCategory.VULNERABILITY, "c", "msg", "fix", line = 5),
        )
        val result = ReviewResult(
            commitHash = "abc",
            commitMessage = "test",
            reviewedAtEpochMs = 1L,
            fileReviews = listOf(
                FileReview("a.kt", "s", listOf(findings[0], findings[2])),
                FileReview("b.kt", "s", listOf(findings[1])),
            ),
        )
        assertEquals(3, result.totalFindings)
        assertEquals(Severity.BLOCKER, result.findings[0].severity)
        assertEquals(Severity.CRITICAL, result.findings[1].severity)
        assertEquals(1, result.countBySeverity()[Severity.BLOCKER])
        assertEquals(1, result.countByCategory()[FindingCategory.VULNERABILITY])
    }
}
