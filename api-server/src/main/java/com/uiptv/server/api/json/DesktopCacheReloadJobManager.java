package com.uiptv.server.api.json;

import com.uiptv.model.Account;
import com.uiptv.service.AccountResolver;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.cache.SkipAccountReloadException;
import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import com.uiptv.util.XtremeCredentialsJson;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class DesktopCacheReloadJobManager {
    private static final DesktopCacheReloadJobManager INSTANCE = new DesktopCacheReloadJobManager();
    private static final int MAX_ACCOUNTS_PER_JOB = 200;
    private static final int MAX_LOG_LINES = 2_000;
    private static final long DECISION_TIMEOUT_MINUTES = 30;
    private static final String GLOBAL_SERIES_CATEGORY_LIST_FAILED = "Global SERIES category list failed:";
    private static final String GLOBAL_STALKER_GET_ALL_CHANNELS_FAILED = "Global Stalker get_all_channels failed";
    private static final String GLOBAL_VOD_CATEGORY_LIST_FAILED = "Global VOD category list failed:";
    private static final String GLOBAL_XTREME_CHANNEL_LOOKUP_FAILED = "Global Xtreme channel lookup failed";
    private static final String NETWORK_ERROR_LOADING_CATEGORIES = "Network error while loading categories";
    private static final Pattern FOUND_CHANNELS_PATTERN =
            Pattern.compile("Found\\s+Channels\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLLECTED_CHANNELS_PATTERN =
            Pattern.compile("Collected\\s+(\\d+)\\s+channels", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULL_CENSORING_ZERO_RESULT_PATTERN = Pattern.compile(
            "All\\s+(categories|channels)\\s+removed\\s+by\\s+active\\s+censoring\\.\\s+Keeping\\s+existing\\s+cache\\.",
            Pattern.CASE_INSENSITIVE);

    private final CacheService cacheService;
    private final List<Job> jobs = new ArrayList<>();

    private DesktopCacheReloadJobManager() {
        this(new CacheServiceImpl());
    }

    DesktopCacheReloadJobManager(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    static DesktopCacheReloadJobManager getInstance() {
        return INSTANCE;
    }

    synchronized Job start(List<String> accountIds, String failurePolicy) {
        if (hasRunningJob()) {
            throw new IllegalStateException("A cache reload is already running");
        }
        if (accountIds == null || accountIds.isEmpty()) {
            throw new IllegalArgumentException("At least one account is required");
        }
        if (accountIds.size() > MAX_ACCOUNTS_PER_JOB) {
            throw new IllegalArgumentException("Too many accounts selected");
        }
        FailurePolicy policy = FailurePolicy.fromValue(failurePolicy);
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(accountIds);
        List<Account> accounts = new ArrayList<>(uniqueIds.size());
        for (String id : uniqueIds) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Account IDs must not be blank");
            }
            Account account = AccountService.getInstance().getById(id);
            if (account == null) {
                throw new IllegalArgumentException("An account no longer exists");
            }
            if (!Account.CACHE_SUPPORTED.contains(account.getType())) {
                throw new IllegalArgumentException("An account type does not support cache reload");
            }
            accounts.add(account);
        }
        Job job = new Job(UUID.randomUUID().toString(), accounts, policy);
        jobs.add(job);
        while (jobs.size() > 50) {
            Job oldest = jobs.getFirst();
            if (oldest.isActive()) break;
            jobs.removeFirst();
        }
        Thread.ofVirtual().name("uiptv-desktop-cache-reload-" + job.id).start(() -> run(job, accounts));
        return job;
    }

    synchronized Job get(String id) {
        return jobs.stream().filter(job -> job.id.equals(id)).findFirst().orElse(null);
    }

    List<AccountResolver.AccountRow> availableAccounts() {
        return new AccountResolver().resolveAccounts().stream()
                .filter(account -> Account.CACHE_SUPPORTED.stream()
                        .anyMatch(type -> type.name().equals(account.getType())))
                .toList();
    }

    boolean stop(String id) {
        Job job = get(id);
        if (job == null || !job.isActive()) return false;
        job.stopRequested = true;
        CompletableFuture<FailureDecision> pending = job.pendingDecision;
        if (pending != null) pending.complete(FailureDecision.STOP_ALL);
        job.addLog("Stop requested; the active account will finish before the next account is skipped.");
        return true;
    }

    boolean decide(String id, String decisionValue) {
        Job job = get(id);
        if (job == null) return false;
        PendingFailure pending = job.pendingFailure;
        CompletableFuture<FailureDecision> future = job.pendingDecision;
        if (pending == null || future == null || future.isDone()) return false;
        FailureDecision decision = FailureDecision.fromValue(decisionValue);
        if (decision == FailureDecision.MARK_BAD_AND_IGNORE_DOMAIN && !pending.canIgnoreDomain) {
            throw new IllegalArgumentException("This account cannot ignore similar URL failures");
        }
        return future.complete(decision);
    }

    private synchronized boolean hasRunningJob() {
        return jobs.stream().anyMatch(Job::isActive);
    }

    private void run(Job job, List<Account> accounts) {
        job.status = JobStatus.RUNNING;
        Set<String> ignoredDomains = new HashSet<>();
        try {
            for (int index = 0; index < accounts.size(); index++) {
                if (job.stopRequested) break;
                Account account = accounts.get(index);
                AccountState state = job.accountStates.get(index);
                state.status = "running";
                if (!account.isCacheEnabled()) {
                    state.status = "skipped";
                    state.log(I18n.tr("reloadCacheDisabledSkipping"));
                    job.completeAccount();
                    continue;
                }
                String domainKey = repeatFailureDomain(account);
                if (domainKey != null && ignoredDomains.contains(domainKey)) {
                    state.status = "skipped";
                    state.log(I18n.tr("reloadSkippedIgnoredDomain", domainKey));
                    job.completeAccount();
                    continue;
                }
                AtomicBoolean globalFailurePrompted = new AtomicBoolean(false);
                try {
                    cacheService.reloadCacheWithPhases(account, (message, phase) -> {
                        state.log("[" + phase.name().toUpperCase(Locale.ROOT) + "] " + message);
                        state.recordMessage(message);
                        String reason = globalFailureReason(phase, message);
                        if (reason == null || !globalFailurePrompted.compareAndSet(false, true)) return;
                        boolean canIgnore = domainKey != null;
                        FailureDecision decision = resolveDecision(job, state, reason, canIgnore);
                        if (decision == FailureDecision.STOP_ALL) {
                            job.stopRequested = true;
                            throw new SkipAccountReloadException();
                        }
                        if (decision == FailureDecision.MARK_BAD_AND_IGNORE_DOMAIN && canIgnore) {
                            ignoredDomains.add(domainKey);
                        }
                        if (decision != FailureDecision.CARRY_ON) {
                            state.status = "bad";
                            throw new SkipAccountReloadException();
                        }
                    });
                    int existingCount = cacheService.getChannelCountForAccount(account.getDbId());
                    state.channelCount = Math.max(state.fetchedChannelCount, existingCount);
                    if (state.criticalFailure) {
                        state.status = existingCount > 0 ? "yellow" : "failed";
                    } else if (state.fetchedChannelCount <= 0 && !state.fullCensoringZeroResult) {
                        state.status = existingCount > 0 ? "yellow" : "empty";
                        state.log(I18n.tr("reloadNoChannelsFound"));
                    } else {
                        state.status = state.fetchedChannelCount > 0 ? "done" : "yellow";
                    }
                } catch (SkipAccountReloadException ignored) {
                    state.channelCount = cacheService.getChannelCountForAccount(account.getDbId());
                    if (job.stopRequested) {
                        state.status = "stopped";
                    } else {
                        state.status = state.channelCount > 0 ? "yellow" : "failed";
                    }
                } catch (Exception error) {
                    state.channelCount = cacheService.getChannelCountForAccount(account.getDbId());
                    state.status = state.channelCount > 0 ? "yellow" : "failed";
                    state.log("Reload failed (" + error.getClass().getSimpleName() + "): "
                            + DesktopApiLogBuffer.redact(safeMessage(error)));
                } finally {
                    job.completeAccount();
                }
            }
            job.status = job.stopRequested ? JobStatus.STOPPED : JobStatus.COMPLETED;
        } catch (Throwable error) {
            job.status = JobStatus.FAILED;
            job.addLog("Reload job failed (" + error.getClass().getSimpleName() + ")");
        } finally {
            job.pendingFailure = null;
            job.pendingDecision = null;
            job.finishedAt = Instant.now().toString();
        }
    }

    private FailureDecision resolveDecision(
            Job job,
            AccountState account,
            String reason,
            boolean canIgnoreDomain) {
        if (job.failurePolicy != FailurePolicy.ASK) {
            return job.failurePolicy.asDecision(canIgnoreDomain);
        }
        CompletableFuture<FailureDecision> future = new CompletableFuture<>();
        job.pendingFailure = new PendingFailure(account.dbId, account.accountName, reason, canIgnoreDomain);
        job.pendingDecision = future;
        job.status = JobStatus.WAITING_FOR_DECISION;
        try {
            return future.get(DECISION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return FailureDecision.STOP_ALL;
        } catch (ExecutionException | TimeoutException error) {
            return FailureDecision.MARK_BAD;
        } finally {
            job.pendingFailure = null;
            job.pendingDecision = null;
            if (!job.stopRequested) job.status = JobStatus.RUNNING;
        }
    }

    private String globalFailureReason(Account.AccountAction phase, String message) {
        if (message == null) return null;
        String text = message.trim();
        if (text.startsWith("No channels returned by get_all_channels")
                || text.startsWith(GLOBAL_STALKER_GET_ALL_CHANNELS_FAILED)) {
            return I18n.tr("reloadGlobalLiveTvChannelCallFailed");
        }
        if (text.startsWith(GLOBAL_XTREME_CHANNEL_LOOKUP_FAILED)
                || text.startsWith("Global Xtreme channel lookup returned no channels")
                || text.startsWith("Global Xtreme channel lookup returned uncategorized rows only")) {
            return I18n.tr("reloadGlobalXtremeLookupFailed");
        }
        if (text.startsWith(GLOBAL_VOD_CATEGORY_LIST_FAILED)) {
            return I18n.tr("reloadModeCategoryListCallFailed", I18n.tr("categoryTabVideoOnDemand"));
        }
        if (text.startsWith(GLOBAL_SERIES_CATEGORY_LIST_FAILED)) {
            return I18n.tr("reloadModeCategoryListCallFailed", I18n.tr("categoryTabTvSeries"));
        }
        return null;
    }

    private String repeatFailureDomain(Account account) {
        if (!canIgnoreDomainFailures(account)) return null;
        String raw = switch (account.getType()) {
            case STALKER_PORTAL, XTREME_API -> account.getUrl();
            case M3U8_URL -> account.getM3u8Path();
            default -> null;
        };
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        try {
            String host = URI.create(trimmed).getHost();
            if (host != null && !host.isBlank()) return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException _) {
            // Fall back to the portion before the first path/port separator.
        }
        int protocol = trimmed.indexOf("://");
        if (protocol >= 0) {
            String host = trimmed.substring(protocol + 3).split("[/:]", 2)[0];
            if (!host.isBlank()) return host.toLowerCase(Locale.ROOT);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean canIgnoreDomainFailures(Account account) {
        if (account.getType() == AccountType.M3U8_URL) return true;
        if (account.getType() == AccountType.XTREME_API) {
            return XtremeCredentialsJson.parse(account.getXtremeCredentialsJson()).size() <= 1;
        }
        if (account.getType() == AccountType.STALKER_PORTAL) {
            String macs = account.getMacAddressList();
            if (macs == null || macs.isBlank()) macs = account.getMacAddress();
            if (macs == null || macs.isBlank()) return true;
            return java.util.Arrays.stream(macs.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .limit(2)
                    .count() <= 1;
        }
        return false;
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "no detail" : message;
    }

    enum JobStatus {
        QUEUED("queued"), RUNNING("running"), WAITING_FOR_DECISION("waiting_for_decision"),
        COMPLETED("completed"), STOPPED("stopped"), FAILED("failed");

        private final String value;

        JobStatus(String value) {
            this.value = value;
        }
    }

    enum FailurePolicy {
        ASK("ask"), CARRY_ON("carryOn"), MARK_BAD("markBad"),
        MARK_BAD_IGNORE_DOMAIN("markBadIgnoreDomain"), STOP_ALL("stopAll");

        private final String value;

        FailurePolicy(String value) {
            this.value = value;
        }

        static FailurePolicy fromValue(String value) {
            for (FailurePolicy policy : values()) {
                if (policy.value.equals(value)) return policy;
            }
            throw new IllegalArgumentException("Unsupported failure policy");
        }

        FailureDecision asDecision(boolean canIgnoreDomain) {
            return switch (this) {
                case CARRY_ON -> FailureDecision.CARRY_ON;
                case MARK_BAD -> FailureDecision.MARK_BAD;
                case MARK_BAD_IGNORE_DOMAIN -> canIgnoreDomain
                        ? FailureDecision.MARK_BAD_AND_IGNORE_DOMAIN : FailureDecision.MARK_BAD;
                case STOP_ALL -> FailureDecision.STOP_ALL;
                case ASK -> throw new IllegalStateException("Ask policy requires a user decision");
            };
        }
    }

    enum FailureDecision {
        CARRY_ON("carryOn"), MARK_BAD("markBad"),
        MARK_BAD_AND_IGNORE_DOMAIN("markBadIgnoreDomain"), STOP_ALL("stopAll");

        private final String value;

        FailureDecision(String value) {
            this.value = value;
        }

        static FailureDecision fromValue(String value) {
            for (FailureDecision decision : values()) {
                if (decision.value.equals(value)) return decision;
            }
            throw new IllegalArgumentException("Unsupported failure decision");
        }
    }

    record PendingFailure(String accountDbId, String accountName, String reason, boolean canIgnoreDomain) {
    }

    static final class Job {
        private final String id;
        private final FailurePolicy failurePolicy;
        private final List<AccountState> accountStates;
        private final ArrayDeque<String> logs = new ArrayDeque<>();
        private volatile JobStatus status = JobStatus.QUEUED;
        private volatile PendingFailure pendingFailure;
        private volatile CompletableFuture<FailureDecision> pendingDecision;
        private volatile boolean stopRequested;
        private volatile int completed;
        private volatile String finishedAt = "";

        private Job(String id, List<Account> accounts, FailurePolicy failurePolicy) {
            this.id = id;
            this.failurePolicy = failurePolicy;
            this.accountStates = accounts.stream().map(AccountState::new).toList();
        }

        private boolean isActive() {
            return status == JobStatus.QUEUED || status == JobStatus.RUNNING
                    || status == JobStatus.WAITING_FOR_DECISION;
        }

        private synchronized void addLog(String line) {
            logs.addLast(Instant.now() + " " + DesktopApiLogBuffer.redact(line));
            while (logs.size() > MAX_LOG_LINES) logs.removeFirst();
        }

        private synchronized void completeAccount() {
            completed++;
        }

        synchronized JSONObject toJson() {
            JSONArray accounts = new JSONArray();
            accountStates.stream().map(AccountState::toJson).forEach(accounts::put);
            JSONObject response = new JSONObject()
                    .put("jobId", id)
                    .put("status", status.value)
                    .put("total", accountStates.size())
                    .put("completed", completed)
                    .put("failurePolicy", failurePolicy.value)
                    .put("stopRequested", stopRequested)
                    .put("accounts", accounts)
                    .put("logs", new JSONArray(logs))
                    .put("finishedAt", finishedAt);
            PendingFailure currentFailure = pendingFailure;
            if (currentFailure != null) {
                response.put("pendingFailure", new JSONObject()
                        .put("accountDbId", currentFailure.accountDbId())
                        .put("accountName", currentFailure.accountName())
                        .put("reason", DesktopApiLogBuffer.redact(currentFailure.reason()))
                        .put("canIgnoreDomain", currentFailure.canIgnoreDomain()));
            }
            return response;
        }
    }

    private static final class AccountState {
        private final String dbId;
        private final String accountName;
        private final String type;
        private volatile String status = "queued";
        private volatile int channelCount;
        private volatile int fetchedChannelCount;
        private volatile boolean criticalFailure;
        private volatile boolean fullCensoringZeroResult;
        private final List<String> logs = new ArrayList<>();

        private AccountState(Account account) {
            dbId = account.getDbId();
            accountName = account.getAccountName();
            type = account.getType() == null ? "" : account.getType().name();
        }

        synchronized void log(String message) {
            logs.add(DesktopApiLogBuffer.redact(message));
            if (logs.size() > 500) logs.removeFirst();
        }

        void recordMessage(String rawMessage) {
            if (rawMessage == null) return;
            Matcher fetched = FOUND_CHANNELS_PATTERN.matcher(rawMessage);
            if (!fetched.find()) fetched = COLLECTED_CHANNELS_PATTERN.matcher(rawMessage);
            if (fetched.find(0)) {
                try {
                    fetchedChannelCount = Integer.parseInt(fetched.group(1));
                } catch (NumberFormatException _) {
                    fetchedChannelCount = 0;
                }
            }
            String trimmed = rawMessage.trim();
            if (FULL_CENSORING_ZERO_RESULT_PATTERN.matcher(trimmed).matches()) {
                fullCensoringZeroResult = true;
            }
            if (trimmed.startsWith("Reload failed:")
                    || trimmed.startsWith("Failed:")
                    || trimmed.equals("Handshake failed.")
                    || trimmed.startsWith("Handshake failed for")
                    || trimmed.startsWith(NETWORK_ERROR_LOADING_CATEGORIES)
                    || trimmed.startsWith("Failed to parse channels")
                    || trimmed.startsWith("Last-resort fetch failed for category")) {
                criticalFailure = true;
            }
        }

        synchronized JSONObject toJson() {
            return new JSONObject()
                    .put("dbId", dbId)
                    .put("accountName", accountName)
                    .put("type", type)
                    .put("status", status)
                    .put("channelCount", channelCount)
                    .put("logs", new JSONArray(logs));
        }
    }
}