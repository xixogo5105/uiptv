package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.VodWatchStateService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public class HttpWatchingNowVodJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            ex.getResponseHeaders().set("Allow", "GET");
            ex.sendResponseHeaders(405, -1);
            return;
        }
        byte[] responseBytes = buildPayload().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, responseBytes.length);
        ex.getResponseBody().write(responseBytes);
        ex.getResponseBody().close();
    }

    private String buildPayload() {
        List<VodRow> rows = new ArrayList<>();
        for (Account account : AccountService.getInstance().getAll().values()) {
            if (account == null || isBlank(account.getDbId())) {
                continue;
            }
            for (VodWatchState state : VodWatchStateService.getInstance().getAllByAccount(account.getDbId())) {
                VodRow row = buildRow(account, state);
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        rows.sort(Comparator.comparingLong((VodRow row) -> row.updatedAt).reversed()
                .thenComparing(row -> row.vodName, String.CASE_INSENSITIVE_ORDER));
        JSONArray payload = new JSONArray();
        for (VodRow row : rows) {
            payload.put(row.toJson());
        }
        return payload.toString();
    }

    private VodRow buildRow(Account account, VodWatchState state) {
        if (account == null || state == null || isBlank(state.getVodId())) {
            return null;
        }
        Channel provider = resolveProviderChannel(account, state);
        Channel playbackChannel = provider != null ? provider : buildFallbackChannel(state);
        String title = firstNonBlank(provider == null ? "" : provider.getName(), state.getVodName(), state.getVodId());
        String logo = firstNonBlank(provider == null ? "" : provider.getLogo(), state.getVodLogo());
        String plot = firstNonBlank(provider == null ? "" : provider.getDescription(), "");
        String releaseDate = firstNonBlank(provider == null ? "" : provider.getReleaseDate(), "");
        String rating = firstNonBlank(provider == null ? "" : provider.getRating(), "");
        String duration = firstNonBlank(provider == null ? "" : provider.getDuration(), "");
        return new VodRow(account, state, playbackChannel, title, logo, plot, releaseDate, rating, duration);
    }

    private Channel resolveProviderChannel(Account account, VodWatchState state) {
        Channel direct = VodChannelDb.get().getChannelByChannelId(state.getVodId(), safe(state.getCategoryId()), account.getDbId());
        if (direct != null) {
            return direct;
        }
        List<Channel> matches = VodChannelDb.get().getAll(
                " WHERE accountId=? AND channelId=?",
                new String[]{account.getDbId(), state.getVodId()}
        );
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private Channel buildFallbackChannel(VodWatchState state) {
        Channel channel = new Channel();
        channel.setChannelId(state.getVodId());
        channel.setCategoryId(state.getCategoryId());
        channel.setName(state.getVodName());
        channel.setCmd(state.getVodCmd());
        channel.setLogo(state.getVodLogo());
        return channel;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static final class VodRow {
        private final String accountId;
        private final String accountName;
        private final String accountType;
        private final String categoryId;
        private final String vodId;
        private final String vodName;
        private final String vodLogo;
        private final String plot;
        private final String releaseDate;
        private final String rating;
        private final String duration;
        private final long updatedAt;
        private final Channel playItem;

        private VodRow(Account account, VodWatchState state, Channel playItem, String title, String logo, String plot, String releaseDate, String rating, String duration) {
            this.accountId = safeStatic(account.getDbId());
            this.accountName = safeStatic(account.getAccountName());
            this.accountType = safeStatic(account.getType() != null ? account.getType().name() : "");
            this.categoryId = safeStatic(state.getCategoryId());
            this.vodId = safeStatic(state.getVodId());
            this.vodName = safeStatic(title);
            this.vodLogo = safeStatic(logo);
            this.plot = safeStatic(plot);
            this.releaseDate = safeStatic(releaseDate);
            this.rating = safeStatic(rating);
            this.duration = safeStatic(duration);
            this.updatedAt = state.getUpdatedAt();
            this.playItem = playItem;
        }

        private JSONObject toJson() {
            JSONObject item = new JSONObject();
            item.put("accountId", accountId);
            item.put("accountName", accountName);
            item.put("accountType", accountType);
            item.put("categoryId", categoryId);
            item.put("vodId", vodId);
            item.put("vodName", vodName);
            item.put("vodLogo", vodLogo);
            item.put("plot", plot);
            item.put("releaseDate", releaseDate);
            item.put("rating", rating);
            item.put("duration", duration);
            item.put("updatedAt", updatedAt);
            if (playItem != null) {
                item.put("playItem", new JSONObject(playItem.toJson()));
            }
            return item;
        }

        private static String safeStatic(String value) {
            return value == null ? "" : value;
        }
    }
}
