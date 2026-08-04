package com.kcodereview.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdeChangeCollectorTest {

    @Test
    fun `toRelativePath maps absolute file under project`() {
        val rel = IdeChangeCollector.toRelativePath(
            "/Users/x/proj",
            "/Users/x/proj/src/main/java/com/acme/BannerService.java",
        )
        assertEquals("src/main/java/com/acme/BannerService.java", rel)
    }

    @Test
    fun `toRelativePath rejects files outside project`() {
        assertNull(
            IdeChangeCollector.toRelativePath(
                "/Users/x/proj",
                "/Users/x/other/BannerService.java",
            ),
        )
    }

    @Test
    fun `isReviewable filters binaries and lockfiles`() {
        assertTrue(IdeChangeCollector.isReviewable("src/A.java"))
        assertFalse(IdeChangeCollector.isReviewable("lib/x.jar"))
        assertFalse(IdeChangeCollector.isReviewable("package-lock.json"))
    }

    @Test
    fun `merger keeps both staged controller and unstaged service`() {
        data class F(val path: String)
        val staged = listOf(F("src/main/java/com/haradan/domain/controller/AdvertController.java"))
        val unstaged = listOf(F("src/main/java/com/haradan/domain/service/BannerService.java"))
        val merged = ChangeSetMerger.mergeByPath(staged, unstaged) { it.path }
        assertEquals(2, merged.size)
        assertEquals(
            listOf("AdvertController.java", "BannerService.java"),
            merged.map { it.path.substringAfterLast('/') },
        )
    }
}
