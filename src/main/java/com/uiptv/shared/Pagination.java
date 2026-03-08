package com.uiptv.shared;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Pagination extends BaseJson {
    int paginationLimit, maxPageItems, pageCount;

    public Pagination(int maxPageItems, int paginationLimit) {
        this.paginationLimit = paginationLimit;
        this.maxPageItems = maxPageItems;
        this.pageCount = (int) (Math.ceil((double) maxPageItems / (double) paginationLimit));
    }
}
