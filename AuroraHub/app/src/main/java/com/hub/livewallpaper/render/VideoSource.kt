package com.hub.livewallpaper.render

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Surface

class VideoSource(private val context: Context) {

    interface Callback {
        fun onVideoReady(width: Int, height: Int)
        fun onVideoFatal(message: String)
    }

    private var player: MediaPlayer? = null
    private var decodeSurface: Surface? = null
    private var afd: AssetFileDescriptor? = null
    private var retriever: MediaMetadataRetriever? = null

    private var prepared = false
    private var wantPlaying = true
    private var appliedSpeed = -1f
    private var appliedMute: Boolean? = null

    var displayWidth = 0; private set
    var displayHeight = 0; private set

    val isPrepared: Boolean get() = prepared

    fun open(assetPath: String, st: SurfaceTexture, mute: Boolean, speed: Float, cb: Callback) {
        release()
        try {
            // Verifica se é um arquivo do celular (URI ou caminho absoluto) ou de dentro do APK
            val isUri = assetPath.startsWith("content://") || assetPath.startsWith("file://") || assetPath.startsWith("/")

            var rotation = 0
            var rawW = 0
            var rawH = 0
            try {
                val mmr = MediaMetadataRetriever()
                if (isUri) {
                    mmr.setDataSource(context, Uri.parse(assetPath))
                } else {
                    val descriptor = context.assets.openFd(assetPath)
                    afd = descriptor
                    mmr.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                }
                rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                rawW = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                rawH = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                retriever = mmr
            } catch (t: Throwable) {
                Log.w(TAG, "Metadados indisponíveis para $assetPath", t)
            }

            val surface = Surface(st)
            decodeSurface = surface

            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            
            // Configura a fonte de dados corretamente
            if (isUri) {
                mp.setDataSource(context, Uri.parse(assetPath))
            } else {
                val descriptor = afd ?: context.assets.openFd(assetPath).also { afd = it }
                mp.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            
            mp.setSurface(surface)
            mp.isLooping = true
            mp.setVolume(if (mute) 0f else 0.5f, if (mute) 0f else 0.5f)
            appliedMute = mute

            runCatching { mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) }

            mp.setOnPreparedListener { m ->
                prepared = true
                val w = if (m.videoWidth > 0) m.videoWidth else rawW
                val h = if (m.videoHeight > 0) m.videoHeight else rawH
                val swap = rotation == 90 || rotation == 270
                displayWidth = if (swap) h else w
                displayHeight = if (swap) w else h
                st.setDefaultBufferSize(maxOf(1, w), maxOf(1, h))
                cb.onVideoReady(displayWidth, displayHeight)
                if (wantPlaying) m.start()
                applySpeed(speed)
            }

            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer erro what=$what extra=$extra")
                cb.onVideoFatal("MediaPlayer what=$what extra=$extra")
                true
            }

            mp.prepareAsync()
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao abrir $assetPath", t)
            cb.onVideoFatal(t.message ?: t.javaClass.simpleName)
        }
    }

    fun applySpeed(speed: Float) {
        val mp = player ?: return
        if (!prepared) return
        val s = speed.coerceIn(0.15f, 2.5f)
        if (kotlin.math.abs(s - appliedSpeed) < 0.01f) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val params = PlaybackParams().setSpeed(s).setPitch(1f)
                mp.playbackParams = params
                appliedSpeed = s
            }
        } catch (t: Throwable) {
            appliedSpeed = 1f
        }
    }

    fun applyMute(mute: Boolean) {
        val mp = player ?: return
        if (appliedMute == mute) return
        appliedMute = mute
        runCatching { mp.setVolume(if (mute) 0f else 0.5f, if (mute) 0f else 0.5f) }
    }

    fun setPlaying(playing: Boolean) {
        wantPlaying = playing
        val mp = player ?: return
        if (!prepared) return
        runCatching {
            if (playing) { if (!mp.isPlaying) mp.start() } else { if (mp.isPlaying) mp.pause() }
        }
    }

    fun release() {
        prepared = false
        appliedSpeed = -1f
        appliedMute = null
        player?.let { mp ->
            runCatching { mp.setOnPreparedListener(null) }
            runCatching { mp.setOnErrorListener(null) }
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.setSurface(null) }
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
        player = null
        decodeSurface?.let { runCatching { it.release() } }
        decodeSurface = null
        retriever?.let { runCatching { it.release() } }
        retriever = null
        afd?.let { runCatching { it.close() } }
        afd = null
    }

    private companion object { const val TAG = "VideoSource" }
}
