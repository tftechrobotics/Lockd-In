package app.focus.probe

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** Bare-bones control screen for the experiment. Product UI is Milestone 2. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var packages: EditText
    private lateinit var minutes: EditText
    private lateinit var detectorGroup: RadioGroup
    private lateinit var coverGroup: RadioGroup
    private lateinit var pollGroup: RadioGroup
    private lateinit var audioFocus: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        fun note(s: String, size: Float = 14f, top: Int = 24) =
            col.addView(TextView(this).apply { this.text = s; textSize = size; setPadding(0, top, 0, 0) })
        fun button(label: String, action: () -> Unit) =
            col.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        fun radios(options: List<Pair<Int, String>>, checked: Int) = RadioGroup(this).apply {
            options.forEach { (id, label) -> addView(RadioButton(this@MainActivity).apply { this.id = id; this.text = label }) }
            this.check(checked)
            col.addView(this)
        }

        note("Focus probe — experiment build", 18f, 0)
        note(
            "Measures whether this phone can detect and block a chosen app. Start with the smoke test, " +
                "which uses the harmless Focus Probe Target app and ends by itself after 3 minutes."
        )
        note(
            "Privacy: the log on this phone records the package names of apps you open during a session, " +
                "which can reveal what you use. It stays on this phone unless you tap Share log.",
            12f,
        )
        status = TextView(this).apply { textSize = 14f; setPadding(0, 24, 0, 8) }
        col.addView(status)

        button("Allow usage access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        button("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button("Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        button("Optional: accessibility detector") { accessibilityDisclosure() }

        note("Smoke test", 16f)
        button("Run smoke test (harmless target, 3 minutes)") { smokeTest() }

        note("Custom session", 16f)
        note("Package names to block (comma-separated):", 14f, 8)
        packages = EditText(this).apply {
            setText(ProbeSession.blocked(this@MainActivity).joinToString(",").ifEmpty { ProbeSession.TARGET_PACKAGE })
        }
        col.addView(packages)
        note("Minutes (1–60):", 14f, 8)
        minutes = EditText(this).apply { setText("5"); inputType = InputType.TYPE_CLASS_NUMBER }
        col.addView(minutes)

        note("Detector:", 14f, 8)
        detectorGroup = radios(
            listOf(
                1 to "Usage access (polling)",
                2 to "Accessibility events",
                3 to "None — battery baseline run (blocks nothing)",
            ),
            when (ProbeSession.detector(this)) {
                ProbeSession.Detector.USAGE_STATS -> 1
                ProbeSession.Detector.ACCESSIBILITY -> 2
                ProbeSession.Detector.NONE_BASELINE -> 3
            },
        )
        note("When a blocked app opens:", 14f, 8)
        coverGroup = radios(
            listOf(
                11 to "Show block screen (moves the app to the background)",
                12 to "Overlay only (app keeps running underneath)",
                13 to "Go to home screen",
            ),
            when (ProbeSession.cover(this)) {
                ProbeSession.Cover.BLOCK_SCREEN -> 11
                ProbeSession.Cover.OVERLAY -> 12
                ProbeSession.Cover.HOME -> 13
            },
        )
        note("Polling interval (usage access only):", 14f, 8)
        pollGroup = radios(listOf(21 to "250 ms", 22 to "500 ms", 23 to "1000 ms"), 22)
        audioFocus = CheckBox(this).apply {
            text = "Ask other apps to pause sound while the block screen is shown"
            isChecked = ProbeSession.requestAudioFocus(this@MainActivity)
        }
        col.addView(audioFocus)

        button("Start session") { startSession(readSettings()) }
        button("End now (emergency recovery)") { ProbeService.stop(this); refresh() }
        button("Share log") {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ProbeLog.all(this)),
                    "Share probe log",
                )
            )
        }
        button("Clear log") { ProbeLog.clear(this); refresh() }

        logView = TextView(this).apply { textSize = 11f; setTextIsSelectable(true); setPadding(0, 24, 0, 0) }
        col.addView(logView)
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private data class SessionSettings(
        val blocked: Set<String>,
        val minutes: Int,
        val detector: ProbeSession.Detector,
        val cover: ProbeSession.Cover,
        val pollMs: Long,
        val audioFocus: Boolean,
    )

    private fun readSettings() = SessionSettings(
        blocked = packages.text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        minutes = minutes.text.toString().toIntOrNull() ?: 5,
        detector = when (detectorGroup.checkedRadioButtonId) {
            2 -> ProbeSession.Detector.ACCESSIBILITY
            3 -> ProbeSession.Detector.NONE_BASELINE
            else -> ProbeSession.Detector.USAGE_STATS
        },
        cover = when (coverGroup.checkedRadioButtonId) {
            12 -> ProbeSession.Cover.OVERLAY
            13 -> ProbeSession.Cover.HOME
            else -> ProbeSession.Cover.BLOCK_SCREEN
        },
        pollMs = when (pollGroup.checkedRadioButtonId) {
            21 -> 250L
            23 -> 1_000L
            else -> 500L
        },
        audioFocus = audioFocus.isChecked,
    )

    private fun smokeTest() = startSession(
        SessionSettings(
            blocked = setOf(ProbeSession.TARGET_PACKAGE),
            minutes = 3,
            detector = ProbeSession.Detector.USAGE_STATS,
            cover = ProbeSession.Cover.BLOCK_SCREEN,
            pollMs = 500L,
            audioFocus = false,
        ),
        label = "smoke_test",
    )

    private fun startSession(s: SessionSettings, label: String = "custom") {
        val missing = buildList {
            if (!Capabilities.notifications(this@MainActivity)) add("notifications")
            if (s.detector != ProbeSession.Detector.NONE_BASELINE && !Capabilities.overlay(this@MainActivity)) {
                add("display over other apps")
            }
            if (s.detector == ProbeSession.Detector.USAGE_STATS && !Capabilities.usageAccess(this@MainActivity)) {
                add("usage access")
            }
            if (s.detector == ProbeSession.Detector.ACCESSIBILITY && !Capabilities.accessibilityEnabled(this@MainActivity)) {
                add("accessibility detector")
            }
        }
        if (missing.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("Not ready")
                .setMessage("Still needed: ${missing.joinToString()}. Nothing was started.")
                .setPositiveButton("OK", null).show()
            return
        }
        if (s.blocked.isEmpty() && s.detector != ProbeSession.Detector.NONE_BASELINE) return
        ProbeSession.start(this, s.blocked, s.minutes, s.detector, s.cover, s.pollMs, s.audioFocus)
        ProbeLog.log(
            this, "session_requested",
            JSONObject().put("label", label).put("blocked", s.blocked.joinToString()).put("minutes", s.minutes)
                .put("detector", s.detector.name).put("cover", s.cover.name).put("poll_ms", s.pollMs)
                .put("audio_focus", s.audioFocus).put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("release", Build.VERSION.RELEASE)
        )
        ProbeService.start(this)
        refresh()
    }

    /** Practice run of the Google Play prominent disclosure, before opening Settings. */
    private fun accessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility detector")
            .setMessage(
                "This experiment can use Android's accessibility service to learn which app just opened, " +
                    "so it can block restricted apps faster. It receives only the app's package name — not " +
                    "screen content or anything you type. Nothing leaves this phone. You can turn it off in " +
                    "Settings at any time. If Advanced Protection is on, Android may not allow this."
            )
            .setPositiveButton("Agree and open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("No thanks", null)
            .show()
    }

    private fun refresh() {
        fun mark(ok: Boolean) = if (ok) "✓" else "✗"
        val active = ProbeSession.isActive(this)
        status.text = buildString {
            appendLine("${mark(Capabilities.usageAccess(this@MainActivity))} Usage access")
            appendLine("${mark(Capabilities.overlay(this@MainActivity))} Display over other apps")
            appendLine("${mark(Capabilities.notifications(this@MainActivity))} Notifications")
            appendLine("${mark(Capabilities.accessibilityEnabled(this@MainActivity))} Accessibility detector (optional)")
            appendLine("${mark(Capabilities.batteryUnrestricted(this@MainActivity))} Battery unrestricted (record, don't change for the smoke test)")
            appendLine(
                "Advanced Protection: " + when (Capabilities.advancedProtection(this@MainActivity)) {
                    true -> "ON"
                    false -> "off"
                    null -> "unknown on this Android version (record it from Settings)"
                }
            )
            appendLine()
            append(if (active) "Session active." else "No session running.")
        }
        logView.text = ProbeLog.tail(this)
        logView.visibility = View.VISIBLE
    }
}
