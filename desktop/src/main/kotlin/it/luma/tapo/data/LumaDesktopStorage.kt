package it.luma.tapo.data

import org.json.JSONObject
import java.io.File

/**
 * File-backed persistence that mirrors the small subset of the Android SharedPreferences API
 * used by [LumaService], so the exact same service logic runs on the desktop without Android.
 *
 * The state is stored as a single JSON document inside the user's Luma folder.
 */
class LumaDesktopStorage private constructor(private val file: File) {

    private val lock = Any()
    private var map: MutableMap<String, String> = mutableMapOf()

    init {
        map = readFromFile()
    }

    fun getString(key: String, defValue: String?): String? = synchronized(lock) {
        map[key] ?: defValue
    }

    fun contains(key: String): Boolean = synchronized(lock) {
        map.containsKey(key)
    }

    fun edit(): Editor = Editor()

    private fun readFromFile(): MutableMap<String, String> {
        return try {
            if (!file.exists()) return mutableMapOf()
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val out = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                out[key] = obj.optString(key, "")
            }
            out
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun persist() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(JSONObject(map as Map<*, *>).toString(), Charsets.UTF_8)
        } catch (_: Exception) {
            // Ignore write errors: keep the in-memory state usable
        }
    }

    inner class Editor {
        private val changes = mutableMapOf<String, String?>()

        fun putString(key: String, value: String): Editor {
            changes[key] = value
            return this
        }

        fun remove(key: String): Editor {
            changes[key] = null
            return this
        }

        fun apply() {
            synchronized(lock) {
                changes.forEach { (key, value) ->
                    if (value == null) map.remove(key) else map[key] = value
                }
                changes.clear()
                persist()
            }
        }
    }

    companion object {
        @Synchronized
        fun createDefault(): LumaDesktopStorage {
            val dir = File(System.getProperty("user.home"), "Luma")
            if (!dir.exists()) dir.mkdirs()
            return LumaDesktopStorage(File(dir, "luma_storage.json"))
        }
    }
}
