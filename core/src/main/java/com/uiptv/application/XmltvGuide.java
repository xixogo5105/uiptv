package com.uiptv.application;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Streaming XMLTV reader. External DTDs and entities are never fetched. */
public record XmltvGuide(Map<String, List<EpgProgramme>> programmes, Map<String, String> names, Map<String, String> qualityNames, Map<String,String> icons) {
    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");
    private static final java.util.regex.Pattern QUALITY = java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])(?:fhd|uhd|hd|sd|4k|8k|720p|1080p|2160p)(?![\\p{L}\\p{N}])");
    private static final java.util.regex.Pattern EMPTY_BRACKETS = java.util.regex.Pattern.compile("\\([\\s|/,-]*\\)|\\[[\\s|/,-]*\\]");
    private static final java.util.regex.Pattern EDGE_SEPARATORS = java.util.regex.Pattern.compile("^[\\s|/,-]+|[\\s|/,-]+$");
    private static final DateTimeFormatter XMLTV_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(java.time.format.ResolverStyle.STRICT);

    public XmltvGuide(Map<String, List<EpgProgramme>> programmes, Map<String, String> names) {
        this(programmes,names,qualityAliases(names),Map.of());
    }
    public XmltvGuide(Map<String,List<EpgProgramme>> programmes,Map<String,String> names,Map<String,String> qualityNames) {
        this(programmes,names,qualityNames,Map.of());
    }
    public static XmltvGuide read(InputStream input, Instant now) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        // Large XMLTV descriptions can exceed the JDK default entity-text limit.
        // Keep this bounded per reader; DTDs and external entities remain disabled.
        if (factory.isPropertySupported("jdk.xml.maxGeneralEntitySizeLimit"))
            factory.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "8388608");
        if (factory.isPropertySupported("jdk.xml.totalEntitySizeLimit"))
            factory.setProperty("jdk.xml.totalEntitySizeLimit", "8388608");
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, base, namespace) -> { throw new javax.xml.stream.XMLStreamException("External entities disabled"); });
        CompactProgrammeMap.Builder programmes = new CompactProgrammeMap.Builder();
        Map<String, String> names = new HashMap<>();
        Map<String,String> icons=new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        Map<String, String> qualityNames = new HashMap<>();
        Set<String> ambiguousQuality = new HashSet<>();
        XMLStreamReader reader = factory.createXMLStreamReader(input);
        String id = null, channel = null, title = "", description = "";
        Instant start = null, stop = null;
        int count = 0;
        long retainedBytes = 0;
        try {
            while (reader.hasNext()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "channel" -> id = reader.getAttributeValue(null, "id");
                        case "display-name" -> {
                            String name = normalize(boundedText(reader, 512));
                            retainedBytes += 256L + name.length() * 4L;
                            if (retainedBytes > 32L * 1024 * 1024) throw new IllegalArgumentException("Guide exceeds size limit");
                            if (id != null && !name.isBlank()) {
                                String previous = names.putIfAbsent(name, id);
                                if (previous != null && !previous.equals(id)) ambiguous.add(name);
                                String qualityName = normalizeQuality(name);
                                String qualityPrevious = qualityNames.putIfAbsent(qualityName, id);
                                if (qualityPrevious != null && !qualityPrevious.equals(id)) ambiguousQuality.add(qualityName);
                            }
                        }
                        case "icon" -> {
                            String icon=reader.getAttributeValue(null,"src");
                            if(id!=null && channel==null && icon!=null && !icon.isBlank() && icon.length()<=4096) {
                                icons.putIfAbsent(id,icon.strip()); retainedBytes+=256L+icon.length()*4L;
                                if(retainedBytes>32L*1024*1024) throw new IllegalArgumentException("Guide exceeds size limit");
                            }
                        }
                        case "programme" -> {
                            channel = reader.getAttributeValue(null, "channel");
                            start = time(reader.getAttributeValue(null, "start"));
                            stop = time(reader.getAttributeValue(null, "stop"));
                            title = ""; description = "";
                        }
                        case "title" -> { String text = boundedText(reader, 512); if (title.isEmpty()) title = text.strip(); }
                        case "desc" -> { String text = boundedText(reader, 2048); if (description.isEmpty()) description = text.strip(); }
                        default -> { }
                    }
                } else if(event==XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("channel")) {
                    id=null;
                } else if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("programme")) {
                    if (channel != null && start != null && !title.isBlank()
                            && start.isBefore(now.plus(Duration.ofDays(7)))
                            && (stop == null || stop.isAfter(now.minus(Duration.ofHours(6))))
                            && start.isAfter(now.minus(Duration.ofDays(1)))) {
                        if (++count > 200_000) throw new IllegalArgumentException("Guide exceeds programme limit");
                        programmes.add(channel, new EpgProgramme(title, description,
                                start.toEpochMilli(), stop == null ? 0 : stop.toEpochMilli()));
                    }
                    channel = null;
                }
            }
        } finally { reader.close(); }
        ambiguous.forEach(names::remove);
        ambiguousQuality.forEach(qualityNames::remove);
        qualityNames.remove("");
        return new XmltvGuide(programmes.build(),Map.copyOf(names),Map.copyOf(qualityNames),Map.copyOf(icons));
    }

    /** Conservative retained-size estimate, including strings, lists and map entries. */
    long estimatedBytes() {
        long bytes = 256;
        if (programmes instanceof CompactProgrammeMap compact) bytes += compact.estimatedBytes();
        else for (var entry : programmes.entrySet()) {
            bytes += 192L + 2L * entry.getKey().length();
            for (var p : entry.getValue()) bytes += 192L + 2L * (p.title().length() + p.description().length());
        }
        for (var aliases : List.of(names,qualityNames,icons))
            for (var entry : aliases.entrySet()) bytes += 128L + 2L * (entry.getKey().length() + entry.getValue().length());
        return bytes;
    }

    // Consume the whole element without ever building an unbounded description string.
    private static String boundedText(XMLStreamReader reader, int limit) throws javax.xml.stream.XMLStreamException {
        StringBuilder text = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT) return text.toString();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA || event == XMLStreamConstants.SPACE) {
                int length = Math.min(limit - text.length(), reader.getTextLength());
                if (length > 0) text.append(reader.getTextCharacters(), reader.getTextStart(), length);
            } else if (event == XMLStreamConstants.START_ELEMENT || event == XMLStreamConstants.ENTITY_REFERENCE) {
                throw new javax.xml.stream.XMLStreamException("Invalid XMLTV text element");
            }
        }
        throw new javax.xml.stream.XMLStreamException("Unterminated XMLTV text element");
    }

    public List<EpgProgramme> forChannel(String providerId, String name) {
        String id = providerId != null && programmes.containsKey(providerId) ? providerId : names.get(normalize(name));
        if (id == null) id = qualityNames.getOrDefault(normalizeQuality(name), "");
        return programmes.getOrDefault(id, List.of());
    }

    static String normalize(String name) { return name == null ? "" : WHITESPACE.matcher(name.strip().toLowerCase(Locale.ROOT)).replaceAll(" "); }

    static String normalizeQuality(String name) {
        String value = QUALITY.matcher(normalize(name)).replaceAll(" ");
        value = EDGE_SEPARATORS.matcher(EMPTY_BRACKETS.matcher(value).replaceAll(" ")).replaceAll("");
        // Playlist availability annotations are not part of the broadcast name.
        value = value.replaceAll("[Ⓐ-Ⓩⓐ-ⓩ]", "");
        // Keep + so time-shift channels never collapse into their parent station.
        return value.replaceAll("[^\\p{L}\\p{N}+]", "");
    }
    private static Map<String, String> qualityAliases(Map<String, String> names) {
        Map<String, String> result = new HashMap<>(); Set<String> ambiguous = new HashSet<>();
        names.forEach((name, id) -> {
            String key = normalizeQuality(name);
            String previous = result.putIfAbsent(key, id);
            if (previous != null && !previous.equals(id)) ambiguous.add(key);
        });
        ambiguous.forEach(result::remove); result.remove(""); return Map.copyOf(result);
    }

    static Instant time(String value) {
        if (value == null) return null;
        try {
            String[] parts = value.strip().split("\\s+");
            String digits = parts[0];
            if (digits.length() < 12) return null;
            if (digits.length() == 12) digits += "00";
            ZoneOffset offset = parts.length == 1 ? ZoneOffset.UTC : switch (parts[1]) {
                case "UTC", "GMT" -> ZoneOffset.UTC;
                case "BST" -> ZoneOffset.ofHours(1);
                default -> ZoneOffset.of(parts[1]);
            };
            return LocalDateTime.parse(digits, XMLTV_TIME).toInstant(offset);
        } catch (RuntimeException e) { return null; }
    }
}
