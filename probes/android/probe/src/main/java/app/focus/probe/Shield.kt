package app.focus.probe

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen overlay used by the OVERLAY cover method. It covers the app but
 * the app stays resumed underneath, so video or audio can keep playing — the
 * wording says so. Some apps and system screens can hide overlays. Main thread only.
 */
object Shield {
    private var view: View? = null

    fun isShowing() = view != null

    fun show(ctx: Context, pkg: String): Boolean {
        if (view != null) return true
        if (!Capabilities.overlay(ctx)) return false
        val app = ctx.applicationContext
        val until = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ProbeSession.endsAtMs(app)))
        val label = appLabel(app, pkg)

        val root = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(0xF2111315.toInt())
        }
        root.addView(TextView(app).apply {
            text = "$label is blocked until $until"
            textSize = 22f
            setTextColor(0xFFF2F2F0.toInt())
            gravity = Gravity.CENTER
        })
        root.addView(TextView(app).apply {
            text = "It is covered, not closed. It may still be running and playing sound underneath."
            textSize = 16f
            setTextColor(0xFFB8BBBE.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 48)
        })
        root.addView(Button(app).apply {
            text = "Go to home screen"
            setOnClickListener {
                hide()
                app.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        return try {
            app.getSystemService(WindowManager::class.java).addView(root, params)
            view = root
            true
        } catch (e: Exception) {
            ProbeLog.log(app, "overlay_failed", JSONObject().put("error", e.javaClass.simpleName))
            false
        }
    }

    fun hide() {
        val v = view ?: return
        runCatching { v.context.getSystemService(WindowManager::class.java).removeView(v) }
        view = null
    }

    fun appLabel(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
