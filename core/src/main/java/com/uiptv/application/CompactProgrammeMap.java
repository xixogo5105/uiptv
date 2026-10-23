package com.uiptv.application;

import java.io.*;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Disk-backed compressed XMLTV schedules; only requested channels are expanded. */
final class CompactProgrammeMap extends AbstractMap<String, List<EpgProgramme>> {
    private static final Cleaner CLEANER = Cleaner.create();
    private record Chunk(long offset, int length, byte[] memory) { }
    private final Map<String, List<Chunk>> channels;
    private final long heapBytes;
    private final Path spool;
    private final Map<String, List<EpgProgramme>> decoded = new LinkedHashMap<>(16, .75f, true);
    private final Cleaner.Cleanable cleanable;

    private CompactProgrammeMap(Map<String, List<Chunk>> channels, long heapBytes, Path spool) {
        this.channels = Map.copyOf(channels);
        this.heapBytes = heapBytes;
        this.spool = spool;
        this.cleanable = spool == null ? null : CLEANER.register(this, new DeleteSpool(spool));
    }

    private record DeleteSpool(Path path) implements Runnable {
        @Override public void run() { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }
    }

    void close() { if (cleanable != null) cleanable.clean(); }
    long estimatedBytes() { return heapBytes + channels.size() * 64L + 256 * 1024; }
    @Override public boolean containsKey(Object key) { return channels.containsKey(key); }
    @Override public int size() { return channels.size(); }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof Map<?, ?> map && entrySet().equals(map.entrySet());
    }
    @Override public int hashCode() { return entrySet().hashCode(); }

    @Override public synchronized List<EpgProgramme> get(Object key) {
        List<Chunk> chunks = channels.get(key);
        if (chunks == null) return List.of();
        List<EpgProgramme> cached = decoded.get(key);
        if (cached != null) return cached;
        List<EpgProgramme> programmes = new ArrayList<>();
        try (var file = spool == null ? null : new RandomAccessFile(spool.toFile(), "r")) {
            for (Chunk chunk : chunks) {
                byte[] bytes = chunk.memory();
                if (bytes == null) {
                    bytes = new byte[chunk.length()];
                    file.seek(chunk.offset());
                    file.readFully(bytes);
                }
                try (var input = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))) {
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
        private final Map<String, List<Chunk>> chunks = new HashMap<>();
        private final Map<String, List<EpgProgramme>> pending = new HashMap<>();
        private final Path spool;
        private final RandomAccessFile output;
        private final long maxSpoolBytes;
        private final boolean diskBacked;
        private long pendingBytes;
        private long spoolBytes;

        Builder() throws IOException { this(32L * 1024 * 1024, false); }

        Builder(long maxSpoolBytes, boolean diskBacked) throws IOException {
            this.maxSpoolBytes = maxSpoolBytes;
            this.diskBacked = diskBacked;
            if (diskBacked) {
                spool = Files.createTempFile("uiptv-epg-", ".bin");
                spool.toFile().deleteOnExit();
                output = new RandomAccessFile(spool.toFile(), "rw");
            } else {
                spool = null;
                output = null;
            }
        }

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
                if (spoolBytes + chunk.length > maxSpoolBytes)
                    throw new IllegalArgumentException("Guide exceeds size limit");
                long offset = diskBacked ? spoolBytes : -1;
                if (diskBacked) output.write(chunk);
                chunks.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>()).add(
                        new Chunk(offset, chunk.length, diskBacked ? null : chunk));
                spoolBytes += chunk.length;
            }
            pending.clear(); pendingBytes = 0;
        }

        CompactProgrammeMap build() throws IOException {
            flush();
            if (output != null) output.close();
            chunks.replaceAll((_, value) -> List.copyOf(value));
            long references = chunks.values().stream().mapToLong(List::size).sum();
            return new CompactProgrammeMap(chunks, chunks.size() * 64L + references * 24L + (diskBacked ? 0 : spoolBytes), spool);
        }

        void close() {
            if (output != null) try { output.close(); } catch (IOException ignored) { }
            if (spool != null) try { Files.deleteIfExists(spool); } catch (IOException ignored) { }
        }
    }
}
