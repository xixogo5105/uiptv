package com.uiptv.application;

import java.io.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** In-memory compressed XMLTV schedules; only requested channels are expanded. */
final class CompactProgrammeMap extends AbstractMap<String, List<EpgProgramme>> {
    private final Map<String, List<byte[]>> channels;
    private final long bytes;
    private final Map<String, List<EpgProgramme>> decoded = new LinkedHashMap<>(16, .75f, true);

    private CompactProgrammeMap(Map<String, List<byte[]>> channels, long bytes) {
        this.channels = Map.copyOf(channels);
        this.bytes = bytes;
    }

    long estimatedBytes() { return bytes + channels.size() * 192L + 512 * 1024; }
    @Override public boolean containsKey(Object key) { return channels.containsKey(key); }
    @Override public int size() { return channels.size(); }

    @Override public synchronized List<EpgProgramme> get(Object key) {
        List<byte[]> chunks = channels.get(key);
        if (chunks == null) return null;
        List<EpgProgramme> cached = decoded.get(key);
        if (cached != null) return cached;
        List<EpgProgramme> programmes = new ArrayList<>();
        try {
            for (byte[] chunk : chunks) {
                try (var input = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(chunk)))) {
                    int count = input.readInt();
                    for (int i = 0; i < count; i++)
                        programmes.add(new EpgProgramme(input.readUTF(), input.readUTF(), input.readLong(), input.readLong()));
                }
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        programmes.sort(Comparator.comparingLong(EpgProgramme::start));
        List<EpgProgramme> completed = new ArrayList<>();
        for (int i = 0; i < programmes.size(); i++) {
            EpgProgramme p = programmes.get(i);
            long stop = p.stop() == 0 && i + 1 < programmes.size() ? programmes.get(i + 1).start() : p.stop();
            if (stop > p.start()) completed.add(new EpgProgramme(p.title(), p.description(), p.start(), stop));
        }
        List<EpgProgramme> result = EpgSchedules.normalize(completed);
        decoded.put((String) key, result);
        long retained = decoded.values().stream().mapToLong(CompactProgrammeMap::expandedBytes).sum();
        var entries = decoded.entrySet().iterator();
        while (entries.hasNext() && (decoded.size() > 8 || retained > 512 * 1024)) {
            retained -= expandedBytes(entries.next().getValue()); entries.remove();
        }
        return result;
    }

    private static long expandedBytes(List<EpgProgramme> programmes) {
        return programmes.stream().mapToLong(p -> 192L + 2L * (p.title().length() + p.description().length())).sum();
    }

    @Override public Set<Entry<String, List<EpgProgramme>>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return channels.size(); }
            @Override public Iterator<Entry<String, List<EpgProgramme>>> iterator() {
                Iterator<String> ids = channels.keySet().iterator();
                return new Iterator<>() {
                    public boolean hasNext() { return ids.hasNext(); }
                    public Entry<String, List<EpgProgramme>> next() {
                        String id = ids.next(); return Map.entry(id, get(id));
                    }
                };
            }
        };
    }

    static final class Builder {
        private final Map<String, List<byte[]>> chunks = new HashMap<>();
        private final Map<String, List<EpgProgramme>> pending = new HashMap<>();
        private long pendingBytes;
        private long compressedBytes;

        void add(String id, EpgProgramme programme) throws IOException {
            pending.computeIfAbsent(id, _ -> new ArrayList<>()).add(programme);
            pendingBytes += 192L + 2L * (id.length() + programme.title().length() + programme.description().length());
            if (pendingBytes >= 512 * 1024) flush();
        }

        private void flush() throws IOException {
            for (var entry : pending.entrySet()) {
                var bytes = new ByteArrayOutputStream();
                try (var output = new DataOutputStream(new DeflaterOutputStream(bytes))) {
                    output.writeInt(entry.getValue().size());
                    for (EpgProgramme p : entry.getValue()) {
                        output.writeUTF(p.title()); output.writeUTF(p.description());
                        output.writeLong(p.start()); output.writeLong(p.stop());
                    }
                }
                byte[] chunk = bytes.toByteArray();
                chunks.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>()).add(chunk);
                compressedBytes += chunk.length + 32L;
            }
            pending.clear(); pendingBytes = 0;
            if (compressedBytes + chunks.size() * 192L > 32L * 1024 * 1024)
                throw new IllegalArgumentException("Guide exceeds size limit");
        }

        CompactProgrammeMap build() throws IOException {
            flush();
            chunks.replaceAll((_, value) -> List.copyOf(value));
            return new CompactProgrammeMap(chunks, compressedBytes);
        }
    }
}
