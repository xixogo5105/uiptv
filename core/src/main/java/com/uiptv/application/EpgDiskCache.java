package com.uiptv.application;

import com.uiptv.util.ConfigFileReader;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;

/** Validated XMLTV only; filenames never contain source URLs or credentials. */
final class EpgDiskCache {
    private static Path directory() throws IOException {
        String configured = ConfigFileReader.getEpgTmpCacheDir();
        java.util.List<Path> candidates = new java.util.ArrayList<>();
        if (configured != null) candidates.add(Path.of(configured));
        candidates.add(Path.of(System.getProperty("java.io.tmpdir"), "uiptv-epg-cache"));
        for (Path candidate : candidates) {
            try {
                Files.createDirectories(candidate);
                if (Files.isWritable(candidate)) return candidate;
            } catch (IOException _) { /* Try the same temporary-directory fallback as thumbnails. */ }
        }
        throw new IOException("EPG cache directory unavailable");
    }
    static Path path(String source) throws Exception {
        return directory().resolve("epg-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))) + ".xml");
    }
    static long ttlMillis() { return Duration.ofHours(ConfigFileReader.getEpgCacheTtlHours()).toMillis(); }
    static long freshUntil(String source) {
        try { return Files.getLastModifiedTime(path(source)).toMillis() + ttlMillis(); }
        catch (Exception _) { return 0; }
    }
    static XmltvGuide read(String source, boolean allowStale) {
        try {
            Path file = path(source);
            if (!Files.isRegularFile(file) || (!allowStale && System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() >= ttlMillis())) return null;
            try (InputStream input = Files.newInputStream(file)) {
                return XmltvGuide.read(new EpgApplicationService.LimitedInputStream(input, EpgApplicationService.sourceSizeLimit()), Instant.now());
            }
        } catch (Exception _) { return null; }
    }
    static XmltvGuide parseAndSave(String source, InputStream xml) throws Exception {
        Path target;
        try { target = path(source); } catch (Exception _) { return XmltvGuide.read(xml, Instant.now()); }
        Path temporary;
        try { temporary = Files.createTempFile(target.getParent(), "epg-", ".part"); }
        catch (IOException _) { return XmltvGuide.read(xml, Instant.now()); }
        try {
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary), 256 * 1024)) {
                xml.transferTo(output);
            }
            XmltvGuide guide;
            try (InputStream input = Files.newInputStream(temporary)) { guide = XmltvGuide.read(input, Instant.now()); }
            try {
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException _) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException _) { /* Disk caching is optional; valid guide is still usable. */ }
            return guide;
        } finally { Files.deleteIfExists(temporary); }
    }
    static void clear() {
        try (var files = Files.newDirectoryStream(directory(), "epg-*.xml")) {
            for (Path file : files) try { Files.deleteIfExists(file); } catch (IOException _) { }
        } catch (IOException _) { }
    }
}
