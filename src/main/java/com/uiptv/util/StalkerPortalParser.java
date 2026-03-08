package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.util.UiptUtils.getNameFromUrl;

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
    public List<Account> parseAndSave(String text, boolean groupAccountsByMac, boolean convertM3uToXtreme) {
        String sanitizedText = sanitizeInput(text);
        List<Account> parsedAccounts = new ArrayList<>();
        Account account = new Account();
        MacAttributeParser macParser = new MacAttributeParser();
        UrlAttributeParser urlParser = new UrlAttributeParser();
        String lastSeenUrl = null;
        for (String line : sanitizedText.split("\\R")) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;

            ParsedLine parsedLine = parseLine(line, trimmedLine, urlParser, macParser);
            lastSeenUrl = parsedLine.url != null ? parsedLine.url : lastSeenUrl;
            if (shouldStartNewAccount(account, parsedLine.mac)) {
                addParsedAccount(parsedAccounts, account);
                account = newAccountWithLastSeenUrl(lastSeenUrl);
            }
            applyLineMetadata(account, trimmedLine);
            applyParsedAttributes(account, line);
        }
        addParsedAccount(parsedAccounts, account);
        return saveAccounts(parsedAccounts, groupAccountsByMac);
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

    private List<Account> saveAccounts(List<Account> accounts, boolean groupAccountsByMac) {
        Map<String, Account> groupedAccounts = new LinkedHashMap<>();
        List<Account> individualAccounts = new ArrayList<>();
        List<Account> createdAccounts = new ArrayList<>();
        Set<String> processedNames = new HashSet<>();

        List<Account> validAccounts = accounts.stream()
                .filter(acc -> isNotBlank(acc.getUrl()) && isNotBlank(acc.getMacAddress()))
                .toList();

        for (Account currentAccount : validAccounts) {
            if (groupAccountsByMac && !hasExtraParams(currentAccount)) {
                saveGroupedAccount(currentAccount, groupedAccounts, createdAccounts, processedNames);
            } else {
                saveIndividualAccount(currentAccount, individualAccounts, createdAccounts, processedNames);
            }
        }

        individualAccounts.forEach(accountSaver);
        groupedAccounts.values().forEach(accountSaver);
        return createdAccounts;
    }

    private String sanitizeInput(String text) {
        return Arrays.stream(text.split("\\R"))
                .filter(line -> !(line.contains("http") && line.contains("?") && line.contains("=")))
                .map(UiptUtils::sanitizeStalkerText)
                .collect(Collectors.joining("\n"));
    }

    private ParsedLine parseLine(String line, String trimmedLine, UrlAttributeParser urlParser, MacAttributeParser macParser) {
        String url = urlParser.parse(line);
        String mac = url == null ? macParser.parse(line) : null;
        return new ParsedLine(trimmedLine, url, mac);
    }

    private boolean shouldStartNewAccount(Account account, String mac) {
        return mac != null && isNotBlank(account.getMacAddress());
    }

    private void addParsedAccount(List<Account> parsedAccounts, Account account) {
        if (isNotBlank(account.getUrl()) && isNotBlank(account.getMacAddress())) {
            parsedAccounts.add(account);
        }
    }

    private Account newAccountWithLastSeenUrl(String lastSeenUrl) {
        Account account = new Account();
        if (lastSeenUrl != null) {
            account.setUrl(lastSeenUrl);
            account.setType(AccountType.STALKER_PORTAL);
        }
        return account;
    }

    private void applyLineMetadata(Account account, String trimmedLine) {
        if ("POST".equalsIgnoreCase(trimmedLine)) {
            account.setHttpMethod("POST");
        }
        String detectedTimezone = detectTimezone(trimmedLine);
        if (detectedTimezone != null) {
            account.setTimezone(detectedTimezone);
        }
    }

    private void applyParsedAttributes(Account account, String line) {
        for (StalkerAttributeParser parser : attributeParsers) {
            String value = parser.parse(line);
            if (value != null) {
                applyValueToAccount(account, value, parser.getAttributeType());
                return;
            }
        }
    }

    private boolean hasExtraParams(Account account) {
        return isNotBlank(account.getSerialNumber())
                || isNotBlank(account.getDeviceId1())
                || isNotBlank(account.getDeviceId2())
                || isNotBlank(account.getSignature());
    }

    private void saveGroupedAccount(Account currentAccount, Map<String, Account> groupedAccounts,
                                    List<Account> createdAccounts, Set<String> processedNames) {
        String name = getNameFromUrl(currentAccount.getUrl().replace("_", ""));
        currentAccount.setAccountName(name);
        Account existing = groupedAccounts.get(name);
        if (existing != null) {
            appendMacAddress(existing, currentAccount.getMacAddress());
            return;
        }
        Account existingInDb = accountProvider.apply(name);
        if (existingInDb != null) {
            appendMacAddress(existingInDb, currentAccount.getMacAddress());
            groupedAccounts.put(name, existingInDb);
            processedNames.add(existingInDb.getAccountName());
            return;
        }
        currentAccount.setMacAddressList(currentAccount.getMacAddress());
        groupedAccounts.put(name, currentAccount);
        processedNames.add(name);
        createdAccounts.add(currentAccount);
    }

    private void saveIndividualAccount(Account currentAccount, List<Account> individualAccounts,
                                       List<Account> createdAccounts, Set<String> processedNames) {
        String uniqueName = nextUniqueAccountName(currentAccount.getUrl(), processedNames);
        currentAccount.setAccountName(uniqueName);
        currentAccount.setMacAddressList(currentAccount.getMacAddress());
        individualAccounts.add(currentAccount);
        processedNames.add(uniqueName);
        createdAccounts.add(currentAccount);
    }

    private String nextUniqueAccountName(String url, Set<String> processedNames) {
        String baseName = getNameFromUrl(url);
        int counter = 1;
        String uniqueName = baseName + "(" + counter + ")";
        while (accountProvider.apply(uniqueName) != null || processedNames.contains(uniqueName)) {
            counter++;
            uniqueName = baseName + "(" + counter + ")";
        }
        return uniqueName;
    }

    private void appendMacAddress(Account account, String macAddress) {
        String macList = account.getMacAddressList() != null ? account.getMacAddressList() : account.getMacAddress();
        if (!macList.contains(macAddress)) {
            account.setMacAddressList(macList + "," + macAddress);
        }
    }

    private record ParsedLine(String trimmedLine, String url, String mac) {
    }

    /**
     * Detects if the given line contains a valid timezone identifier.
     * Performs case-insensitive matching against all available ZoneIds.
     *
     * @param line The line to check for timezone
     * @return The matched timezone string, or null if no timezone is found
     */
    private String detectTimezone(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        String lowerLine = line.toLowerCase();

        // Get all available zone IDs and perform case-insensitive contains matching
        for (String zoneId : java.time.ZoneId.getAvailableZoneIds()) {
            if (lowerLine.contains(zoneId.toLowerCase())) {
                return zoneId;
            }
        }

        return null;
    }
}
