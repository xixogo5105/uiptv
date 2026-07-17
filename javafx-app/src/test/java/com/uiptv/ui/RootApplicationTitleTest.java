package com.uiptv.ui;

import com.uiptv.widget.AppNavigationController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RootApplicationTitleTest {
    @AfterEach
    void resetNavigationController() {
        AppNavigationController.reset();
    }

    @Test
    void applicationTitleUsesSectionFirstAndNoStatusText() throws Exception {
        AppNavigationController.setCurrentTarget(AppNavigationController.Target.BOOKMARKS);

        assertEquals("Favourite - UIPTV", buildApplicationTitle());
    }

    private static String buildApplicationTitle() throws Exception {
        Method method = RootApplication.class.getDeclaredMethod("buildApplicationTitle");
        method.setAccessible(true);
        return (String) method.invoke(new RootApplication());
    }
}
