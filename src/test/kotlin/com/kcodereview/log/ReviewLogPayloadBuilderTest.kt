package com.kcodereview.log

import com.kcodereview.model.FileReview
import com.kcodereview.model.Finding
import com.kcodereview.model.FindingCategory
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitProjectInfoTest {

    @Test
    fun `parseRemote handles https github url`() {
        val (owner, repo) = GitProjectInfo.parseRemote("https://github.com/acme/backend.git")
        assertEquals("acme", owner)
        assertEquals("backend", repo)
    }

    @Test
    fun `parseRemote handles scp style url`() {
        val (owner, repo) = GitProjectInfo.parseRemote("git@github.com:acme/backend.git")
        assertEquals("acme", owner)
        assertEquals("backend", repo)
    }

    @Test
    fun `parseRemote handles nested group path`() {
        val (owner, repo) = GitProjectInfo.parseRemote("https://gitlab.com/group/sub/backend.git")
        assertEquals("group/sub", owner)
        assertEquals("backend", repo)
    }

    @Test
    fun `parseRemote blank returns empty`() {
        assertEquals("" to "", GitProjectInfo.parseRemote("  "))
    }
}

class ReviewLogPayloadBuilderTest {

    @Test
    fun `example template is slim with required keys and no issue details`() {
        val json = ReviewLogPayloadBuilder.exampleTemplateJson()
        assertTrue(json.contains("\"event\""))
        assertTrue(json.contains(ReviewLogPayloadBuilder.EVENT))
        assertTrue(json.contains("\"git\""))
        assertTrue(json.contains("\"username\""))
        assertTrue(json.contains("\"repoName\""))
        assertTrue(json.contains("\"bySeverity\""))
        assertTrue(json.contains("\"critical\""))
        assertTrue(json.contains("\"totalFindings\""))
        assertFalse(json.contains("\"findings\""))
        assertFalse(json.contains("\"fileSummaries\""))
        assertFalse(json.contains("\"className\""))
        assertFalse(json.contains("Hardcoded secret"))
        assertFalse(json.contains("\"userEmail\""))
        assertFalse(json.contains("\"basePath\""))
        assertFalse(json.contains("\"remoteUrl\""))
    }

    @Test
    fun `build includes basics and severity counts only`() {
        val json = ReviewLogPayloadBuilder.build(
            result = sampleResult(),
            git = sampleGit(),
            projectName = "haradan",
            projectBasePath = "/tmp/haradan",
            modelName = "Gemini 3.5 Flash",
            pluginVersion = "1.0.15",
            reviewAuthor = "omer",
        )
        assertTrue(json.contains("\"username\":\"omer\""))
        assertTrue(json.contains("\"repoName\":\"haradan\""))
        assertTrue(json.contains("\"branch\":\"main\""))
        assertTrue(json.contains("\"name\":\"haradan\""))
        assertTrue(json.contains("\"totalFindings\":2"))
        assertTrue(json.contains("\"critical\":1"))
        assertTrue(json.contains("\"major\":1"))
        assertTrue(json.contains("\"minor\":0"))
        assertFalse(json.contains("\"findings\""))
        assertFalse(json.contains("NPE"))
        assertFalse(json.contains("AQ."))
        assertFalse(json.contains("\"userEmail\""))
        assertFalse(json.contains("/tmp/haradan"))
    }

