package com.uiptv.shared

class Pagination() : BaseJson() {
    var paginationLimit: Int = 0
    var maxPageItems: Int = 0
    var pageCount: Int = 0

    constructor(maxPageItems: Int, paginationLimit: Int) : this() {
        this.paginationLimit = paginationLimit
        this.maxPageItems = maxPageItems
        this.pageCount = kotlin.math.ceil(maxPageItems.toDouble() / paginationLimit.toDouble()).toInt()
    }
}
