package com.uiptv.application;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Streaming XMLTV reader. External DTDs and entities are never fetched. */
public record XmltvGuide(Map<String, List<EpgProgramme>> programmes, Map<String, String> names, Map<String, String> qualityNames) {
    public XmltvGuide(Map<String, List<EpgProgramme>> programmes, Map<String, String> names) {
        this(programmes, names, qualityAliases(names));
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
        Map<String, List<EpgProgramme>> programmes = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        Map<String, String> qualityNames = new HashMap<>();
        Set<String> ambiguousQuality = new HashSet<>();
        XMLStreamReader reader = factory.createXMLStreamReader(input);
        String id = null, channel = null, title = "", description = "";
        Instant start = null, stop = null;
        int count = 0;
        try {
            while (reader.hasNext()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "channel" -> id = reader.getAttributeValue(null, "id");
                        case "display-name" -> {
                            String name = normalize(reader.getElementText());
                            if (id != null && !name.isBlank()) {
                                String previous = names.putIfAbsent(name, id);
                                if (previous != null && !previous.equals(id)) ambiguous.add(name);
                                String qualityName = normalizeQuality(name);
                                String qualityPrevious = qualityNames.putIfAbsent(qualityName, id);
                                if (qualityPrevious != null && !qualityPrevious.equals(id)) ambiguousQuality.add(qualityName);
                            }
                        }
                        case "programme" -> {
                            channel = reader.getAttributeValue(null, "channel");
                            start = time(reader.getAttributeValue(null, "start"));
                            stop = time(reader.getAttributeValue(null, "stop"));
                            title = ""; description = "";
                        }
                        case "title" -> { String text = reader.getElementText(); if (title.isEmpty()) title = text.strip(); }
                        case "desc" -> { String text = reader.getElementText(); if (description.isEmpty()) description = text.strip(); }
                        default -> { }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("programme")) {
                    if (channel != null && start != null && !title.isBlank()
                            && start.isBefore(now.plus(Duration.ofDays(7)))
                            && (stop == null || stop.isAfter(now.minus(Duration.ofHours(6))))
                            && start.isAfter(now.minus(Duration.ofDays(1)))) {
                        if (++count > 200_000) throw new IllegalArgumentException("Guide exceeds programme limit");
                        programmes.computeIfAbsent(channel, _ -> new ArrayList<>()).add(new EpgProgramme(title, description,
                                start.toEpochMilli(), stop == null ? 0 : stop.toEpochMilli()));
                    }
                    channel = null;
                }
            }
        } finally { reader.close(); }
        ambiguous.forEach(names::remove);
        ambiguousQuality.forEach(qualityNames::remove);
        qualityNames.remove("");
        programmes.replaceAll((key, list) -> {
            list.sort(Comparator.comparingLong(EpgProgramme::start));
            List<EpgProgramme> completed = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                EpgProgramme p = list.get(i);
                long end = p.stop() == 0 && i + 1 < list.size() ? list.get(i + 1).start() : p.stop();
                if (end > p.start()) completed.add(new EpgProgramme(p.title(), p.description(), p.start(), end));
            }
            return EpgSchedules.normalize(completed);
        });
        return new XmltvGuide(Map.copyOf(programmes), Map.copyOf(names), Map.copyOf(qualityNames));
    }

    public List<EpgProgramme> forChannel(String providerId, String name) {
        if (providerId != null && programmes.containsKey(providerId)) return programmes.get(providerId);
        String exact = names.get(normalize(name));
        if (exact != null) return programmes.getOrDefault(exact, List.of());
        return programmes.getOrDefault(qualityNames.getOrDefault(normalizeQuality(name), ""), List.of());
    }

    static String normalize(String name) { return name == null ? "" : name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }

    static String normalizeQuality(String name) {
        String value = normalize(name).replaceAll("(?<![\\p{L}\\p{N}])(?:fhd|uhd|hd|sd|4k|8k|720p|1080p|2160p)(?![\\p{L}\\p{N}])", " ");
        value = value.replaceAll("\\([\\s|/,-]*\\)|\\[[\\s|/,-]*\\]", " ").replaceAll("^[\\s|/,-]+|[\\s|/,-]+$", "");
        return normalize(value);
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
            return LocalDateTime.parse(digits, DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(java.time.format.ResolverStyle.STRICT)).toInstant(offset);
        } catch (RuntimeException e) { return null; }
    }
}
