package com.kcodereview.log

/**
 * Merges [ReviewLogSection] contributions into a stable, ordered map.
 */
class ReviewLogAssembler(
    private val schema: ReviewLogSchema = ReviewLogSchema.default(),
) {
    fun assemble(ctx: ReviewLogContext): Map<String, Any?> =
        linkedMapOf<String, Any?>().apply {
            schema.sections.forEach { section ->
                val value = section.contribute(ctx)
                if (value != null) put(section.key(), value)
            }
        }

    fun assembleExample(): Map<String, Any?> =
        linkedMapOf<String, Any?>().apply {
            schema.sections.forEach { section ->
                val value = section.example()
                if (value != null) put(section.key(), value)
            }
        }
}
