package com.uiptv.api

fun interface Callback<P> {
    fun call(param: P)
}
