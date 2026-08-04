package com.kcodereview.ai

import com.kcodereview.model.FindingCategory
import com.kcodereview.model.Severity
import com.kcodereview.ui.FindingsTreeBuilder
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

    @Test
    fun `repairs trailing commas that gson strict mode rejects`() {
        val raw = """
            {
              "summary": "ok",
              "findings": [
                {
                  "severity": "MAJOR",
                  "category": "BUG",
                  "title": "t",
                  "message": "m",
                  "howToFix": "f",
                  "line": 1,
                },
              ],
            }
        """.trimIndent()
        val parsed = ReviewParser.parse("A.java", raw)
        assertEquals("ok", parsed.summary)
        assertEquals(1, parsed.findings.size)
        assertEquals("t", parsed.findings.single().title)
    }

    @Test
    fun `salvages truncated findings array instead of crashing`() {
        // Mimics Gemini cutting off at max tokens mid-object (user error around line 47).
        val raw = """
            {
              "summary": "Partial",
              "findings": [
                {
                  "severity": "CRITICAL",
                  "category": "VULNERABILITY",
                  "title": "Hardcoded secret",
                  "message": "API key in source",
                  "howToFix": "Move to env",
                  "line": 12
                },
                {
                  "severity": "MAJOR",
                  "category": "BUG",
                  "title": "NPE risk",
                  "message": "Unchecked null
        """.trimIndent()

        val parsed = ReviewParser.parse("Ctrl.java", raw)
        assertEquals(1, parsed.findings.size)
        assertEquals("Hardcoded secret", parsed.findings.single().title)
        assertEquals(Severity.CRITICAL, parsed.findings.single().severity)
    }

    @Test
    fun `ignores trailing garbage after balanced json object`() {
        val raw = """
            {"summary":"clean","findings":[]}
            Thanks for reviewing!
            }extra
        """.trimIndent()
        val parsed = ReviewParser.parse("A.kt", raw)
        assertEquals("clean", parsed.summary)
        assertTrue(parsed.findings.isEmpty())
    }

    @Test
    fun `unreadable json returns empty findings without throwing`() {
        val parsed = ReviewParser.parse("A.kt", "not json at all {{{")
        assertTrue(parsed.findings.isEmpty())
        assertTrue(parsed.summary.contains("unreadable", ignoreCase = true) || parsed.summary.isNotBlank())
    }

    @Test
    fun `repairCommonIssues strips trailing commas`() {
        val repaired = ReviewParser.repairCommonIssues("""{"a":1,}""")
        assertEquals("""{"a":1}""", repaired)
    }

    @Test
    fun `uses reported filePath to classify finding under another class`() {
        val reviewed = "src/main/java/com/acme/First.java"
        val other = "src/main/java/com/acme/Second.java"
        val raw = """
            {
              "summary": "cross",
              "findings": [{
                "severity": "MAJOR",
                "category": "BUG",
                "title": "In Second",
                "message": "m",
                "howToFix": "f",
                "filePath": "$other",
                "line": 9
              }]
            }
        """.trimIndent()
        val parsed = ReviewParser.parse(reviewed, raw)
        assertEquals(other, parsed.findings.single().filePath)
        assertEquals(
            "Second",
            FindingsTreeBuilder.classNameOf(parsed.findings.single().filePath),
        )
    }

    @Test
    fun `resolveFindingPath keeps reviewed file for bare class names`() {
        val reviewed = "src/main/java/com/acme/Foo.java"
        assertEquals(reviewed, ReviewParser.resolveFindingPath(reviewed, "Foo"))
        assertEquals(reviewed, ReviewParser.resolveFindingPath(reviewed, null))
    }
}
