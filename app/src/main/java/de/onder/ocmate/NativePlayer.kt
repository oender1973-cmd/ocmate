package de.onder.ocmate

import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Echter Android-Player (ExoPlayer) für Live-TV, Filme und Serien.
 * Das Bild liegt in einer eigenen Fläche HINTER der durchsichtigen Web-Oberfläche; die Oberfläche
 * meldet, wo das Videofenster gerade ist. Nutzt die Hardware-Decoder des Geräts (4K/HEVC, Dolby).
 *
 * Alle öffentlichen Methoden dürfen von jedem Thread aufgerufen werden.
 */
class NativePlayer(
    private val activity: Activity,
    root: FrameLayout,
    private val emit: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val surface = SurfaceView(activity)
    private var player: ExoPlayer? = null
    private var rect = intArrayOf(0, 0, 0, 0)
    private var videoW = 0
    private var videoH = 0
    private var pixelRatio = 1f
    /** Bildformat: "" = Original mit Balken, "zoom" = füllt und schneidet ab, "fill" = strecken */
    private var fit = ""

    /** Position, Dauer und Zustand als JSON; wird zweimal pro Sekunde aufgefrischt. */
    @Volatile var state: String = "{}"
        private set

    @Volatile var active: Boolean = false
        private set

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            val dur = if (p.duration == C.TIME_UNSET) 0L else p.duration
            state = JSONObject()
                .put("pos", p.currentPosition)
                .put("dur", dur)
                .put("playing", p.isPlaying)
                .put("buffering", p.playbackState == Player.STATE_BUFFERING)
                .put("w", videoW)
                .put("h", videoH)
                .toString()
            main.postDelayed(this, 500)
        }
    }

    init {
        surface.visibility = View.GONE
        // Index 0: unter der Web-Oberfläche
        root.addView(surface, 0, FrameLayout.LayoutParams(1, 1))
    }

    fun play(id: Int, url: String, userAgent: String, startMs: Long) {
        main.post {
            release()
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
            // Einstellungen für Live-MPEG-TS, wie sie IPTV-Anbieter senden
            val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            val sources = DefaultMediaSourceFactory(http, extractors)
            val p = ExoPlayer.Builder(activity).setMediaSourceFactory(sources).build()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            p.setVideoSurfaceView(surface)
            p.addListener(listener(id))

            val item = MediaItem.Builder().setUri(Uri.parse(url))
            if (Regex("\\.m3u8(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)) {
                item.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            p.setMediaItem(item.build(), if (startMs > 0) startMs else C.TIME_UNSET)
            p.playWhenReady = true
            p.prepare()

            player = p
            active = true
            videoW = 0
            videoH = 0
            applyRect()
            main.removeCallbacks(ticker)
            main.post(ticker)
        }
    }

    fun stop() {
        main.post { release() }
    }

    fun setRect(x: Int, y: Int, w: Int, h: Int) {
        main.post {
            rect = intArrayOf(x, y, w, h)
            applyRect()
        }
    }

    fun pause(pause: Boolean) {
        main.post { player?.playWhenReady = !pause }
    }

    fun seek(ms: Long) {
        main.post { player?.seekTo(ms) }
    }

    fun setFit(mode: String) {
        main.post {
            fit = mode
            applyRect()
        }
    }

    fun setSpeed(speed: Float) {
        main.post { player?.setPlaybackSpeed(speed.coerceIn(0.5f, 2f)) }
    }

    /** Tonspur oder Untertitel wählen; groupIndex < 0 schaltet die Art ab (Untertitel aus). */
    fun selectTrack(kind: String, groupIndex: Int, trackIndex: Int) {
        main.post {
            val p = player ?: return@post
            val type = if (kind == "audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
            val builder = p.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
            if (groupIndex < 0) {
                builder.setTrackTypeDisabled(type, true)
            } else {
                val groups = p.currentTracks.groups
                if (groupIndex >= groups.size) return@post
                builder.setTrackTypeDisabled(type, false)
                builder.addOverride(TrackSelectionOverride(groups[groupIndex].mediaTrackGroup, trackIndex))
            }
            p.trackSelectionParameters = builder.build()
        }
    }

    private fun release() {
        main.removeCallbacks(ticker)
        player?.release()
        player = null
        active = false
        state = "{}"
        surface.visibility = View.GONE
    }

    /** Legt das Bild seitenrichtig in das gemeldete Fenster. Fenster ohne Größe = Bild ausblenden, Ton läuft weiter. */
    private fun applyRect() {
        val x = rect[0]
        val y = rect[1]
        val w = rect[2]
        val h = rect[3]
        if (player == null || w <= 0 || h <= 0) {
            surface.visibility = View.GONE
            return
        }
        var vw = w
        var vh = h
        if (videoW > 0 && videoH > 0 && fit != "fill") {
            val ratio = videoW * pixelRatio / videoH
            val windowWider = w.toFloat() / h > ratio
            // Zoom: die kürzere Seite füllt das Fenster, der Überstand liegt außerhalb
            if (windowWider != (fit == "zoom")) vw = (h * ratio).toInt() else vh = (w / ratio).toInt()
        }
        val lp = FrameLayout.LayoutParams(maxOf(vw, 1), maxOf(vh, 1))
        lp.leftMargin = x + (w - vw) / 2
        lp.topMargin = y + (h - vh) / 2
        surface.layoutParams = lp
        surface.visibility = View.VISIBLE
    }

    private fun send(id: Int, event: String, data: String) {
        emit("window.ocPlayer&&ocPlayer($id,${JSONObject.quote(event)},${JSONObject.quote(data)})")
    }

    private fun listener(id: Int) = object : Player.Listener {
        private var announced = false

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !announced) {
                announced = true
                send(id, "ready", "")
            }
            if (playbackState == Player.STATE_ENDED) send(id, "ended", "")
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player
            // Live-Stream hinter das Sendefenster gefallen: einfach wieder an die Live-Kante springen
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && p != null) {
                p.seekToDefaultPosition()
                p.prepare()
                return
            }
            var msg = error.errorCodeName
            val cause = error.cause
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                msg += " · HTTP ${cause.responseCode}"
            } else {
                val text = cause?.message
                if (text != null) msg += " · " + text.take(100)
            }
            send(id, "error", msg)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoW = videoSize.width
            videoH = videoSize.height
            pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
            applyRect()
        }

        override fun onTracksChanged(tracks: Tracks) {
            send(id, "tracks", tracksJson(tracks))
        }

        override fun onCues(cueGroup: CueGroup) {
            val text = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
            send(id, "cue", text)
        }
    }

    private fun tracksJson(tracks: Tracks): String {
        val audio = JSONArray()
        val text = JSONArray()
        val groups = tracks.groups
        for (gi in 0 until groups.size) {
            val group = groups[gi]
            if (group.type != C.TRACK_TYPE_AUDIO && group.type != C.TRACK_TYPE_TEXT) continue
            for (ti in 0 until group.length) {
                if (!group.isTrackSupported(ti)) continue
                val format = group.getTrackFormat(ti)
                val label = listOfNotNull(
                    format.label,
                    format.language,
                    format.sampleMimeType?.substringAfter('/')
                ).joinToString(" · ")
                val entry = JSONObject()
                    .put("g", gi)
                    .put("t", ti)
                    .put("label", label)
                    .put("sel", group.isTrackSelected(ti))
                if (group.type == C.TRACK_TYPE_AUDIO) audio.put(entry) else text.put(entry)
            }
        }
        return JSONObject().put("audio", audio).put("text", text).toString()
    }
}
