/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.uiptv.util;

import java.io.IOException;
import java.util.Locale;

public class SystemUtils {
    private SystemUtils() {
    }

    public static final boolean IS_OS_WINDOWS;
    public static final boolean IS_OS_LINUX;
    public static final boolean IS_OS_MAC_OSX;

    static {
        boolean isWindows = false;
        boolean isLinux = false;
        boolean isMacOsx = false;
        try {
            String osName = System.getProperty("os.name");
            if (osName == null) {
                throw new IOException("os.name not found");
            }
            osName = osName.toLowerCase(Locale.ENGLISH);
            // match
            if (osName.contains("windows")) {
                isWindows = true;
            } else if (osName.contains("linux") ||
                    osName.contains("mpe/ix") ||
                    osName.contains("freebsd") ||
                    osName.contains("openbsd") ||
                    osName.contains("irix") ||
                    osName.contains("digital unix") ||
                    osName.contains("unix")
            ) {
                isLinux = true;
            } else if (osName.contains("mac os x")) {
                isMacOsx = true;
            } else if (osName.contains("sun os") ||
                    osName.contains("sunos") ||
                    osName.contains("solaris")) {
                // POSIX Unix variants do not need extra flags here.
            } else if (osName.contains("hp-ux") || osName.contains("aix")) {
                // POSIX Unix variants do not need extra flags here.
            }

        } catch (final Exception _) {
            // Leave OS flags false if detection fails.
        }
        IS_OS_WINDOWS = isWindows;
        IS_OS_LINUX = isLinux;
        IS_OS_MAC_OSX = isMacOsx;
    }
}
