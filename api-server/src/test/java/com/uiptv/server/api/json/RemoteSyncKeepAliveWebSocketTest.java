package com.uiptv.server.api.json;

import com.uiptv.application.RemoteSyncApplicationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class RemoteSyncKeepAliveWebSocketTest {
    @Test
    void heartbeatPongIgnoresMalformedAndRecordsMatchingSequence() throws Exception {
        Class<?> socketType = Class.forName("com.uiptv.server.api.json.RemoteSyncKeepAliveWebSocket$GuestHeartbeatSocket");
        Constructor<?> constructor = socketType.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        Object socket = constructor.newInstance("session");
        Field awaiting = socketType.getDeclaredField("awaitingPong");
        awaiting.setAccessible(true);
        ((AtomicLong) awaiting.get(socket)).set(7L);

        var pong = socketType.getMethod("onWebSocketPong", ByteBuffer.class);
        assertDoesNotThrow(() -> pong.invoke(socket, ByteBuffer.allocate(1)));
        try (var service = mockStatic(RemoteSyncApplicationService.class)) {
            var applicationService = mock(RemoteSyncApplicationService.class);
            service.when(RemoteSyncApplicationService::getInstance).thenReturn(applicationService);
            ByteBuffer payload = ByteBuffer.allocate(Long.BYTES);
            payload.putLong(7L).flip();
            assertDoesNotThrow(() -> pong.invoke(socket, payload));
            verify(applicationService).recordGuestHeartbeat("session");
        }
    }
}
