package com.uiptv.util

import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

object VersionManager {
    private const val VERSION_RESOURCE = "app-version.properties"
    private const val MAVEN_POM_PROPERTIES = "META-INF/maven/com.spc/uiptv/pom.properties"
    private const val VERSION_PROPERTY = "version"
    private const val RELEASE_URL = "https://github.com/xixogo5105/uiptv/releases/latest"
    const val NOT_AVAILABLE = "N/A"
    const val RELEASE_DESCRIPTION = NOT_AVAILABLE

    @JvmStatic
    fun getCurrentVersion(): String {
        val versionProperties = readPropertiesResource(VERSION_RESOURCE)
        val filteredResourceVersion = versionProperties.getProperty(VERSION_PROPERTY)
        val implementationVersion = VersionManager::class.java.`package`?.implementationVersion
        val mavenProperties = readPropertiesResource(MAVEN_POM_PROPERTIES)
        val mavenPomVersion = mavenProperties.getProperty(VERSION_PROPERTY)
        val localPomVersion = readLocalPomVersion()
        return resolveVersion(filteredResourceVersion, implementationVersion, mavenPomVersion, localPomVersion)
    }

    @JvmStatic
    fun getReleaseUrl(): String = RELEASE_URL

    @JvmStatic
    fun resolveVersion(vararg candidates: String?): String {
        for (candidate in candidates) {
            if (isResolvedVersionValue(candidate)) {
                return candidate!!.trim()
            }
        }
        return NOT_AVAILABLE
    }

    @JvmStatic
    fun isResolvedVersionValue(value: String?): Boolean {
        val trimmed = value?.trim() ?: return false
        return trimmed.isNotBlank() && !NOT_AVAILABLE.equals(trimmed, ignoreCase = true) && !trimmed.contains("\${")
    }

    private fun readPropertiesResource(resourceName: String): Properties {
        return try {
            val input = VersionManager::class.java.classLoader.getResourceAsStream(resourceName) ?: return Properties()
            input.use {
                val properties = Properties()
                InputStreamReader(it, StandardCharsets.UTF_8).use(properties::load)
                properties
            }
        } catch (_: IOException) {
            AppLog.addErrorLog(VersionManager::class.java, "Failed to read application version metadata from $resourceName")
            Properties()
        }
    }

    private fun readLocalPomVersion(): String? {
        val pomPath = Path.of(System.getProperty("user.dir", "."), "pom.xml")
        if (!Files.isRegularFile(pomPath)) {
            return null
        }
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.isExpandEntityReferences = false
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(pomPath.toFile())
            val versionNodes = document.documentElement.getElementsByTagName(VERSION_PROPERTY)
            if (versionNodes.length == 0) null else versionNodes.item(0).textContent
        } catch (_: Exception) {
            null
        }
    }
}
