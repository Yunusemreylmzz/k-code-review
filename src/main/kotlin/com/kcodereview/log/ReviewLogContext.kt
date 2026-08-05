package com.kcodereview.log

import com.kcodereview.model.ReviewResult

/**
 * Immutable input for assembling a review-log JSON payload.
 * New optional context data can be added here without breaking section APIs.
 */
data class ReviewLogContext(
    val result: ReviewResult,
    val git: GitProjectInfo,
    val projectName: String,
    val projectBasePath: String,
    val modelName: String,
    val pluginVersion: String,
    val reviewAuthor: String = git.username,
    val timestampIso: String? = null,
    /** Extension bag for custom integrations (key → value). */
    val extras: Map<String, Any?> = emptyMap(),
)
