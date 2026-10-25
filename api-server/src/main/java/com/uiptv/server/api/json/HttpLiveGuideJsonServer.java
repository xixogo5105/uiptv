package com.uiptv.server.api.json;

import com.sun.net.httpserver.*;
import com.uiptv.application.LiveGuideApplicationService;
import org.json.*;
import java.io.IOException;
import static com.uiptv.util.ServerUtils.*;

public class HttpLiveGuideJsonServer implements HttpHandler {
    private static final String ACCOUNT_ID = "accountId";

    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        JSONArray rows = new JSONArray();
        LiveGuideApplicationService service = new LiveGuideApplicationService();
        String visible = getParam(exchange, "visibleIds");
        var result = visible == null || visible.isBlank() ? service.list(getParam(exchange, ACCOUNT_ID))
                : service.visible(getParam(exchange, ACCOUNT_ID), visible);
        for (LiveGuideApplicationService.Row row : result) {
            JSONObject channel=new JSONObject(row.channel());
            rows.put(new JSONObject().put(ACCOUNT_ID, row.accountId()).put("accountName", row.accountName())
                    .put("nativePending", row.nativePending()).put("nativeProvider", row.nativeProvider()).put("bookmarkId", row.bookmarkId()).put("channel",channel)
                    .put("programmes", new JSONArray(row.programmes())));
        }
        generateJsonResponse(exchange, rows.toString());
    }
}
