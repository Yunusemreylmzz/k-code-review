package com.kcodereview.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChangeSetMergerTest {

    data class F(val path: String, val body: String)

    @Test
    fun `prefers unstaged when both staged and unstaged exist`() {
        val staged = listOf(F("src/A.java", "staged-A"), F("src/B.java", "staged-B"))
        val unstaged = listOf(F("src/B.java", "work-B"), F("src/C.java", "work-C"))
        val merged = ChangeSetMerger.mergeByPath(staged, unstaged) { it.path }
        assertEquals(
            listOf("src/A.java", "src/B.java", "src/C.java"),
            merged.map { it.path },
        )
        assertEquals("staged-A", merged[0].body)
        assertEquals("work-B", merged[1].body) // working tree wins
        assertEquals("work-C", merged[2].body)
    }

    @Test
    fun `normalizes windows separators`() {
        val staged = listOf(F("src\\A.java", "a"))
        val unstaged = listOf(F("src/A.java", "b"))
        val merged = ChangeSetMerger.mergeByPath(staged, unstaged) { it.path }
        assertEquals(1, merged.size)
        assertEquals("b", merged.single().body)
    }
}
