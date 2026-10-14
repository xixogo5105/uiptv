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
        LiveGuideApplicationService service = new LiveGuideApplicationService();
        String visible = getParam(exchange, "visibleIds");
        var result = visible == null || visible.isBlank() ? service.list(getParam(exchange, "accountId"))
                : service.visible(getParam(exchange, "accountId"), visible);
        for (LiveGuideApplicationService.Row row : result) {
            rows.put(new JSONObject().put("accountId", row.accountId()).put("accountName", row.accountName())
                    .put("nativePending", row.nativePending()).put("nativeProvider", row.nativeProvider()).put("bookmarkId", row.bookmarkId()).put("channel", new JSONObject(row.channel()))
                    .put("programmes", new JSONArray(row.programmes())));
        }
        String warning = new com.uiptv.application.EpgApplicationService().globalWarning();
        if (!warning.isBlank()) exchange.getResponseHeaders().set("X-EPG-Warning", warning);
        generateJsonResponse(exchange, rows.toString());
    }
}
