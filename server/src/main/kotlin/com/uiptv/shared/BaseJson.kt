package com.uiptv.shared

import com.uiptv.api.JsonCompliant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.Serializable
import java.lang.reflect.Modifier

open class BaseJson : Serializable, JsonCompliant {
    override fun toJson(): String {
        val source = this
        return buildJsonObject {
            for (field in source.javaClass.declaredFields) {
                val ignoredField = Modifier.isStatic(field.modifiers) || Modifier.isTransient(field.modifiers)
                if (ignoredField || (!field.canAccess(source) && !field.trySetAccessible())) {
                    continue
                }
                try {
                    val value = field.get(source)
                    when {
                        field.type == Boolean::class.javaPrimitiveType ->
                            put(field.name, JsonPrimitive(if (value as Boolean) "1" else "0"))
                        else -> put(field.name, value.toJsonElement())
                    }
                } catch (_: IllegalAccessException) {
                }
            }
        }.toString()
    }

    override fun toString(): String = toJson()

    private fun Any?.toJsonElement(): JsonElement =
        when (this) {
            null -> JsonPrimitive("")
            is JsonElement -> this
            is JsonCompliant -> parseJsonElement(toJson())
            is Map<*, *> -> JsonObject(
                entries
                    .filter { it.key != null }
                    .associate { it.key.toString() to it.value.toJsonElement() }
            )
            is Iterable<*> -> buildJsonArray {
                for (item in this@toJsonElement) {
                    add(item.toJsonElement())
                }
            }
            is Array<*> -> buildJsonArray {
                for (index in this@toJsonElement.indices) {
                    add(this@toJsonElement[index].toJsonElement())
                }
            }
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is String -> JsonPrimitive(this)
            else -> JsonPrimitive(toString())
        }

    private fun parseJsonElement(raw: String): JsonElement =
        try {
            Json.parseToJsonElement(raw)
        } catch (_: Exception) {
            JsonPrimitive(raw)
        }
}
