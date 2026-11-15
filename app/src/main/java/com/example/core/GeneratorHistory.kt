package com.example.core

/**
 * In-memory history for the generator tab.
 *
 * Deliberately NOT persisted: generated credentials are secrets, so they live in
 * RAM only, are capped, and are dropped when the vault locks (the ViewModel
 * clears this on lock).
 */
data class GeneratedCredential(
    val value: String,
    val isPassphrase: Boolean,
    val createdAtMillis: Long,
) {
    val length: Int get() = value.length
}

class GeneratorHistory(private val maxEntries: Int = 10) {

    private val entries = ArrayDeque<GeneratedCredential>()

    init {
        require(maxEntries >= 1) { "history must keep at least one entry" }
    }

    fun add(credential: GeneratedCredential) {
        if (credential.value.isBlank()) return
        // Re-generating can produce the same value only for tiny alphabets; keep
        // the list free of immediate duplicates but allow repeats across time.
        if (entries.firstOrNull()?.value == credential.value) return
        entries.addFirst(credential)
        while (entries.size > maxEntries) entries.removeLast()
    }

    fun snapshot(): List<GeneratedCredential> = entries.toList()

    fun clear() = entries.clear()

    val size: Int get() = entries.size
}
