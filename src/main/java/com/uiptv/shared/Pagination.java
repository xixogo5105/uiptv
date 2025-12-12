package com.uiptv.shared;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Pagination extends BaseJson {
    int paginationLimit, maxPageItems, pageCount;

    public Pagination(int maxPageItems, int paginationLimit) {
        this.paginationLimit = paginationLimit;
        this.maxPageItems = maxPageItems;
        this.pageCount = (int) (Math.ceil((double) maxPageItems / (double) paginationLimit));
    }
}
