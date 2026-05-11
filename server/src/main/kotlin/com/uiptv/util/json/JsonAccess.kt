package com.uiptv.util.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private val accessJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun parseJsonObject(raw: String?): JsonObject? =
    try {
        if (raw.isNullOrBlank()) null else accessJson.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) {
        null
    }

fun parseJsonArray(raw: String?): JsonArray? =
    try {
        if (raw.isNullOrBlank()) null else accessJson.parseToJsonElement(raw).jsonArray
    } catch (_: Exception) {
        null
    }

fun JsonObject.optObject(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.optArray(key: String): JsonArray? = this[key] as? JsonArray

fun JsonObject.optString(key: String, defaultValue: String = ""): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: defaultValue

fun JsonObject.optInt(key: String, defaultValue: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: defaultValue

fun JsonObject.optBoolean(key: String, defaultValue: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: defaultValue

fun JsonArray.optObject(index: Int): JsonObject? = getOrNull(index) as? JsonObject

fun JsonArray.optString(index: Int, defaultValue: String = ""): String =
    (getOrNull(index) as? JsonPrimitive)?.contentOrNull ?: defaultValue

fun JsonElement?.asJsonString(): String = this?.toString().orEmpty()

fun JsonObject.toPlainMap(): Map<String, Any?> =
    entries.associate { (key, value) -> key to value.toPlainValue() }

fun JsonArray.toPlainList(): List<Any?> =
    map { it.toPlainValue() }

fun JsonElement.toPlainValue(): Any? =
    when (this) {
        is JsonObject -> toPlainMap()
        is JsonArray -> toPlainList()
        is JsonPrimitive -> booleanOrNull ?: intOrNull ?: contentOrNull ?: content
    }
