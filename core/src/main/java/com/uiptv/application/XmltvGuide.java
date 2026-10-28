package com.uiptv.application;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Streaming XMLTV reader. External DTDs and entities are never fetched. */
public record XmltvGuide(Map<String, List<EpgProgramme>> programmes, Map<String, String> names, Map<String, String> qualityNames, Map<String,String> icons) implements AutoCloseable {
    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");
    private static final java.util.regex.Pattern QUALITY = java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])(?:fhd|uhd|hd|sd|4k|8k|\\d{3,4}[pi])(?![\\p{L}\\p{N}])");
    private static final java.util.regex.Pattern DISPLAY_RESOLUTION = java.util.regex.Pattern.compile("\\s*\\((?:144|240|360|480|540|576|720|1080|1440|2160|4320)[pi]\\)");
    private static final java.util.regex.Pattern AVAILABILITY = java.util.regex.Pattern.compile("\\s*\\[geo-blocked\\]");
    private static final java.util.regex.Pattern EMPTY_BRACKETS = java.util.regex.Pattern.compile("\\([\\s|/,-]*\\)|\\[[\\s|/,-]*\\]");
    private static final java.util.regex.Pattern EDGE_SEPARATORS = java.util.regex.Pattern.compile("[\\s|/,-]+");
    private static final String CHANNEL_ELEMENT = "channel";
    private static final DateTimeFormatter XMLTV_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    record Limits(int maxProgrammes, long maxMetadataBytes) {
        static Limits standard() { return new Limits(200_000, 32L * 1024 * 1024); }
    }

    public XmltvGuide(Map<String, List<EpgProgramme>> programmes, Map<String, String> names) {
        this(programmes,names,qualityAliases(names),Map.of());
    }
    public XmltvGuide(Map<String,List<EpgProgramme>> programmes,Map<String,String> names,Map<String,String> qualityNames) {
        this(programmes,names,qualityNames,Map.of());
    }
    public static XmltvGuide read(InputStream input, Instant now)
            throws javax.xml.stream.XMLStreamException, InterruptedException {
        return read(input, now, Limits.standard());
    }

    static XmltvGuide read(InputStream input, Instant now, Limits limits)
            throws javax.xml.stream.XMLStreamException, InterruptedException {
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
        ParseContext context = new ParseContext(now, limits);
        try (XmlReaderResource readerClose = new XmlReaderResource(factory.createXMLStreamReader(input))) {
            XMLStreamReader reader = readerClose.reader();
            while (reader.hasNext()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) handleStartElement(reader, context);
                if (event == XMLStreamConstants.END_ELEMENT) handleEndElement(reader, context);
            }
            context.ambiguous.forEach(context.names::remove);
            context.ambiguousQuality.forEach(context.qualityNames::remove);
            context.qualityNames.remove("");
            return new XmltvGuide(completeProgrammes(context.programmes), Map.copyOf(context.names),
                    Map.copyOf(context.qualityNames), Map.copyOf(context.icons));
        }
    }

    private static final class XmlReaderResource implements AutoCloseable {
        private final XMLStreamReader reader;

        private XmlReaderResource(XMLStreamReader reader) {
            this.reader = reader;
        }

        private XMLStreamReader reader() { return reader; }

        @Override
        public void close() throws javax.xml.stream.XMLStreamException { reader.close(); }
    }

    private static Map<String,List<EpgProgramme>> completeProgrammes(Map<String,List<EpgProgramme>> source) {
        Map<String,List<EpgProgramme>> result = new HashMap<>();
        source.forEach((id, values) -> {
            values.sort(Comparator.comparingLong(EpgProgramme::start));
            List<EpgProgramme> completed = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                var programme = values.get(i);
                long stop = programme.stop();
                if (stop <= programme.start()) {
                    stop = i + 1 < values.size() ? values.get(i + 1).start() : 0;
                }
                if (stop > programme.start()) completed.add(new EpgProgramme(programme.title(), programme.description(), programme.start(), stop));
            }
            result.put(id, EpgSchedules.normalize(completed));
        });
        return Map.copyOf(result);
    }

    private static final class ParseContext {
        private final Instant now;
        private final Limits limits;
        private final Map<String,List<EpgProgramme>> programmes;
        private final Map<String, String> names = new HashMap<>();
        private final Map<String, String> icons = new HashMap<>();
        private final Set<String> ambiguous = new HashSet<>();
        private final Map<String, String> qualityNames = new HashMap<>();
        private final Set<String> ambiguousQuality = new HashSet<>();
        private String id;
        private String channel;
        private String title = "";
        private String description = "";
        private Instant start;
        private Instant stop;
        private int count;
        private long retainedBytes;

        private ParseContext(Instant now, Limits limits) {
            this.now = now;
            this.limits = limits;
            this.programmes = new HashMap<>();
        }
    }

    private static void handleStartElement(XMLStreamReader reader, ParseContext context)
            throws XMLStreamException {
        switch (reader.getLocalName()) {
            case CHANNEL_ELEMENT -> context.id = reader.getAttributeValue(null, "id");
            case "display-name" -> readDisplayName(reader, context);
            case "icon" -> readIcon(reader, context);
            case "programme" -> beginProgramme(reader, context);
            case "title" -> { String text = boundedText(reader, 512); if (context.title.isEmpty()) context.title = text.strip(); }
            case "desc" -> { String text = boundedText(reader, 2048); if (context.description.isEmpty()) context.description = text.strip(); }
            default -> reader.getLocalName();
        }
    }

    private static void readDisplayName(XMLStreamReader reader, ParseContext context) throws XMLStreamException {
        String name = normalize(boundedText(reader, 512));
        retain(context, 256L + name.length() * 4L);
        if (context.id == null || name.isBlank()) return;
        String previous = context.names.putIfAbsent(name, context.id);
        if (previous != null && !previous.equals(context.id)) context.ambiguous.add(name);
        String qualityName = normalizeQuality(name);
        String qualityPrevious = context.qualityNames.putIfAbsent(qualityName, context.id);
        if (qualityPrevious != null && !qualityPrevious.equals(context.id)) context.ambiguousQuality.add(qualityName);
    }

    private static void readIcon(XMLStreamReader reader, ParseContext context) {
        String icon = reader.getAttributeValue(null, "src");
        if (context.id == null || context.channel != null || icon == null || icon.isBlank() || icon.length() > 4096) return;
        context.icons.putIfAbsent(context.id, icon.strip());
        retain(context, 256L + icon.length() * 4L);
    }

    private static void beginProgramme(XMLStreamReader reader, ParseContext context) {
        context.channel = reader.getAttributeValue(null, CHANNEL_ELEMENT);
        context.start = time(reader.getAttributeValue(null, "start"));
        context.stop = time(reader.getAttributeValue(null, "stop"));
        context.title = "";
        context.description = "";
    }

    private static void handleEndElement(XMLStreamReader reader, ParseContext context) {
        if (reader.getLocalName().equals(CHANNEL_ELEMENT)) context.id = null;
        if (reader.getLocalName().equals("programme")) finishProgramme(context);
    }

    private static void finishProgramme(ParseContext context) {
        if (context.channel == null || context.start == null || context.title.isBlank()
                || !context.start.isBefore(context.now.plus(Duration.ofDays(7)))
                || context.stop != null && !context.stop.isAfter(context.now.minus(Duration.ofHours(6)))
                || !context.start.isAfter(context.now.minus(Duration.ofDays(1)))) {
            context.channel = null;
            return;
        }
        if (++context.count > context.limits.maxProgrammes()) throw new IllegalArgumentException("Guide exceeds programme limit");
        context.programmes.computeIfAbsent(context.channel, ignored -> new ArrayList<>()).add(new EpgProgramme(context.title, context.description,
                context.start.toEpochMilli(), context.stop == null ? 0 : context.stop.toEpochMilli()));
        context.channel = null;
    }

    private static void retain(ParseContext context, long bytes) {
        context.retainedBytes += bytes;
        if (context.retainedBytes > context.limits.maxMetadataBytes()) throw new IllegalArgumentException("Guide exceeds size limit");
    }

    /** Conservative retained-size estimate, including strings, lists and map entries. */
    long estimatedBytes() {
        long bytes = 256;
        for (var entry : programmes.entrySet()) {
            bytes += 192L + 2L * entry.getKey().length();
            for (var p : entry.getValue()) bytes += 192L + 2L * (p.title().length() + p.description().length());
        }
        for (var aliases : List.of(names,qualityNames,icons))
            for (var entry : aliases.entrySet()) bytes += 128L + 2L * (entry.getKey().length() + entry.getValue().length());
        return bytes;
    }

    @Override public void close() {
        // Parsing results belong only to their caller; no external storage exists.
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
        if (id == null) id = names.get(normalizeDisplayName(name));
        if (id == null) id = qualityNames.getOrDefault(normalizeQuality(name), "");
        return programmes.getOrDefault(id, List.of());
    }

    public static String normalize(String name) { return name == null ? "" : WHITESPACE.matcher(name.strip().toLowerCase(Locale.ROOT)).replaceAll(" "); }

    /**
     * Normalise an XMLTV/provider guide identifier without discarding any
     * meaningful identifier token. Providers commonly vary only the
     * punctuation and case, for example StarGoldSelect.in vs
     * STAR.GOLD.SELECT.in.
     */
    public static String normalizeIdentifier(String value) {
        if (value == null) return "";
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Strip playlist presentation labels, retaining HD/SD, regions and time shifts. */
    public static String normalizeDisplayName(String name) {
        return AVAILABILITY.matcher(DISPLAY_RESOLUTION.matcher(normalize(name)).replaceAll("")).replaceAll("").strip();
    }

    static String normalizeQuality(String name) {
        String value = QUALITY.matcher(normalizeDisplayName(name)).replaceAll(" ");
        value = EMPTY_BRACKETS.matcher(value).replaceAll(" ");
        value = value.strip();
        while (!value.isEmpty() && EDGE_SEPARATORS.matcher(value.substring(0, 1)).matches()) {
            value = value.substring(1);
        }
        while (!value.isEmpty() && EDGE_SEPARATORS.matcher(value.substring(value.length() - 1)).matches()) {
            value = value.substring(0, value.length() - 1);
        }
        // Playlist availability annotations are not part of the broadcast name.
        value = value.replaceAll("[Ⓐ-Ⓩⓐ-ⓩ]", "");
        // Keep + so time-shift channels never collapse into their parent station.
        return value.replaceAll("[^\\p{L}\\p{N}+]", "");
    }
    private static Map<String, String> qualityAliases(Map<String, String> names) {
        Map<String, String> result = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
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
        } catch (RuntimeException _) { return null; }
    }
}
