package com.uiptv.server.api.json;

import com.sun.net.httpserver.*;
import com.uiptv.application.LiveGuideApplicationService;
import org.json.*;
import java.io.IOException;
import static com.uiptv.util.ServerUtils.*;

public class HttpLiveGuideJsonServer implements HttpHandler {
    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        JSONArray rows = new JSONArray();
        for (LiveGuideApplicationService.Row row : new LiveGuideApplicationService().list(getParam(exchange, "accountId"))) {
            rows.put(new JSONObject().put("accountId", row.accountId()).put("accountName", row.accountName())
                    .put("bookmarkId", row.bookmarkId()).put("channel", new JSONObject(row.channel()))
                    .put("programmes", new JSONArray(row.programmes())));
        }
        generateJsonResponse(exchange, rows.toString());
    }
}
