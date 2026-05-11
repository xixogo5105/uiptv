package com.uiptv.server

import com.uiptv.util.Platform.getWebServerRootPath
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object WebStaticContentSupport {
    fun readStaticUtf8(requestPath: String): String =
        StaticWebFileResolver.readUtf8(StaticWebFileResolver.resolveRequestPath(requestPath))

    fun readSpaHtml(htmlFileName: String): String =
        Files.readString(Path.of(getWebServerRootPath(), htmlFileName), StandardCharsets.UTF_8)

    fun readIconBytes(): ByteArray {
        try {
            return Files.readAllBytes(StaticWebFileResolver.resolveRequestPath("/icon.ico"))
        } catch (_: IOException) {
            WebStaticContentSupport::class.java.getResourceAsStream("/icon.ico")?.use { input ->
                return IOUtils.toByteArray(input)
            }
        }
        throw IOException("Icon not found")
    }
}
