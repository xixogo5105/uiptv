#pragma once

#include <jni.h>

// JNI diagnostics must never bypass the app's explicit debug logging gate.
// MPV log events still reach Java observers for decoder recovery and opted-in logs.
#define ALOGE(...) ((void)0)
#define ALOGV(...) ((void)0)

void die(JNIEnv *env, const char *msg);
void throw_java_exception(JNIEnv *env, const char *msg);
