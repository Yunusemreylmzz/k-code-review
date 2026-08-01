package com.kcodereview.ui

import com.kcodereview.model.FileReview
import com.kcodereview.model.Finding
import com.kcodereview.model.FindingCategory
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity

object DemoFindings {
    fun reviewResult(): ReviewResult {
        val findings = listOf(
            Finding(
                id = "demo-1",
                filePath = "src/main/kotlin/com/kcodereview/service/CodeReviewService.kt",
                severity = Severity.CRITICAL,
                category = FindingCategory.VULNERABILITY,
                title = "Hardcoded secret risk in configuration path",
                message = "API keys must never be stored in source or logged. Prefer PasswordSafe / secret managers so credentials are not leaked via VCS or crash dumps.",
                howToFix = "1) Keep secrets only in PasswordSafe.\n2) Never print the key.\n3) Rotate any key that was shared in chat or tickets.",
                fixedCode = "fun getApiKey(): String =\n    PasswordSafe.instance.getPassword(attrs).orEmpty()",
                line = 64,
                ruleKey = "hardcoded-secret",
            ),
            Finding(
                id = "demo-2",
                filePath = "src/main/kotlin/com/kcodereview/ui/ReviewToolWindowPanel.kt",
                severity = Severity.MAJOR,
                category = FindingCategory.BUG,
                title = "Navigate without null-safe file lookup",
                message = "If the project base path or virtual file is missing, navigation should fail gracefully instead of silently doing nothing without user feedback.",
                howToFix = "1) Resolve VirtualFile safely.\n2) If missing, show a balloon.\n3) Prefer relative project paths from the review payload.",
                fixedCode = "val vf = LocalFileSystem.getInstance()\n    .findFileByPath(\"\$base/\${finding.filePath}\")\n    ?: return",
                line = 220,
                ruleKey = "null-safe-nav",
            ),
            Finding(
                id = "demo-3",
                filePath = "src/main/kotlin/com/kcodereview/commit/KCodeReviewCheckinHandlerFactory.kt",
                severity = Severity.MINOR,
                category = FindingCategory.CODE_SMELL,
                title = "PasswordSafe read on EDT",
                message = "Credential store access is a slow operation and must not run on the Event Dispatch Thread during commit checks.",
                howToFix = "1) Read the API key inside Task.Modal / background thread.\n2) Or wrap with SlowOperations.knownIssue for UI-only settings reads.",
                fixedCode = "ProgressManager.getInstance().run(object : Task.Modal(...) {\n  override fun run(indicator: ProgressIndicator) {\n    val key = settings.getApiKey()\n  }\n})",
                line = 46,
                ruleKey = "edt-slow-op",
            ),
        )
        return ReviewResult(
            commitHash = "DEMO",
            commitMessage = "Demo Code Analysis panel",
            reviewedAtEpochMs = System.currentTimeMillis(),
            fileReviews = listOf(
                FileReview(
                    filePath = "demo",
                    summary = "Demo warnings to verify left list + right inspector layout.",
                    findings = findings,
                ),
            ),
        )
    }
}
