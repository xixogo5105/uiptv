package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.uiptv.util.Platform.getWebServerRootPath;
import static com.uiptv.util.StringUtils.isBlank;

final class StaticWebFileResolver {
    private StaticWebFileResolver() {
    }

    static Path resolve(HttpExchange exchange) throws IOException {
        if (exchange == null || exchange.getRequestURI() == null) {
            throw new IOException("Invalid request");
        }
        String requestPath = exchange.getRequestURI().getPath();
        if (isBlank(requestPath)) {
            throw new IOException("Invalid path");
        }

        String relativePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        if (isBlank(relativePath)) {
            throw new IOException("Invalid path");
        }

        Path root = Paths.get(getWebServerRootPath()).toAbsolutePath().normalize();
        Path rootReal = root.toRealPath();
        Path resolved = rootReal.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootReal) || !Files.isRegularFile(resolved)) {
            throw new IOException("File not found");
        }
        Path resolvedReal = resolved.toRealPath();
        if (!resolvedReal.startsWith(rootReal) || !Files.isRegularFile(resolvedReal)) {
            throw new IOException("File not found");
        }
        return resolvedReal;
    }

    static String readUtf8(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
