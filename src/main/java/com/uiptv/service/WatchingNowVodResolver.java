package com.uiptv.service;

import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;

import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public class WatchingNowVodResolver {

    public List<VodRow> resolveAll() {
        List<VodRow> rows = new ArrayList<>();
        for (Account account : AccountService.getInstance().getAll().values()) {
            rows.addAll(resolveForAccount(account));
        }
        return rows;
    }

    public List<VodRow> resolveForAccount(Account account) {
        List<VodRow> rows = new ArrayList<>();
        if (account == null || isBlank(account.getDbId())) {
            return rows;
        }
        for (VodWatchState state : VodWatchStateService.getInstance().getAllByAccount(account.getDbId())) {
            VodRow row = buildRow(account, state);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private VodRow buildRow(Account account, VodWatchState state) {
        if (account == null || state == null || isBlank(state.getVodId())) {
            return null;
        }
        Channel provider = resolveProviderChannel(account, state);
        VodMetadata providerMetadata = resolveMetadataFromProvider(provider);
        String title = firstNonBlank(providerMetadata.name, state.getVodName(), state.getVodId());
        String logo = firstNonBlank(providerMetadata.logo, state.getVodLogo());
        String plot = firstNonBlank(providerMetadata.plot, "");
        String releaseDate = firstNonBlank(providerMetadata.releaseDate, "");
        String rating = firstNonBlank(providerMetadata.rating, "");
        String duration = firstNonBlank(providerMetadata.duration, "");
        Channel playbackChannel = provider != null ? provider : buildFallbackChannel(state);
        VodMetadata metadata = new VodMetadata(logo, plot, releaseDate, rating, duration);
        return new VodRow(account, state, playbackChannel, title, metadata);
    }

    private VodMetadata resolveMetadataFromProvider(Channel provider) {
        if (provider == null) {
            return new VodMetadata("", "", "", "", "");
        }
        String name = safe(provider.getName());
        String logo = safe(provider.getLogo());
        String plot = safe(provider.getDescription());
        String releaseDate = safe(provider.getReleaseDate());
        String rating = safe(provider.getRating());
        String duration = safe(provider.getDuration());

        String extraJson = safe(provider.getExtraJson());
        if (!isBlank(extraJson)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject(extraJson);
                if (isBlank(name)) {
                    name = firstNonBlank(json.optString("name"), json.optString("o_name"));
                }
                if (isBlank(logo)) {
                    logo = firstNonBlank(json.optString("stream_icon"), json.optString("cover_big"), json.optString("cover"));
                }
                if (isBlank(plot)) {
                    plot = firstNonBlank(json.optString("description"), json.optString("plot"), json.optString("overview"));
                }
                if (isBlank(releaseDate)) {
                    releaseDate = firstNonBlank(json.optString("release_date"), json.optString("released"), json.optString("year"));
                }
                if (isBlank(rating)) {
                    rating = firstNonBlank(json.optString("rating_imdb"), json.optString("rating"));
                }
                if (isBlank(duration)) {
                    duration = firstNonBlank(json.optString("duration"), json.optString("runtime"), json.optString("time"));
                }
            } catch (Exception _) {
                // Ignore malformed provider metadata.
            }
        }
        return new VodMetadata(safe(logo), safe(plot), safe(releaseDate), safe(rating), safe(duration), safe(name));
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class VodRow {
        private final Account account;
        private final VodWatchState state;
        private final Channel playbackChannel;
        private final String displayTitle;
        private final VodMetadata metadata;

        private VodRow(Account account, VodWatchState state, Channel playbackChannel, String displayTitle, VodMetadata metadata) {
            this.account = account;
            this.state = state;
            this.playbackChannel = playbackChannel;
            this.displayTitle = displayTitle;
            this.metadata = metadata;
        }

        public Account getAccount() {
            return account;
        }

        public VodWatchState getState() {
            return state;
        }

        public Channel getPlaybackChannel() {
            return playbackChannel;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        public VodMetadata getMetadata() {
            return metadata;
        }
    }

    public static final class VodMetadata {
        private final String logo;
        private final String plot;
        private final String releaseDate;
        private final String rating;
        private final String duration;
        private final String name;

        private VodMetadata(String logo, String plot, String releaseDate, String rating, String duration) {
            this(logo, plot, releaseDate, rating, duration, "");
        }

        private VodMetadata(String logo, String plot, String releaseDate, String rating, String duration, String name) {
            this.logo = logo;
            this.plot = plot;
            this.releaseDate = releaseDate;
            this.rating = rating;
            this.duration = duration;
            this.name = name;
        }

        public String getLogo() {
            return logo;
        }

        public String getPlot() {
            return plot;
        }

        public String getReleaseDate() {
            return releaseDate;
        }

        public String getRating() {
            return rating;
        }

        public String getDuration() {
            return duration;
        }

        public String getName() {
            return name;
        }
    }
}
