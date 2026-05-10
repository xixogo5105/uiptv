package com.uiptv.util

import com.uiptv.model.Account
import java.net.URI

object ImageUrlNormalizer {
    @JvmStatic
    fun normalizeImageUrl(imageUrl: String?, account: Account?): String {
        if (StringUtils.isBlank(imageUrl)) {
            return ""
        }
        var value = imageUrl!!.trim().replace("\\/", "/")
        value = trimWrappedImageQuotes(value)
        if (StringUtils.isBlank(value)) {
            return ""
        }
        if (isAbsoluteImageUrl(value) || isInlineImageUrl(value)) {
            return value
        }
        val base = resolveBaseUri(account)
        val scheme = resolveBaseScheme(base)
        val host = resolveBaseHost(base)
        val port = base?.port ?: -1
        if (value.startsWith("//")) {
            return "$scheme:$value"
        }
        if (value.startsWith("/")) {
            return buildRootRelativeImageUrl(value, scheme, host, port)
        }
        if (value.matches(Regex("^[a-zA-Z0-9.-]+(?::\\d+)?/.*")) && StringUtils.isBlank(host)) {
            val slash = value.indexOf('/')
            val hostCandidate = if (slash > 0) value.substring(0, slash) else value
            if (hostCandidate.contains(".") || hostCandidate.equals("localhost", ignoreCase = true)) {
                return "$scheme://$value"
            }
        }
        return buildRelativeImageUrl(value, scheme, host, port)
    }

    private fun resolveBaseUri(account: Account?): URI? {
        if (account == null) {
            return null
        }
        val candidates = ArrayList<String>()
        if (!StringUtils.isBlank(account.serverPortalUrl)) {
            candidates.add(account.serverPortalUrl!!)
        }
        if (!StringUtils.isBlank(account.url)) {
            candidates.add(account.url!!)
        }
        for (candidate in candidates) {
            val resolved = parseCandidateBaseUri(candidate)
            if (resolved != null) {
                return resolved
            }
        }
        return null
    }

    private fun resolveBaseScheme(base: URI?): String =
        if (base != null && !StringUtils.isBlank(base.scheme)) base.scheme else "https"

    private fun resolveBaseHost(base: URI?): String =
        if (base != null && !StringUtils.isBlank(base.host)) base.host else ""

    private fun trimWrappedImageQuotes(value: String): String {
        if (StringUtils.isBlank(value)) {
            return ""
        }
        val trimmed = value.trim()
        if (trimmed.length < 2) {
            return trimmed
        }
        return if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else {
            trimmed
        }
    }

    private fun isAbsoluteImageUrl(value: String): Boolean =
        value.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))

    private fun isInlineImageUrl(value: String): Boolean =
        value.startsWith("data:") || value.startsWith("blob:") || value.startsWith("file:")

    private fun buildRootRelativeImageUrl(value: String, scheme: String, host: String, port: Int): String {
        return if (!StringUtils.isBlank(host)) {
            "$scheme://$host${formatPort(port)}$value"
        } else {
            value
        }
    }

    private fun buildRelativeImageUrl(value: String, scheme: String, host: String, port: Int): String {
        val normalized = if (value.startsWith("./")) value.substring(2) else value
        return if (!StringUtils.isBlank(host)) {
            "$scheme://$host${formatPort(port)}/$normalized"
        } else {
            ServerUrlUtil.getLocalServerUrl() + "/$normalized"
        }
    }

    private fun formatPort(port: Int): String = if (port > 0) ":$port" else ""

    private fun parseCandidateBaseUri(candidate: String?): URI? {
        if (StringUtils.isBlank(candidate)) {
            return null
        }
        return try {
            val uri = URI.create(candidate!!.trim())
            if (!StringUtils.isBlank(uri.host)) {
                return uri
            }
            if (StringUtils.isBlank(uri.scheme)) {
                val withScheme = URI.create("http://${candidate.trim()}")
                if (!StringUtils.isBlank(withScheme.host)) {
                    return withScheme
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
