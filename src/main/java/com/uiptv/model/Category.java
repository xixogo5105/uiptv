package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONObject;

import static com.uiptv.util.StringUtils.safeGetString;

@Data
@NoArgsConstructor
public class Category extends BaseJson {
    private String dbId, accountId, accountType, categoryId, title, alias;
    private String extraJson;
    private boolean activeSub;
    private int censored;

    public Category(String categoryId, String title, String alias, boolean activeSub, int censored) {
        this.categoryId = categoryId;
        this.title = title;
        this.alias = alias;
        this.activeSub = activeSub;
        this.censored = censored;
    }

    public static Category fromJson(String json) {
        try {
            JSONObject jsonObj = new JSONObject(json);
            Category category = new Category();
            category.setDbId(safeGetString(jsonObj, "dbId"));
            category.setAccountId(safeGetString(jsonObj, "accountId"));
            category.setAccountType(safeGetString(jsonObj, "accountType"));
            category.setCategoryId(safeGetString(jsonObj, "categoryId"));
            category.setTitle(safeGetString(jsonObj, "title"));
            category.setAlias(safeGetString(jsonObj, "alias"));
            category.setExtraJson(safeGetString(jsonObj, "extraJson"));
            category.setActiveSub(jsonObj.optBoolean("activeSub"));
            category.setCensored(jsonObj.optInt("censored"));
            return category;
        } catch (Exception e) {
            return null;
        }
    }
}
