package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemUtilsTest {

    @Test
    void detectsWindowsLinuxMacAndUnknownOsNames() throws Exception {
        assertFlags("Windows 11", true, false, false);
        assertFlags("Linux", false, true, false);
        assertFlags("Mac OS X", false, false, true);
        assertFlags("Solaris", false, false, false);
    }

    private void assertFlags(String osName, boolean windows, boolean linux, boolean mac) throws Exception {
        String original = System.getProperty("os.name");
        try {
            System.setProperty("os.name", osName);
            Class<?> shadow = Class.forName("com.uiptv.util.SystemUtils", true, new ShadowLoader());
            assertEquals(windows, shadow.getField("IS_OS_WINDOWS").getBoolean(null));
            assertEquals(linux, shadow.getField("IS_OS_LINUX").getBoolean(null));
            assertEquals(mac, shadow.getField("IS_OS_MAC_OSX").getBoolean(null));
        } finally {
            if (original == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", original);
            }
        }
    }

    private static final class ShadowLoader extends ClassLoader {
        private ShadowLoader() {
            super(SystemUtils.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!"com.uiptv.util.SystemUtils".equals(name)) {
                return super.loadClass(name, resolve);
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try (InputStream input = SystemUtils.class.getClassLoader()
                        .getResourceAsStream("com/uiptv/util/SystemUtils.class")) {
                    if (input == null) {
                        throw new ClassNotFoundException(name);
                    }
                    byte[] bytes = input.readAllBytes();
                    loaded = defineClass(name, bytes, 0, bytes.length);
                } catch (Exception ex) {
                    throw new ClassNotFoundException(name, ex);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }
}
