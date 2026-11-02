package com.eymen.os

import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** The small live window on the home screen: TV tuner first, internet stream when the satellite drops. */
class Preview(ctx: Context) {
    val tvView = TvView(ctx)
    val texture = TextureView(ctx)
    private val exo: ExoPlayer = ExoPlayer.Builder(ctx).build()
    private var token = 0
    private var muted = false
    private var webActive = false
    var status by mutableStateOf("")
    var playingKey: String? = null
        private set

    init {
        tvView.isFocusable = false
        tvView.isFocusableInTouchMode = false
        texture.isFocusable = false
        texture.visibility = View.INVISIBLE
        exo.setVideoTextureView(texture)
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (webActive) status = "Yayın açılamadı"
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && webActive) status = ""
            }
        })
    }

    fun play(ch: Ch, mute: Boolean) {
        val t = ++token
        muted = mute
        playingKey = ch.key
        stopWeb()
        if (ch.isWeb || ch.tvId < 0) {
            try {
                tvView.reset()
            } catch (e: Exception) {
            }
            val url = ch.url
            if (url == null) {
                status = "Bu kanalın yayın adresi yok"
                return
            }
            status = "Yayın açılıyor…"
            startWeb(url)
            return
        }
        status = "Kanal açılıyor…"
        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String?) {
                if (t != token) return
                status = ""
                if (webActive) stopWeb()
                setTvVolume()
            }

            override fun onVideoUnavailable(inputId: String?, reason: Int) {
                if (t != token || webActive) return
                status = when (reason) {
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL -> "Uydu sinyali zayıf"
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY -> "Yalnızca ses yayını"
                    else -> "Kanal açılıyor…"
                }
                val url = ch.url
                if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL && url != null) {
                    try {
                        tvView.setStreamVolume(0f)
                    } catch (e: Exception) {
                    }
                    startWeb(url)
                }
            }
        })
        try {
            tvView.tune(ch.inputId ?: "", TvContract.buildChannelUri(ch.tvId))
            setTvVolume()
        } catch (e: Exception) {
            status = "Kanal açılamadı"
        }
    }

    private fun setTvVolume() {
        try {
            tvView.setStreamVolume(if (muted) 0f else 1f)
        } catch (e: Exception) {
        }
    }

    fun setMuted(mute: Boolean) {
        muted = mute
        if (webActive) exo.volume = if (mute) 0f else 1f else setTvVolume()
    }

    private fun startWeb(url: String) {
        val item = if (url.contains(".m3u8", ignoreCase = true)) {
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        } else {
            MediaItem.fromUri(url)
        }
        exo.setMediaItem(item)
        exo.prepare()
        exo.volume = if (muted) 0f else 1f
        exo.playWhenReady = true
        webActive = true
        texture.visibility = View.VISIBLE
    }

    private fun stopWeb() {
        if (webActive) {
            try {
                exo.stop()
                exo.clearMediaItems()
            } catch (e: Exception) {
            }
        }
        webActive = false
        texture.visibility = View.INVISIBLE
    }

    fun stop() {
        token++
        playingKey = null
        try {
            tvView.reset()
        } catch (e: Exception) {
        }
        stopWeb()
    }

    fun release() {
        stop()
        try {
            exo.release()
        } catch (e: Exception) {
        }
    }
}
