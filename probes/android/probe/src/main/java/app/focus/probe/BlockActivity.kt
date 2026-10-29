package app.focus.probe

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/**
 * Block screen used by the BLOCK_SCREEN cover method. Bringing it to the front
 * moves the restricted app to the background (it is stopped from the UI's
 * point of view, not closed). Optionally asks other apps to pause playback by
 * taking audio focus while this screen is visible.
 */
class BlockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioLine: TextView
    private var focusRequest: AudioFocusRequest? = null

    private val refresh = object : Runnable {
        override fun run() {
            if (!ProbeSession.isActive(this@BlockActivity)) {
                finish()
                return
            }
            val music = getSystemService(AudioManager::class.java).isMusicActive
            audioLine.text = if (music) {
                "Sound is still playing from another app. Covering an app does not stop its audio."
            } else {
                "No other app is playing sound right now."
            }
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val until = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ProbeSession.endsAtMs(this)))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }
        col.addView(TextView(this).apply {
            text = "${Shield.appLabel(this@BlockActivity, pkg)} is blocked until $until"
            textSize = 22f
            gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = "Focus moved it to the background. It was not closed, and anything you had open in it is still there."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        })
        audioLine = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 48) }
        col.addView(audioLine)
        col.addView(Button(this).apply { text = "Go to home screen"; setOnClickListener { goHome() } })
        setContentView(col)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goHome() }
        }
        ProbeLog.log(this, "block_screen_shown", JSONObject().put("pkg", pkg))
    }

    override fun onResume() {
        super.onResume()
        if (!ProbeSession.isActive(this)) {
            finish()
            return
        }
        if (ProbeSession.requestAudioFocus(this)) takeAudioFocus()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onDestroy() {
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        super.onDestroy()
    }

    @Deprecated("Used only below API 33")
    override fun onBackPressed() = goHome()

    /** Ask well-behaved media apps to stop playback. Allowed because this screen is visible. */
    private fun takeAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        val result = getSystemService(AudioManager::class.java).requestAudioFocus(request)
        focusRequest = request
        ProbeLog.log(this, "audio_focus_requested", JSONObject().put("granted", result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED))
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PKG = "pkg"

        fun intent(ctx: Context, pkg: String): Intent =
            Intent(ctx, BlockActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
