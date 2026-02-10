package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.uiptv.util.UiptUtils.getNameFromUrl;
import static com.uiptv.util.UiptUtils.replaceAllNonPrintableChars;

/**
 * Handles parsing of Xtreme accounts.
 */
public class XtremeParser implements AccountParser {
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");
    private static final Pattern LABELED_USER = Pattern.compile("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*(\\S+)");
    private static final Pattern LABELED_PASS = Pattern.compile("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*(\\S+)");

    @Override
    public void parseAndSave(String text, boolean groupAccountsByMac, boolean convertM3uToXtreme) {
        List<String> lines = Arrays.asList(text.split("\\R"));
        List<String> currentBlock = new ArrayList<>();

        for (String line : lines) {
            String trimmed = replaceAllNonPrintableChars(line).trim();
            if (trimmed.isEmpty()) {
                if (!currentBlock.isEmpty()) {
                    processBlock(currentBlock);
                    currentBlock.clear();
                }
                continue;
            }
            currentBlock.add(trimmed);
        }
        if (!currentBlock.isEmpty()) {
            processBlock(currentBlock);
        }
    }

    private void processBlock(List<String> block) {
        String joinedBlock = String.join(" ", block);
        String url = null, username = null, password = null;

        Matcher mUrl = URL_PATTERN.matcher(joinedBlock);
        if (mUrl.find()) url = mUrl.group(1);
        if (url == null) return;

        Matcher mUser = LABELED_USER.matcher(joinedBlock);
        if (mUser.find()) username = mUser.group(2);
        
        Matcher mPass = LABELED_PASS.matcher(joinedBlock);
        if (mPass.find()) password = mPass.group(3);

        if (username == null || password == null) {
            String remaining = joinedBlock.replace(url, "");
            if (username != null) remaining = remaining.replaceAll("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*" + Pattern.quote(username), "");
            if (password != null) remaining = remaining.replaceAll("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*" + Pattern.quote(password), "");

            String[] tokens = remaining.trim().split("\\s+");
            List<String> unlabeled = Arrays.stream(tokens).filter(s -> !s.isEmpty() && s.length() > 1).collect(Collectors.toList());

            if (username == null && !unlabeled.isEmpty()) username = unlabeled.remove(0);
            if (password == null && !unlabeled.isEmpty()) password = unlabeled.remove(0);
        }

        if (url != null && username != null && password != null) {
            String name = getNameFromUrl(url);
            AccountService.getInstance().save(new Account(name, username, password, url, null, null, null, null, null, null,
                    AccountType.XTREME_API, null, url, false));
        }
    }
}
