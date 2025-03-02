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

    /**
     * Singleton instance, used mainly for testing.
     */
    private static final SystemUtils INSTANCE = new SystemUtils();

    /**
     * Operating system state flag for error.
     */
    private static final int INIT_PROBLEM = -1;
    /**
     * Operating system state flag for neither Unix nor Windows.
     */
    private static final int OTHER = 0;
    /**
     * Operating system state flag for Windows.
     */
    private static final int WINDOWS = 1;
    /**
     * Operating system state flag for Unix.
     */
    private static final int UNIX = 2;
    private static final int MAX_OSX = 14;
    /**
     * Operating system state flag for Posix flavour Unix.
     */
    private static final int POSIX_UNIX = 3;

    /**
     * The operating system flag.
     */
    private static final int OS;
    /**
     * The path to df
     */
    private static final String DF;
    public static boolean IS_OS_WINDOWS;
    public static boolean IS_OS_LINUX;
    public static boolean IS_OS_MAC_OSX;

    static {
        int os = OTHER;
        String dfPath = "df";
        try {
            String osName = System.getProperty("os.name");
            if (osName == null) {
                throw new IOException("os.name not found");
            }
            osName = osName.toLowerCase(Locale.ENGLISH);
            // match
            if (osName.contains("windows")) {
                os = WINDOWS;
                IS_OS_WINDOWS = true;
            } else if (osName.contains("linux") ||
                    osName.contains("mpe/ix") ||
                    osName.contains("freebsd") ||
                    osName.contains("openbsd") ||
                    osName.contains("irix") ||
                    osName.contains("digital unix") ||
                    osName.contains("unix")
            ) {
                os = UNIX;
                IS_OS_LINUX = true;
            } else if (osName.contains("mac os x")) {
                os = MAX_OSX;
                IS_OS_MAC_OSX = true;
            } else if (osName.contains("sun os") ||
                    osName.contains("sunos") ||
                    osName.contains("solaris")) {
                os = POSIX_UNIX;
                dfPath = "/usr/xpg4/bin/df";
            } else if (osName.contains("hp-ux") ||
                    osName.contains("aix")) {
                os = POSIX_UNIX;
            }

        } catch (final Exception ex) {
            os = INIT_PROBLEM;
        }
        OS = os;
        DF = dfPath;
    }
}
