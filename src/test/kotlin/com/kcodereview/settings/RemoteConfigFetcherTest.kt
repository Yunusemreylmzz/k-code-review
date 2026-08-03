package com.kcodereview.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemoteConfigFetcherTest {

    // -------------------------------------------------------------------------
    // URL normalisation
    // -------------------------------------------------------------------------

    @Test
    fun `toRawUrl converts github blob URL to raw URL`() {
        val blob = "https://github.com/recepp/kcodereview-settings/blob/main/key.txt"
        val expected = "https://raw.githubusercontent.com/recepp/kcodereview-settings/main/key.txt"
        assertEquals(expected, RemoteConfigFetcher.toRawUrl(blob))
    }

    @Test
    fun `toRawUrl converts github blob URL with deep path`() {
        val blob = "https://github.com/org/repo/blob/feature-branch/dir/sub/file.txt"
        val expected = "https://raw.githubusercontent.com/org/repo/feature-branch/dir/sub/file.txt"
        assertEquals(expected, RemoteConfigFetcher.toRawUrl(blob))
    }

    @Test
    fun `toRawUrl leaves raw githubusercontent URL unchanged`() {
        val raw = "https://raw.githubusercontent.com/user/repo/main/prompt.txt"
        assertEquals(raw, RemoteConfigFetcher.toRawUrl(raw))
    }

    @Test
    fun `toRawUrl leaves arbitrary HTTPS URL unchanged`() {
        val url = "https://example.com/config/key.txt"
        assertEquals(url, RemoteConfigFetcher.toRawUrl(url))
    }

    @Test
    fun `toRawUrl handles http scheme blob URL`() {
        val blob = "http://github.com/user/repo/blob/main/file.txt"
        val expected = "https://raw.githubusercontent.com/user/repo/main/file.txt"
        assertEquals(expected, RemoteConfigFetcher.toRawUrl(blob))
    }

    // -------------------------------------------------------------------------
    // Cache invalidation (unit-testable without network)
    // -------------------------------------------------------------------------

    @Test
    fun `invalidate with null clears entire cache without throwing`() {
        RemoteConfigFetcher.invalidate(null)  // should not throw
    }

    @Test
    fun `invalidate with URL does not throw for unknown URL`() {
        RemoteConfigFetcher.invalidate("https://github.com/x/y/blob/main/z.txt")
    }
}
