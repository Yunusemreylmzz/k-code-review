package com.kcodereview.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FindingDetailContentTest {
    @Test
    fun `maps all fields required by right panel`() {
        val finding = DemoFindings.reviewResult().findings.first()
        val content = FindingDetailContent.from(finding)
        assertTrue(content.title.isNotBlank())
        assertEquals("CRITICAL", content.priority)
        assertTrue(content.location.contains(":"))
        assertTrue(content.explanation.length > 20)
        assertTrue(content.howToFix.contains("1)"))
        assertTrue(content.fixedCode.isNotBlank())
    }

    @Test
    fun `each warning maps to distinct detail content`() {
        val findings = DemoFindings.reviewResult().findings
        assertTrue(findings.size >= 2)
        val first = FindingDetailContent.from(findings[0])
        val second = FindingDetailContent.from(findings[1])
        assertTrue(first.title != second.title)
        assertTrue(first.fixedCode != second.fixedCode)
    }

    @Test
    fun `grid split contract left list size matches findings`() {
        val result = DemoFindings.reviewResult()
        assertEquals(result.findings.size, result.findings.map { FindingDetailContent.from(it) }.size)
        result.findings.forEach {
            val c = FindingDetailContent.from(it)
            assertTrue(c.priority.isNotBlank())
            assertTrue(c.explanation.isNotBlank())
            assertTrue(c.howToFix.isNotBlank())
        }
    }
}
