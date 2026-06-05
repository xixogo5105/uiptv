package com.uiptv.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchTargetTest {
    @Test
    void applyForwardsQueryWhenTargetExistsAndIgnoresNullTarget() {
        AtomicReference<String> query = new AtomicReference<>();

        SearchTarget.apply(query::set, "bbc");
        SearchTarget.apply(null, "ignored");

        assertEquals("bbc", query.get());
    }
}
