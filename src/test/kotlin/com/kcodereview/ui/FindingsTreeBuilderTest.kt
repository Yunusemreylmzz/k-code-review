package com.kcodereview.ui

import com.kcodereview.model.FileReview
import com.kcodereview.model.Finding
import com.kcodereview.model.FindingCategory
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FindingsTreeBuilderTest {

    @Test
    fun `groups findings by class and sorts riskiest class first`() {
        val result = multiClassResult()
        val model = FindingsTreeBuilder.build(result)

        assertEquals(4, model.root.totalFindings)
        assertEquals(3, model.root.classCount)
        assertTrue(model.root.label.contains("3 classes") || model.root.label.contains("3 reviewed"))

        assertEquals(
            listOf("AdvertController", "AdvertService", "AdvertMapper"),
            model.groups.map { it.className },
        )
        assertEquals(2, model.groups[0].count)
        assertEquals(Severity.CRITICAL, model.groups[0].highestSeverity)
        assertEquals("com.haradan.domain.controller", model.groups[0].packagePath)
        assertEquals("Hardcoded secret", model.firstFinding?.title)
    }

    @Test
    fun `sorts findings inside a class by severity then line`() {
        val path = "src/main/java/com/acme/Foo.java"
        val findings = listOf(
            finding(path, "late minor", Severity.MINOR, line = 90),
            finding(path, "early major", Severity.MAJOR, line = 10),
            finding(path, "later major", Severity.MAJOR, line = 40),
            finding(path, "critical", Severity.CRITICAL, line = 5),
        )
        val sorted = FindingsTreeBuilder.sortFindings(findings)
        assertEquals(
            listOf("critical", "early major", "later major", "late minor"),
            sorted.map { it.title },
        )
    }

    @Test
    fun `includes reviewed class with zero findings so second class is visible`() {
        val withIssues = finding(
            path = "src/main/java/com/acme/First.java",
            title = "Bug",
            severity = Severity.MAJOR,
        )
        val result = ReviewResult(
            commitHash = "x",
            commitMessage = "m",
            reviewedAtEpochMs = 1,
            fileReviews = listOf(
                FileReview(withIssues.filePath, "has issues", listOf(withIssues)),
                FileReview(
                    "src/main/java/com/acme/Second.java",
                    "No issues found in this class.",
                    emptyList(),
                ),
            ),
        )
        val model = FindingsTreeBuilder.build(result)
        assertEquals(2, model.groups.size)
        assertEquals("First", model.groups[0].className)
        assertEquals("Second", model.groups[1].className)
        assertTrue(model.groups[1].findings.isEmpty())
        assertTrue(model.groups[1].reviewedCleanOrFailed)
    }

    @Test
    fun `single class label uses singular wording`() {
        val finding = finding(
            path = "src/main/java/com/acme/Foo.java",
            title = "Only",
            severity = Severity.MINOR,
        )
        val model = FindingsTreeBuilder.build(
            ReviewResult(
                commitHash = "a",
                commitMessage = "m",
                reviewedAtEpochMs = 1,
                fileReviews = listOf(FileReview(finding.filePath, "s", listOf(finding))),
            ),
        )
        assertEquals(1, model.root.classCount)
        assertTrue(model.root.label.contains("1 class"))
        assertEquals("Foo", model.groups.single().className)
    }

    @Test
    fun `empty review still lists reviewed classes`() {
        val model = FindingsTreeBuilder.build(
            ReviewResult(
                "h",
                "m",
                1L,
                listOf(FileReview("src/main/java/a/A.java", "clean", emptyList())),
            ),
        )
        assertEquals(1, model.groups.size)
        assertTrue(model.root.label.contains("No issues found"))
        assertEquals("A", model.groups.single().className)
    }

    @Test
    fun `classNameOf strips path and extension`() {
        assertEquals(
            "AdvertController",
            FindingsTreeBuilder.classNameOf("src/main/java/com/x/AdvertController.java"),
        )
        assertEquals(
            "ReviewToolWindowPanel",
            FindingsTreeBuilder.classNameOf("src/main/kotlin/com/x/ReviewToolWindowPanel.kt"),
        )
    }

    @Test
    fun `demo multi-class structure is self-consistent`() {
        val demo = DemoFindings.reviewResult()
        val model = FindingsTreeBuilder.build(demo)
        assertEquals(3, model.groups.size)
        assertEquals(demo.totalFindings, model.groups.sumOf { it.count })
        assertEquals(demo.findings.toSet(), model.groups.flatMap { it.findings }.toSet())
        // Within each class, findings are severity/line sorted.
        model.groups.forEach { group ->
            assertEquals(FindingsTreeBuilder.sortFindings(group.findings), group.findings)
        }
    }

    private fun multiClassResult(): ReviewResult {
        val f1 = finding(
            path = "src/main/java/com/haradan/domain/controller/AdvertController.java",
            title = "Hardcoded secret",
            severity = Severity.CRITICAL,
            line = 10,
        )
        val f2 = finding(
            path = "src/main/java/com/haradan/domain/controller/AdvertController.java",
            title = "String ==",
            severity = Severity.MAJOR,
            line = 20,
        )
        val f3 = finding(
            path = "src/main/java/com/haradan/domain/service/AdvertService.java",
            title = "Swallowed exception",
            severity = Severity.MAJOR,
            line = 30,
        )
        val f4 = finding(
            path = "src/main/java/com/haradan/domain/mapper/AdvertMapper.java",
            title = "Null map",
            severity = Severity.MINOR,
            line = 40,
        )
        return ReviewResult(
            commitHash = "abc",
            commitMessage = "multi",
            reviewedAtEpochMs = 1,
            fileReviews = listOf(
                FileReview(f1.filePath, "c", listOf(f1, f2)),
                FileReview(f3.filePath, "s", listOf(f3)),
                FileReview(f4.filePath, "m", listOf(f4)),
            ),
        )
    }

    private fun finding(
        path: String,
        title: String,
        severity: Severity,
        line: Int = 1,
    ) = Finding(
        id = "$title-$line",
        filePath = path,
        severity = severity,
        category = FindingCategory.BUG,
        title = title,
        message = "m",
        howToFix = "f",
        line = line,
    )
}
