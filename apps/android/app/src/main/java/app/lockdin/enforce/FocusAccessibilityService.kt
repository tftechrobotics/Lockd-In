package app.lockdin.enforce

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import app.lockdin.state.Store

/**
 * Optional "Accessibility" detection (owner decision 2026-11-01, U8): Android tells this service
 * the moment another app's window comes to the front, so blocked apps are covered immediately,
 * and in supported browsers it reads the web address shown in the address bar so blocked sites
 * can be covered too.
 *
 * What it reads, exactly: the package name of the window in front, and — only for the browsers
 * listed in [BROWSER_URL_BARS], only during a focus period with sites to block — the text of the
 * address bar, which is reduced to a domain and compared with the block list. Nothing is stored,
 * logged or sent; it reads nothing else on screen and never what you type elsewhere. It does
 * nothing at all outside focus periods. It never performs actions in other apps.
 *
 * Covering works as with usage access: Lockd In's block screen comes to the front; the app or
 * page stays open behind it. Turning the service off in Settings is always possible and is
 * detected and reported.
 */
class FocusAccessibilityService : AccessibilityService() {
    private var essentials: Set<String> = emptySet()
    private var essentialsAt = 0L
    private var lastCover: Pair<String, Long>? = null
    private var lastUrlCheck = 0L

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Safety net: Android doesn't send a window event for every way an app returns to the
     * front (some recents and gesture paths). Once a second during focus, look at which app owns
     * the active window and cover it if it's blocked.
     */
    private val check = object : Runnable {
        override fun run() {
            try {
                val session = Store.get(this@FocusAccessibilityService).state.active
                val now = System.currentTimeMillis()
                if (session != null && session.rules.restrictsApps && FocusService.evaluate(session, now).restricting && !BlockActivity.showing) {
                    val root = rootInActiveWindow
                    val pkg = root?.packageName?.toString()
                    root?.recycleCompat()
                    if (pkg != null && pkg != packageName && BlockPolicy.isRestricted(pkg, session.rules, essentials(now))) {
                        Log.i(TAG, "safety_net_cover")
                        cover(pkg, null, null)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "check_failed: ${e.javaClass.simpleName}")
            }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    override fun onServiceConnected() {
        handler.removeCallbacks(check)
        handler.postDelayed(check, CHECK_MS)
        connected = true
        Log.i(TAG, "connected")
        FocusService.sync(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        handler.removeCallbacks(check)
        connected = false
        Log.i(TAG, "disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        connected = false
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        val session = Store.get(this).state.active ?: return
        val now = System.currentTimeMillis()
        if (!FocusService.evaluate(session, now).restricting || !session.rules.restrictsAnything) return
        val rules = session.rules
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (BlockPolicy.isRestricted(pkg, rules, essentials(now))) {
                    // Event time is on the uptime clock; report the delay to our request.
                    cover(pkg, null, SystemClock.uptimeMillis() - e.eventTime)
                } else checkAddress(pkg, rules, force = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkAddress(pkg, rules, force = false)
        }
    }

    /** Reads the address bar of a supported browser and covers it if the site is blocked. */
    private fun checkAddress(pkg: String, rules: AppRules, force: Boolean) {
        if (rules.sites.isEmpty()) return
        val id = BROWSER_URL_BARS[pkg] ?: return
        val t = SystemClock.elapsedRealtime()
        if (!force && t - lastUrlCheck < URL_CHECK_MS) return
        lastUrlCheck = t
        val root = rootInActiveWindow ?: return
        try {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val bar = nodes.firstOrNull()
            val text = bar?.text?.toString()
            // Don't act while the address bar is being edited: that text is what the user is typing.
            val editing = bar?.isFocused == true
            nodes.forEach { it.recycleCompat() }
            if (editing) return
            val host = BlockPolicy.domainOf(text) ?: return
            if (BlockPolicy.isSiteBlocked(host, rules.sites)) cover(pkg, host, null)
        } finally {
            root.recycleCompat()
        }
    }

    private fun cover(pkg: String, site: String?, latencyMs: Long?) {
        val key = site ?: pkg
        val t = SystemClock.elapsedRealtime()
        // Only skip duplicate events for the same opening: the block screen is already up, or
        // was asked for a moment ago. Opening the app again later is always covered again.
        if (BlockActivity.showing) return
        lastCover?.let { (k, at) -> if (k == key && t - at < DEBOUNCE_MS) return }
        lastCover = key to t
        try {
            startActivity(BlockActivity.intent(this, pkg, site))
            Store.get(this).state.active?.let { Store.get(this).recordBlocked(it.id, if (site != null) "site:$site" else pkg) }
            Log.i(TAG, "cover_requested source=a11y kind=${if (site == null) "app" else "site"} detect_ms=${latencyMs ?: -1}")
        } catch (e: Exception) {
            Log.w(TAG, "cover_failed: ${e.javaClass.simpleName}")
        }
    }

    private fun essentials(now: Long): Set<String> {
        if (now - essentialsAt > 60_000) { essentials = Capabilities.essentials(this); essentialsAt = now }
        return essentials
    }

    @Suppress("DEPRECATION")
    private fun android.view.accessibility.AccessibilityNodeInfo.recycleCompat() {
        if (android.os.Build.VERSION.SDK_INT < 33) recycle()
    }

    companion object {
        private const val TAG = "LockdInFocus"
        private const val URL_CHECK_MS = 400L
        private const val DEBOUNCE_MS = 400L
        private const val CHECK_MS = 1_000L

        /** The service is bound by Android right now (process-local; reset by process death). */
        @Volatile var connected = false
            private set

        /** Address-bar view IDs of supported browsers. Others are blocked as whole apps only. */
        val BROWSER_URL_BARS: Map<String, String> = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "com.chrome.beta" to "com.chrome.beta:id/url_bar",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.opera.browser" to "com.opera.browser:id/url_field",
        )
    }
}
