package com.kcodereview.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DemoFindingsTest {
    @Test
    fun `demo result has warnings with fixed code for UI`() {
        val result = DemoFindings.reviewResult()
        assertTrue(result.totalFindings >= 3)
        assertEquals("DEMO", result.commitHash)
        result.findings.forEach { finding ->
            assertTrue(finding.title.isNotBlank())
            assertTrue(finding.message.isNotBlank())
            assertTrue(finding.howToFix.isNotBlank())
            assertTrue(!finding.fixedCode.isNullOrBlank())
            assertTrue(finding.line != null && finding.line!! > 0)
        }
    }
}
