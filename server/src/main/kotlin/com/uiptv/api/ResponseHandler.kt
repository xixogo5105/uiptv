package com.uiptv.api

fun interface ResponseHandler {
    fun onResponse(responseBody: String)
}
