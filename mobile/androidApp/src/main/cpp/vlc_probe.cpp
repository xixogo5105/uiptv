#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#define LOG_TAG "UIPTV-VlcProbe"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

using libvlc_event_type_t = int;

struct libvlc_event_manager_t;
struct libvlc_media_player_t;
struct libvlc_media_t;

enum libvlc_track_type_t {
    libvlc_track_unknown = -1,
    libvlc_track_audio = 0,
    libvlc_track_video = 1,
    libvlc_track_text = 2,
};

struct libvlc_video_viewpoint_t {
    float f_yaw;
    float f_pitch;
    float f_roll;
    float f_field_of_view;
};

struct libvlc_video_track_t {
    unsigned i_height;
    unsigned i_width;
    unsigned i_sar_num;
    unsigned i_sar_den;
    unsigned i_frame_rate_num;
    unsigned i_frame_rate_den;
    int i_orientation;
    int i_projection;
    libvlc_video_viewpoint_t pose;
};

struct libvlc_audio_track_t {
    unsigned i_channels;
    unsigned i_rate;
};

struct libvlc_subtitle_track_t {
    char *psz_encoding;
};

struct libvlc_media_track_t {
    uint32_t i_codec;
    uint32_t i_original_fourcc;
    int i_id;
    libvlc_track_type_t i_type;
    int i_profile;
    int i_level;
    union {
        libvlc_audio_track_t *audio;
        libvlc_video_track_t *video;
        libvlc_subtitle_track_t *subtitle;
    };
    unsigned int i_bitrate;
    char *psz_language;
    char *psz_description;
};

struct libvlc_event_t {
    int type;
    void *p_obj;
    union {
        struct {
            float new_cache;
        } media_player_buffering;
        struct {
            int64_t new_time;
        } media_player_time_changed;
        struct {
            int new_count;
        } media_player_vout;
        struct {
            libvlc_track_type_t i_type;
            int i_id;
        } media_player_es_changed;
    } u;
};

using libvlc_callback_t = void (*)(const libvlc_event_t *, void *);
using event_manager_fn = libvlc_event_manager_t *(*)(libvlc_media_player_t *);
using event_attach_fn = int (*)(libvlc_event_manager_t *, libvlc_event_type_t, libvlc_callback_t, void *);
using event_detach_fn = void (*)(libvlc_event_manager_t *, libvlc_event_type_t, libvlc_callback_t, void *);
using video_get_size_fn = int (*)(libvlc_media_player_t *, unsigned, unsigned *, unsigned *);
using video_get_track_fn = int (*)(libvlc_media_player_t *);
using media_player_get_media_fn = libvlc_media_t *(*)(libvlc_media_player_t *);
using media_retain_fn = void (*)(libvlc_media_t *);
using media_release_fn = void (*)(libvlc_media_t *);
using media_tracks_get_fn = unsigned (*)(libvlc_media_t *, libvlc_media_track_t ***);
using media_tracks_release_fn = void (*)(libvlc_media_track_t **, unsigned);
using media_get_codec_description_fn = const char *(*)(libvlc_track_type_t, uint32_t);
using audio_set_mute_fn = void (*)(libvlc_media_player_t *, int);

struct LibVlcSymbols {
    void *handle = nullptr;
    event_manager_fn event_manager = nullptr;
    event_attach_fn event_attach = nullptr;
    event_detach_fn event_detach = nullptr;
    video_get_size_fn video_get_size = nullptr;
    video_get_track_fn video_get_track = nullptr;
    media_player_get_media_fn media_player_get_media = nullptr;
    media_retain_fn media_retain = nullptr;
    media_release_fn media_release = nullptr;
    media_tracks_get_fn media_tracks_get = nullptr;
    media_tracks_release_fn media_tracks_release = nullptr;
    media_get_codec_description_fn media_get_codec_description = nullptr;
    audio_set_mute_fn audio_set_mute = nullptr;
};

struct VideoSnapshot {
    int width = 0;
    int height = 0;
    std::string codec;
};

struct ProbeHandle {
    libvlc_media_player_t *player = nullptr;
    libvlc_event_manager_t *event_manager = nullptr;
    jobject listener = nullptr;
    jmethodID on_event = nullptr;
    std::vector<int> event_types;
};

JavaVM *g_vm = nullptr;
LibVlcSymbols g_symbols;

constexpr int kMediaPlayerPlaying = 260;
constexpr int kMediaPlayerVout = 274;
constexpr int kMediaPlayerESAdded = 276;
constexpr int kMediaPlayerESDeleted = 277;
constexpr int kMediaPlayerESSelected = 278;

template <typename T>
T loadSymbol(const char *name) {
    auto symbol = reinterpret_cast<T>(dlsym(g_symbols.handle, name));
    if (!symbol) {
        LOGW("Missing libVLC symbol %s", name);
    }
    return symbol;
}

