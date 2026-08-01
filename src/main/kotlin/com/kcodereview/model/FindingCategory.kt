package com.kcodereview.model

enum class FindingCategory(val displayName: String) {
    BUG("Bug"),
    VULNERABILITY("Vulnerability"),
    SECURITY_HOTSPOT("Security Hotspot"),
    CODE_SMELL("Code Smell"),
    PERFORMANCE("Performance"),
    MAINTAINABILITY("Maintainability");

    companion object {
        fun from(raw: String?): FindingCategory {
            if (raw.isNullOrBlank()) return CODE_SMELL
            val normalized = raw.trim().uppercase().replace(' ', '_').replace('-', '_')
            return entries.firstOrNull { it.name == normalized } ?: CODE_SMELL
        }
    }
}
