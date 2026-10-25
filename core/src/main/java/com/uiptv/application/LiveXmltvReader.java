package com.uiptv.application;

import javax.xml.stream.*;
import java.io.InputStream;
import java.time.*;
import java.util.*;

/** Streams a response and retains only the requested channel's programme list for its open view. */
final class LiveXmltvReader {
    private LiveXmltvReader() { }
    static List<EpgProgramme> read(InputStream input, String guideId, String name, Instant now)
            throws Exception {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, base, namespace) -> { throw new XMLStreamException("External entities disabled"); });
        var reader = factory.createXMLStreamReader(input);
        String requested = guideId == null ? "" : guideId.strip();
        String selected = requested, definition = "", channel = "", title = "", description = "";
        int priority = requested.isBlank() ? 0 : 1;
        boolean ambiguous = false;
        Instant start = null, stop = null;
        List<EpgProgramme> programmes = new ArrayList<>();
        try {
            while (reader.hasNext()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "channel" -> {
                            definition = Objects.toString(reader.getAttributeValue(null, "id"), "");
                            if (!requested.isBlank() && definition.equals(requested)) { selected = requested; priority = 4; ambiguous = false; }
                        }
                        case "display-name" -> {
                            String display = text(reader, 512);
                            int match = !requested.isBlank() && requested.equals(definition) ? 4
                                    : !XmltvGuide.normalize(name).isBlank() && XmltvGuide.normalize(display).equals(XmltvGuide.normalize(name)) ? 3
                                    : !XmltvGuide.normalizeQuality(name).isBlank() && XmltvGuide.normalizeQuality(display).equals(XmltvGuide.normalizeQuality(name)) ? 2 : 0;
                            if (match > priority) { selected = definition; priority = match; ambiguous = false; }
                            else if (match > 1 && match == priority && !selected.equals(definition)) ambiguous = true;
                        }
                        case "programme" -> {
                            channel = Objects.toString(reader.getAttributeValue(null, "channel"), "");
                            start = XmltvGuide.time(reader.getAttributeValue(null, "start"));
                            stop = XmltvGuide.time(reader.getAttributeValue(null, "stop"));
                            title = ""; description = "";
                        }
                        case "title" -> { if (channel.equals(selected) && !ambiguous) title = text(reader, 512); }
                        case "desc" -> { if (channel.equals(selected) && !ambiguous) description = text(reader, 2048); }
                        default -> { }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (reader.getLocalName().equals("channel")) definition = "";
                    if (reader.getLocalName().equals("programme")) {
                        if (!ambiguous && !selected.isBlank() && channel.equals(selected) && start != null && !title.isBlank()
                                && start.isAfter(now.minus(Duration.ofDays(1))) && start.isBefore(now.plus(Duration.ofDays(7)))
                                && (stop == null || stop.isAfter(now))) {
                            if (programmes.size() >= 2000) break;
                            programmes.add(new EpgProgramme(title, description, start.toEpochMilli(), stop == null ? 0 : stop.toEpochMilli()));
                        }
                        channel = "";
                    }
                }
            }
        } finally { reader.close(); }
        programmes.sort(Comparator.comparingLong(EpgProgramme::start));
        List<EpgProgramme> completed = new ArrayList<>();
        for (int i = 0; i < programmes.size(); i++) {
            var p = programmes.get(i);
            long end = p.stop() > p.start() ? p.stop() : i + 1 < programmes.size() ? programmes.get(i + 1).start() : 0;
            if (end > p.start()) completed.add(new EpgProgramme(p.title(), p.description(), p.start(), end));
        }
        return EpgSchedules.normalize(completed);
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