bool ensureLibVlcSymbols() {
    if (g_symbols.handle) {
        return true;
    }
    g_symbols.handle = dlopen("libvlc.so", RTLD_LAZY);
    if (!g_symbols.handle) {
        LOGW("Unable to open libvlc.so: %s", dlerror());
        return false;
    }
    g_symbols.event_manager = loadSymbol<event_manager_fn>("libvlc_media_player_event_manager");
    g_symbols.event_attach = loadSymbol<event_attach_fn>("libvlc_event_attach");
    g_symbols.event_detach = loadSymbol<event_detach_fn>("libvlc_event_detach");
    g_symbols.video_get_size = loadSymbol<video_get_size_fn>("libvlc_video_get_size");
    g_symbols.video_get_track = loadSymbol<video_get_track_fn>("libvlc_video_get_track");
    g_symbols.media_player_get_media = loadSymbol<media_player_get_media_fn>("libvlc_media_player_get_media");
    g_symbols.media_retain = loadSymbol<media_retain_fn>("libvlc_media_retain");
    g_symbols.media_release = loadSymbol<media_release_fn>("libvlc_media_release");
    g_symbols.media_tracks_get = loadSymbol<media_tracks_get_fn>("libvlc_media_tracks_get");
    g_symbols.media_tracks_release = loadSymbol<media_tracks_release_fn>("libvlc_media_tracks_release");
    g_symbols.media_get_codec_description =
        loadSymbol<media_get_codec_description_fn>("libvlc_media_get_codec_description");
    g_symbols.audio_set_mute = loadSymbol<audio_set_mute_fn>("libvlc_audio_set_mute");
    return g_symbols.event_manager &&
        g_symbols.event_attach &&
        g_symbols.event_detach &&
        g_symbols.video_get_size &&
        g_symbols.video_get_track &&
        g_symbols.media_player_get_media &&
        g_symbols.media_tracks_get &&
        g_symbols.media_tracks_release;
}

std::string fourccToString(uint32_t fourcc) {
    char value[5] = {
        static_cast<char>(fourcc & 0xff),
        static_cast<char>((fourcc >> 8) & 0xff),
        static_cast<char>((fourcc >> 16) & 0xff),
        static_cast<char>((fourcc >> 24) & 0xff),
        '\0',
    };
    for (int i = 0; i < 4; ++i) {
        if (value[i] < 32 || value[i] > 126) {
            return "";
        }
    }
    return std::string(value);
}

std::string codecLabel(const libvlc_media_track_t *track) {
    if (!track) {
        return "";
    }
    if (g_symbols.media_get_codec_description) {
        const char *description = g_symbols.media_get_codec_description(track->i_type, track->i_codec);
        if (description && description[0] != '\0') {
            return std::string(description);
        }
    }
    return fourccToString(track->i_codec);
}

VideoSnapshot collectVideoSnapshot(libvlc_media_player_t *player, int eventEsType, int eventEsId) {
    VideoSnapshot snapshot;
    unsigned video_width = 0;
    unsigned video_height = 0;
    if (g_symbols.video_get_size(player, 0, &video_width, &video_height) == 0) {
        snapshot.width = static_cast<int>(video_width);
        snapshot.height = static_cast<int>(video_height);
    }

    libvlc_media_t *media = g_symbols.media_player_get_media(player);
    if (!media) {
        return snapshot;
    }
    if (g_symbols.media_retain) {
        g_symbols.media_retain(media);
    }

    libvlc_media_track_t **tracks = nullptr;
    const unsigned track_count = g_symbols.media_tracks_get(media, &tracks);
    const int selected_track_id = g_symbols.video_get_track(player);
    const libvlc_media_track_t *fallback_track = nullptr;
    const libvlc_media_track_t *best_track = nullptr;

    for (unsigned i = 0; i < track_count; ++i) {
        const libvlc_media_track_t *track = tracks[i];
        if (!track || track->i_type != libvlc_track_video || !track->video) {
            continue;
        }
        if (eventEsType == libvlc_track_video && eventEsId >= 0 && track->i_id == eventEsId) {
            best_track = track;
            break;
        }
        if (selected_track_id >= 0 && track->i_id == selected_track_id) {
            best_track = track;
            break;
        }
        const int area = static_cast<int>(track->video->i_width * track->video->i_height);
        const int fallback_area = fallback_track && fallback_track->video
            ? static_cast<int>(fallback_track->video->i_width * fallback_track->video->i_height)
            : -1;
        if (!fallback_track || area > fallback_area) {
            fallback_track = track;
        }
    }

    const libvlc_media_track_t *track = best_track ? best_track : fallback_track;
    if (track && track->video) {
        if (track->video->i_width > 0 && track->video->i_height > 0) {
            snapshot.width = static_cast<int>(track->video->i_width);
            snapshot.height = static_cast<int>(track->video->i_height);
        }
        snapshot.codec = codecLabel(track);
    }

    if (tracks) {
        g_symbols.media_tracks_release(tracks, track_count);
    }
    if (g_symbols.media_release) {
        g_symbols.media_release(media);
    }
    return snapshot;
}

