// Profile summary (R64): opening an existing profile lands on a read-only view of what it blocks.
// "Edit apps and sites" opens the existing ProfileEditor unchanged; back returns here.
package app.lockdin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lockdin.audio.MusicAccess
import app.lockdin.enforce.EnforcerStatus
import app.lockdin.enforce.InstalledApps
import app.lockdin.enforce.RuleMode
import app.lockdin.state.Profile
import app.lockdin.state.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the status pill says. Pure, so the "only when verified" rule is unit-testable. */
enum class ProfileStatus { ACTIVE, NOT_IN_USE, NOT_SET_UP }

/**
 * "Blocking active" needs a running session with this profile AND a fresh PROTECTING report for it.
 * Missing permissions read as "Blocking isn't set up"; anything else is "Not in use".
 */
fun profileStatus(
    profileId: String, activeSessionId: String?, activeProfileId: String?, report: EnforcerStatus.Report?, missingPermissions: Boolean,
    nowElapsed: Long = android.os.SystemClock.elapsedRealtime(),
): ProfileStatus = when {
    activeSessionId != null && activeProfileId == profileId && EnforcerStatus.protecting(activeSessionId, report, nowElapsed) -> ProfileStatus.ACTIVE
    missingPermissions -> ProfileStatus.NOT_SET_UP
    else -> ProfileStatus.NOT_IN_USE
}

private data class AppRow(val pkg: String, val label: String, val installed: Boolean)

@Composable
fun ProfileSummary(store: Store, profile: Profile, onEdit: () -> Unit, openBlocking: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val rules = profile.rules
    val allowOnly = rules.mode == RuleMode.APPROVED_ONLY
    val apps by produceState<List<AppRow>?>(null, rules.packages) {
        value = withContext(Dispatchers.IO) {
            rules.packages.map { pkg ->
                val installed = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)
                AppRow(pkg, if (installed) InstalledApps.label(ctx, pkg) else pkg, installed)
            }.sortedBy { it.label.lowercase() }
        }
    }
    val report by EnforcerStatus.reports.collectAsState()
    val missing = rememberMissing()
    val needsA11y = !rules.restrictsApps && rules.restrictsSites && !app.lockdin.enforce.Capabilities.accessibility(ctx)
    val active = store.state.active
    val status = profileStatus(profile.id, active?.id, active?.profileId, report, rules.restrictsAnything && (missing.isNotEmpty() || needsA11y))

    WithActionBar(
        content = {
            // WithActionBar already scrolls and pads its content.
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s4)) {
                Text(profile.name, style = Type.display, color = c.ink, modifier = Modifier.semantics { heading() })
                StatusPill(status, openBlocking)
                Text("These stay closed during your focus rounds. Calls and the apps you need stay open.", style = UiType.body, color = c.inkMuted)

                // Apps
                val list = apps
                when {
                    list == null -> Text("Loading apps…", style = UiType.caption, color = c.inkMuted)
                    list.isEmpty() && !allowOnly -> Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                        Text("No apps blocked yet", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                        SecondaryButton("Add apps", onEdit)
                    }
                    else -> Column {
                        SummaryHeading(if (allowOnly) "Allowed apps · ${list.size}" else "Blocked apps · ${list.size}")
                        if (allowOnly) Text("Every other app is covered during focus.", style = UiType.caption, color = c.inkMuted, modifier = Modifier.padding(bottom = Dim.s2))
                        list.forEachIndexed { i, a ->
                            if (i > 0) HorizontalDivider(color = c.line)
                            AppSummaryRow(a, if (allowOnly) "Allowed" else "Blocked")
                        }
                    }
                }

                // Websites
                if (rules.sites.isNotEmpty() || rules.siteMode == RuleMode.APPROVED_ONLY) Column {
                    SummaryHeading("Websites · ${rules.sites.size}")
                    if (rules.siteMode == RuleMode.APPROVED_ONLY) Text("Only these sites are allowed.", style = UiType.caption, color = c.inkMuted, modifier = Modifier.padding(bottom = Dim.s2))
                    rules.sites.sorted().forEachIndexed { i, d ->
                        if (i > 0) HorizontalDivider(color = c.line)
                        SiteRow(d, if (rules.siteMode == RuleMode.APPROVED_ONLY) "Allowed" else "Blocked")
                    }
                }

                // Other rules (only when set)
                val music = profile.audio.access
                if (music != MusicAccess.NORMAL) Column(verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
                    SummaryHeading("Other rules")
                    Text(
                        when (music) {
                            MusicAccess.LIMITED -> "Music apps: ${profile.audio.allowanceMin} minutes of browsing per session"
                            MusicAccess.ALWAYS -> "Music apps: always allowed"
                            MusicAccess.NORMAL -> "Music apps: follow the rules above"
                        },
                        style = UiType.body, color = c.ink,
                    )
                }
            }
        },
        actions = { MainButton("Edit apps and sites", onEdit, modifier = Modifier.testTag("edit-profile")) },
    )
}

