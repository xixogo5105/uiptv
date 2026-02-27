package com.uiptv.service;

import com.uiptv.ui.LogDisplayUI;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHlsService {
    private static InMemoryHlsService instance;
    // Use ConcurrentHashMap for thread safety.
    // We will manually manage the size to prevent memory leaks.
    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();
    private final Map<String, Long> timestamps = new ConcurrentHashMap<>();
    
    // Safety limit: FFmpeg is configured for list_size 5. 
    // We allow a buffer (e.g., 20) to account for network lag or player buffering.
    // Each segment is ~2-5MB. 20 segments is ~100MB max, which is safe.
    private static final int MAX_SEGMENTS = 20;

    private InMemoryHlsService() {
    }

    public static synchronized InMemoryHlsService getInstance() {
        if (instance == null) {
            instance = new InMemoryHlsService();
        }
        return instance;
    }

    public void put(String name, byte[] data) {
        // If it's a segment (.ts), check capacity
        if (name.endsWith(".ts")) {
            cleanupOldSegments();
        }
        
        storage.put(name, data);
        timestamps.put(name, System.currentTimeMillis());
    }

    public byte[] get(String name) {
        // Update timestamp on access (optional, but good for LRU if we implemented strict LRU)
        if (storage.containsKey(name)) {
            timestamps.put(name, System.currentTimeMillis());
        }
        return storage.get(name);
    }

    public void remove(String name) {
        storage.remove(name);
        timestamps.remove(name);
    }

    public boolean exists(String name) {
        return storage.containsKey(name);
    }

    public void clear() {
        storage.clear();
        timestamps.clear();
    }

    private void cleanupOldSegments() {
        // Simple protection: if we have too many TS files, remove the oldest ones.
        // This protects against FFmpeg failing to send DELETE requests.
        
        long tsCount = storage.keySet().stream().filter(k -> k.endsWith(".ts")).count();
        
        if (tsCount > MAX_SEGMENTS) {
            // Find the oldest TS file
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;

            for (Map.Entry<String, Long> entry : timestamps.entrySet()) {
                if (entry.getKey().endsWith(".ts")) {
                    if (entry.getValue() < oldestTime) {
                        oldestTime = entry.getValue();
                        oldestKey = entry.getKey();
                    }
                }
            }

            if (oldestKey != null) {
                remove(oldestKey);
                LogDisplayUI.addLog("InMemoryHlsService: Evicted old segment " + oldestKey + " to free memory.");
            }
        }
    }
}
