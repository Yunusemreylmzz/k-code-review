package com.kcodereview.settings

/**
 * Settings password-field helpers.
 *
 * The field stays empty when a key is already saved (hint text explains that).
 * We never put a fake mask into the field value — that previously risked saving
 * {@code ********} as the real key or skipping a real save.
 */
object ApiKeyFieldState {

    /** Reject accidental persistence of a display placeholder. */
    const val SAVED_MASK = "********"

    fun emptyHint(hasStoredKey: Boolean): String =
        if (hasStoredKey) {
            "Key saved — leave blank to keep, type a new key to replace"
        } else {
            "AI Studio key (AQ.… or AIza…)"
        }

    fun shouldPersist(typedRaw: String): Boolean {
        val typed = typedRaw.trim()
        return typed.isNotEmpty() && typed != SAVED_MASK
    }

    fun isModified(typedRaw: String): Boolean = shouldPersist(typedRaw)
}
