package com.uiptv.util.json

import com.uiptv.api.JsonCompliant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private val kJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

class KJsonObject() {
    companion object {
        @JvmField
        val NULL: Any? = null
    }

    private val values: LinkedHashMap<String, Any?> = LinkedHashMap()

    constructor(rawJson: String) : this() {
        if (rawJson.isBlank()) return
        val parsed = kJson.parseToJsonElement(rawJson).jsonObject
        parsed.forEach { (key, value) -> values[key] = value.toNodeValue() }
    }

    constructor(seed: Map<String, *>) : this() {
        seed.forEach { (key, value) -> values[key] = value.toNodeValue() }
    }

    fun put(key: String, value: Any?): KJsonObject {
        values[key] = value.toNodeValue()
        return this
    }

    @JvmOverloads
    fun optString(key: String, defaultValue: String = ""): String =
        values[key].asStringOrNull() ?: defaultValue

    fun getString(key: String): String =
        values[key].asStringOrNull()
            ?: throw IllegalArgumentException("Missing string value for key '$key'")

    fun optJSONObject(key: String): KJsonObject? = values[key] as? KJsonObject

    fun getJSONObject(key: String): KJsonObject =
        optJSONObject(key) ?: throw IllegalArgumentException("Missing object value for key '$key'")

    fun optJSONArray(key: String): KJsonArray? = values[key] as? KJsonArray

    fun getJSONArray(key: String): KJsonArray =
        optJSONArray(key) ?: throw IllegalArgumentException("Missing array value for key '$key'")

    @JvmOverloads
    fun optInt(key: String, defaultValue: Int = 0): Int =
        values[key].asIntOrNull() ?: defaultValue

    @JvmOverloads
    fun optBoolean(key: String, defaultValue: Boolean = false): Boolean =
        values[key].asBooleanOrNull() ?: defaultValue

    @JvmOverloads
    fun optDouble(key: String, defaultValue: Double = 0.0): Double =
        values[key].asDoubleOrNull() ?: defaultValue

    fun opt(key: String): Any? = values[key]

    fun get(key: String): Any =
        values[key] ?: throw IllegalArgumentException("Missing value for key '$key'")

    fun has(key: String): Boolean = values.containsKey(key)

    fun length(): Int = values.size

    val isEmpty: Boolean
        get() = values.isEmpty()

    fun keys(): Set<String> = LinkedHashSet(values.keys)

    fun toMap(): Map<String, Any?> = values.mapValues { (_, value) -> value.toPlainValue() }

    fun toJsonObject(): JsonObject =
        JsonObject(values.mapValues { (_, value) -> value.toJsonElement() })

    override fun toString(): String = toJsonObject().toString()
}

class KJsonArray() {
    private val values: MutableList<Any?> = ArrayList()

    constructor(rawJson: String) : this() {
        if (rawJson.isBlank()) return
        val parsed = kJson.parseToJsonElement(rawJson).jsonArray
        parsed.forEach { values += it.toNodeValue() }
    }

    constructor(seed: Iterable<*>) : this() {
        seed.forEach { values += it.toNodeValue() }
    }

    internal constructor(seed: List<Any?>) : this() {
        values.addAll(seed)
    }

    fun put(value: Any?): KJsonArray {
        values += value.toNodeValue()
        return this
    }

    fun opt(index: Int): Any? = values.getOrNull(index)

    fun get(index: Int): Any =
        values.getOrNull(index) ?: throw IllegalArgumentException("Missing value at index $index")

    fun length(): Int = values.size

    val isEmpty: Boolean
        get() = values.isEmpty()

    fun toList(): List<Any?> = values.map { it.toPlainValue() }

    fun optJSONObject(index: Int): KJsonObject? = values.getOrNull(index) as? KJsonObject

    fun getJSONObject(index: Int): KJsonObject =
        optJSONObject(index) ?: throw IllegalArgumentException("Missing object value at index $index")

    @JvmOverloads
    fun optString(index: Int, defaultValue: String = ""): String =
        values.getOrNull(index).asStringOrNull() ?: defaultValue

    fun toJsonArray(): JsonArray = JsonArray(values.map { it.toJsonElement() })

    override fun toString(): String = toJsonArray().toString()
}

private fun JsonElement.toNodeValue(): Any? =
    when (this) {
        JsonNull -> null
        is JsonObject -> KJsonObject(mapValues { (_, value) -> value.toNodeValue() })
        is JsonArray -> KJsonArray(map { it.toNodeValue() })
        is JsonPrimitive -> booleanOrNull ?: intOrNull ?: doubleOrNull ?: content
    }

private fun Any?.toNodeValue(): Any? =
    when (this) {
        is KJsonObject, is KJsonArray,
        null, is String, is Number, is Boolean -> this
        is JsonCompliant -> KJsonObject(toJson())
        is JsonElement -> toNodeValue()
        is Map<*, *> -> KJsonObject(
            entries
                .filter { it.key != null }
                .associate { it.key.toString() to it.value.toNodeValue() }
        )
        is Iterable<*> -> KJsonArray(map { it.toNodeValue() })
        is Array<*> -> KJsonArray(map { it.toNodeValue() })
        else -> toString()
    }

private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is KJsonObject -> toJsonObject()
        is KJsonArray -> toJsonArray()
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }

private fun Any?.toPlainValue(): Any? =
    when (this) {
        is KJsonObject -> toMap()
        is KJsonArray -> toList()
        else -> this
    }

private fun Any?.asStringOrNull(): String? =
    when (this) {
        null -> null
        is String -> this
        is Number, is Boolean -> toString()
        else -> toString()
    }

private fun Any?.asIntOrNull(): Int? =
    when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }

private fun Any?.asDoubleOrNull(): Double? =
    when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }

private fun Any?.asBooleanOrNull(): Boolean? =
    when (this) {
        is Boolean -> this
        is String -> when {
            equals("true", true) -> true
            equals("false", true) -> false
            else -> null
        }
        else -> null
    }
