package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Category extends BaseJson {
    private String dbId, accountId, accountType, categoryId, title, alias;
    private boolean activeSub;
    private int censored;

    public Category(String categoryId, String title, String alias, boolean activeSub, int censored) {
        this.categoryId = categoryId;
        this.title = title;
        this.alias = alias;
        this.activeSub = activeSub;
        this.censored = censored;
    }
}