@Composable
private fun SummaryHeading(text: String) {
    val c = LocalColors.current
    Text(text, style = UiType.section, color = c.inkMuted, modifier = Modifier.padding(bottom = Dim.s1).semantics { heading() })
}

@Composable
private fun StatusPill(status: ProfileStatus, openBlocking: () -> Unit) {
    val c = LocalColors.current
    val text = when (status) {
        ProfileStatus.ACTIVE -> "Blocking active"
        ProfileStatus.NOT_IN_USE -> "Not in use"
        ProfileStatus.NOT_SET_UP -> "Blocking isn’t set up"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3)) {
        // No olive token exists in the palette, and new colours are out of scope, so "active" uses the
        // warm status accent (statusDot) the app already uses for live protection.
        val bg = if (status == ProfileStatus.ACTIVE) c.statusDot.copy(alpha = 0.22f) else c.taupeSoft
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 14.dp, vertical = 7.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Status: $text"; liveRegion = LiveRegionMode.Polite }
                .testTag("profile-status"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (status == ProfileStatus.ACTIVE) LockdIcon(LIcon.Focus, size = 16.dp, tint = c.ink)
            Text(text, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.clearAndSetSemantics { })
        }
        if (status == ProfileStatus.NOT_SET_UP) TextAction("Set up in Settings", openBlocking)
    }
}

@Composable
private fun AppSummaryRow(a: AppRow, state: String) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<android.graphics.Bitmap?>(null, a.pkg) {
        value = if (a.installed) withContext(Dispatchers.IO) { InstalledApps.icon(ctx, a.pkg, px) } else null
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Dim.s2)
            .semantics(mergeDescendants = true) { contentDescription = if (a.installed) "${a.label}, ${state.lowercase()}" else "${a.label}, not installed, ${state.lowercase()}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            val b = icon
            if (b != null) Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
            else Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.taupeSoft), contentAlignment = Alignment.Center) {
                LockdIcon(LIcon.Phone, size = 20.dp, tint = c.inkMuted)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(a.label, style = UiType.label, color = c.ink)
            if (!a.installed) Text("Not installed", style = UiType.caption, color = c.inkMuted)
        }
        Text(state, style = UiType.caption, color = c.inkMuted)
    }
}

@Composable
private fun SiteRow(domain: String, state: String) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Dim.s2)
            .semantics(mergeDescendants = true) { contentDescription = "$domain, ${state.lowercase()}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.taupeSoft), contentAlignment = Alignment.Center) {
            LockdIcon(LIcon.Laptop, size = 20.dp, tint = c.inkMuted)
        }
        Text(domain, style = UiType.label, color = c.ink, modifier = Modifier.weight(1f))
        Text(state, style = UiType.caption, color = c.inkMuted)
    }
}

