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
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen overlay explaining that an app is paused. It covers the app but
 * does not close it or touch its data. Main thread only.
 */
object Shield {
    private var view: View? = null

    fun isShowing() = view != null

    /** Returns false if the overlay permission is missing or the system refused the window. */
    fun show(ctx: Context, pkg: String): Boolean {
        if (view != null) return true
        if (!Capabilities.overlay(ctx)) return false
        val app = ctx.applicationContext
        val until = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ProbeSession.endsAtMs(app)))
        val label = runCatching {
            app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        val root = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(0xF2111315.toInt())
        }
        root.addView(TextView(app).apply {
            text = "$label is paused until $until"
            textSize = 22f
            setTextColor(0xFFF2F2F0.toInt())
            gravity = Gravity.CENTER
        })
        root.addView(TextView(app).apply {
            text = "It hasn't been closed. Anything you had open is still there when your focus session ends."
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
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            ProbeLog.log(app, "shield_failed", org.json.JSONObject().put("error", e.javaClass.simpleName))
            false
        }
    }

    fun hide() {
        val v = view ?: return
        runCatching { v.context.getSystemService(WindowManager::class.java).removeView(v) }
        view = null
    }
}