    @Test
    fun `toMap bySeverity matches finding severities`() {
        val result = ReviewResult(
            commitHash = "a",
            commitMessage = "m",
            reviewedAtEpochMs = 1,
            fileReviews = listOf(
                FileReview(
                    "src/A.java",
                    "s",
                    listOf(
                        finding("1", Severity.CRITICAL),
                        finding("2", Severity.CRITICAL),
                        finding("3", Severity.MINOR),
                        finding("4", Severity.INFO),
                    ),
                ),
            ),
        )
        val map = ReviewLogPayloadBuilder.toMap(
            result = result,
            git = GitProjectInfo.EMPTY,
            projectName = "p",
            projectBasePath = "/p",
            modelName = "m",
            pluginVersion = "1",
        )
        @Suppress("UNCHECKED_CAST")
        val review = map["review"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val bySeverity = review["bySeverity"] as Map<String, Int>
        assertEquals(4, review["totalFindings"])
        assertEquals(1, review["fileCount"])
        assertEquals(0, bySeverity["blocker"])
        assertEquals(2, bySeverity["critical"])
        assertEquals(0, bySeverity["major"])
        assertEquals(1, bySeverity["minor"])
        assertEquals(1, bySeverity["info"])
        assertFalse(review.containsKey("findings"))
        assertFalse(review.containsKey("classes"))
        assertFalse(review.containsKey("fileSummaries"))
    }

    @Test
    fun `git section omits email remote and owner`() {
        val map = ReviewLogPayloadBuilder.toMap(
            result = sampleResult(),
            git = sampleGit(),
            projectName = "haradan",
            projectBasePath = "/tmp/haradan",
            modelName = "m",
            pluginVersion = "1",
        )
        @Suppress("UNCHECKED_CAST")
        val git = map["git"] as Map<String, Any?>
        assertEquals(setOf("username", "repoName", "branch"), git.keys)
        @Suppress("UNCHECKED_CAST")
        val project = map["project"] as Map<String, Any?>
        assertEquals(setOf("name"), project.keys)
    }

    @Test
    fun `schema withReviewFields extends payload without changing core`() {
        val custom = DefaultReviewFields.field("ideVersion") { "2024.3" }
        val schema = ReviewLogSchema.default().withReviewFields(custom)
        val map = ReviewLogAssembler(schema).assemble(
            ReviewLogContext(
                result = sampleResult(),
                git = sampleGit(),
                projectName = "p",
                projectBasePath = "/p",
                modelName = "m",
                pluginVersion = "1",
            ),
        )
        @Suppress("UNCHECKED_CAST")
        val review = map["review"] as Map<String, Any?>
        assertEquals("2024.3", review["ideVersion"])
        assertTrue(review.containsKey("bySeverity"))
        assertFalse(review.containsKey("findings"))
    }

    @Test
    fun `schema plus adds top-level section`() {
        val env = object : ReviewLogSection {
            override fun key() = "environment"
            override fun contribute(ctx: ReviewLogContext) = linkedMapOf("os" to "macOS")
        }
        val map = ReviewLogAssembler(ReviewLogSchema.default().plus(env)).assemble(
            ReviewLogContext(
                result = sampleResult(),
                git = sampleGit(),
                projectName = "p",
                projectBasePath = "/p",
                modelName = "m",
                pluginVersion = "1",
            ),
        )
        assertTrue(map.containsKey("environment"))
        @Suppress("UNCHECKED_CAST")
        assertEquals("macOS", (map["environment"] as Map<*, *>)["os"])
    }

    private fun sampleResult() = ReviewResult(
        commitHash = "LOCAL",
        commitMessage = "msg",
        reviewedAtEpochMs = 1L,
        fileReviews = listOf(
            FileReview(
                filePath = "src/main/java/com/acme/Foo.java",
                summary = "ok",
                findings = listOf(
                    Finding(
                        id = "1",
                        filePath = "src/main/java/com/acme/Foo.java",
                        severity = Severity.MAJOR,
                        category = FindingCategory.BUG,
                        title = "NPE",
                        message = "null risk",
                        howToFix = "fix",
                        line = 10,
                        ruleKey = "npe",
                    ),
                    Finding(
                        id = "2",
                        filePath = "src/main/java/com/acme/Foo.java",
                        severity = Severity.CRITICAL,
                        category = FindingCategory.VULNERABILITY,
                        title = "Secret",
                        message = "hardcoded",
                        howToFix = "vault",
                        line = 20,
                        ruleKey = "secret",
                    ),
                ),
            ),
        ),
    )

    private fun finding(id: String, severity: Severity) = Finding(
        id = id,
        filePath = "src/A.java",
        severity = severity,
        category = FindingCategory.BUG,
        title = "t",
        message = "m",
        howToFix = "f",
        line = 1,
        ruleKey = "r",
    )

    private fun sampleGit() = GitProjectInfo(
        username = "omer",
        userEmail = "o@x.com",
        repoName = "haradan",
        repoOwner = "recepp",
        repoFullName = "recepp/haradan",
        remoteUrl = "https://github.com/recepp/haradan.git",
        branch = "main",
    )
}

class ReviewLogClientTest {

    @Test
    fun `blank url is no-op`() {
        assertFalse(ReviewLogClient.post("", "{}"))
        assertFalse(ReviewLogClient.post("   ", "{}"))
    }
}
