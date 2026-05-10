package com.uiptv.server

import com.sun.net.httpserver.HttpExchange
import com.uiptv.util.Platform.getWebServerRootPath
import com.uiptv.util.StringUtils.isBlank
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class StaticWebFileResolver private constructor() {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun resolve(exchange: HttpExchange?): Path {
            if (exchange?.requestURI == null) {
                throw IOException("Invalid request")
            }
            val requestPath = exchange.requestURI.path
            if (isBlank(requestPath)) {
                throw IOException("Invalid path")
            }
            val relativePath = if (requestPath.startsWith("/")) requestPath.substring(1) else requestPath
            if (isBlank(relativePath)) {
                throw IOException("Invalid path")
            }
            val root = Paths.get(getWebServerRootPath()).toAbsolutePath().normalize()
            val rootReal = try {
                root.toRealPath()
            } catch (_: IOException) {
                throw IOException("File not found")
            }
            val resolved = rootReal.resolve(relativePath).normalize()
            if (!resolved.startsWith(rootReal) || !Files.isRegularFile(resolved)) {
                throw IOException("File not found")
            }
            val resolvedReal = resolved.toRealPath()
            if (!resolvedReal.startsWith(rootReal) || !Files.isRegularFile(resolvedReal)) {
                throw IOException("File not found")
            }
            return resolvedReal
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readUtf8(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)
    }
}
