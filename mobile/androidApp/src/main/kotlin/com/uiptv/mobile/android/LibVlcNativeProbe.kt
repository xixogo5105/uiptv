package com.uiptv.mobile.android

import android.util.Log
import org.videolan.libvlc.MediaPlayer

object LibVlcNativeProbe {
    private const val LogTag = "UIPTV-VlcProbe"

    private val nativeAvailable: Boolean = runCatching {
        System.loadLibrary("uiptv_vlc_probe")
        true
    }.getOrElse {
        Log.w(LogTag, "Native libVLC probe unavailable", it)
        false
    }

    fun attach(player: MediaPlayer, onEvent: (NativeEvent) -> Unit): Long {
        if (!nativeAvailable) {
            return 0L
        }
        return runCatching {
            nativeAttach(
                player.instance,
                object : Listener {
                    override fun onNativeVlcEvent(
                        eventType: Int,
                        esType: Int,
                        esId: Int,
                        width: Int,
                        height: Int,
                        codec: String?
                    ) {
                        onEvent(
                            NativeEvent(
                                eventType = eventType,
                                esType = esType,
                                esId = esId,
                                width = width,
                                height = height,
                                codec = codec.orEmpty()
                            )
                        )
                    }
                }
            )
        }.getOrElse {
            Log.w(LogTag, "Unable to attach native libVLC probe", it)
            0L
        }
    }

    fun detach(handle: Long) {
        if (handle == 0L || !nativeAvailable) {
            return
        }
        runCatching { nativeDetach(handle) }
            .onFailure { Log.w(LogTag, "Unable to detach native libVLC probe", it) }
    }

    fun setMute(player: MediaPlayer, muted: Boolean): Boolean {
        if (!nativeAvailable) {
            return false
        }
        return runCatching { nativeSetMute(player.instance, muted) }
            .getOrElse {
                Log.w(LogTag, "Unable to set native libVLC mute state", it)
                false
            }
    }

    data class NativeEvent(
        val eventType: Int,
        val esType: Int,
        val esId: Int,
        val width: Int,
        val height: Int,
        val codec: String
    )

    interface Listener {
        fun onNativeVlcEvent(
            eventType: Int,
            esType: Int,
            esId: Int,
            width: Int,
            height: Int,
            codec: String?
        )
    }

    private external fun nativeAttach(playerInstance: Long, listener: Listener): Long

    private external fun nativeDetach(handle: Long)

    private external fun nativeSetMute(playerInstance: Long, muted: Boolean): Boolean
}
