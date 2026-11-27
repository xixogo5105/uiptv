package com.uiptv.application;

import com.uiptv.model.Account;
import com.uiptv.service.AccountResolver;
import com.uiptv.util.TextParserService;

import java.util.List;
import java.util.Locale;

public final class BulkAccountImportApplicationService {
    private final AccountResolver accountResolver = new AccountResolver();

    public BulkAccountImportApplicationService() {
    }

    public List<AccountResolver.AccountRow> importAccounts(
            String sourceType,
            String data,
            boolean groupAccounts,
            boolean convertM3uToXtreme) {
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("Account data is required");
        }
        String parserMode = switch (sourceType == null ? "" : sourceType.trim().toLowerCase(Locale.ROOT)) {
            case "stalker", "stalker portal" -> TextParserService.MODE_STALKER;
            case "xtreme" -> TextParserService.MODE_XTREME;
            case "m3u", "m3u8", "m3u playlists" -> TextParserService.MODE_M3U;
            default -> throw new IllegalArgumentException("Unsupported account source type");
        };
        List<Account> accounts = TextParserService.saveBulkAccounts(
                data,
                parserMode,
                groupAccounts,
                convertM3uToXtreme);
        if (accounts == null) {
            return List.of();
        }
        return accounts.stream().map(accountResolver::fromAccount).toList();
    }

}