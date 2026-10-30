package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.util.I18n;

import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.AccountType.*;

public class CategoryResolver {
    public static final String ALL_CATEGORY_ID = CategoryType.ALL.identifier();

    public List<Category> resolveCategories(Account account, List<Category> categories) {
        List<Category> processed = new ArrayList<>(categories == null ? List.of() : categories);
        processed = filterM3uCategories(account, processed);

        boolean hasAllCategory = processed.stream().anyMatch(this::isAllCategory);
        return addAllCategoryIfNeeded(account, processed, hasAllCategory);
    }

    private List<Category> filterM3uCategories(Account account, List<Category> categories) {
        if (account == null || (account.getType() != M3U8_LOCAL && account.getType() != M3U8_URL)) {
            return categories;
        }
        return categories.stream()
                .filter(category -> shouldKeepM3uCategory(account, category))
                .toList();
    }

    private boolean shouldKeepM3uCategory(Account account, Category category) {
        if (category == null || !account.isCacheEnabled() || !isUncategorized(category)) {
            return category != null;
        }
        return ChannelService.getInstance().hasCachedLiveChannelsByDbCategoryId(category.getDbId());
    }

    private List<Category> addAllCategoryIfNeeded(Account account, List<Category> categories, boolean hasAllCategory) {
        boolean providerNeedsMultipleCategories = account != null
                && (account.getType() == STALKER_PORTAL || account.getType() == XTREME_API);
        if (hasAllCategory || (providerNeedsMultipleCategories && categories.size() < 2)) {
            return categories;
        }
        List<Category> withAll = new ArrayList<>(categories.size() + 1);
        withAll.add(buildAllCategory());
        withAll.addAll(categories);
        return withAll;
    }

    private boolean isUncategorized(Category category) {
        if (category == null || category.getTitle() == null) {
            return false;
        }
        return CategoryType.isUncategorized(category.getTitle());
    }

    private boolean isAllCategory(Category category) {
        if (category == null) {
            return false;
        }
        String title = category.getTitle();
        String categoryId = category.getCategoryId();
        String dbId = category.getDbId();
        return isAllValue(title) || isAllValue(categoryId) || isAllValue(dbId);
    }

    private boolean isAllValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        if (ALL_CATEGORY_ID.equalsIgnoreCase(normalized)) {
            return true;
        }
        if (CategoryType.isAll(normalized)) {
            return true;
        }
        return I18n.tr("commonAll").equalsIgnoreCase(normalized);
    }

    private Category buildAllCategory() {
        Category category = new Category();
        category.setDbId(ALL_CATEGORY_ID);
        category.setCategoryId(ALL_CATEGORY_ID);
        category.setTitle(CategoryType.ALL.displayName());
        category.setAlias(CategoryType.ALL.displayName());
        return category;
    }
}
