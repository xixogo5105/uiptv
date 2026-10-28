package com.uiptv.util;

import com.sun.net.httpserver.HttpExchange;
import com.uiptv.api.JsonCompliant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.StringUtils.isNotBlank;

public class ServerUtils {
    /** Maximum JSON/text request body accepted by the local HTTP API. */
    public static final int MAX_REQUEST_BODY_BYTES = 8 * 1024 * 1024;
    private static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private ServerUtils() {
    }

    public static final String CONTENT_TYPE_HTML = "text/html";
    public static final String CONTENT_TYPE_JAVASCRIPT = "text/javascript";
    public static final String CONTENT_TYPE_CSS = "text/css";
    public static final String CONTENT_TYPE_TEXT = "text/plain";
    public static final String CONTENT_TYPE_TS = "video/mp2t";
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_M3U8 = "vnd.apple.mpegurl";
    // Removed CONTENT_TYPE_JAVASCRIPT and CONTENT_TYPE_CSS from DOWNLOADABLE to allow inline execution/rendering
    private static final List<String> DOWNLOADABLE = Arrays.asList(CONTENT_TYPE_TS);

    private static Map<String, String> queryToMap(String query) {
        if (query == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            if (param == null || param.isEmpty()) continue;
            String[] entry = param.split("=", 2);
            String rawKey = entry[0];
            String rawValue = entry.length > 1 ? entry[1] : "";
            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    public static String objectToJson(List<? extends JsonCompliant> readCase) {
        if (readCase != null && !readCase.isEmpty()) {
            StringBuilder jsonArrayString = new StringBuilder("[");
            readCase.forEach(channel -> jsonArrayString.append(channel.toJson()).append(","));
            jsonArrayString.deleteCharAt(jsonArrayString.length() - 1);
            return jsonArrayString.append("]").toString();
        }
        return "[]";
    }

    public static String getParam(HttpExchange httpExchange, String key) {
        Map<String, String> params = queryToMap(httpExchange.getRequestURI().getRawQuery());
        return params.get(key);
    }

    public static void generateHtmlResponse(HttpExchange httpExchange, String response) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_HTML, null);
    }

    public static void generateJsonResponse(HttpExchange httpExchange, String response) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_JSON, null);
    }

    public static void writeJsonResponse(HttpExchange httpExchange, int statusCode, String response) throws IOException {
        writeResponse(httpExchange, statusCode, response == null ? new byte[0] : response.getBytes(StandardCharsets.UTF_8),
                CONTENT_TYPE_JSON + "; charset=UTF-8");
    }

    public static void generateJavascriptResponse(HttpExchange httpExchange, String response, String fileName) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_JAVASCRIPT, fileName);
    }

    public static void generateCssResponse(HttpExchange httpExchange, String response, String fileName) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_CSS, fileName);
    }

    public static void generateM3u8Response(HttpExchange httpExchange, String response, String fileName) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_M3U8, fileName);
    }
    public static void generateTs8Response(HttpExchange httpExchange, String response, String fileName) throws IOException {
        generateResponse(httpExchange, response, CONTENT_TYPE_TS, fileName);
    }

    public static void generateResponseText(HttpExchange httpExchange, int statusCode, String response) throws IOException {
        writeResponse(httpExchange, statusCode, response == null ? new byte[0] : response.getBytes(StandardCharsets.UTF_8),
                CONTENT_TYPE_TEXT + "; charset=UTF-8");
    }

    public static void writeBinaryResponse(HttpExchange httpExchange, int statusCode, byte[] responseBytes, String contentType) throws IOException {
        writeResponse(httpExchange, statusCode, responseBytes == null ? new byte[0] : responseBytes, contentType);
    }

    public static String readRequestBodyText(HttpExchange httpExchange) throws IOException {
        return new String(readRequestBodyBytes(httpExchange), StandardCharsets.UTF_8);
    }

    public static byte[] readRequestBodyBytes(HttpExchange httpExchange) throws IOException {
        return readRequestBodyBytes(httpExchange, MAX_REQUEST_BODY_BYTES);
    }

    public static byte[] readRequestBodyBytes(HttpExchange httpExchange, int maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative");
        }
        try (InputStream inputStream = httpExchange.getRequestBody()) {
            ByteArrayOutputStream body = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (read > maxBytes - total) {
                    throw new RequestBodyTooLargeException(maxBytes);
                }
                body.write(buffer, 0, read);
                total += read;
            }
            return body.toByteArray();
        }
    }

    public static void writeFileResponse(HttpExchange httpExchange,
                                         int statusCode,
                                         Path file,
                                         String contentType) throws IOException {
        long length = Files.size(file);
        httpExchange.getResponseHeaders().add(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        httpExchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, contentType);
        httpExchange.sendResponseHeaders(statusCode, length);
        try (InputStream input = Files.newInputStream(file);
             OutputStream output = httpExchange.getResponseBody()) {
            input.transferTo(output);
        }
    }

    public static final class RequestBodyTooLargeException extends IOException {
        public RequestBodyTooLargeException(int maxBytes) {
            super("Request body exceeds the maximum allowed size of " + maxBytes + " bytes");
        }
    }

    public static void generateResponse(HttpExchange httpExchange, String response, String contentType, String fileName) throws IOException {
        if (!"GET".equals(httpExchange.getRequestMethod())) {
            httpExchange.getResponseHeaders().set("Allow", "GET");
            httpExchange.sendResponseHeaders(405, -1);
            return;
        }


        httpExchange.getResponseHeaders().add(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        httpExchange.getResponseHeaders().add(CONTENT_TYPE_HEADER, contentType);
        if (isNotBlank(fileName) && DOWNLOADABLE.contains(contentType)) {
            String safeFileName = sanitizeContentDispositionFileName(fileName);
            httpExchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + safeFileName + "\"");
            httpExchange.getResponseHeaders().add("Content-Transfer-Encoding", "binary");
        }
        byte[] responseBytes = (response == null ? "" : response).getBytes(StandardCharsets.UTF_8);
        httpExchange.getResponseHeaders().add("Content-length", Long.toString(responseBytes.length));
        httpExchange.sendResponseHeaders(200, responseBytes.length);

        OutputStream os = httpExchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private static String sanitizeContentDispositionFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName
                .replace("\r", "_")
                .replace("\n", "_")
                .replace("\"", "_")
                .replace(";", "_")
                .replace("\\", "_")
                .replace("/", "_");
    }

    private static void writeResponse(HttpExchange httpExchange, int statusCode, byte[] responseBytes, String contentType) throws IOException {
        httpExchange.getResponseHeaders().add(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        httpExchange.getResponseHeaders().add(CONTENT_TYPE_HEADER, contentType);
        httpExchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = httpExchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
