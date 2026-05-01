package com.uiptv.service;

@FunctionalInterface
public interface ConfigurationChangeListener {
    void onConfigurationChanged(long revision);
}
