// What may be on screen during focus, and what the phone can honestly claim about it.
// Pure Kotlin (no Android types) so every rule is unit-tested on the JVM.
package app.lockdin.enforce

/** Block list: the chosen apps are blocked. Approved only: everything except the chosen apps is blocked. */
enum class RuleMode { BLOCK_LIST, APPROVED_ONLY }

data class AppRules(val mode: RuleMode = RuleMode.BLOCK_LIST, val packages: Set<String> = emptySet()) {
    /** False for an empty block list: such a session is a timer only and must say so. */
    val restrictsAnything: Boolean get() = mode == RuleMode.APPROVED_ONLY || packages.isNotEmpty()
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

enum class Capability { USAGE_ACCESS, DISPLAY_OVER_APPS }

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
    )

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
