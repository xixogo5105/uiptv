package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import static com.uiptv.util.UiptUtils.*;

public class StalkerPortalTextParserService {
    public static void saveBulkAccounts(String text, boolean pauseCaching, boolean collateAccounts) {
        String currentUrl = null;
        for (String line : text.split("\\R")) {
            for (String potentialUrlOrMac : replaceAllNonPrintableChars(line).split(SPACER)) {
                if (isValidURL(potentialUrlOrMac)) {
                    currentUrl = potentialUrlOrMac;
                } else if (currentUrl != null && isValidMACAddress(potentialUrlOrMac)) {

                    String name = getNameFromUrl(currentUrl);
                    Account account = AccountService.getInstance().getByName(name);
                    boolean accountExist = collateAccounts && account != null;
                    if (accountExist) {
                        AccountService.getInstance().save(new Account(name, account.getUsername(), account.getPassword(), currentUrl, account.getMacAddress(), account.getMacAddressList() + "," + potentialUrlOrMac, account.getSerialNumber(), account.getDeviceId1(), account.getDeviceId2(), account.getSignature(),
                                AccountType.STALKER_PORTAL, account.getEpg(), account.getM3u8Path(), pauseCaching));
                    } else {
                        String uniqueName = collateAccounts ? name : getUniqueNameFromUrl(currentUrl);
                        AccountService.getInstance().save(new Account(uniqueName, null, null, currentUrl, potentialUrlOrMac, potentialUrlOrMac, null, null, null, null,
                                AccountType.STALKER_PORTAL, null, null, pauseCaching));
                    }

                }
            }
        }
    }
}