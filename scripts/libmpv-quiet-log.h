#pragma once

#include <jni.h>
#include <mpv/client.h>

// JNI diagnostics must never bypass the app's explicit debug logging gate.
// MPV log events still reach Java observers for decoder recovery and opted-in logs.
#define ALOGE(...) ((void)0)
#define ALOGV(...) ((void)0)

// libmpv-android 0.42.0 writes mpv log events to Android logcat directly from
// event.cpp. Keep its API references available while compiling that write away.
#define LOG_TAG "mpv"
#define __android_log_print(...) ((void)0)
static inline int mpv_level_to_android(int level) {
    (void)level;
    return 0;
}

void die(JNIEnv *env, const char *msg);
void throw_java_exception(JNIEnv *env, const char *msg);
