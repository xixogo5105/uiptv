package com.uiptv.service;

import com.uiptv.db.ThemeCssOverrideDb;
import com.uiptv.model.ThemeCssOverride;

public class ThemeCssOverrideService {
    private ThemeCssOverrideService() {
    }

    private static class SingletonHelper {
        private static final ThemeCssOverrideService INSTANCE = new ThemeCssOverrideService();
    }

    public static ThemeCssOverrideService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public ThemeCssOverride read() {
        return ThemeCssOverrideDb.get().read();
    }

    public void save(ThemeCssOverride override) {
        ThemeCssOverrideDb.get().save(override);
    }
}
