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
                if (session != null && session.rules.restrictsAnything &&
                    FocusService.evaluate(session, now).restricting && !BlockActivity.showing) {
                    checkFront(session.rules, "safety_net")
                }
            } catch (e: Exception) {
                Log.w(TAG, "check_failed: ${e.javaClass.simpleName}")
            }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    private val recheck = Runnable { check.run().also { handler.removeCallbacks(check); handler.postDelayed(check, CHECK_MS) } }

    /**
     * Looks at the app that owns the window in front and covers it if it, or the site it shows,
     * is restricted. Used by the safety net and shortly after a window event that arrived before
     * its app was in front (a page opened from a link, for example).
     */
    private fun checkFront(rules: AppRules, source: String) {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        root?.recycleCompat()
        if (pkg == null || pkg == packageName) return
        val now = System.currentTimeMillis()
        when {
            BlockPolicy.isRestricted(pkg, rules, essentials(now)) -> { Log.i(TAG, "${source}_cover"); cover(pkg, null, null) }
            unsupportedBrowserBlocked(pkg, rules) -> { Log.i(TAG, "${source}_cover"); cover(pkg, null, null) }
            else -> checkAddress(pkg, rules, force = true)
        }
    }

    private val frontCheck = Runnable {
        val session = Store.get(this).state.active ?: return@Runnable
        if (session.rules.restrictsAnything && FocusService.evaluate(session, System.currentTimeMillis()).restricting && !BlockActivity.showing)
            checkFront(session.rules, "front_recheck")
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
                // Only act on the app that actually owns the window in front. Panels, pop-ups and
                // quick-access sheets (e.g. a wallet's swipe-up card on the home screen) also send
                // this event; covering them made the home screen unusable.
                if (!ownsActiveWindow(pkg)) {
                    // The window may not be in front yet: look again in a moment.
                    handler.removeCallbacks(frontCheck)
                    handler.postDelayed(frontCheck, 300)
                    handler.postDelayed(frontCheck, 900)
                    return
                }
                when {
                    BlockPolicy.isRestricted(pkg, rules, essentials(now)) ->
                        // Event time is on the uptime clock; report the delay to our request.
                        cover(pkg, null, SystemClock.uptimeMillis() - e.eventTime)
                    unsupportedBrowserBlocked(pkg, rules) -> cover(pkg, null, null)
                    else -> checkAddress(pkg, rules, force = true)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkAddress(pkg, rules, force = false)
        }
    }

    private fun ownsActiveWindow(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try { root.packageName?.toString() == pkg } finally { root.recycleCompat() }
    }

    /**
     * "Allow only these websites" can't be checked in browsers whose address bar we can't read,
     * so those browsers are blocked as a whole during focus (the block screen says why).
     */
    private fun unsupportedBrowserBlocked(pkg: String, rules: AppRules): Boolean {
        if (rules.siteMode != RuleMode.APPROVED_ONLY || pkg in BROWSER_URL_BARS) return false
        val now = SystemClock.elapsedRealtime()
        if (now - browsersAt > 60_000) { browsers = Capabilities.browsers(this); browsersAt = now }
        return pkg in browsers && pkg !in essentials(System.currentTimeMillis())
    }

    private val debuggable by lazy { (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 }

    private var browsers: Set<String> = emptySet()
    private var browsersAt = 0L

    /** Reads the address bar of a supported browser and covers it if the site is restricted. */
    private fun checkAddress(pkg: String, rules: AppRules, force: Boolean) {
        if (!rules.restrictsSites) return
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
            val host = BlockPolicy.domainOf(text)
            // Debug builds only: what the address check saw (domain only). Release builds log nothing here.
            if (debuggable) Log.d(TAG, "url_check pkg=$pkg bar=${bar != null} editing=$editing host=$host")
            if (editing || host == null) return
            if (BlockPolicy.isSiteRestricted(host, rules)) cover(pkg, host, null)
        } finally {
            root.recycleCompat()
        }
    }

    private fun cover(pkg: String, site: String?, latencyMs: Long?) {
        val key = site ?: pkg
        val t = SystemClock.elapsedRealtime()
        // Only skip duplicate events for the same opening: the block screen is already up, or
        // was asked for a moment ago. Opening the app again later is always covered again.
        if (BlockActivity.showing) {
            // The block screen may be on its way out (Home was pressed and the app reopened
            // straight away). Look again once it has gone.
            handler.removeCallbacks(recheck)
            handler.postDelayed(recheck, 350)
            return
        }
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
