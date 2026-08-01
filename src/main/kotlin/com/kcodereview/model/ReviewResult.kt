package com.kcodereview.model

data class FileReview(
    val filePath: String,
    val summary: String,
    val findings: List<Finding>,
)

data class ReviewResult(
    val commitHash: String,
    val commitMessage: String,
    val reviewedAtEpochMs: Long,
    val fileReviews: List<FileReview>,
) {
    val findings: List<Finding>
        get() = fileReviews.flatMap { it.findings }.sorted()

    fun countBySeverity(): Map<Severity, Int> =
        Severity.entries.associateWith { severity -> findings.count { it.severity == severity } }

    fun countByCategory(): Map<FindingCategory, Int> =
        FindingCategory.entries.associateWith { category -> findings.count { it.category == category } }

    val totalFindings: Int get() = findings.size
}
