package com.uiptv.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;

import static com.uiptv.widget.UIptvAlert.showError;

public class VersionManager {
    private static final String VERSION_RESOURCE = "app-version.properties";
    private static final String MAVEN_POM_PROPERTIES = "META-INF/maven/com.spc/uiptv/pom.properties";
    private static final String VERSION_PROPERTY = "version";
    private static final String RELEASE_URL = "https://github.com/xixogo5105/uiptv/releases/latest";
    public static final String NOT_AVAILABLE = "N/A";
    public static final String RELEASE_DESCRIPTION = NOT_AVAILABLE;

    private VersionManager() {
    }

    public static String getCurrentVersion() {
        Properties versionProperties = readPropertiesResource(VERSION_RESOURCE);
        String filteredResourceVersion = versionProperties.getProperty(VERSION_PROPERTY);
        String implementationVersion = VersionManager.class.getPackage() == null
                ? null
                : VersionManager.class.getPackage().getImplementationVersion();
        Properties mavenProperties = readPropertiesResource(MAVEN_POM_PROPERTIES);
        String mavenPomVersion = mavenProperties.getProperty(VERSION_PROPERTY);
        String localPomVersion = readLocalPomVersion();
        return resolveVersion(filteredResourceVersion, implementationVersion, mavenPomVersion, localPomVersion);
    }

    public static String getReleaseUrl() {
        return RELEASE_URL;
    }

    static String resolveVersion(String... candidates) {
        if (candidates == null) {
            return NOT_AVAILABLE;
        }
        for (String candidate : candidates) {
            if (isResolvedVersionValue(candidate)) {
                return candidate.trim();
            }
        }
        return NOT_AVAILABLE;
    }

    static boolean isResolvedVersionValue(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.isBlank() && !NOT_AVAILABLE.equalsIgnoreCase(trimmed) && !trimmed.contains("${");
    }

    private static Properties readPropertiesResource(String resourceName) {
        try (InputStream input = VersionManager.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                return new Properties();
            }
            Properties properties = new Properties();
            properties.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException ex) {
            showError("Failed to read application version metadata from " + resourceName, ex);
            return new Properties();
        }
    }

    private static String readLocalPomVersion() {
        Path pomPath = Path.of(System.getProperty("user.dir", "."), "pom.xml");
        if (!Files.isRegularFile(pomPath)) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            var document = builder.parse(pomPath.toFile());
            var versionNodes = document.getDocumentElement().getElementsByTagName(VERSION_PROPERTY);
            if (versionNodes.getLength() == 0) {
                return null;
            }
            return versionNodes.item(0).getTextContent();
        } catch (Exception _) {
            return null;
        }
    }
}
