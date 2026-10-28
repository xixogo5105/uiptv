package com.uiptv.server.api.json;

import com.uiptv.application.RemoteSyncApplicationService;
import com.uiptv.service.remotesync.RemoteSyncStatus;
import com.uiptv.util.AppLog;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WebSocketPingPongListener;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Host-driven lease checks; importing guests must answer each WebSocket ping with a pong. */
public final class RemoteSyncKeepAliveWebSocket extends JettyWebSocketServlet {
    private static final long PING_INTERVAL_SECONDS = 30;

    @Override
    protected void configure(JettyWebSocketServletFactory factory) {
        factory.setCreator((request, response) -> {
            List<String> sessionIds = request.getParameterMap().get("sessionId");
            if (sessionIds == null || sessionIds.isEmpty() || sessionIds.getFirst().isBlank()) return null;
            String sessionId = sessionIds.getFirst();
            try {
                RemoteSyncStatus status = RemoteSyncApplicationService.getInstance()
                        .getSessionState(sessionId).status();
                if (status != RemoteSyncStatus.PENDING_APPROVAL
                        && status != RemoteSyncStatus.APPROVED
                        && status != RemoteSyncStatus.READY_FOR_DOWNLOAD) return null;
                return new GuestHeartbeatSocket(sessionId);
            } catch (IllegalArgumentException | IllegalStateException _) {
                return null;
            }
        });
    }

    private static final class GuestHeartbeatSocket extends WebSocketAdapter implements WebSocketPingPongListener {
        private final String sessionId;
        private final AtomicLong sequence = new AtomicLong();
        private final AtomicLong awaitingPong = new AtomicLong();
        private ScheduledExecutorService scheduler;

        private GuestHeartbeatSocket(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public void onWebSocketConnect(Session session) {
            super.onWebSocketConnect(session);
            scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "remote-sync-host-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
            scheduler.scheduleWithFixedDelay(this::sendHostPing, 0,
                    PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }

        private void sendHostPing() {
            if (!isConnected()) return;
            long previous = awaitingPong.getAndSet(0);
            if (previous != 0) {
                close(1011, "Guest heartbeat timed out");
                return;
            }
            long pingId = sequence.incrementAndGet();
            awaitingPong.set(pingId);
            ByteBuffer payload = ByteBuffer.allocate(Long.BYTES).putLong(pingId);
            payload.flip();
            try {
                getRemote().sendPing(payload);
            } catch (IOException _) {
                close(1011, "Could not ping sync guest");
            }
        }

        @Override
        public void onWebSocketPong(ByteBuffer payload) {
            if (payload.remaining() != Long.BYTES) return;
            long pingId = payload.getLong();
            if (!awaitingPong.compareAndSet(pingId, 0)) return;
            try {
                RemoteSyncApplicationService.getInstance().recordGuestHeartbeat(sessionId);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                AppLog.addInfoLog(RemoteSyncKeepAliveWebSocket.class,
                        "Closing remote sync keep-alive for inactive session: " + ex.getMessage());
                close(1008, "Remote sync session is no longer active");
            }
        }

        private void close(int status, String reason) {
            if (scheduler != null) scheduler.shutdownNow();
            if (isConnected()) getSession().close(status, reason);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            if (scheduler != null) scheduler.shutdownNow();
            super.onWebSocketClose(statusCode, reason);
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            if (scheduler != null) scheduler.shutdownNow();
            super.onWebSocketError(cause);
        }
    }
}
