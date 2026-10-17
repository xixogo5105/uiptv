package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.RemoteSyncApplicationService;
import com.uiptv.service.remotesync.RemoteSyncJson;
import com.uiptv.service.remotesync.RemoteSyncSessionService;
import com.uiptv.util.AppLog;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.SQLException;

import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public class HttpRemoteSyncUploadServer implements HttpHandler {
    private static final String REMOTE_SYNC_FAILED_MESSAGE = "Remote database sync failed.";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "PUT");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
            if (contentLength != null && Long.parseLong(contentLength) > RemoteSyncSessionService.MAX_TRANSFER_BYTES) {
                writeJsonResponse(exchange, 413, new JSONObject().put("message", "Remote sync transfer is too large.").toString());
                return;
            }
            writeJsonResponse(exchange, 200, RemoteSyncJson.toJson(
                    RemoteSyncApplicationService.getInstance().acceptUpload(getParam(exchange, "sessionId"), exchange.getRequestBody())
            ).toString());
        } catch (RemoteSyncSessionService.TransferTooLargeException ex) {
            writeJsonResponse(exchange, 413, new JSONObject().put("message", "Remote sync transfer is too large.").toString());
        } catch (NumberFormatException ex) {
            writeJsonResponse(exchange, 400, new JSONObject().put("message", "Invalid Content-Length.").toString());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            writeJsonResponse(exchange, 400, new JSONObject().put("message", ex.getMessage()).toString());
        } catch (IOException | SQLException ex) {
            AppLog.addErrorLog(HttpRemoteSyncUploadServer.class, "Remote sync upload failed: " + ex.getMessage());
            writeJsonResponse(exchange, 500, new JSONObject().put("message", REMOTE_SYNC_FAILED_MESSAGE).toString());
        }
    }
}
