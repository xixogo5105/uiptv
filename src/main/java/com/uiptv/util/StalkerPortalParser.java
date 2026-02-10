package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.util.UiptUtils.getNameFromUrl;
import static com.uiptv.util.UiptUtils.getUniqueNameFromUrl;

/**
 * Handles parsing of Stalker Portal accounts.
 */
public class StalkerPortalParser implements AccountParser {
    private static final List<StalkerAttributeParser> attributeParsers = Arrays.asList(
            new UrlAttributeParser(),
            new MacAttributeParser(),
            new SerialCutAttributeParser(),
            new SerialAttributeParser(),
            new SignatureAttributeParser(),
            new DeviceId1AttributeParser(),
            new DeviceId2AttributeParser()
    );

    private final Function<String, Account> accountProvider;
    private final Consumer<Account> accountSaver;

    public StalkerPortalParser() {
        this(
            name -> AccountService.getInstance().getByName(name),
            account -> AccountService.getInstance().save(account)
        );
    }

    public StalkerPortalParser(Function<String, Account> accountProvider, Consumer<Account> accountSaver) {
        this.accountProvider = accountProvider;
        this.accountSaver = accountSaver;
    }

    @Override
    public void parseAndSave(String text, boolean groupAccountsByMac, boolean convertM3uToXtreme) {
        String sanitizedText = Arrays.stream(text.split("\\R"))
            .filter(line -> !(line.contains("http") && line.contains("?") && line.contains("=")))
            .map(UiptUtils::sanitizeStalkerText)
            .collect(Collectors.joining("\n"));
        List<Account> parsedAccounts = new ArrayList<>();
        Account account = new Account();
        MacAttributeParser macParser = new MacAttributeParser();
        UrlAttributeParser urlParser = new UrlAttributeParser();
        String lastSeenUrl = null;
        for (String line : sanitizedText.split("\\R")) {
            if (line.trim().isEmpty()) continue;

            String url = urlParser.parse(line);
            String mac = null;
            if (url != null) {
                lastSeenUrl = url;
            } else {
                mac = macParser.parse(line);
            }
            if (mac != null && isNotBlank(account.getMacAddress())) {
                if (isNotBlank(account.getUrl())) {
                    parsedAccounts.add(account);
                }
                account = new Account();
                if (lastSeenUrl != null) {
                    account.setUrl(lastSeenUrl);
                    account.setType(AccountType.STALKER_PORTAL);
                }
            }

            for (StalkerAttributeParser parser : attributeParsers) {
                String value = parser.parse(line);
                if (value != null) {
                    applyValueToAccount(account, value, parser.getAttributeType());
                    break;
                }
            }
        }
        if (isNotBlank(account.getUrl()) && isNotBlank(account.getMacAddress())) {
            parsedAccounts.add(account);
        }
        saveAccounts(parsedAccounts, groupAccountsByMac);
    }

    private void applyValueToAccount(Account account, String value, StalkerAttributeType type) {
        switch (type) {
            case URL:
                if (!isNotBlank(account.getUrl())) {
                    account.setUrl(value);
                    account.setType(AccountType.STALKER_PORTAL);
                }
                break;
            case MAC:
                account.setMacAddress(value);
                break;
            case SERIAL:
                if (!isNotBlank(account.getSerialNumber())) {
                    account.setSerialNumber(value);
                }
                break;
            case SERIAL_CUT:
                account.setSerialNumber(value);
                break;
            case DEVICE_ID_1:
                account.setDeviceId1(value);
                break;
            case DEVICE_ID_2:
                account.setDeviceId2(value);
                break;
            case SIGNATURE:
                if (!isNotBlank(account.getSignature())) {
                    account.setSignature(value);
                }
                break;
        }
    }

    private void saveAccounts(List<Account> accounts, boolean groupAccountsByMac) {
        Map<String, Account> groupedAccounts = new LinkedHashMap<>();
        List<Account> individualAccounts = new ArrayList<>();
        Set<String> processedNames = new HashSet<>();

        List<Account> validAccounts = accounts.stream()
                .filter(acc -> isNotBlank(acc.getUrl()) && isNotBlank(acc.getMacAddress()))
                .collect(Collectors.toList());

        for (Account currentAccount : validAccounts) {
            boolean hasExtraParams = isNotBlank(currentAccount.getSerialNumber()) || isNotBlank(currentAccount.getDeviceId1()) || isNotBlank(currentAccount.getDeviceId2()) || isNotBlank(currentAccount.getSignature());

            if (groupAccountsByMac && !hasExtraParams) {
                String name = getNameFromUrl(currentAccount.getUrl().replace("_", ""));
                currentAccount.setAccountName(name);

                if (groupedAccounts.containsKey(name)) {
                    Account existing = groupedAccounts.get(name);
                    String macList = existing.getMacAddressList() != null ? existing.getMacAddressList() : existing.getMacAddress();
                    if (!macList.contains(currentAccount.getMacAddress())) {
                        existing.setMacAddressList(macList + "," + currentAccount.getMacAddress());
                    }
                } else {
                    Account existingInDb = accountProvider.apply(name);
                    if (existingInDb != null) {
                        String macList = existingInDb.getMacAddressList() != null ? existingInDb.getMacAddressList() : existingInDb.getMacAddress();
                        if (!macList.contains(currentAccount.getMacAddress())) {
                            existingInDb.setMacAddressList(macList + "," + currentAccount.getMacAddress());
                        }
                        groupedAccounts.put(name, existingInDb);
                        processedNames.add(existingInDb.getAccountName());
                    } else {
                        currentAccount.setMacAddressList(currentAccount.getMacAddress());
                        groupedAccounts.put(name, currentAccount);
                        processedNames.add(name);
                    }
                }
            } else {
                String baseName = getUniqueNameFromUrl(currentAccount.getUrl());
                String uniqueName = baseName;
                int counter = 1;
                while (accountProvider.apply(uniqueName) != null || processedNames.contains(uniqueName)) {
                    uniqueName = baseName + "(" + counter++ + ")";
                }
                currentAccount.setAccountName(uniqueName);
                currentAccount.setMacAddressList(currentAccount.getMacAddress());
                individualAccounts.add(currentAccount);
                processedNames.add(uniqueName);
            }
        }

        individualAccounts.forEach(accountSaver);
        groupedAccounts.values().forEach(accountSaver);
    }
}
