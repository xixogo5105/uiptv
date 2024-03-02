package com.uiptv.model;


import com.uiptv.api.JsonCompliant;
import com.uiptv.util.StringUtils;

import java.io.Serializable;

public class BaseJson implements Serializable, JsonCompliant {
    @Override
    public String toJson() {
        return StringUtils.toJson(this);
    }
}
