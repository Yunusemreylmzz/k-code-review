package com.kcodereview.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.intellij.openapi.diagnostic.Logger
import com.kcodereview.model.Finding
import com.kcodereview.model.FindingCategory
import com.kcodereview.model.Severity
import java.io.StringReader
import java.util.UUID

/**
 * Parses LLM review JSON. Models often return slightly invalid JSON
 * (trailing commas, markdown fences, truncated output). This parser
 * repairs common cases instead of failing the whole review.
 */
object ReviewParser {

    private val log = Logger.getInstance(ReviewParser::class.java)

    data class ParsedFileReview(
        val summary: String,
        val findings: List<Finding>,
    )

    fun parse(filePath: String, rawAiResponse: String): ParsedFileReview {
        val jsonText = extractJson(rawAiResponse)
        val root = parseJsonObject(jsonText)
            ?: return ParsedFileReview(
                summary = "AI returned unreadable JSON; re-run review or switch model.",
                findings = emptyList(),
            )

        val summary = root.get("summary")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            .ifBlank { "No summary provided" }

        val findingsArray = root.getAsJsonArray("findings")
        val findings = mutableListOf<Finding>()
        if (findingsArray != null) {
            for (element in findingsArray) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val howToFix = stringOrEmpty(obj, "howToFix")
                    .ifBlank { "No remediation steps provided." }
                val fixedCode = stringOrEmpty(obj, "fixedCode").takeIf { it.isNotBlank() }
                    ?: extractCodeFromHowToFix(howToFix)

                findings += Finding(
                    id = UUID.randomUUID().toString(),
                    filePath = resolveFindingPath(filePath, obj),
                    severity = Severity.from(stringOrNull(obj, "severity")),
                    category = FindingCategory.from(stringOrNull(obj, "category")),
                    title = stringOrEmpty(obj, "title").ifBlank { "Untitled finding" },
                    message = stringOrEmpty(obj, "message"),
                    howToFix = stripCodeFencesFromSteps(howToFix),
                    fixedCode = fixedCode,
                    line = obj.get("line")?.takeIf { it.isJsonPrimitive && !it.isJsonNull }
                        ?.asString?.toIntOrNull(),
                    ruleKey = stringOrNull(obj, "ruleKey"),
                )
            }
        }
        return ParsedFileReview(summary = summary, findings = findings.sorted())
    }

    fun extractJson(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("\uFEFF")
            .trim()

        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val candidate = when {
            !fenced.isNullOrBlank() && fenced.contains('{') -> fenced
            trimmed.startsWith("{") -> trimmed
            else -> {
                val start = trimmed.indexOf('{')
                if (start < 0) return trimmed
                trimmed.substring(start)
            }
        }

        return extractBalancedObject(candidate) ?: candidate.trim()
    }

    /**
     * Lenient parse + repairs (trailing commas, truncated objects/arrays).
     * Returns null only when nothing salvageable remains.
     */
    internal fun parseJsonObject(jsonText: String): JsonObject? {
        val attempts = linkedSetOf(
            jsonText.trim(),
            repairCommonIssues(jsonText),
            salvageTruncatedJson(repairCommonIssues(jsonText)),
        ).filter { it.isNotBlank() }

        for (attempt in attempts) {
            val parsed = runCatching { parseLenient(attempt) }.getOrNull()
            if (parsed != null && parsed.isJsonObject) {
                return parsed.asJsonObject
            }
        }

        log.warn(
            "ReviewParser: could not parse AI JSON. Preview: " +
                jsonText.take(500).replace('\n', ' '),
        )
        return null
    }

    private fun parseLenient(json: String): JsonElement {
        val reader = JsonReader(StringReader(json)).apply {
            strictness = Strictness.LENIENT
        }
        return JsonParser.parseReader(reader)
    }

    /** Strip // and /* */ comments, trailing commas, BOM. */
    internal fun repairCommonIssues(json: String): String {
        var s = json.trim().removePrefix("\uFEFF")
        // Remove // line comments outside strings — coarse but effective for LLM output.
        s = s.replace(Regex("""(?m)^\s*//.*$"""), "")
        s = s.replace(Regex("""/\*[\s\S]*?\*/"""), "")
        // Trailing commas before } or ]
        s = s.replace(Regex(""",(\s*[}\]])"""), "$1")
        return s.trim()
    }

    /**
     * When the model hits max tokens mid-JSON, close open strings/brackets
     * and drop a trailing incomplete finding object if needed.
     */
    internal fun salvageTruncatedJson(json: String): String {
        var s = json.trim()
        if (s.isEmpty()) return s

        // If we have a findings array, try cutting back to the last complete object.
        val findingsIdx = s.indexOf("\"findings\"")
        if (findingsIdx >= 0) {
            val arrayStart = s.indexOf('[', findingsIdx)
            if (arrayStart > 0) {
                val complete = takeCompleteArrayElements(s, arrayStart)
                if (complete != null) {
                    return complete
                }
            }
        }

        // Close an open string if truncated mid-value.
        if (isInsideString(s)) {
            s += "\""
        }
        // Drop a dangling key or colon at the end.
        s = s.replace(Regex("""[,:]\s*$"""), "")
        s = s.replace(Regex(""",(\s*)$"""), "$1")

        val opens = ArrayDeque<Char>()
        var inString = false
        var escape = false
        for (c in s) {
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> opens.addLast(c)
                '}' -> if (opens.lastOrNull() == '{') opens.removeLast()
                ']' -> if (opens.lastOrNull() == '[') opens.removeLast()
            }
        }
        while (opens.isNotEmpty()) {
            s += when (opens.removeLast()) {
                '{' -> '}'
                '[' -> ']'
                else -> ""
            }
        }
        return repairCommonIssues(s)
    }

    /**
     * Rebuilds `{"summary":...,"findings":[ ...complete objs... ]}` from a truncated blob.
     */
    private fun takeCompleteArrayElements(full: String, arrayStart: Int): String? {
        val prefix = full.substring(0, arrayStart + 1) // includes '['
        val objects = mutableListOf<String>()
        var i = arrayStart + 1
        while (i < full.length) {
            while (i < full.length && (full[i].isWhitespace() || full[i] == ',')) {
                i++
            }
            if (i >= full.length) break
            if (full[i] == ']') {
                break
            }
            if (full[i] != '{') {
                // Incomplete element — stop.
                break
            }
            val end = findMatchingBrace(full, i) ?: break
            objects += full.substring(i, end + 1)
            i = end + 1
        }

        if (objects.isEmpty() && !full.contains("\"findings\"")) return null

        val summary = Regex(""""summary"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .find(full)
            ?.groupValues
            ?.getOrNull(1)
            ?: "Partial review (response truncated)"

        return buildString {
            append("{\"summary\":\"")
            append(summary.replace("\"", "\\\""))
            append("\",\"findings\":[")
            append(objects.joinToString(","))
            append("]}")
        }
    }

    private fun findMatchingBrace(s: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun extractBalancedObject(s: String): String? {
        val start = s.indexOf('{')
        if (start < 0) return null
        val end = findMatchingBrace(s, start) ?: return s.substring(start)
        return s.substring(start, end + 1)
    }

    private fun isInsideString(s: String): Boolean {
        var inString = false
        var escape = false
        for (c in s) {
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
            } else if (c == '"') {
                inString = true
            }
        }
        return inString
    }

    private fun stringOrEmpty(obj: JsonObject, key: String): String =
        stringOrNull(obj, key).orEmpty().trim()

    private fun stringOrNull(obj: JsonObject, key: String): String? {
        val el = obj.get(key) ?: return null
        if (el.isJsonNull) return null
        return when {
            el.isJsonPrimitive -> el.asString
            else -> el.toString()
        }
    }

    /**
     * Prefer an explicit path from the model when it looks like a real project path;
     * otherwise keep the file currently under review.
     */
    internal fun resolveFindingPath(reviewedFile: String, obj: JsonObject): String {
        val reported = stringOrNull(obj, "filePath")
            ?: stringOrNull(obj, "file")
            ?: stringOrNull(obj, "path")
        return resolveFindingPath(reviewedFile, reported)
    }

    internal fun resolveFindingPath(reviewedFile: String, reported: String?): String {
        val fallback = reviewedFile.trim().replace('\\', '/').trimStart('/')
        if (reported.isNullOrBlank()) return fallback

        var candidate = reported.trim().replace('\\', '/').removePrefix("./").trimStart('/')
        // Bare class / file name → attribute to the file under review.
        if ('/' !in candidate) return fallback

        val srcIdx = candidate.indexOf("src/")
        if (srcIdx > 0) candidate = candidate.substring(srcIdx)

        val lower = candidate.lowercase()
        val looksLikeSource = listOf(
            ".java", ".kt", ".kts", ".groovy", ".scala",
            ".py", ".ts", ".tsx", ".js", ".jsx", ".go", ".rs", ".cs",
        ).any { lower.endsWith(it) }
        return if (looksLikeSource) candidate else fallback
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
