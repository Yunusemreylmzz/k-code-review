package com.kcodereview.ai

import com.kcodereview.model.FindingCategory
import com.kcodereview.model.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReviewParserTest {

    @Test
    fun `parses clean json findings sorted by severity`() {
        val raw = """
            {
              "summary": "Two issues found",
              "findings": [
                {
                  "severity": "MINOR",
                  "category": "CODE_SMELL",
                  "title": "Long method",
                  "message": "Method is hard to read",
                  "howToFix": "Extract helpers",
                  "line": 80
                },
                {
                  "severity": "CRITICAL",
                  "category": "VULNERABILITY",
                  "title": "SQL injection",
                  "message": "User input concatenated into SQL",
                  "howToFix": "Use PreparedStatement",
                  "fixedCode": "ps.setString(1, query);",
                  "line": 42,
                  "ruleKey": "sql-injection"
                }
              ]
            }
        """.trimIndent()

        val parsed = ReviewParser.parse("src/Main.java", raw)
        assertEquals("Two issues found", parsed.summary)
        assertEquals(2, parsed.findings.size)
        assertEquals(Severity.CRITICAL, parsed.findings[0].severity)
        assertEquals(FindingCategory.VULNERABILITY, parsed.findings[0].category)
        assertEquals("Use PreparedStatement", parsed.findings[0].howToFix)
        assertEquals("ps.setString(1, query);", parsed.findings[0].fixedCode)
        assertEquals(Severity.MINOR, parsed.findings[1].severity)
        assertNull(parsed.findings[1].fixedCode)
    }

    @Test
    fun `extracts fixedCode from howToFix fences when missing`() {
        val raw = """
            {
              "summary": "one",
              "findings": [{
                "severity": "MAJOR",
                "category": "BUG",
                "title": "NPE",
                "message": "nullable",
                "howToFix": "Use requireNotNull.\n```kotlin\nrequireNotNull(x)\n```",
                "line": 10
              }]
            }
        """.trimIndent()
        val finding = ReviewParser.parse("A.kt", raw).findings.single()
        assertEquals("requireNotNull(x)", finding.fixedCode)
        assertTrue(finding.howToFix.contains("Use requireNotNull"))
        assertTrue(!finding.howToFix.contains("```"))
    }

    @Test
    fun `extracts json from markdown fences`() {
        val raw = """
            Here is the review:
            ```json
            {"summary":"ok","findings":[]}
            ```
        """.trimIndent()
        val json = ReviewParser.extractJson(raw)
        assertTrue(json.startsWith("{"))
        val parsed = ReviewParser.parse("A.kt", raw)
        assertEquals("ok", parsed.summary)
        assertTrue(parsed.findings.isEmpty())
    }

    @Test
    fun `maps unknown severity to INFO`() {
        val raw = """
            {"summary":"x","findings":[{"severity":"WEIRD","category":"BUG","title":"t","message":"m","howToFix":"f"}]}
        """.trimIndent()
        val parsed = ReviewParser.parse("X.kt", raw)
        assertEquals(Severity.INFO, parsed.findings.single().severity)
        assertEquals(FindingCategory.BUG, parsed.findings.single().category)
    }
}
