package com.kcodereview.model

data class Finding(
    val id: String,
    val filePath: String,
    val severity: Severity,
    val category: FindingCategory,
    val title: String,
    val message: String,
    val howToFix: String,
    val fixedCode: String? = null,
    val line: Int? = null,
    val ruleKey: String? = null,
) : Comparable<Finding> {
    override fun compareTo(other: Finding): Int {
        val bySeverity = severity.rank.compareTo(other.severity.rank)
        if (bySeverity != 0) return bySeverity
        val byFile = filePath.compareTo(other.filePath)
        if (byFile != 0) return byFile
        return (line ?: Int.MAX_VALUE).compareTo(other.line ?: Int.MAX_VALUE)
    }

    fun locationLabel(): String =
        if (line != null) "$filePath:$line" else filePath
}
