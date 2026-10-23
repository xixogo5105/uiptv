package com.uiptv.application;

import com.uiptv.util.HtmlEntityDecoder;

/** UTC epoch milliseconds keep guide times portable across desktop and web clients. */
public record EpgProgramme(String title, String description, long start, long stop) {
    /**
     * XMLTV feeds sometimes contain HTML-escaped text inside XML. Decode it once when creating the
     * shared programme value so desktop, web, and API consumers all display the same readable title.
     */
    public EpgProgramme {
        title = HtmlEntityDecoder.decode(title);
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public long getStart() { return start; }
    public long getStop() { return stop; }
}
