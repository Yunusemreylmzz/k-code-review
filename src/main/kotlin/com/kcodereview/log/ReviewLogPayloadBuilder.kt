package com.kcodereview.log

import com.google.gson.GsonBuilder
import com.kcodereview.model.ReviewResult

/**
 * Facade: schema → assemble → JSON.
 * Prefer extending [ReviewLogSchema] / [ReviewLogField] instead of editing this class.
 */
object ReviewLogPayloadBuilder {

    const val EVENT = "code_review_completed"

    private val prettyGson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
    private val compactGson = GsonBuilder().disableHtmlEscaping().create()

    @Volatile
    var schema: ReviewLogSchema = ReviewLogSchema.default()

    private fun assembler(): ReviewLogAssembler = ReviewLogAssembler(schema)

    fun exampleTemplateJson(): String =
        prettyGson.toJson(assembler().assembleExample())

    fun build(
        result: ReviewResult,
        git: GitProjectInfo,
        projectName: String,
        projectBasePath: String,
        modelName: String,
        pluginVersion: String,
        reviewAuthor: String = git.username,
        pretty: Boolean = false,
        extras: Map<String, Any?> = emptyMap(),
    ): String {
        val ctx = ReviewLogContext(
            result = result,
            git = git,
            projectName = projectName,
            projectBasePath = projectBasePath,
            modelName = modelName,
            pluginVersion = pluginVersion,
            reviewAuthor = reviewAuthor,
            extras = extras,
        )
        return toJson(assembler().assemble(ctx), pretty)
    }

    fun toMap(
        result: ReviewResult,
        git: GitProjectInfo,
        projectName: String,
        projectBasePath: String,
        modelName: String,
        pluginVersion: String,
        reviewAuthor: String = git.username,
        extras: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val ctx = ReviewLogContext(
            result = result,
            git = git,
            projectName = projectName,
            projectBasePath = projectBasePath,
            modelName = modelName,
            pluginVersion = pluginVersion,
            reviewAuthor = reviewAuthor,
            extras = extras,
        )
        return assembler().assemble(ctx)
    }

    fun toJson(payload: Map<String, Any?>, pretty: Boolean = false): String =
        if (pretty) prettyGson.toJson(payload) else compactGson.toJson(payload)
}
