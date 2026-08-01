package com.kcodereview.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.kcodereview.model.ChangedFile
import java.security.MessageDigest

/**
 * Remembers that the user already saw a pre-commit block for a given staged snapshot.
 * Second Commit click with the same staged fingerprint is allowed without re-blocking.
 */
@Service(Service.Level.PROJECT)
class PreCommitOverrideService(@Suppress("unused") private val project: Project) {

    @Volatile
    private var armedFingerprint: String? = null

    fun isOverrideArmedFor(fingerprint: String): Boolean =
        armedFingerprint != null && armedFingerprint == fingerprint

    fun arm(fingerprint: String) {
        armedFingerprint = fingerprint
    }

    fun consume(fingerprint: String): Boolean {
        if (!isOverrideArmedFor(fingerprint)) {
            if (armedFingerprint != null && armedFingerprint != fingerprint) {
                clear()
            }
            return false
        }
        clear()
        return true
    }

    fun clear() {
        armedFingerprint = null
    }

    companion object {
        fun fingerprint(files: List<ChangedFile>): String {
            val material = files
                .sortedBy { it.path }
                .joinToString("\n") { file ->
                    "${file.path}|${file.changeType}|${file.content.length}|${sha256(file.content)}|${file.patch?.length ?: 0}"
                }
            return sha256(material)
        }

        private fun sha256(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
