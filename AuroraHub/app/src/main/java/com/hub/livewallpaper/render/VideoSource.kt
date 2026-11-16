package com.hub.livewallpaper.render

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import android.view.Surface

/**
 * =====================================================================
 *  Fonte de vídeo: MediaPlayer + SurfaceTexture, em loop, sem áudio.
 * =====================================================================
 *
 *  Por que SurfaceTexture e não holder.surface direto?
 *
 *  Entregar o Surface do holder ao MediaPlayer é o caminho "fácil", mas o
 *  enquadramento fica nas mãos do compositor: em vários aparelos ele faz
 *  CENTER_INSIDE e aparecem as bordas pretas. Além disso,
 *  setVideoScalingMode(SCALE_TO_FIT_WITH_CROPPING) só é honrado quando o
 *  destino é um SurfaceView de verdade — o que NÃO é o caso de um Engine
 *  de WallpaperService em todos os OEMs (a One UI 4.1 inclusa).
 *
 *  Com SurfaceTexture nós recebemos o frame como textura e fazemos o
 *  center-crop no fragment shader, com a matriz do próprio SurfaceTexture
 *  (que já corrige flip e rotação do decoder). O resultado é determinístico
 *  em qualquer aparelho: 100% da tela coberta, zero bordas.
 *
 *  Todo o ciclo de vida roda na RenderThread (que tem Looper), então os
 *  callbacks do MediaPlayer chegam na mesma thread que faz o GL.
 */
class VideoSource(private val context: Context) {

    interface Callback {
        /** Vídeo preparado: tamanho já corrigido pela rotação dos metadados. */
        fun onVideoReady(width: Int, height: Int)
        /** Falha irrecuperável: o chamador deve cair no modo procedural. */
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

    /**
     * Abre um MP4 de assets/ e começa a decodificar para [st].
     * @param assetPath ex.: "wallpapers/oxygen_ribbon.mp4"
     */
    fun open(assetPath: String, st: SurfaceTexture, mute: Boolean, speed: Float, cb: Callback) {
        release()
        try {
            val descriptor = context.assets.openFd(assetPath)
            afd = descriptor

            // Rotação: MediaPlayer.videoWidth devolve o tamanho CODIFICADO.
            // Se o arquivo tem rotate=90 nos metadados, o quadro exibido é
            // transposto — e a matemática do crop precisa saber disso.
            var rotation = 0
            var rawW = 0
            var rawH = 0
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
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
            // offset + length OBRIGATÓRIOS: dentro do APK o asset pode estar
            // deslocado no arquivo resources.arsc/assets concatenados.
            mp.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            mp.setSurface(surface)
            mp.isLooping = true
            mp.setVolume(if (mute) 0f else 0.5f, if (mute) 0f else 0.5f)
            appliedMute = mute

            // Mantemos o modo de escala do framework coerente com o nosso
            // shader (útil se o caminho direto ao Surface for usado).
            runCatching { mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) }

            mp.setOnPreparedListener { m ->
                prepared = true
                val w = if (m.videoWidth > 0) m.videoWidth else rawW
                val h = if (m.videoHeight > 0) m.videoHeight else rawH
                val swap = rotation == 90 || rotation == 270
                displayWidth = if (swap) h else w
                displayHeight = if (swap) w else h
                // O buffer do decoder continua no tamanho CODIFICADO;
                // quem transpõe é a matriz do SurfaceTexture.
                st.setDefaultBufferSize(maxOf(1, w), maxOf(1, h))
                cb.onVideoReady(displayWidth, displayHeight)
                if (wantPlaying) m.start()
                applySpeed(speed)
                Log.i(TAG, "pronto: ${displayWidth}x${displayHeight} rotate=$rotation loop=${m.isLooping}")
            }

            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) Log.d(TAG, "primeiro frame apresentado")
                false
            }

            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer erro what=$what extra=$extra")
                cb.onVideoFatal("MediaPlayer what=$what extra=$extra")
                true // tratado: não chamar OnCompletion nem travar o processo
            }

            mp.prepareAsync()
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao abrir $assetPath", t)
            cb.onVideoFatal(t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Velocidade real de reprodução via PlaybackParams (API 23+).
     * Alguns codecs da Samsung rejeitam speed != 1 com trilha de áudio;
     * como o wallpaper é mudo por padrão, isso raramente acontece — e se
     * acontecer, seguimos em 1x em vez de derrubar o wallpaper.
     */
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
            Log.w(TAG, "PlaybackParams recusou speed=$s; mantendo 1x", t)
            appliedSpeed = 1f
        }
    }

    fun applyMute(mute: Boolean) {
        val mp = player ?: return
        if (appliedMute == mute) return
        appliedMute = mute
        runCatching { mp.setVolume(if (mute) 0f else 0.5f, if (mute) 0f else 0.5f) }
    }

    /** Pausa/retoma sem destruir o decoder (tela apagada, economia de bateria). */
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
            runCatching { mp.setOnInfoListener(null) }
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
