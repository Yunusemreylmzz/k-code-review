package com.kcodereview.model

data class ChangedFile(
    val path: String,
    val content: String,
    val patch: String?,
    val changeType: ChangeType,
)

enum class ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    UNKNOWN,
}

data class CommitSnapshot(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val files: List<ChangedFile>,
)
