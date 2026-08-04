package com.kcodereview.ui

import com.kcodereview.model.FileReview
import com.kcodereview.model.Finding
import com.kcodereview.model.FindingCategory
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity

object DemoFindings {
    fun reviewResult(): ReviewResult {
        val controllerFindings = listOf(
            Finding(
                id = "demo-1",
                filePath = "src/main/java/com/haradan/domain/controller/AdvertController.java",
                severity = Severity.CRITICAL,
                category = FindingCategory.VULNERABILITY,
                title = "Hardcoded API secret in controller",
                message = "API keys must never be stored in source. Prefer env / secret managers so credentials are not leaked via VCS.",
                howToFix = "1) Move the secret to environment / vault.\n2) Inject via configuration.\n3) Rotate any key that was committed.",
                fixedCode = "@Value(\"\${app.api.secret}\")\nprivate String apiSecret;",
                line = 64,
                ruleKey = "hardcoded-secret",
            ),
            Finding(
                id = "demo-2",
                filePath = "src/main/java/com/haradan/domain/controller/AdvertController.java",
                severity = Severity.MAJOR,
                category = FindingCategory.BUG,
                title = "String comparison with ==",
                message = "Reference equality on Strings is incorrect; use equals().",
                howToFix = "1) Replace == with Objects.equals or String.equals.\n2) Add a unit test for the branch.",
                fixedCode = "if (Objects.equals(status, expected)) { ... }",
                line = 112,
                ruleKey = "string-equality",
            ),
        )
        val serviceFindings = listOf(
            Finding(
                id = "demo-3",
                filePath = "src/main/java/com/haradan/domain/service/AdvertService.java",
                severity = Severity.MAJOR,
                category = FindingCategory.BUG,
                title = "Exception swallowed returning null",
                message = "Catching Exception and returning null hides failures and causes NPEs downstream.",
                howToFix = "1) Log with context.\n2) Rethrow a domain exception or return Optional.empty().",
                fixedCode = "catch (Exception ex) {\n  log.error(\"Failed to load advert {}\", id, ex);\n  throw new AdvertNotFoundException(id, ex);\n}",
                line = 88,
                ruleKey = "swallowed-exception",
            ),
        )
        val mapperFindings = listOf(
            Finding(
                id = "demo-4",
                filePath = "src/main/java/com/haradan/domain/mapper/AdvertMapper.java",
                severity = Severity.MINOR,
                category = FindingCategory.CODE_SMELL,
                title = "Nullable mapping without null check",
                message = "Mapper may NPE when source nested fields are null.",
                howToFix = "1) Use null-safe mapping.\n2) Or document non-null contract on DTO.",
                fixedCode = "@Mapping(target = \"city\", source = \"address.city\", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)",
                line = 24,
                ruleKey = "null-safe-map",
            ),
        )
        return ReviewResult(
            commitHash = "DEMO",
            commitMessage = "Demo: multi-class Code Analysis grouping",
            reviewedAtEpochMs = System.currentTimeMillis(),
            fileReviews = listOf(
                FileReview(
                    filePath = "src/main/java/com/haradan/domain/controller/AdvertController.java",
                    summary = "Controller issues",
                    findings = controllerFindings,
                ),
                FileReview(
                    filePath = "src/main/java/com/haradan/domain/service/AdvertService.java",
                    summary = "Service issues",
                    findings = serviceFindings,
                ),
                FileReview(
                    filePath = "src/main/java/com/haradan/domain/mapper/AdvertMapper.java",
                    summary = "Mapper issues",
                    findings = mapperFindings,
                ),
            ),
        )
    }
}
