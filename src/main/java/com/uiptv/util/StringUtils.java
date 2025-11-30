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

import com.uiptv.api.JsonCompliant;
import com.uiptv.model.Account;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Operations on {@link String} that are
 * {@code null} safe.
 *
 * <ul>
 *  <li><b>IsEmpty/IsBlank</b>
 *      - checks if a String contains text</li>
 *  <li><b>Trim/Strip</b>
 *      - removes leading and trailing whitespace</li>
 *  <li><b>Equals/Compare</b>
 *      - compares two strings in a null-safe manner</li>
 *  <li><b>startsWith</b>
 *      - check if a String starts with a prefix in a null-safe manner</li>
 *  <li><b>endsWith</b>
 *      - check if a String ends with a suffix in a null-safe manner</li>
 *  <li><b>IndexOf/LastIndexOf/Contains</b>
 *      - null-safe index-of checks
 *  <li><b>IndexOfAny/LastIndexOfAny/IndexOfAnyBut/LastIndexOfAnyBut</b>
 *      - index-of any of a set of Strings</li>
 *  <li><b>ContainsOnly/ContainsNone/ContainsAny</b>
 *      - checks if String contains only/none/any of these characters</li>
 *  <li><b>Substring/Left/Right/Mid</b>
 *      - null-safe substring extractions</li>
 *  <li><b>SubstringBefore/SubstringAfter/SubstringBetween</b>
 *      - substring extraction relative to other strings</li>
 *  <li><b>Split/Join</b>
 *      - splits a String into an array of substrings and vice versa</li>
 *  <li><b>Remove/Delete</b>
 *      - removes part of a String</li>
 *  <li><b>Replace/Overlay</b>
 *      - Searches a String and replaces one String with another</li>
 *  <li><b>Chomp/Chop</b>
 *      - removes the last part of a String</li>
 *  <li><b>AppendIfMissing</b>
 *      - appends a suffix to the end of the String if not present</li>
 *  <li><b>PrependIfMissing</b>
 *      - prepends a prefix to the start of the String if not present</li>
 *  <li><b>LeftPad/RightPad/Center/Repeat</b>
 *      - pads a String</li>
 *  <li><b>UpperCase/LowerCase/SwapCase/Capitalize/Uncapitalize</b>
 *      - changes the case of a String</li>
 *  <li><b>CountMatches</b>
 *      - counts the number of occurrences of one String in another</li>
 *  <li><b>IsAlpha/IsNumeric/IsWhitespace/IsAsciiPrintable</b>
 *      - checks the characters in a String</li>
 *  <li><b>DefaultString</b>
 *      - protects against a null input String</li>
 *  <li><b>Rotate</b>
 *      - rotate (circular shift) a String</li>
 *  <li><b>Reverse/ReverseDelimited</b>
 *      - reverses a String</li>
 *  <li><b>Abbreviate</b>
 *      - abbreviates a string using ellipses or another given String</li>
 *  <li><b>Difference</b>
 *      - compares Strings and reports on their differences</li>
 *  <li><b>LevenshteinDistance</b>
 *      - the number of changes needed to change one String into another</li>
 * </ul>
 *
 * <p>The {@link StringUtils} class defines certain words related to
 * String handling.</p>
 *
 * <ul>
 *  <li>null - {@code null}</li>
 *  <li>empty - a zero-length string ({@code ""})</li>
 *  <li>space - the space character ({@code ' '}, char 32)</li>
 *  <li>whitespace - the characters defined by {@link Character#isWhitespace(char)}</li>
 *  <li>trim - the characters &lt;= 32 as in {@link String#trim()}</li>
 * </ul>
 *
 * <p>{@link StringUtils} handles {@code null} input Strings quietly.
 * That is to say that a {@code null} input will return {@code null}.
 * Where a {@code boolean} or {@code int} is being returned
 * details vary by method.</p>
 *
 * <p>A side effect of the {@code null} handling is that a
 * {@link NullPointerException} should be considered a bug in
 * {@link StringUtils}.</p>
 *
 * <p>Methods in this class include sample code in their Javadoc comments to explain their operation.
 * The symbol {@code *} is used to indicate any input including {@code null}.</p>
 *
 * <p>#ThreadSafe#</p>
 *
 * @see String
 * @since 1.0
 */
//@Immutable
public class StringUtils {

    public static final String SPACE = " ";
    /**
     * The empty String {@code ""}.
     *
     * @since 2.0
     */
    public static final String EMPTY = "";
    /**
     * A String for linefeed LF ("\n").
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.10.6">JLF: Escape Sequences
     * for Character and String Literals</a>
     * @since 3.2
     */
    public static final String LF = "\n";
    /**
     * A String for carriage return CR ("\r").
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.10.6">JLF: Escape Sequences
     * for Character and String Literals</a>
     * @since 3.2
     */
    public static final String CR = "\r";
    /**
     * Represents a failed index search.
     *
     * @since 2.1
     */
    public static final int INDEX_NOT_FOUND = -1;
    /**
     * The maximum size to which the padding constant(s) can expand.
     */
    private static final int PAD_LIMIT = 8192;
    private static final Pattern STRIP_ACCENTS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+"); //$NON-NLS-1$

    /**
     * {@link StringUtils} instances should NOT be constructed in
     * standard programming. Instead, the class should be used as
     * {@code StringUtils.trim(" foo ");}.
     *
     * <p>This constructor is public to permit tools that require a JavaBean
     * instance to operate.</p>
     */
    private StringUtils() {
        super();
    }

    public static boolean isBlank(final CharSequence cs) {
        final int strLen = length(cs);
        if (strLen == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(final CharSequence cs) {
        return !isBlank(cs);
    }

    public static int length(final CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }

    public static String nullSafeEncode(String s) {
        return encode(isNotBlank(s) ? s : EMPTY, UTF_8);
    }

    public static String safeGetString(JSONObject jsonCategory, String key) {
        try {
            return String.valueOf(jsonCategory.get(key));
        } catch (Exception ignored) {

        }
        return null;
    }

    public static String safeGetString(Map map, String key) {
        try {
            return String.valueOf(map.get(key));
        } catch (Exception ignored) {

        }
        return null;
    }

    public static String safeJson(String val) {
        try {
            if (!isBlank(val)) {
                return val.replaceAll("\\p{C}", "").replace("\\", "\\\\").replace("\"", "\\\"");
            }
        } catch (Exception ignored) {

        }
        return EMPTY;
    }

    public static String getXtremeStreamUrl(Account account, String streamId, String extension) {
        switch (account.getAction()) {
            case vod:
                return account.getM3u8Path() + "movie/" + account.getUsername() + "/" + account.getPassword() + "/" + streamId + "." + extension;
            case series:
                return account.getM3u8Path() + "series/" + account.getUsername() + "/" + account.getPassword() + "/" + streamId + "." + extension;
        }
        return account.getM3u8Path() + account.getUsername() + "/" + account.getPassword() + "/" + streamId;
    }

    public static <T extends JsonCompliant> String toJson(T t) {
        return t.toJson();
    }

    public static String[] split(String str) {
        if (isBlank(str)) {
            return new String[0];
        }
        return str.split(SPACE);
    }

    public static String safeUtf(String input) {
        if (isBlank(input)) return "";
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return "";
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
        byte[] bytes = normalized.getBytes(UTF_8);
        return new String(bytes, UTF_8);
    }
}
