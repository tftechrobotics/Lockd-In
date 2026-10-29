package app.focus.probe

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Alternative detector compared against UsageEvents polling. Receives only
 * window-change events (no window content). Inert unless a probe session is
 * active and this detector was selected.
 */
class ProbeAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!ProbeSession.isActive(this)) return
        if (ProbeSession.detector(this) != ProbeSession.Detector.ACCESSIBILITY) return
        val pkg = event.packageName?.toString() ?: return
        val latency = SystemClock.uptimeMillis() - event.eventTime
        Enforcer.onForeground(this, pkg, ProbeSession.Detector.ACCESSIBILITY, latency)
    }

    override fun onInterrupt() = Unit
}
