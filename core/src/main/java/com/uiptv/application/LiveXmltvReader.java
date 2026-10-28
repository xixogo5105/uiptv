package com.uiptv.application;

import javax.xml.stream.*;
import java.io.InputStream;
import java.time.*;
import java.util.*;

/** Streams a response and retains only the requested channel's programme list for its open view. */
final class LiveXmltvReader {
    private static final String CHANNEL_ELEMENT = "channel";
    private LiveXmltvReader() { }
    static List<EpgProgramme> read(InputStream input, String guideId, String name, Instant now)
            throws XMLStreamException, InterruptedException {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, base, namespace) -> { throw new XMLStreamException("External entities disabled"); });
        var reader = factory.createXMLStreamReader(input);
        ParseState state = new ParseState(guideId);
        try {
            while (reader.hasNext()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    handleStart(reader, state, name);
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (handleEnd(reader, state, now)) break;
                }
            }
        } finally { reader.close(); }
        state.programmes.sort(Comparator.comparingLong(EpgProgramme::start));
        List<EpgProgramme> completed = new ArrayList<>();
        for (int i = 0; i < state.programmes.size(); i++) {
            var p = state.programmes.get(i);
            long end = p.stop();
            if (end <= p.start()) {
                end = i + 1 < state.programmes.size() ? state.programmes.get(i + 1).start() : 0;
            }
            if (end > p.start()) completed.add(new EpgProgramme(p.title(), p.description(), p.start(), end));
        }
        return EpgSchedules.normalize(completed);
    }

    private static void handleStart(XMLStreamReader reader, ParseState state, String name) throws XMLStreamException {
        switch (reader.getLocalName()) {
            case CHANNEL_ELEMENT -> handleChannelStart(reader, state);
            case "display-name" -> handleDisplayName(reader, state, name);
            case "programme" -> handleProgrammeStart(reader, state);
            case "title" -> {
                if (state.channel.equals(state.selected) && !state.ambiguous) state.title = text(reader, 512);
            }
            case "desc" -> {
                if (state.channel.equals(state.selected) && !state.ambiguous) state.description = text(reader, 2048);
            }
            default -> { }
        }
    }

    private static void handleChannelStart(XMLStreamReader reader, ParseState state) {
        state.definition = Objects.toString(reader.getAttributeValue(null, "id"), "");
        if (!state.requested.isBlank() && state.definition.equals(state.requested)) {
            state.selected = state.requested;
            state.priority = 4;
            state.ambiguous = false;
        }
    }

    private static void handleDisplayName(XMLStreamReader reader, ParseState state, String name)
            throws XMLStreamException {
        String display = text(reader, 512);
        int match = displayMatch(state, display, name);
        if (match > state.priority) {
            state.selected = state.definition;
            state.priority = match;
            state.ambiguous = false;
        } else if (match > 1 && match == state.priority && !state.selected.equals(state.definition)) {
            state.ambiguous = true;
        }
    }

    private static int displayMatch(ParseState state, String display, String name) {
        if (!state.requested.isBlank() && state.requested.equals(state.definition)) return 4;
        String normalizedName = XmltvGuide.normalize(name);
        if (!normalizedName.isBlank() && XmltvGuide.normalize(display).equals(normalizedName)) return 3;
        String qualityName = XmltvGuide.normalizeQuality(name);
        return !qualityName.isBlank() && XmltvGuide.normalizeQuality(display).equals(qualityName) ? 2 : 0;
    }

    private static void handleProgrammeStart(XMLStreamReader reader, ParseState state) {
        state.channel = Objects.toString(reader.getAttributeValue(null, CHANNEL_ELEMENT), "");
        state.start = XmltvGuide.time(reader.getAttributeValue(null, "start"));
        state.stop = XmltvGuide.time(reader.getAttributeValue(null, "stop"));
        state.title = "";
        state.description = "";
    }

    private static boolean handleEnd(XMLStreamReader reader, ParseState state, Instant now) {
        String element = reader.getLocalName();
        if (CHANNEL_ELEMENT.equals(element)) state.definition = "";
        if (!"programme".equals(element)) return false;
        if (shouldKeepProgramme(state, now)) {
            if (state.programmes.size() >= 2000) return true;
            state.programmes.add(new EpgProgramme(state.title, state.description, state.start.toEpochMilli(),
                    state.stop == null ? 0 : state.stop.toEpochMilli()));
        }
        state.channel = "";
        return false;
    }

    private static boolean shouldKeepProgramme(ParseState state, Instant now) {
        return !state.ambiguous && !state.selected.isBlank() && state.channel.equals(state.selected)
                && state.start != null && !state.title.isBlank()
                && state.start.isAfter(now.minus(Duration.ofDays(1)))
                && state.start.isBefore(now.plus(Duration.ofDays(7)))
                && (state.stop == null || state.stop.isAfter(now));
    }

    private static final class ParseState {
        private final String requested;
        private final List<EpgProgramme> programmes = new ArrayList<>();
        private String selected;
        private String definition = "";
        private String channel = "";
        private String title = "";
        private String description = "";
        private int priority;
        private boolean ambiguous;
        private Instant start;
        private Instant stop;

        private ParseState(String guideId) {
            requested = guideId == null ? "" : guideId.strip();
            selected = requested;
            priority = requested.isBlank() ? 0 : 1;
        }
    }
    private static String text(XMLStreamReader reader, int maximum) throws XMLStreamException {
        StringBuilder value = new StringBuilder();
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) depth++;
            else if (event == XMLStreamConstants.END_ELEMENT) depth--;
            else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) && value.length() < maximum)
                value.append(reader.getText(), 0, Math.min(reader.getTextLength(), maximum - value.length()));
        }
        return value.toString().strip();
    }
}
