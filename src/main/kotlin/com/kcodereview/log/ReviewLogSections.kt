package com.kcodereview.log

import com.kcodereview.model.Severity
import java.time.Instant

/** Top-level: `"event"`. */
object MetaEventSection : ReviewLogSection {
    override fun key(): String = "event"
    override fun contribute(ctx: ReviewLogContext): Any = ReviewLogPayloadBuilder.EVENT
}

/** Top-level: `"timestamp"`. */
object TimestampLogSection : ReviewLogSection {
    override fun key(): String = "timestamp"
    override fun contribute(ctx: ReviewLogContext): Any =
        ctx.timestampIso ?: Instant.now().toString()
}

/** Top-level: `"pluginVersion"`. */
object PluginVersionLogSection : ReviewLogSection {
    override fun key(): String = "pluginVersion"
    override fun contribute(ctx: ReviewLogContext): Any = ctx.pluginVersion
}

/**
 * Top-level: `"git"` — identity only (no email / remote URL).
 */
object GitLogSection : ReviewLogSection {
    override fun key(): String = "git"
    override fun contribute(ctx: ReviewLogContext): Any = linkedMapOf(
        "username" to ctx.git.username,
        "repoName" to ctx.git.repoName,
        "branch" to ctx.git.branch,
    )
}

/**
 * Top-level: `"project"` — name only.
 */
object ProjectLogSection : ReviewLogSection {
    override fun key(): String = "project"
    override fun contribute(ctx: ReviewLogContext): Any = linkedMapOf(
        "name" to ctx.projectName,
    )
}

/**
 * Top-level: `"review"` — counts + basics; no finding/issue details.
 *
 * Extend with [ReviewLogSchema.withReviewFields].
 */
class ReviewDetailsSection(
    val fields: List<ReviewLogField>,
) : ReviewLogSection {
    override fun key(): String = "review"

    override fun contribute(ctx: ReviewLogContext): Any =
        linkedMapOf<String, Any?>().apply {
            fields.forEach { field ->
                val value = field.contribute(ctx)
                if (value != null) put(field.key(), value)
            }
        }

    override fun example(): Any =
        linkedMapOf<String, Any?>().apply {
            fields.forEach { field ->
                val value = field.example()
                if (value != null) put(field.key(), value)
            }
        }
}

/**
 * Default slim `review.*` fields (totals + severity counts only).
 */
object DefaultReviewFields {

    val author = field("author") { ctx ->
        ctx.reviewAuthor.ifBlank { ctx.git.username }
            .ifBlank { System.getProperty("user.name").orEmpty() }
    }
    val model = field("model") { it.modelName }
    val fileCount = field("fileCount") { it.result.fileReviews.size }
    val totalFindings = field("totalFindings") { it.result.totalFindings }
    val bySeverity = field("bySeverity") { ctx ->
        val counts = ctx.result.countBySeverity()
        linkedMapOf(
            "blocker" to (counts[Severity.BLOCKER] ?: 0),
            "critical" to (counts[Severity.CRITICAL] ?: 0),
            "major" to (counts[Severity.MAJOR] ?: 0),
            "minor" to (counts[Severity.MINOR] ?: 0),
            "info" to (counts[Severity.INFO] ?: 0),
        )
    }

    val all: List<ReviewLogField> = listOf(
        author,
        model,
        fileCount,
        totalFindings,
        bySeverity,
    )

    fun field(key: String, value: (ReviewLogContext) -> Any?): ReviewLogField =
        object : ReviewLogField {
            override fun key(): String = key
            override fun contribute(ctx: ReviewLogContext): Any? = value(ctx)
        }
}
