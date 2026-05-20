package com.uiptv.service;

import com.uiptv.model.Account;

public record CategoryCacheRemovalResult(
        int requestedCategoryCount,
        int removedCategoryCount,
        int removedItemCount,
        Account.AccountAction mode
) {
}
