package com.uiptv.api;

public interface Callback<P> {
    void call(P param);
}