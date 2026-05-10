package com.uiptv.util

import org.json.JSONArray
import org.json.JSONObject

object XtremeCredentialsJson {
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_DEFAULT = "default"

    data class Entry(val username: String, val password: String, val isDefault: Boolean) {
        fun username(): String = username

        fun password(): String = password
    }

    @JvmStatic
    fun parse(rawJson: String?): List<Entry> {
        if (StringUtils.isBlank(rawJson)) {
            return ArrayList()
        }
        return try {
            val array = JSONArray(rawJson!!.trim())
            val entries = ArrayList<Entry>()
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val username = obj.optString(KEY_USERNAME, "").trim()
                val password = obj.optString(KEY_PASSWORD, "")
                val isDefault = obj.optBoolean(KEY_DEFAULT, false)
                if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
                    entries.add(Entry(username, password, isDefault))
                }
            }
            normalize(entries, null)
        } catch (_: Exception) {
            ArrayList()
        }
    }

    @JvmStatic
    fun normalize(entries: List<Entry>?, defaultUsername: String?): List<Entry> {
        if (entries.isNullOrEmpty()) {
            return ArrayList()
        }
        val normalized = dedupe(entries)
        val withDefault = applyDefault(normalized, defaultUsername)
        return ensureDefault(withDefault)
    }

    @JvmStatic
    fun resolveDefault(entries: List<Entry>?): Entry? {
        if (entries.isNullOrEmpty()) {
            return null
        }
        for (entry in entries) {
            if (entry.isDefault) {
                return entry
            }
        }
        return entries[0]
    }

    @JvmStatic
    fun toJson(entries: List<Entry>?): String {
        if (entries.isNullOrEmpty()) {
            return ""
        }
        val normalized = normalize(entries, null)
        val array = JSONArray()
        for (entry in normalized) {
            val obj = JSONObject()
            obj.put(KEY_USERNAME, entry.username)
            obj.put(KEY_PASSWORD, entry.password)
            if (entry.isDefault) {
                obj.put(KEY_DEFAULT, true)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun dedupe(entries: List<Entry>): List<Entry> {
        val unique = LinkedHashMap<String, Entry>()
        for (entry in entries) {
            val username = entry.username.trim()
            val password = entry.password
            if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
                val key = username + "\u0000" + password
                unique.computeIfAbsent(key) { Entry(username, password, entry.isDefault) }
            }
        }
        return ArrayList(unique.values)
    }

    private fun applyDefault(entries: List<Entry>, defaultUsername: String?): List<Entry> {
        var defaultFound = false
        val withDefault = ArrayList<Entry>(entries.size)
        if (StringUtils.isNotBlank(defaultUsername)) {
            val normalizedDefault = defaultUsername!!.trim()
            for (entry in entries) {
                val isDefault = !defaultFound && entry.username == normalizedDefault
                if (isDefault) {
                    defaultFound = true
                }
                withDefault.add(Entry(entry.username, entry.password, isDefault))
            }
        } else {
            for (entry in entries) {
                val isDefault = entry.isDefault && !defaultFound
                if (isDefault) {
                    defaultFound = true
                }
                withDefault.add(Entry(entry.username, entry.password, isDefault))
            }
        }
        return withDefault
    }

    private fun ensureDefault(entries: List<Entry>): List<Entry> {
        if (entries.isEmpty()) {
            return entries
        }
        if (entries.any { it.isDefault }) {
            return entries
        }
        val updated = ArrayList(entries)
        val first = updated[0]
        updated[0] = Entry(first.username, first.password, true)
        return updated
    }
}
