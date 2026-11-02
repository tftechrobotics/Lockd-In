// What may be on screen during focus, and what the phone can honestly claim about it.
// Pure Kotlin (no Android types) so every rule is unit-tested on the JVM.
package app.lockdin.enforce

/** Block list: the chosen apps are blocked. Approved only: everything except the chosen apps is blocked. */
enum class RuleMode { BLOCK_LIST, APPROVED_ONLY }

/**
 * App rules (`mode` + `packages`) and website rules (`siteMode` + `sites`, domains, checked in
 * supported browsers with the Accessibility mode). Each can be a block list or "allow only".
 */
data class AppRules(
    val mode: RuleMode = RuleMode.BLOCK_LIST,
    val packages: Set<String> = emptySet(),
    val sites: Set<String> = emptySet(),
    val siteMode: RuleMode = RuleMode.BLOCK_LIST,
) {
    val restrictsApps: Boolean get() = mode == RuleMode.APPROVED_ONLY || packages.isNotEmpty()
    val restrictsSites: Boolean get() = siteMode == RuleMode.APPROVED_ONLY || sites.isNotEmpty()
    /** False for empty block lists: such a session is a timer only and must say so. */
    val restrictsAnything: Boolean get() = restrictsApps || restrictsSites
}

/** How early a session can end. Emergency recovery is always available and separate. */
enum class Commitment(val overrideDelaySeconds: Int?) {
    /** End or unlock immediately. */
    FLEXIBLE(0),
    /** A cancellable countdown first (U5 default 90 s). */
    DELAYED(90),
    /** No unlock or early end; only emergency recovery. */
    LOCKED(null),
}

/** What this phone reports about restriction right now. Only PROTECTING may close the lock. */
enum class Protection {
    /** No session is running. */
    IDLE,
    /** A session is running but nothing is restricted now: a break, an unlocked round, or timer only. */
    NOT_RESTRICTING,
    /** Restriction is due but a required permission is missing, so nothing is enforced. */
    PERMISSION_NEEDED,
    /** Restriction is due, permissions are present and the enforcer is running. */
    PROTECTING,
}

enum class Capability { USAGE_ACCESS, DISPLAY_OVER_APPS, ACCESSIBILITY }

/** How this phone detects the app in front. */
enum class Detection {
    /** Accessibility service: told immediately when an app opens; can read browser address bars. */
    ACCESSIBILITY,
    /** Usage access, polled about twice a second; can't see web addresses. */
    USAGE_ACCESS,
    NONE,
}

