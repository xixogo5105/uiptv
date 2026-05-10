package com.uiptv.shared

import com.uiptv.api.JsonCompliant
import org.json.JSONObject
import java.io.Serializable
import java.lang.reflect.Modifier

open class BaseJson : Serializable, JsonCompliant {
    override fun toJson(): String {
        val map = LinkedHashMap<String, Any?>()
        for (field in javaClass.declaredFields) {
            val ignoredField = Modifier.isStatic(field.modifiers) || Modifier.isTransient(field.modifiers)
            if (ignoredField || (!field.canAccess(this) && !field.trySetAccessible())) {
                continue
            }
            try {
                val value = field.get(this)
                if (field.type == Boolean::class.javaPrimitiveType) {
                    map[field.name] = if (value as Boolean) "1" else "0"
                } else {
                    map[field.name] = value
                }
            } catch (_: IllegalAccessException) {
            }
        }
        return JSONObject(map as Map<*, *>).toString()
    }

    override fun toString(): String = toJson()
}
