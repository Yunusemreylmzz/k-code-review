package com.kcodereview.log

/**
 * Ordered registry of payload sections.
 *
 * Extend without editing core code:
 * ```
 * ReviewLogSchema.default()
 *   .withReviewFields(MyField)
 *   .plus(MyTopLevelSection)
 * ```
 */
data class ReviewLogSchema(
    val sections: List<ReviewLogSection>,
) {
    fun plus(vararg extra: ReviewLogSection): ReviewLogSchema =
        copy(sections = sections + extra.toList())

    fun withReviewFields(vararg extraFields: ReviewLogField): ReviewLogSchema {
        val current = sections.filterIsInstance<ReviewDetailsSection>().firstOrNull()?.fields
            ?: DefaultReviewFields.all
        val others = sections.filterNot { it is ReviewDetailsSection }
        return copy(sections = others + ReviewDetailsSection(current + extraFields.toList()))
    }

    companion object {
        fun default(): ReviewLogSchema = ReviewLogSchema(
            sections = listOf(
                MetaEventSection,
                TimestampLogSection,
                PluginVersionLogSection,
                GitLogSection,
                ProjectLogSection,
                ReviewDetailsSection(DefaultReviewFields.all),
            ),
        )
    }
}
