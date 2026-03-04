package com.uiptv.service;

import com.uiptv.db.ThemeCssOverrideDb;
import com.uiptv.model.ThemeCssOverride;

public class ThemeCssOverrideService {
    private static ThemeCssOverrideService instance;

    private ThemeCssOverrideService() {
    }

    public static synchronized ThemeCssOverrideService getInstance() {
        if (instance == null) {
            instance = new ThemeCssOverrideService();
        }
        return instance;
    }

    public ThemeCssOverride read() {
        return ThemeCssOverrideDb.get().read();
    }

    public void save(ThemeCssOverride override) {
        ThemeCssOverrideDb.get().save(override);
    }
}
