package com.uiptv.server.api.json;

import com.uiptv.server.TestHttpExchange;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;

class HttpPlayerGatewayServerTest {

    @Test
    void handle_delegatesToPlayerHandler() throws Exception {
        HttpPlayerGatewayServer gateway = new HttpPlayerGatewayServer();
        HttpPlayerJsonServer delegate = Mockito.mock(HttpPlayerJsonServer.class);

        Field field = HttpPlayerGatewayServer.class.getDeclaredField("delegate");
        field.setAccessible(true);
        field.set(gateway, delegate);

        TestHttpExchange exchange = new TestHttpExchange("/player", "GET");
        assertDoesNotThrow(() -> gateway.handle(exchange));
        verify(delegate).handle(exchange);
    }
}