object BlockPolicy {
    /**
     * Never covered, whatever the rules: system UI, calls and emergency, Settings (so the
     * user keeps control of their phone, including revoking our permissions), permission
     * and install dialogs. The home screen, keyboard, default dialer and Lockd In itself are
     * added at runtime because their package names vary by device.
     */
    val ALWAYS_ALLOWED: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.samsung.android.emergency",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        // Payments and wallets: blocking them can break tap-to-pay and their system panels
        // (owner report 2026-11-01: a blocked Wallet made the home screen unusable).
        "com.samsung.android.spay",
        "com.samsung.android.samsungpay.gear",
        "com.google.android.apps.walletnfcrel",
        // Clock/alarms, camera, contacts: basic phone functions.
        "com.sec.android.app.clockpackage",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.camera",
        "com.google.android.GoogleCamera",
        "com.android.camera2",
        "com.samsung.android.app.contacts",
        "com.google.android.contacts",
        "com.android.contacts",
        // System panels and tools that draw over the home screen.
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.app.smartcapture",
        "com.samsung.android.lool",
        // Sign-in codes: blocking them can lock someone out of their accounts.
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        // Accessibility tools.
        "com.google.android.marvin.talkback",
        "com.samsung.accessibility",
        "com.samsung.android.accessibility.talkback",
    )

    /**
     * Which detection works with the permissions granted, and what is still missing for app
     * blocking. Accessibility alone is enough (an accessibility service may bring the block
     * screen forward itself). Otherwise usage access plus "display over other apps" are needed.
     */
    fun detection(accessibility: Boolean, usageAccess: Boolean, displayOverApps: Boolean): Pair<Detection, Set<Capability>> = when {
        accessibility -> Detection.ACCESSIBILITY to emptySet()
        usageAccess && displayOverApps -> Detection.USAGE_ACCESS to emptySet()
        else -> Detection.NONE to buildSet {
            if (!usageAccess) add(Capability.USAGE_ACCESS)
            if (!displayOverApps) add(Capability.DISPLAY_OVER_APPS)
        }
    }

    /**
     * What is missing for these rules: app blocking needs a detection method; a session that only
     * blocks websites needs Accessibility (usage access can't see web addresses).
     */
    fun missingFor(rules: AppRules, detection: Detection, missingForApps: Set<Capability>): Set<Capability> = when {
        !rules.restrictsApps && rules.restrictsSites ->
            if (detection == Detection.ACCESSIBILITY) emptySet() else setOf(Capability.ACCESSIBILITY)
        else -> missingForApps
    }

    /** Lower-case domain, at most 253 characters, at least one dot, letters/digits/hyphens. */
    private val DOMAIN = Regex("^(?=.{3,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")

    /** Normalizes what a user typed ("https://www.YouTube.com/watch") to a domain, or null. */
    fun domainOf(input: String?): String? {
        if (input == null) return null
        var s = input.trim().lowercase()
        if (s.isEmpty() || s.length > 2048 || s.any { it.isWhitespace() }) return null
        s = s.substringAfter("://", s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfter('@').substringBefore(':').trimEnd('.')
        if (s.startsWith("www.")) s = s.removePrefix("www.")
        return s.takeIf { DOMAIN.matches(it) }
    }

    /** True if `host` is a blocked domain or one of its subdomains (m.youtube.com for youtube.com). */
    fun isSiteBlocked(host: String?, sites: Set<String>): Boolean {
        val h = domainOf(host) ?: return false
        return matches(h, sites)
    }

    private fun matches(h: String, sites: Set<String>) = sites.any { d -> h == d || h.endsWith(".$d") }

    /**
     * Website rule for an address shown in a browser. Block list: listed sites are blocked.
     * Allow only: every site except the listed ones (and their subdomains) is blocked. Pages
     * without a web address (new tab, browser settings) are never blocked.
     */
    fun isSiteRestricted(host: String?, rules: AppRules): Boolean {
        val h = domainOf(host) ?: return false
        return when (rules.siteMode) {
            RuleMode.BLOCK_LIST -> matches(h, rules.sites)
            RuleMode.APPROVED_ONLY -> !matches(h, rules.sites)
        }
    }

    /** For a running session: its rules, plus the music-app access it started with (R48). */
    fun isRestrictedIn(pkg: String, session: app.lockdin.state.ActiveSession, essentials: Set<String>): Boolean {
        if (pkg.isBlank() || pkg in ALWAYS_ALLOWED || pkg in essentials) return false
        app.lockdin.audio.MusicAllowance.decide(pkg, session.audio, session.musicUsedMs)?.let { return it }
        return isRestricted(pkg, session.rules, essentials)
    }

    fun isRestricted(pkg: String, rules: AppRules, essentials: Set<String>): Boolean {
        if (pkg.isBlank() || pkg in ALWAYS_ALLOWED || pkg in essentials) return false
        return when (rules.mode) {
            RuleMode.BLOCK_LIST -> pkg in rules.packages
            RuleMode.APPROVED_ONLY -> pkg !in rules.packages
        }
    }

    /**
     * The honest state. `restrictionDue` comes from the session rules (core `evaluate`);
     * a session whose rules restrict nothing is timer only, never "protecting".
     */
    fun protection(sessionActive: Boolean, restrictionDue: Boolean, rules: AppRules, missing: Set<Capability>): Protection = when {
        !sessionActive -> Protection.IDLE
        !restrictionDue || !rules.restrictsAnything -> Protection.NOT_RESTRICTING
        missing.isNotEmpty() -> Protection.PERMISSION_NEEDED
        else -> Protection.PROTECTING
    }
}
