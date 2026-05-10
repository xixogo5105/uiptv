package com.uiptv.util

import java.net.URI
import java.util.LinkedHashSet

object HlsPlaylistResolver {
    @JvmStatic
    fun resolveHlsPlaylistChain(uri: String?, requestHeaders: Map<String, String>, maxDepth: Int): String {
        return resolveHlsPlaylistChain(uri, requestHeaders, maxDepth, LinkedHashSet(), 0) ?: uri.orEmpty()
    }

    private fun resolveHlsPlaylistChain(
        uri: String?,
        requestHeaders: Map<String, String>,
        maxDepth: Int,
        visited: MutableSet<String>,
        depth: Int
    ): String? {
        if (uri == null) {
            return null
        }
        val normalizedUri = uri.trim()
        if (normalizedUri.isEmpty()) {
            return uri
        }
        if (!visited.add(normalizedUri) || depth >= maxDepth || !isLikelyManifest(normalizedUri)) {
            return normalizedUri
        }

        return try {
            val result = HttpUtil.sendRequest(normalizedUri, requestHeaders, "GET")
            if (result.statusCode != 200 || StringUtils.isBlank(result.body) || !isMasterManifest(result.body)) {
                return normalizedUri
            }
            val effectiveBaseUri = if (StringUtils.isBlank(result.requestUri)) normalizedUri else result.requestUri
            val variantUrl = extractBestVariantUrl(effectiveBaseUri, result.body)
            if (StringUtils.isBlank(variantUrl) || variantUrl == normalizedUri) {
                normalizedUri
            } else {
                resolveHlsPlaylistChain(variantUrl, requestHeaders, maxDepth, visited, depth + 1)
            }
        } catch (_: Exception) {
            normalizedUri
        }
    }

    private fun isLikelyManifest(uri: String): Boolean {
        val path = uri.split("\\?".toRegex())[0].lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".m3u")) {
            return true
        }
        val lastSlash = path.lastIndexOf('/')
        val lastSegment = if (lastSlash >= 0) path.substring(lastSlash + 1) else path
        return !lastSegment.contains(".")
    }

    private fun isMasterManifest(body: String): Boolean =
        body.startsWith("#EXTM3U") && body.contains("#EXT-X-STREAM-INF")

    private fun extractBestVariantUrl(baseUrl: String, playlistContent: String): String? {
        val lines = playlistContent.split("\\r?\\n".toRegex())
        var bestVariant: String? = null
        var maxBandwidth = -1L
        for (index in lines.indices) {
            val line = lines[index].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:") && index + 1 < lines.size) {
                val bandwidth = parseBandwidth(line)
                val candidate = lines[index + 1].trim()
                if (candidate.isNotEmpty() && !candidate.startsWith("#") && bandwidth > maxBandwidth) {
                    maxBandwidth = bandwidth
                    bestVariant = candidate
                }
            }
        }
        return if (StringUtils.isBlank(bestVariant)) null else resolveVariantUrl(baseUrl, bestVariant!!)
    }

    private fun parseBandwidth(line: String): Long {
        return try {
            val idx = line.uppercase().indexOf("BANDWIDTH=")
            if (idx < 0) {
                return 0
            }
            val suffix = line.substring(idx + "BANDWIDTH=".length)
            val comma = suffix.indexOf(',')
            val value = if (comma >= 0) suffix.substring(0, comma) else suffix
            value.trim().toLong()
        } catch (_: Exception) {
            0
        }
    }

    private fun resolveVariantUrl(baseUrl: String, variantUrl: String): String? {
        return try {
            val base = URI.create(baseUrl)
            val resolved = base.resolve(variantUrl).normalize()
            if (base.query != null && !variantUrl.contains("?")) {
                val resolvedStr = resolved.toString()
                if (resolvedStr.contains("?")) resolvedStr else "$resolvedStr?${base.query}"
            } else {
                resolved.toString()
            }
        } catch (_: Exception) {
            null
        }
    }
}
