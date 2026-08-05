package com.kcodereview.log

import com.kcodereview.model.FileReview
import com.kcodereview.model.Finding
import com.kcodereview.model.FindingCategory
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity

/**
 * Shared sample context for Settings → Example request body (slim schema).
 */
object ReviewLogExample {

    val result: ReviewResult = ReviewResult(
        commitHash = "LOCAL",
        commitMessage = "Local working tree changes",
        reviewedAtEpochMs = 0L,
        fileReviews = listOf(
            FileReview(
                filePath = "src/main/java/com/acme/AdvertController.java",
                summary = "Controller issues",
                findings = listOf(
                    Finding(
                        id = "ex-1",
                        filePath = "src/main/java/com/acme/AdvertController.java",
                        severity = Severity.CRITICAL,
                        category = FindingCategory.VULNERABILITY,
                        title = "Hardcoded secret",
                        message = "API key must not live in source.",
                        howToFix = "Move to env / vault.",
                        line = 64,
                        ruleKey = "hardcoded-secret",
                    ),
                    Finding(
                        id = "ex-2",
                        filePath = "src/main/java/com/acme/AdvertController.java",
                        severity = Severity.MAJOR,
                        category = FindingCategory.BUG,
                        title = "NPE risk",
                        message = "Possible null.",
                        howToFix = "Guard.",
                        line = 80,
                        ruleKey = "npe",
                    ),
                    Finding(
                        id = "ex-3",
                        filePath = "src/main/java/com/acme/AdvertController.java",
                        severity = Severity.MINOR,
                        category = FindingCategory.CODE_SMELL,
                        title = "Unused import",
                        message = "Remove unused import.",
                        howToFix = "Delete.",
                        line = 3,
                        ruleKey = "unused-import",
                    ),
                ),
            ),
            FileReview(
                filePath = "src/main/java/com/acme/BannerService.java",
                summary = "Service issues",
                findings = emptyList(),
            ),
        ),
    )

    val git: GitProjectInfo = GitProjectInfo(
        username = "jane.doe",
        userEmail = "jane@example.com",
        repoName = "backend",
        repoOwner = "acme",
        repoFullName = "acme/backend",
        remoteUrl = "https://github.com/acme/backend.git",
        branch = "main",
    )

    val context: ReviewLogContext = ReviewLogContext(
        result = result,
        git = git,
        projectName = "backend",
        projectBasePath = "/Users/jane/dev/backend",
        modelName = "Gemini 3.5 Flash",
        pluginVersion = "1.0.15",
        reviewAuthor = "jane.doe",
        timestampIso = "2026-08-04T14:00:00Z",
    )
}
