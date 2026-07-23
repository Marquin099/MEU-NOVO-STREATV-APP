package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class VlcPlayerManager(
    private val context: Context,
    private val loop: Boolean = false,
    private val muted: Boolean = false
) {
    var libVLC: LibVLC? = null
        private set
    var mediaPlayer: MediaPlayer? = null
        private set

    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var errorMessage by mutableStateOf<String?>(null)
    var isBuffering by mutableStateOf(false)
    var isVideoReady by mutableStateOf(false)

    private var lastPlayedUrl: String? = null
    private var lastHeaders: Map<String, String>? = null
    private var currentMedia: Media? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                currentPosition = player.time
                val d = player.length
                if (d > 0) {
                    duration = d
                }
                if (player.isPlaying) {
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }
    }

    init {
        try {
            // Highly optimized global options for IPTV, low-latency, and max compatibility
            val globalOptions = ArrayList<String>().apply {
                add("--http-reconnect")       // Auto-reconnect if connection drops
                add("--network-caching=1000") // 1 second network caching
                add("--live-caching=1000")
                add("--file-caching=1000")
                if (loop) {
                    add("--loop")
                }
            }

            val vlc = LibVLC(context, globalOptions)
            libVLC = vlc

            val player = MediaPlayer(vlc)
            mediaPlayer = player

            if (muted) {
                player.volume = 0
            } else {
                player.volume = 100
            }

            player.setEventListener { event ->
                mainHandler.post {
                    val p = mediaPlayer ?: return@post
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            isPlaying = true
                            isBuffering = false
                            isVideoReady = true
                            errorMessage = null
                            startProgressPolling()
                            Log.d("VlcPlayerManager", "VLC State CHANGED: Playing URL: $lastPlayedUrl")
                        }
                        MediaPlayer.Event.Paused -> {
                            isPlaying = false
                            stopProgressPolling()
                            Log.d("VlcPlayerManager", "VLC State CHANGED: Paused")
                        }
                        MediaPlayer.Event.Stopped -> {
                            isPlaying = false
                            stopProgressPolling()
                            Log.d("VlcPlayerManager", "VLC State CHANGED: Stopped")
                        }
                        MediaPlayer.Event.Buffering -> {
                            val buffPercent = event.buffering
                            isBuffering = buffPercent < 100f
                            Log.d("VlcPlayerManager", "VLC State CHANGED: Buffering ($buffPercent%)")
                        }
                        MediaPlayer.Event.EndReached -> {
                            isPlaying = false
                            isBuffering = false
                            stopProgressPolling()
                            Log.d("VlcPlayerManager", "VLC State CHANGED: EndReached")
                            if (loop) {
                                mainHandler.post {
                                    playUrl(lastPlayedUrl ?: "")
                                }
                            }
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            isPlaying = false
                            isBuffering = false
                            stopProgressPolling()
                            
                            val detail = "Erro de reprodução no VLC. Isso pode ser causado por link expirado, formato de vídeo incompatível ou restrição de rede."
                            errorMessage = "Erro de Reprodução (VLC EncounteredError)"
                            
                            Log.e(
                                "VlcPlayerManager",
                                """
                                ==================================================
                                LIBVLC PLAYBACK ERROR DETECTED
                                ==================================================
                                Played URL: $lastPlayedUrl
                                State: EncounteredError
                                Description: $detail
                                ==================================================
                                """.trimIndent()
                            )
                        }
                        MediaPlayer.Event.TimeChanged -> {
                            currentPosition = p.time
                        }
                        MediaPlayer.Event.LengthChanged -> {
                            val len = p.length
                            if (len > 0) {
                                duration = len
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errorMessage = "Falha ao inicializar o VLC: ${e.localizedMessage}"
            Log.e("VlcPlayerManager", "VLC Initialization error", e)
        }
    }

    fun playUrl(url: String, headers: Map<String, String>? = null) {
        val player = mediaPlayer ?: return
        try {
            var finalUrl = url
            try {
                val uri = Uri.parse(url)
                val pathSegments = uri.pathSegments
                if (pathSegments.size >= 4) {
                    val type = pathSegments[0].lowercase()
                    if (type == "movie" || type == "movies") {
                        val lastSegment = pathSegments.last()
                        if (lastSegment.endsWith(".ts", ignoreCase = true)) {
                            val correctedSegment = lastSegment.substring(0, lastSegment.length - 3) + ".mp4"
                            finalUrl = url.replace(lastSegment, correctedSegment)
                            Log.d("VlcPlayerManager", "Corrected movie URL from .ts to .mp4: $finalUrl")
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e("VlcPlayerManager", "Error correcting URL extension", ex)
            }

            lastPlayedUrl = finalUrl
            lastHeaders = headers
            errorMessage = null
            isBuffering = true
            isVideoReady = false

            val media = Media(libVLC ?: return, Uri.parse(finalUrl))
            
            // Standard performance and network caching options
            media.addOption(":network-caching=1000")
            media.addOption(":live-caching=1000")
            media.addOption(":file-caching=1000")
            
            // Inject user agent only (highly standard desktop player approach)
            val userAgent = headers?.get("User-Agent") ?: "VLC/3.0.18 LibVLC/3.0.18"
            media.addOption(":http-user-agent=$userAgent")

            Log.d("VlcPlayerManager", "Directly playing MRL: $url (User-Agent: $userAgent)")

            currentMedia?.release()
            currentMedia = media

            player.media = media
            player.play()
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Erro ao carregar mídia."
            isBuffering = false
            Log.e("VlcPlayerManager", "playUrl error", e)
        }
    }

    fun play() {
        mediaPlayer?.play()
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun stop() {
        mediaPlayer?.stop()
    }

    fun setMute(mute: Boolean) {
        mediaPlayer?.volume = if (mute) 0 else 100
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            player.time = positionMs
            currentPosition = positionMs
        }
    }

    fun attachVideoLayout(videoLayout: VLCVideoLayout) {
        mediaPlayer?.let { player ->
            player.detachViews()
            player.attachViews(videoLayout, null, true, false)
        }
    }

    fun detachVideoLayout() {
        mediaPlayer?.detachViews()
    }

    private fun startProgressPolling() {
        mainHandler.removeCallbacks(progressRunnable)
        mainHandler.post(progressRunnable)
    }

    private fun stopProgressPolling() {
        mainHandler.removeCallbacks(progressRunnable)
    }

    fun release() {
        stopProgressPolling()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            currentMedia?.release()
            currentMedia = null
        } catch (e: Exception) {
            Log.e("VlcPlayerManager", "Error releasing currentMedia", e)
        }

        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.detachViews()
                player.release()
            }
        } catch (e: Exception) {
            Log.e("VlcPlayerManager", "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
        
        try {
            libVLC?.release()
        } catch (e: Exception) {
            Log.e("VlcPlayerManager", "Error releasing LibVLC", e)
        } finally {
            libVLC = null
        }
    }
}
