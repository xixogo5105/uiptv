package com.uiptv.service

fun interface ConfigurationChangeListener {
    fun onConfigurationChanged(revision: Long)
}
