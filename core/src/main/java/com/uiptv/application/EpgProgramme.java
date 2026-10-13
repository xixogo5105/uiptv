package com.uiptv.application;

/** UTC epoch milliseconds keep guide times portable across desktop and web clients. */
public record EpgProgramme(String title, String description, long start, long stop) {
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public long getStart() { return start; }
    public long getStop() { return stop; }
}
