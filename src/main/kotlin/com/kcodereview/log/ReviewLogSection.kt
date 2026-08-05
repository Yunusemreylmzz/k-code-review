package com.kcodereview.log

/**
 * A top-level JSON object section (e.g. `"git"`, `"review"`).
 *
 * Open/Closed: add a new section implementation and register it via
 * [ReviewLogSchema.plus] without changing the assembler or HTTP client.
 */
interface ReviewLogSection {
    /** Top-level JSON key. */
    fun key(): String

    /** Live value for a real review. Return null to omit the key. */
    fun contribute(ctx: ReviewLogContext): Any?

    /** Sample value for the Settings example template. */
    fun example(): Any? = contribute(ReviewLogExample.context)
}

/**
 * A single field inside a nested object (typically under `"review"`).
 * Add fields by implementing this and registering with [ReviewLogSchema.withReviewFields].
 */
interface ReviewLogField {
    fun key(): String
    fun contribute(ctx: ReviewLogContext): Any?
    fun example(): Any? = contribute(ReviewLogExample.context)
}
