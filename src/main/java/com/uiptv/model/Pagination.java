package com.uiptv.model;

import java.util.Objects;

public class Pagination extends BaseJson {
    int paginationLimit, maxPageItems, pageCount;

    public Pagination(int  maxPageItems, int paginationLimit) {
        this.paginationLimit = paginationLimit;
        this.maxPageItems = maxPageItems;
        this.pageCount = (int) (Math.ceil((double)maxPageItems / (double)paginationLimit));
    }

    public int getPaginationLimit() {
        return paginationLimit;
    }

    public void setPaginationLimit(int paginationLimit) {
        this.paginationLimit = paginationLimit;
    }

    public int getMaxPageItems() {
        return maxPageItems;
    }

    public void setMaxPageItems(int maxPageItems) {
        this.maxPageItems = maxPageItems;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pagination that = (Pagination) o;
        return paginationLimit == that.paginationLimit && maxPageItems == that.maxPageItems && pageCount == that.pageCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(paginationLimit, maxPageItems, pageCount);
    }

    @Override
    public String toString() {
        return "Pagination{" +
                "paginationLimit=" + paginationLimit +
                ", maxPageItems=" + maxPageItems +
                ", pageCount=" + pageCount +
                '}';
    }
}
