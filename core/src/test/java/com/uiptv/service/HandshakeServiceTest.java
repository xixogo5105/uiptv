package com.uiptv.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandshakeServiceTest {

    @Test
    void parseJasonToken_returnsTokenWhenPresent() {
        String json = """
                {
                  "js": {
                    "token": "abc123token"
                  }
                }
                """;

        String token = HandshakeService.getInstance().parseJasonToken(json);

        assertEquals("abc123token", token);
    }
}
