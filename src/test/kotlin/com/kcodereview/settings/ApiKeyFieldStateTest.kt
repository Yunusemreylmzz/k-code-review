package com.kcodereview.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiKeyFieldStateTest {

    @Test
    fun `empty hint reflects saved state`() {
        assertTrue(ApiKeyFieldState.emptyHint(true).contains("saved", ignoreCase = true))
        assertTrue(ApiKeyFieldState.emptyHint(false).contains("AQ"))
    }

    @Test
    fun `should not persist blank or mask`() {
        assertFalse(ApiKeyFieldState.shouldPersist(""))
        assertFalse(ApiKeyFieldState.shouldPersist("   "))
        assertFalse(ApiKeyFieldState.shouldPersist(ApiKeyFieldState.SAVED_MASK))
    }

    @Test
    fun `should persist real key`() {
        assertTrue(ApiKeyFieldState.shouldPersist("AQ.newkey"))
        assertTrue(ApiKeyFieldState.isModified("AQ.replace"))
        assertFalse(ApiKeyFieldState.isModified(""))
    }

    @Test
    fun `cleanKey strips whitespace`() {
        assertEquals("AQab", KCodeReviewSettings.cleanKey(" AQ ab "))
        assertEquals("", KCodeReviewSettings.cleanKey("   "))
    }
}
