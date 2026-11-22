package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The only data path in or out of the vault's sealed storage.
 *
 * Backups travel exclusively through the Storage Access Framework: the user
 * picks a document, we write/read exactly that URI once, and no persistable
 * grant is requested. There is no clipboard fallback and no share sheet, so the
 * encrypted envelope cannot be silently read by another app.
 */
object BackupFileIo {

    /** Hard ceiling on an imported backup; anything larger is not a real envelope. */
    const val MAX_BACKUP_BYTES: Long = 8L * 1024 * 1024

    suspend fun writeText(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
            stream.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Returns null for unreadable or oversized documents. */
    suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = readBounded(input, MAX_BACKUP_BYTES) ?: return@withContext null
                String(bytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads at most [maxBytes] from [input]. The previous implementation called
     * `readBytes()` and compared afterwards: a content provider that streams more
     * than the limit (or an enormous user file) allocated everything first and
     * could OOM the process, which `catch (Exception)` cannot catch.
     *
     * Visible for the robustness tests.
     */
    internal fun readBounded(input: InputStream, maxBytes: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Real file name for status messages. SAF URIs end in an opaque document id
     * ("14"), so ask the provider for OpenableColumns.DISPLAY_NAME first.
     */
    fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.settings_backup_file_default)
    }
}
