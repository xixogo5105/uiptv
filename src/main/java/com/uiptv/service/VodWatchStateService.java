package com.uiptv.service;

import com.uiptv.db.VodWatchStateDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.uiptv.util.StringUtils.isBlank;

@SuppressWarnings("java:S6548")
public class VodWatchStateService {
    private final Set<VodWatchStateChangeListener> listeners = new CopyOnWriteArraySet<>();

    private VodWatchStateService() {
    }

    private static class SingletonHelper {
        private static final VodWatchStateService INSTANCE = new VodWatchStateService();
    }

    public static VodWatchStateService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public VodWatchState getVod(String accountId, String categoryId, String vodId) {
        if (isBlank(accountId) || isBlank(vodId)) {
            return null;
        }
        String canonicalVodId = canonicalizeVodId(vodId);
        VodWatchState exact = VodWatchStateDb.get().getByVod(accountId, normalize(categoryId), canonicalVodId);
        if (exact != null) {
            return exact;
        }
        VodWatchState latest = null;
        for (VodWatchState candidate : VodWatchStateDb.get().getByVod(accountId, canonicalVodId)) {
            if (candidate == null) {
                continue;
            }
            if (latest == null || candidate.getUpdatedAt() > latest.getUpdatedAt()) {
                latest = candidate;
            }
        }
        return latest;
    }

    public List<VodWatchState> getAllByAccount(String accountId) {
        if (isBlank(accountId)) {
            return List.of();
        }
        return VodWatchStateDb.get().getByAccount(accountId);
    }

    public boolean isSaved(String accountId, String categoryId, String vodId) {
        return getVod(accountId, categoryId, vodId) != null;
    }

    public void save(Account account, String categoryId, Channel channel) {
        if (account == null || isBlank(account.getDbId()) || channel == null || isBlank(channel.getChannelId())) {
            return;
        }
        VodWatchState state = new VodWatchState();
        state.setAccountId(account.getDbId());
        state.setCategoryId(normalize(categoryId));
        state.setVodId(canonicalizeVodId(channel.getChannelId()));
        state.setVodName(channel.getName());
        state.setVodCmd(channel.getCmd());
        state.setVodLogo(channel.getLogo());
        state.setUpdatedAt(System.currentTimeMillis());
        VodWatchStateDb.get().upsert(state);
        notifyListeners(account.getDbId(), state.getVodId());
    }

    public void remove(String accountId, String categoryId, String vodId) {
        if (isBlank(accountId) || isBlank(vodId)) {
            return;
        }
        String canonicalVodId = canonicalizeVodId(vodId);
        VodWatchStateDb.get().clear(accountId, normalize(categoryId), canonicalVodId);
        notifyListeners(accountId, canonicalVodId);
    }

    public void clearAll() {
        VodWatchStateDb.get().clearAll();
        notifyListeners("", "");
    }

    public void addChangeListener(VodWatchStateChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeChangeListener(VodWatchStateChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(String accountId, String vodId) {
        for (VodWatchStateChangeListener listener : listeners) {
            listener.onChanged(accountId, vodId);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String canonicalizeVodId(String vodId) {
        String raw = vodId == null ? "" : vodId.trim();
        if (isBlank(raw) || !raw.contains(":")) {
            return raw;
        }
        String[] parts = raw.split(":");
        String last = "";
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = normalize(parts[i]);
            if (!isBlank(p)) {
                last = p;
                break;
            }
        }
        return isBlank(last) ? raw : last;
    }
}
