package com.kcodereview.model

enum class Severity(val rank: Int, val displayName: String) {
    BLOCKER(1, "Blocker"),
    CRITICAL(2, "Critical"),
    MAJOR(3, "Major"),
    MINOR(4, "Minor"),
    INFO(5, "Info");

    companion object {
        fun from(raw: String?): Severity {
            if (raw.isNullOrBlank()) return INFO
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: INFO
        }
    }
}
