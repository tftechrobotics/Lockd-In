package app.lockdin.probetarget

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Harmless app for testing the Lockd In probe. It holds no user data. It logs,
 * from its own point of view, how long it stayed usable ("exposure") before
 * something covered it or sent it to the background, and what happened to its
 * sound. Optionally plays a quiet tone and can enter picture-in-picture.
 */
class TargetActivity : Activity() {
    private var usableSince: Long? = null
    private var resumed = false
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private lateinit var respectFocus: CheckBox
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        col.addView(TextView(this).apply {
            text = "Lockd In Probe Target\n\nA harmless test app. It holds no data and can be closed or uninstalled any time."
            textSize = 18f
        })
        col.addView(Button(this).apply {
            text = "Start / stop quiet tone"
            setOnClickListener { if (track == null) startTone() else stopTone("user") }
        })
        respectFocus = CheckBox(this).apply {
            text = "Pause tone when another app takes audio focus (well-behaved player)"
            isChecked = true
        }
        col.addView(respectFocus)
        col.addView(Button(this).apply {
            text = "Enter picture-in-picture"
            setOnClickListener {
                val ok = runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }.getOrDefault(false)
                log("pip_requested", JSONObject().put("ok", ok))
            }
        })
        col.addView(Button(this).apply {
            text = "Share log"
            setOnClickListener {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, logFile().readTextOrEmpty()),
                        "Share target log",
                    )
                )
            }
        })
        col.addView(Button(this).apply { text = "Clear log"; setOnClickListener { logFile().delete(); refreshLog() } })
        logView = TextView(this).apply { textSize = 11f; setTextIsSelectable(true); setPadding(0, 24, 0, 0) }
        col.addView(logView)
        setContentView(ScrollView(this).apply { addView(col) })
        log("created")
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        log("resumed")
        if (hasWindowFocus()) markUsable()
        refreshLog()
    }

    override fun onPause() {
        endUsable("paused")
        resumed = false
        super.onPause()
    }

    override fun onStop() {
        log("stopped", JSONObject().put("tone_playing_in_app", track != null))
        super.onStop()
    }

    override fun onDestroy() {
        stopTone("destroyed")
        log("destroyed")
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && resumed) markUsable() else if (!hasFocus) endUsable("lost_window_focus")
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        log("pip_changed", JSONObject().put("in_pip", isInPip))
    }

    override fun onMultiWindowModeChanged(isInMultiWindow: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindow, newConfig)
        log("multi_window_changed", JSONObject().put("in_multi_window", isInMultiWindow))
    }

    private fun markUsable() {
        if (usableSince == null) {
            usableSince = SystemClock.elapsedRealtime()
            log("usable")
        }
    }

    /** Logs how long this app was usable before it lost focus or was paused. */
    private fun endUsable(why: String) {
        val since = usableSince ?: return
        usableSince = null
        log(why, JSONObject().put("exposure_ms", SystemClock.elapsedRealtime() - since))
    }

    private fun startTone() {
        val am = getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                log("audio_focus_changed", JSONObject().put("change", change).put("respecting", respectFocus.isChecked))
                if (change == AudioManager.AUDIOFOCUS_LOSS && respectFocus.isChecked) stopTone("lost_audio_focus")
            }
            .build()
        val granted = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focusRequest = request

        val rate = 44_100
        val samples = ShortArray(rate) { i -> (sin(2 * PI * 440 * i / rate) * 0.04 * Short.MAX_VALUE).toInt().toShort() }
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(samples, 0, samples.size)
        t.setLoopPoints(0, samples.size, -1)
        t.play()
        track = t
        log("tone_started", JSONObject().put("focus_granted", granted))
    }

    private fun stopTone(why: String) {
        val t = track ?: return
        runCatching { t.stop() }
        t.release()
        track = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        log("tone_stopped", JSONObject().put("why", why))
    }

    private fun logFile() = File(filesDir, "target.jsonl")

    private fun File.readTextOrEmpty() = if (exists()) readText() else ""

    private fun log(event: String, data: JSONObject = JSONObject()) {
        val line = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("elapsed_ms", SystemClock.elapsedRealtime())
            .put("evt", event)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("data", data)
        logFile().appendText(line.toString() + "\n")
    }

    private fun refreshLog() {
        logView.text = logFile().takeIf { it.exists() }?.readLines()?.takeLast(25)?.joinToString("\n") ?: "(empty)"
    }
}
