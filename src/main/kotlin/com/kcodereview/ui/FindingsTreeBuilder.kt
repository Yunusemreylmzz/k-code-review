package com.kcodereview.ui

import com.kcodereview.model.Finding
import com.kcodereview.model.ReviewResult
import com.kcodereview.model.Severity

/**
 * Builds a SonarQube-style analysis tree: root → class/file groups → findings.
 * Pure logic (no Swing) so it is unit-testable.
 */
object FindingsTreeBuilder {

    sealed class Node {
        data class Root(
            val label: String,
            val totalFindings: Int,
            val classCount: Int,
        ) : Node()

        data class ClassGroup(
            val filePath: String,
            val className: String,
            val packagePath: String,
            val findings: List<Finding>,
            val highestSeverity: Severity,
            /** File was reviewed but produced zero findings (or review failed). */
            val reviewedCleanOrFailed: Boolean = false,
            val reviewSummary: String = "",
        ) : Node() {
            val count: Int get() = findings.size
            fun severityCounts(): Map<Severity, Int> =
                findings.groupingBy { it.severity }.eachCount()
        }

        data class FindingLeaf(val finding: Finding) : Node()
    }

    data class TreeModel(
        val root: Node.Root,
        val groups: List<Node.ClassGroup>,
    ) {
        val isEmpty: Boolean get() = groups.isEmpty()
        val firstFinding: Finding? get() = groups.firstOrNull()?.findings?.firstOrNull()
    }

    fun build(result: ReviewResult): TreeModel {
        val groups = buildGroups(result)
        val withIssues = groups.count { it.findings.isNotEmpty() }
        val root = Node.Root(
            label = when {
                groups.isEmpty() -> "No analysis results"
                result.totalFindings == 0 ->
                    "No issues found (${groups.size} class(es) reviewed)"
                withIssues == 1 ->
                    "Found ${result.totalFindings} issue(s) in 1 class" +
                        if (groups.size > 1) " (${groups.size} reviewed)" else ""
                else ->
                    "Found ${result.totalFindings} issue(s) in $withIssues classes" +
                        if (groups.size > withIssues) " (${groups.size} reviewed)" else ""
            },
            totalFindings = result.totalFindings,
            classCount = groups.size,
        )
        return TreeModel(root = root, groups = groups)
    }

    fun buildGroups(result: ReviewResult): List<Node.ClassGroup> {
        val byPath = linkedMapOf<String, Node.ClassGroup>()

        // 1) Groups from findings (may remap across files).
        for (group in groupByClass(result.findings)) {
            byPath[group.filePath] = group
        }

        // 2) Ensure every reviewed file appears, even with 0 findings.
        for (fr in result.fileReviews) {
            val path = normalizePath(fr.filePath)
            if (path.isBlank()) continue
            if (path in byPath) {
                val existing = byPath.getValue(path)
                if (existing.reviewSummary.isBlank() && fr.summary.isNotBlank()) {
                    byPath[path] = existing.copy(reviewSummary = fr.summary)
                }
                continue
            }
            byPath[path] = Node.ClassGroup(
                filePath = path,
                className = classNameOf(path),
                packagePath = packagePathOf(path),
                findings = emptyList(),
                highestSeverity = Severity.INFO,
                reviewedCleanOrFailed = true,
                reviewSummary = fr.summary,
            )
        }

        return byPath.values.sortedWith(classGroupComparator())
    }

    fun groupByClass(findings: List<Finding>): List<Node.ClassGroup> {
        if (findings.isEmpty()) return emptyList()
        return findings
            .groupBy { normalizePath(it.filePath) }
            .map { (path, items) ->
                val sorted = sortFindings(items)
                Node.ClassGroup(
                    filePath = path,
                    className = classNameOf(path),
                    packagePath = packagePathOf(path),
                    findings = sorted,
                    highestSeverity = sorted.minByOrNull { it.severity.rank }?.severity
                        ?: Severity.INFO,
                    reviewedCleanOrFailed = false,
                )
            }
            .sortedWith(classGroupComparator())
    }

    /** Severity (blocker→info), then line ascending, then title. */
    fun sortFindings(findings: List<Finding>): List<Finding> =
        findings.sortedWith(
            compareBy<Finding> { it.severity.rank }
                .thenBy { it.line ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() },
        )

    private fun classGroupComparator(): Comparator<Node.ClassGroup> =
        compareBy<Node.ClassGroup> { group ->
            // Classes with issues first (by severity), clean/failed last.
            if (group.findings.isEmpty()) 100 else group.highestSeverity.rank
        }
            .thenByDescending { it.count }
            .thenBy { it.className.lowercase() }

    fun classNameOf(filePath: String): String {
        val name = filePath.substringAfterLast('/').substringAfterLast('\\')
        val withoutExt = name.substringBeforeLast('.', name)
        return withoutExt.ifBlank { name.ifBlank { "Unknown" } }
    }

    fun packagePathOf(filePath: String): String {
        val normalized = normalizePath(filePath)
        val slash = normalized.lastIndexOf('/')
        if (slash <= 0) return ""
        val dir = normalized.substring(0, slash)
        val markers = listOf(
            "src/main/java/",
            "src/main/kotlin/",
            "src/test/java/",
            "src/test/kotlin/",
            "src/",
        )
        for (marker in markers) {
            val idx = dir.indexOf(marker)
            if (idx >= 0) {
                return dir.substring(idx + marker.length).replace('/', '.')
            }
        }
        return dir
    }

    fun normalizePath(path: String): String =
        path.trim().replace('\\', '/').trimStart('/')
}
