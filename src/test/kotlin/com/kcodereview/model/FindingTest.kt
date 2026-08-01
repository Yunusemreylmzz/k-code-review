package com.kcodereview.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FindingTest {

    @Test
    fun `location label includes line when present`() {
        val withLine = Finding(
            id = "1",
            filePath = "src/A.kt",
            severity = Severity.MAJOR,
            category = FindingCategory.BUG,
            title = "t",
            message = "m",
            howToFix = "f",
            line = 12,
        )
        assertEquals("src/A.kt:12", withLine.locationLabel())
        assertEquals("src/A.kt", withLine.copy(line = null).locationLabel())
    }
}
