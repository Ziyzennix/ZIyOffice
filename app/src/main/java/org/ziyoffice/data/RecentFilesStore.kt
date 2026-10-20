package org.ziyoffice.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import org.ziyoffice.model.DocumentKind
import org.ziyoffice.model.RecentFile

/**
 * Recently opened files and the SAF grants behind them, in SharedPreferences
 * plus JSON so M0 needs no extra dependency.
 *
 * Nothing here is ever transmitted, and android:allowBackup is off.
 */
class RecentFilesStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<RecentFile> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        RecentFile(
                            uri = obj.getString("uri"),
                            displayName = obj.getString("name"),
                            sizeBytes = obj.optLong("size", -1L),
                            lastOpenedAtEpochMs = obj.optLong("at", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Persists the read/write grant for [uri] and records it in the recents list.
     * Write access is what makes in-place saving possible; a provider that only
     * grants reading still works (saving then reports the limitation honestly).
     *
     * [Remembered.persisted] is false when the provider handed over a one-shot
     * grant (a share or view intent, typically). The file is readable for this
     * session but could not be reopened from a recents row later, so it is not
     * written to the list.
     */
    fun remember(uri: Uri, mimeType: String? = null): Remembered? {
        val persisted = persistGrant(uri)
        val entry = entryFor(uri, mimeType) ?: return null
        if (persisted) record(entry)
        return Remembered(entry, persisted)
    }

    /** A file the store has seen, and whether its grant survives a restart. */
    data class Remembered(val file: RecentFile, val persisted: Boolean)

    /**
     * Takes a persistable read/write grant for [uri]; read-only is accepted when
     * the provider refuses write access. Returns false when neither is possible,
     * which is the normal case for files shared from another app.
     */
    private fun persistGrant(uri: Uri): Boolean {
        val resolver = appContext.contentResolver
        val readWrite = runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        if (readWrite) return true
        return runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
    }

    private fun entryFor(uri: Uri, mimeType: String?): RecentFile? {
        val (name, size) = queryMetadata(uri) ?: return null
        // Some providers report no usable display name and the fallback is the
        // document id itself ("document:1000142160"). Showing that in the title
        // bar is meaningless to a reader, so the file is named after its type
        // instead, with the intent's MIME type supplying the extension — that
        // type is authoritative and is all that decides how it opens.
        val named = DocumentKind.fromFileName(name) != DocumentKind.UNKNOWN
        val displayName = if (named) {
            name
        } else {
            val extension = DocumentKind.extensionFor(mimeType) ?: return null
            val kind = DocumentKind.fromMimeType(mimeType) ?: DocumentKind.UNKNOWN
            "${kind.label}.$extension"
        }
        return RecentFile(
            uri = uri.toString(),
            displayName = displayName,
            sizeBytes = size,
            lastOpenedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun record(entry: RecentFile) {
        val updated = load().filterNot { it.uri == entry.uri }
            .toMutableList()
            .apply { add(0, entry) }
            .take(MAX_ENTRIES)
        save(updated)
    }

    /**
     * Name and size from the provider. Falls back to the uri's last path segment
     * when the provider cannot be queried — an expired or absent grant, or a
     * provider that went away. That is a normal outcome for a shared file, and
     * it must not crash: the editor then reports the real read failure itself.
     */
    private fun queryMetadata(uri: Uri): Pair<String, Long>? {
        var name: String? = null
        var size = -1L
        runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        val resolvedName = name ?: uri.lastPathSegment ?: return null
        return resolvedName to size
    }

    private fun save(entries: List<RecentFile>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("uri", entry.uri)
                    put("name", entry.displayName)
                    put("size", entry.sizeBytes)
                    put("at", entry.lastOpenedAtEpochMs)
                }
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "recent_files"
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 30
    }
}
