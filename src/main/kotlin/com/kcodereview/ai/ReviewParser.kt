package com.kcodereview.ai

import com.google.gson.JsonParser
import com.kcodereview.model.Finding
import com.kcodereview.model.FindingCategory
import com.kcodereview.model.Severity
import java.util.UUID

object ReviewParser {

    data class ParsedFileReview(
        val summary: String,
        val findings: List<Finding>,
    )

    fun parse(filePath: String, rawAiResponse: String): ParsedFileReview {
        val jsonText = extractJson(rawAiResponse)
        val root = JsonParser.parseString(jsonText).asJsonObject
        val summary = root.get("summary")?.asString?.trim().orEmpty().ifBlank {
            "No summary provided"
        }
        val findingsArray = root.getAsJsonArray("findings")
        val findings = mutableListOf<Finding>()
        if (findingsArray != null) {
            for (element in findingsArray) {
                val obj = element.asJsonObject
                val howToFix = obj.get("howToFix")?.asString?.trim().orEmpty()
                    .ifBlank { "No remediation steps provided." }
                val fixedCode = obj.get("fixedCode")?.asString?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: extractCodeFromHowToFix(howToFix)

                findings += Finding(
                    id = UUID.randomUUID().toString(),
                    filePath = filePath,
                    severity = Severity.from(obj.get("severity")?.asString),
                    category = FindingCategory.from(obj.get("category")?.asString),
                    title = obj.get("title")?.asString?.trim().orEmpty().ifBlank { "Untitled finding" },
                    message = obj.get("message")?.asString?.trim().orEmpty(),
                    howToFix = stripCodeFencesFromSteps(howToFix),
                    fixedCode = fixedCode,
                    line = obj.get("line")?.takeIf { !it.isJsonNull }?.asInt,
                    ruleKey = obj.get("ruleKey")?.asString,
                )
            }
        }
        return ParsedFileReview(summary = summary, findings = findings.sorted())
    }

    fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!fenced.isNullOrBlank() && fenced.startsWith("{")) return fenced

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI response did not contain JSON object" }
        return trimmed.substring(start, end + 1)
    }

    internal fun extractCodeFromHowToFix(howToFix: String): String? {
        val fenced = Regex("```(?:\\w+)?\\s*([\\s\\S]*?)```")
            .find(howToFix)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        return fenced?.takeIf { it.isNotBlank() }
    }

    private fun stripCodeFencesFromSteps(howToFix: String): String =
        howToFix.replace(Regex("```(?:\\w+)?\\s*[\\s\\S]*?```"), "")
            .trim()
            .ifBlank { howToFix.trim() }
}
