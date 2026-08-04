package com.kcodereview.git

/**
 * Merges staged + unstaged file lists for local review.
 * Prefer working-tree (unstaged) content when both exist — that is what the user is editing.
 */
object ChangeSetMerger {

    fun <T> mergeByPath(
        staged: List<T>,
        unstaged: List<T>,
        pathOf: (T) -> String,
    ): List<T> {
        val stagedMap = linkedMapOf<String, T>()
        staged.forEach { stagedMap[normalize(pathOf(it))] = it }
        val unstagedMap = linkedMapOf<String, T>()
        unstaged.forEach { unstagedMap[normalize(pathOf(it))] = it }

        val order = linkedSetOf<String>()
        order.addAll(stagedMap.keys)
        order.addAll(unstagedMap.keys)

        return order.mapNotNull { key -> unstagedMap[key] ?: stagedMap[key] }
    }

    fun normalize(path: String): String =
        path.trim().replace('\\', '/').trimStart('/')
}