void dispatchProbeEvent(const libvlc_event_t *event, void *data) {
    auto *probe = static_cast<ProbeHandle *>(data);
    if (!probe || !probe->listener || !probe->on_event || !g_vm) {
        return;
    }

    int es_type = -1;
    int es_id = -1;
    if (event->type == kMediaPlayerESAdded ||
        event->type == kMediaPlayerESDeleted ||
        event->type == kMediaPlayerESSelected) {
        es_type = static_cast<int>(event->u.media_player_es_changed.i_type);
        es_id = event->u.media_player_es_changed.i_id;
    }

    const VideoSnapshot snapshot = collectVideoSnapshot(probe->player, es_type, es_id);

    JNIEnv *env = nullptr;
    bool attached_thread = false;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return;
        }
        attached_thread = true;
    }

    jstring codec = snapshot.codec.empty() ? nullptr : env->NewStringUTF(snapshot.codec.c_str());
    env->CallVoidMethod(
        probe->listener,
        probe->on_event,
        event->type,
        es_type,
        es_id,
        snapshot.width,
        snapshot.height,
        codec
    );
    if (codec) {
        env->DeleteLocalRef(codec);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (attached_thread) {
        g_vm->DetachCurrentThread();
    }
}

void releaseProbe(JNIEnv *env, ProbeHandle *probe) {
    if (!probe) {
        return;
    }
    if (probe->event_manager && g_symbols.event_detach) {
        for (const int event_type : probe->event_types) {
            g_symbols.event_detach(probe->event_manager, event_type, dispatchProbeEvent, probe);
        }
    }
    if (probe->listener) {
        env->DeleteGlobalRef(probe->listener);
    }
    delete probe;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_uiptv_mobile_android_LibVlcNativeProbe_nativeAttach(
    JNIEnv *env,
    jobject,
    jlong player_instance,
    jobject listener
) {
    if (!player_instance || !listener || !ensureLibVlcSymbols()) {
        return 0L;
    }
    auto *player = reinterpret_cast<libvlc_media_player_t *>(player_instance);
    libvlc_event_manager_t *event_manager = g_symbols.event_manager(player);
    if (!event_manager) {
        LOGW("libVLC event manager unavailable");
        return 0L;
    }
    auto *probe = new ProbeHandle();
    probe->player = player;
    probe->event_manager = event_manager;
    probe->listener = env->NewGlobalRef(listener);
    jclass listener_class = env->GetObjectClass(listener);
    probe->on_event = env->GetMethodID(
        listener_class,
        "onNativeVlcEvent",
        "(IIIIILjava/lang/String;)V"
    );
    env->DeleteLocalRef(listener_class);

    if (!probe->listener || !probe->on_event) {
        releaseProbe(env, probe);
        return 0L;
    }

    const int event_types[] = {
        kMediaPlayerPlaying,
        kMediaPlayerVout,
        kMediaPlayerESAdded,
        kMediaPlayerESDeleted,
        kMediaPlayerESSelected,
    };
    for (const int event_type : event_types) {
        const int result = g_symbols.event_attach(event_manager, event_type, dispatchProbeEvent, probe);
        if (result == 0) {
            probe->event_types.push_back(event_type);
        } else {
            LOGW("libVLC event attach failed event=%d result=%d", event_type, result);
        }
    }

    if (probe->event_types.empty()) {
        releaseProbe(env, probe);
        return 0L;
    }
    LOGD("attached native libVLC probe events=%zu", probe->event_types.size());
    return reinterpret_cast<jlong>(probe);
}

extern "C" JNIEXPORT void JNICALL
Java_com_uiptv_mobile_android_LibVlcNativeProbe_nativeDetach(JNIEnv *env, jobject, jlong handle) {
    releaseProbe(env, reinterpret_cast<ProbeHandle *>(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uiptv_mobile_android_LibVlcNativeProbe_nativeSetMute(
    JNIEnv *,
    jobject,
    jlong player_instance,
    jboolean muted
) {
    if (!player_instance || !ensureLibVlcSymbols() || !g_symbols.audio_set_mute) {
        return JNI_FALSE;
    }
    auto *player = reinterpret_cast<libvlc_media_player_t *>(player_instance);
    g_symbols.audio_set_mute(player, muted == JNI_TRUE ? 1 : 0);
    return JNI_TRUE;
}
