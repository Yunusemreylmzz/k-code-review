package com.kcodereview.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SettingsConnectionTesterTest {

    @Test
    fun `formatSuccess joins ok labels`() {
        val results = listOf(
            SettingsConnectionTester.CheckResult("LLM API key", true, "responded (2 chars)"),
            SettingsConnectionTester.CheckResult("Prompt URL", true, "fetched (100 chars)"),
            SettingsConnectionTester.CheckResult("Log API URL", true, "HTTP 202"),
        )
        assertEquals(
            "✅ OK — LLM API key · Prompt URL · Log API URL",
            SettingsConnectionTester.formatSuccess(results),
        )
    }

    @Test
    fun `formatFailure includes label and detail`() {
        val failed = SettingsConnectionTester.CheckResult("Log API URL", false, "HTTP 404")
        assertEquals("❌ Log API URL: HTTP 404", SettingsConnectionTester.formatFailure(failed))
    }

    @Test
    fun `validateHttpUrl rejects non-http schemes`() {
        val ex = assertThrows<IllegalArgumentException> {
            SettingsConnectionTester.validateHttpUrl("ftp://example.com", "Log API URL")
        }
        assertTrue(ex.message!!.contains("http://"))
    }

    @Test
    fun `validateHttpUrl accepts https`() {
        SettingsConnectionTester.validateHttpUrl("https://api.example.com/logs", "Log API URL")
    }
}
