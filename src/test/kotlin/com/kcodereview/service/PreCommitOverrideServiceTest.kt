package com.kcodereview.service

import com.kcodereview.model.ChangeType
import com.kcodereview.model.ChangedFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreCommitOverrideServiceTest {

    @Test
    fun `fingerprint is stable for same files regardless of order`() {
        val files = listOf(
            ChangedFile("A.kt", "class A {}", "diff", ChangeType.MODIFIED),
            ChangedFile("B.kt", "class B {}", null, ChangeType.ADDED),
        )
        assertEquals(
            PreCommitOverrideService.fingerprint(files),
            PreCommitOverrideService.fingerprint(files.reversed()),
        )
    }

    @Test
    fun `fingerprint changes when content changes`() {
        val a = listOf(ChangedFile("A.kt", "v1", null, ChangeType.MODIFIED))
        val b = listOf(ChangedFile("A.kt", "v2", null, ChangeType.MODIFIED))
        assertFalse(PreCommitOverrideService.fingerprint(a) == PreCommitOverrideService.fingerprint(b))
    }

    @Test
    fun `arm then consume clears override`() {
        val svc = TestableOverride()
        val fp = "abc"
        assertFalse(svc.consume(fp))
        svc.arm(fp)
        assertTrue(svc.isOverrideArmedFor(fp))
        assertTrue(svc.consume(fp))
        assertFalse(svc.isOverrideArmedFor(fp))
        assertFalse(svc.consume(fp))
    }

    @Test
    fun `different fingerprint after arm does not consume`() {
        val svc = TestableOverride()
        svc.arm("old")
        assertFalse(svc.consume("new"))
        assertFalse(svc.isOverrideArmedFor("old"))
    }

    /** Mirrors PreCommitOverrideService without IntelliJ Project. */
    private class TestableOverride {
        private var armedFingerprint: String? = null

        fun isOverrideArmedFor(fingerprint: String): Boolean =
            armedFingerprint != null && armedFingerprint == fingerprint

        fun arm(fingerprint: String) {
            armedFingerprint = fingerprint
        }

        fun consume(fingerprint: String): Boolean {
            if (!isOverrideArmedFor(fingerprint)) {
                if (armedFingerprint != null && armedFingerprint != fingerprint) {
                    armedFingerprint = null
                }
                return false
            }
            armedFingerprint = null
            return true
        }
    }
}
