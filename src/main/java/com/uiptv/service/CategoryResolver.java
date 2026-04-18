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

        if (account != null && (account.getType() == M3U8_LOCAL || account.getType() == M3U8_URL)) {
            processed = processed.stream()
                    .filter(category -> {
                        if (category == null) {
                            return false;
                        }
                        if (isUncategorized(category)) {
                            return ChannelService.getInstance().hasCachedLiveChannelsByDbCategoryId(category.getDbId());
                        }
                        return true;
                    })
                    .toList();
        }

        boolean hasAllCategory = processed.stream().anyMatch(this::isAllCategory);
        boolean shouldAddAll = true;
        if (account != null && (account.getType() == STALKER_PORTAL || account.getType() == XTREME_API)) {
            shouldAddAll = processed.size() >= 2;
        }
        if (!hasAllCategory && shouldAddAll) {
            List<Category> withAll = new ArrayList<>();
            withAll.add(buildAllCategory());
            withAll.addAll(processed);
            processed = withAll;
        }

        return processed;
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
