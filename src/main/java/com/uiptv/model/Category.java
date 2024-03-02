package com.uiptv.model;


import com.uiptv.api.JsonCompliant;

import java.io.Serializable;
import java.util.Objects;

import static com.uiptv.util.StringUtils.safeJson;

public class Category implements Serializable, JsonCompliant {
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

    public String getDbId() {
        return dbId;
    }

    public void setDbId(String dbId) {
        this.dbId = dbId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public boolean isActiveSub() {
        return activeSub;
    }

    public void setActiveSub(boolean activeSub) {
        this.activeSub = activeSub;
    }

    public int getCensored() {
        return censored;
    }

    public void setCensored(int censored) {
        this.censored = censored;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return activeSub == category.activeSub && censored == category.censored && Objects.equals(dbId, category.dbId) && Objects.equals(accountId, category.accountId) && Objects.equals(accountType, category.accountType) && Objects.equals(categoryId, category.categoryId) && Objects.equals(title, category.title) && Objects.equals(alias, category.alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbId, accountId, accountType, categoryId, title, alias, activeSub, censored);
    }


    @Override
    public String toString() {
        return "Category{" +
                "dbId='" + dbId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", accountType='" + accountType + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", title='" + title + '\'' +
                ", alias='" + alias + '\'' +
                ", activeSub=" + activeSub +
                ", censored=" + censored +
                '}';
    }

    @Override
    public String toJson() {
        return "{" +
                "        \"dbId\":\"" + dbId + "\"" +
                ",         \"accountId\":\"" + safeJson(accountId) + "\"" +
                ",         \"accountType\":\"" + safeJson(accountType) + "\"" +
                ",         \"categoryId\":\"" + safeJson(categoryId) + "\"" +
                ",         \"title\":\"" + safeJson(title) + "\"" +
                ",         \"alias\":\"" + safeJson(alias) + "\"" +
                ",         \"activeSub\":\"" + activeSub + "\"" +
                ",         \"censored\":\"" + censored + "\"" +
                "}";
    }
}

