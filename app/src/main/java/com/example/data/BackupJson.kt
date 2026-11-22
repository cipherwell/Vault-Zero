package com.example.data

import org.json.JSONObject

/**
 * Guarded JSON entry point for attacker-supplied backup files.
 *
 * `org.json` is a recursive-descent parser: a file with tens of thousands of
 * nested arrays/objects throws `StackOverflowError`, which is an `Error`, not an
 * `Exception`, so a normal try/catch would let it kill the process. Backups are
 * exactly the kind of file an attacker can hand to a user, so parse behind a
 * cheap depth pre-scan and a belt-and-braces `StackOverflowError` catch.
 */
internal object BackupJson {

    /** Far above any real envelope (which is depth 3–5), far below parser stack limits. */
    const val MAX_DEPTH = 64

    fun parse(json: String): JSONObject {
        if (exceedsDepth(json, MAX_DEPTH)) {
            throw VaultException(VaultError.INVALID_JSON, "file is not valid JSON")
        }
        return try {
            JSONObject(json)
        } catch (e: StackOverflowError) {
            // Some org.json builds recurse before our scan sees the whole string.
            throw VaultException(VaultError.INVALID_JSON, "file is not valid JSON")
        } catch (e: Exception) {
            throw VaultException(VaultError.INVALID_JSON, "file is not valid JSON")
        }
    }

    /**
     * Counts `{`/`[` nesting outside JSON strings. Not a validator: only depth
     * matters here, the real parser still rejects malformed content.
     */
    internal fun exceedsDepth(json: String, maxDepth: Int): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (ch in json) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    if (depth > maxDepth) return true
                }
                '}', ']' -> if (depth > 0) depth--
            }
        }
        return false
    }
}
