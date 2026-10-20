package org.ziyoffice.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import org.ziyoffice.model.RecentFile

/**
 * Local-only store for recently opened files and the persisted Storage Access
 * Framework grants behind them. Backed by SharedPreferences + JSON so M0 needs
 * zero extra dependencies; replaced by Room when the editor lands in M1.
 *
 * Nothing in here is ever transmitted or backed up (android:allowBackup="false").
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
     * Returns the stored entry, or null if the file metadata could not be read.
     */
    fun remember(uri: Uri): RecentFile? {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        val (name, size) = queryMetadata(uri) ?: return null
        val entry = RecentFile(
            uri = uri.toString(),
            displayName = name,
            sizeBytes = size,
            lastOpenedAtEpochMs = System.currentTimeMillis(),
        )
        val updated = load().filterNot { it.uri == entry.uri }
            .toMutableList()
            .apply { add(0, entry) }
            .take(MAX_ENTRIES)
        save(updated)
        return entry
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long>? {
        var name: String? = null
        var size = -1L
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
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
