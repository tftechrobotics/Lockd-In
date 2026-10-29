package app.focus.probe

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** Bare-bones control screen. Product UI is designed separately (Milestone 2). */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var packages: EditText
    private lateinit var minutes: EditText
    private lateinit var detectorGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        fun button(label: String, action: () -> Unit) =
            col.addView(Button(this).apply { text = label; setOnClickListener { action() } })

        col.addView(TextView(this).apply {
            text = "Focus probe — experiment build. Measures whether this phone can detect and cover restricted apps."
            textSize = 16f
        })
        status = TextView(this).apply { textSize = 14f; setPadding(0, 24, 0, 24) }
        col.addView(status)

        button("1. Allow usage access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        button("2. Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button("3. Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        button("Optional: enable accessibility detector") { accessibilityDisclosure() }

        col.addView(TextView(this).apply { text = "Package names to restrict (comma-separated):"; setPadding(0, 24, 0, 0) })
        packages = EditText(this).apply {
            setText(ProbeSession.blocked(this@MainActivity).joinToString(",").ifEmpty {
                "com.zhiliaoapp.musically,com.ss.android.ugc.trill,com.google.android.youtube"
            })
        }
        col.addView(packages)
        col.addView(TextView(this).apply { text = "Minutes (1–60):" })
        minutes = EditText(this).apply { setText("5"); inputType = InputType.TYPE_CLASS_NUMBER }
        col.addView(minutes)

        detectorGroup = RadioGroup(this).apply {
            addView(RadioButton(this@MainActivity).apply { id = 1; text = "Detect with usage access (polling)" })
            addView(RadioButton(this@MainActivity).apply { id = 2; text = "Detect with accessibility events" })
            check(if (ProbeSession.detector(this@MainActivity) == ProbeSession.Detector.ACCESSIBILITY) 2 else 1)
        }
        col.addView(detectorGroup)

        button("Start probe session") { startSession() }
        button("End now (emergency release)") {
            ProbeService.stop(this)
            refresh()
        }
        button("Share log") {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ProbeLog.all(this)),
                "Share probe log"
            ))
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

    private fun startSession() {
        val detector = if (detectorGroup.checkedRadioButtonId == 2) ProbeSession.Detector.ACCESSIBILITY
        else ProbeSession.Detector.USAGE_STATS
        val missing = buildList {
            if (!Capabilities.overlay(this@MainActivity)) add("display over other apps")
            if (detector == ProbeSession.Detector.USAGE_STATS && !Capabilities.usageAccess(this@MainActivity)) add("usage access")
            if (detector == ProbeSession.Detector.ACCESSIBILITY && !Capabilities.accessibilityEnabled(this@MainActivity)) add("accessibility detector")
        }
        if (missing.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("Not ready")
                .setMessage("Still needed: ${missing.joinToString()}. The session was not started.")
                .setPositiveButton("OK", null).show()
            return
        }
        val blocked = packages.text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val mins = minutes.text.toString().toIntOrNull() ?: 5
        ProbeSession.start(this, blocked, mins, detector)
        ProbeLog.log(this, "session_requested", JSONObject().put("blocked", blocked.joinToString()).put("minutes", mins))
        ProbeService.start(this)
        refresh()
    }

    /** Practice run of the Play "prominent disclosure" before sending the user to Settings. */
    private fun accessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility detector")
            .setMessage(
                "This experiment can use Android's accessibility service to learn which app just " +
                    "opened, so it can pause restricted apps faster. It receives only the app's package " +
                    "name — not screen content, text you type, or anything else. Nothing leaves this phone. " +
                    "You can turn it off in Settings at any time."
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
            appendLine()
            append(if (active) "Session active." else "No session running.")
        }
        logView.text = ProbeLog.tail(this)
    }
}
