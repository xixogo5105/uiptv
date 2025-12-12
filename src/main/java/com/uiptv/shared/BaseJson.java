package com.uiptv.shared;


import com.uiptv.api.JsonCompliant;
import org.json.JSONObject;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class BaseJson implements Serializable, JsonCompliant {
    @Override
    public String toJson() {
        Map<String, Object> map = new HashMap<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(this);
                if (field.getType() == boolean.class) {
                    map.put(field.getName(), (boolean) value ? "1" : "0");
                } else {
                    map.put(field.getName(), value);
                }
            } catch (IllegalAccessException e) {
                // This should not happen given field.setAccessible(true)
            }
        }
        return new JSONObject(map).toString();
    }

    @Override
    public String toString() {
        return toJson();
    }
}
