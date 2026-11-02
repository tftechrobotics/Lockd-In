// App state and its JSON form. Same shape and rules as the web app's store:
// versioned, validated on read, anything unexpected falls back to defaults.
// Pure (org.json only) so it is unit-tested on the JVM; Store.kt persists it.
package app.lockdin.state

import app.lockdin.enforce.AppRules
import app.lockdin.enforce.Commitment
import app.lockdin.enforce.RuleMode
import app.lockdin.engine.OverrideRecord
import app.lockdin.engine.OverrideScope
import app.lockdin.engine.PhaseKind
import app.lockdin.engine.ReminderSettings
import app.lockdin.engine.ScheduledSession
import app.lockdin.engine.Segment
import app.lockdin.engine.SessionControls
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

enum class ThemeChoice { SYSTEM, LIGHT, DARK }
enum class Tone { CALM, DIRECT, PLAYFUL, QUIET }

/** `rules` are this phone's own app rules for the profile (each device keeps its own). */
data class Profile(val id: String, val name: String, val description: String, val builtIn: Boolean, val rules: AppRules = AppRules(), val audio: ProfileAudio = ProfileAudio())

/**
 * A profile's study audio (roadmap step 3, B2). `ambient` is generated on the phone; `musicLink`
 * is a playlist in the person's own music app, which Lockd In opens and controls but does not play.
 */
data class ProfileAudio(
    val ambient: app.lockdin.audio.Ambient = app.lockdin.audio.Ambient.NONE,
    val level: Float = 0.6f,
    val musicLink: String? = null,
    /** Start the sound (and open the playlist, for starts made in the app) when focus begins. */
    val autoPlay: Boolean = true,
    /** Keep ambient sound on during breaks. Off: it pauses for breaks and comes back with focus. */
    val duringBreaks: Boolean = false,
) {
    val hasSound: Boolean get() = ambient != app.lockdin.audio.Ambient.NONE || musicLink != null
    val musicProvider: app.lockdin.audio.MusicProvider? get() = app.lockdin.audio.Music.providerOf(musicLink)
}

/**
 * A reusable session setup (roadmap step 2, B). `due` and `steps` are optional study details:
 * the due date shows on the preset, the steps show during a session started from it.
 */
data class Preset(
    val id: String,
    val name: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int,
    val profileId: String,
    val task: String? = null,
    val due: java.time.LocalDate? = null,
    val steps: List<String> = emptyList(),
)

data class LastChoice(val focusMinutes: Int, val breakMinutes: Int, val rounds: Int, val profileId: String)

data class ActiveSession(
    val id: String,
    /** Wall-clock start. Clock-change protection (core::clock) is wired in with enforcement. */
    val start: Long,
    val segments: List<Segment>,
    val intention: String?,
    val profileId: String,
    val controls: SessionControls,
    /** The profile's rules when the session started: editing a profile never changes a running session. */
    val rules: AppRules = AppRules(),
    val commitment: Commitment = Commitment.DELAYED,
    /**
     * Blocked attempts during this session, kept on this phone only for the recap: app package
     * names, or "site:<domain>" for websites, with how many times each was covered.
     */
    val blocked: Map<String, Int> = emptyMap(),
    /** Set when started for a planned occurrence ("<scheduleId>@<startMs>"). */
    val occurrenceKey: String? = null,
    /** The preset it was started from, if any (its steps show during the session). */
    val presetId: String? = null,
    /** The profile's study audio when the session started. */
    val audio: ProfileAudio = ProfileAudio(),
)

data class AppState(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val tone: Tone = Tone.CALM,
    val profiles: List<Profile> = BUILT_IN_PROFILES,
    val schedule: List<ScheduledSession> = emptyList(),
    val last: LastChoice = LastChoice(40, 10, 1, "study"),
    val active: ActiveSession? = null,
    val recentIntentions: List<String> = emptyList(),
    /** Default commitment for new sessions. */
    val commitment: Commitment = Commitment.DELAYED,
    val presets: List<Preset> = emptyList(),
    /** Calendar suggestions (C): off until the person turns them on; calendars they excluded. */
    val calendarOn: Boolean = false,
    val calendarHidden: Set<Long> = emptySet(),
    /** History kept for this many days; 0 = keep everything. */
    val historyDays: Int = 365,
    /** Manual-start animation feedback: haptic clicks (on) and a quiet click sound (off). */
    val startHaptics: Boolean = true,
    val startSound: Boolean = false,
    val reminders: ReminderSettings = ReminderSettings(),
    /** Planner events already handled ("KIND:<occurrence key>", also session notices); newest last. */
    val planLog: List<String> = emptyList(),
    /** When recent sessions started (missed-session check). */
    val recentStarts: List<Long> = emptyList(),
    /** When reminders / follow-ups were shown (frequency limit). */
    val notifyLog: List<Long> = emptyList(),
    /** The last finished session, until dismissed (Home shows it). */
    val lastSession: ActiveSession? = null,
) {
    companion object {
        /** v2 (2026-11): per-profile app rules, commitment, the session's rule snapshot. v1 reads as v2 with defaults. */
        const val VERSION = 2
        const val MAX_RULE_PACKAGES = 500
        val BUILT_IN_PROFILES = listOf(
            Profile("study", "Study", "Course apps, notes, PDFs and a calculator", true),
            Profile("writing", "Writing", "A word processor and your sources", true),
            Profile("coding", "Coding", "Editor, terminal and documentation", true),
            Profile("custom", "Custom", "Only what you add", true),
        )
    }
}

object StateCodec {
    fun encode(s: AppState): String = JSONObject().apply {
        put("version", AppState.VERSION)
        put("theme", s.theme.name)
        put("tone", s.tone.name)
        put("profiles", JSONArray(s.profiles.map {
            JSONObject().put("id", it.id).put("name", it.name).put("description", it.description).put("builtIn", it.builtIn)
                .put("rules", rulesJson(it.rules)).put("audio", audioJson(it.audio))
        }))
        put("commitment", s.commitment.name)
        put("presets", JSONArray(s.presets.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("focusMinutes", p.focusMinutes).put("breakMinutes", p.breakMinutes)
                .put("rounds", p.rounds).put("profileId", p.profileId).put("task", p.task ?: JSONObject.NULL)
                .put("due", p.due?.toString() ?: JSONObject.NULL).put("steps", JSONArray(p.steps))
        }))
        put("historyDays", s.historyDays)
        put("calendarOn", s.calendarOn)
        put("calendarHidden", JSONArray(s.calendarHidden.sorted()))
        put("startHaptics", s.startHaptics)
        put("startSound", s.startSound)
        put("schedule", JSONArray(s.schedule.map {
            JSONObject().put("id", it.id).put("title", it.title)
                .put("days", JSONArray(it.days.map { d -> d.value }.sorted()))
                .put("startTime", it.startTime.toString()).put("focusMinutes", it.focusMinutes)
                .put("breakMinutes", it.breakMinutes).put("rounds", it.rounds).put("profileId", it.profileId)
                .put("date", it.date?.toString() ?: JSONObject.NULL).put("enabled", it.enabled)
                .put("autoStart", it.autoStart).put("remindMinutes", it.remindMinutes)
                .put("skipped", JSONArray(it.skipped.map { d -> d.toString() }.sorted()))
        }))
        put("reminders", JSONObject().put("notificationsOn", s.reminders.notificationsOn).put("blockingAlertsOn", s.reminders.blockingAlertsOn)
            .put("remindersOn", s.reminders.remindersOn).put("sessionEventsOn", s.reminders.sessionEventsOn)
            .put("missedFollowUpOn", s.reminders.missedFollowUpOn)
            .put("quietStart", s.reminders.quietStart?.toString() ?: JSONObject.NULL)
            .put("quietEnd", s.reminders.quietEnd?.toString() ?: JSONObject.NULL)
            .put("maxPerHour", s.reminders.maxPerHour))
        put("planLog", JSONArray(s.planLog.takeLast(MAX_LOG)))
        put("recentStarts", JSONArray(s.recentStarts.takeLast(MAX_STARTS)))
        put("notifyLog", JSONArray(s.notifyLog.takeLast(MAX_STARTS)))
        s.lastSession?.let { put("lastSession", activeJson(it)) }
        put("last", JSONObject().put("focusMinutes", s.last.focusMinutes).put("breakMinutes", s.last.breakMinutes)
            .put("rounds", s.last.rounds).put("profileId", s.last.profileId))
        s.active?.let { put("active", activeJson(it)) }
        put("recentIntentions", JSONArray(s.recentIntentions))
    }.toString()

    /** Never throws: corrupt or unknown data yields defaults, invalid items are dropped. */
    fun decode(raw: String?): AppState {
        if (raw.isNullOrBlank()) return AppState()
        return try {
            val o = JSONObject(raw)
            // v1 has the same shape without rules/commitment; the optional reads below supply defaults.
            if (o.optInt("version", -1) !in 1..AppState.VERSION) return AppState()
            val base = AppState()
            AppState(
                theme = enumOr(o.optString("theme"), ThemeChoice.SYSTEM),
                tone = enumOr(o.optString("tone"), Tone.CALM),
                profiles = o.optJSONArray("profiles")?.objects()?.mapNotNull { p ->
                    val id = p.optString("id"); val name = p.optString("name")
                    if (id.isBlank() || name.isBlank()) null
                    else Profile(id, name.take(40), p.optString("description").take(120), p.optBoolean("builtIn"), rulesOf(p.optJSONObject("rules")), audioOf(p.optJSONObject("audio")))
                }?.takeIf { it.isNotEmpty() } ?: base.profiles,
                schedule = o.optJSONArray("schedule")?.objects()?.mapNotNull(::scheduleOf) ?: emptyList(),
                last = o.optJSONObject("last")?.let {
                    LastChoice(it.optInt("focusMinutes", 40), it.optInt("breakMinutes", 10), it.optInt("rounds", 1), it.optString("profileId", "study"))
                } ?: base.last,
                active = o.optJSONObject("active")?.let(::activeOf),
                recentIntentions = o.optJSONArray("recentIntentions")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
                }?.take(8) ?: emptyList(),
                commitment = enumOr(o.optString("commitment"), Commitment.DELAYED),
                presets = o.optJSONArray("presets")?.objects()?.mapNotNull(::presetOf)?.take(MAX_PRESETS) ?: emptyList(),
                historyDays = o.optInt("historyDays", 365).let { if (it in app.lockdin.engine.History.RETENTION_CHOICES) it else 365 },
                calendarOn = o.optBoolean("calendarOn", false),
                calendarHidden = longs(o.optJSONArray("calendarHidden")).take(200).toSet(),
                startHaptics = o.optBoolean("startHaptics", true),
                startSound = o.optBoolean("startSound", false),
                reminders = o.optJSONObject("reminders")?.let(::remindersOf) ?: ReminderSettings(),
                planLog = o.optJSONArray("planLog")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { x -> x.isNotBlank() && x.length < 200 } } }?.takeLast(MAX_LOG) ?: emptyList(),
                recentStarts = longs(o.optJSONArray("recentStarts")).takeLast(MAX_STARTS),
                notifyLog = longs(o.optJSONArray("notifyLog")).takeLast(MAX_STARTS),
                lastSession = o.optJSONObject("lastSession")?.let(::activeOf),
            )
        } catch (e: JSONException) {
            AppState()
        }
    }

    private fun scheduleOf(s: JSONObject): ScheduledSession? = try {
        val days = s.optJSONArray("days")?.let { a -> (0 until a.length()).map { DayOfWeek.of(a.getInt(it)) }.toSet() } ?: emptySet()
        // Older saves have no "date"/"enabled": weekly and on.
        val date = if (s.isNull("date") || !s.has("date")) null else java.time.LocalDate.parse(s.getString("date"))
        ScheduledSession(
            s.getString("id"), s.getString("title").take(80), days, LocalTime.parse(s.getString("startTime")),
            s.getInt("focusMinutes"), s.getInt("breakMinutes"), s.getInt("rounds"), s.getString("profileId"),
            date = date, enabled = s.optBoolean("enabled", true),
            autoStart = s.optBoolean("autoStart", false),
            remindMinutes = s.optInt("remindMinutes", 10).takeIf { it in app.lockdin.engine.Planner.REMIND_CHOICES } ?: 10,
            skipped = s.optJSONArray("skipped")?.let { a -> (0 until a.length()).mapNotNull { runCatching { java.time.LocalDate.parse(a.getString(it)) }.getOrNull() } }
                ?.takeLast(60)?.toSet() ?: emptySet(),
        ).takeIf { (it.date != null || it.days.isNotEmpty()) && it.focusMinutes in 1..240 && it.rounds in 1..12 && it.breakMinutes in 0..120 }
    } catch (e: Exception) {
        null // DateTimeException, JSONException, bad day numbers: drop the item
    }

    private fun presetOf(p: JSONObject): Preset? = try {
        Preset(
            p.getString("id").take(64), p.getString("name").trim().take(60).ifEmpty { return null },
            p.getInt("focusMinutes").coerceIn(1, 240), p.optInt("breakMinutes", 0).coerceIn(0, 60), p.optInt("rounds", 1).coerceIn(1, 6),
            p.getString("profileId"),
            if (p.isNull("task") || !p.has("task")) null else p.getString("task").take(120),
            if (p.isNull("due") || !p.has("due")) null else runCatching { java.time.LocalDate.parse(p.getString("due")) }.getOrNull(),
            p.optJSONArray("steps")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().take(120).ifEmpty { null } } }?.take(20) ?: emptyList(),
        )
    } catch (e: JSONException) { null }

    private fun activeOf(a: JSONObject): ActiveSession? = try {
        val segs = a.getJSONArray("segments").objects().map { Segment(PhaseKind.valueOf(it.getString("kind")), it.getInt("minutes")) }
        val overrides = a.optJSONArray("overrides")?.objects()?.map {
            OverrideRecord(
                it.getString("id"), OverrideScope.valueOf(it.getString("scope")), it.getInt("phaseIndex"),
                it.getLong("requestedAt"), it.getInt("delaySeconds"),
                if (it.isNull("cancelledAt")) null else it.getLong("cancelledAt"),
            )
        } ?: emptyList()
        ActiveSession(
            a.getString("id"), a.getLong("start"), segs,
            if (a.isNull("intention")) null else a.getString("intention").take(120),
            a.getString("profileId"),
            SessionControls(overrides, if (a.isNull("emergencyRecoveryAt")) null else a.getLong("emergencyRecoveryAt")),
            rulesOf(a.optJSONObject("rules")),
            enumOr(a.optString("commitment"), Commitment.DELAYED),
            a.optJSONObject("blocked")?.let { o ->
                o.keys().asSequence().take(MAX_BLOCKED_KEYS)
                    .filter { it.length <= 260 }
                    .associateWith { o.optInt(it, 0).coerceIn(0, 100_000) }
                    .filterValues { it > 0 }
            } ?: emptyMap(),
            if (a.isNull("occurrenceKey") || !a.has("occurrenceKey")) null else a.getString("occurrenceKey").take(120),
            if (a.isNull("presetId") || !a.has("presetId")) null else a.getString("presetId").take(64),
            audioOf(a.optJSONObject("audio")),
        )
    } catch (e: Exception) {
        null
    }

    private fun remindersOf(r: JSONObject): ReminderSettings {
        fun time(k: String) = if (r.isNull(k) || !r.has(k)) null else runCatching { LocalTime.parse(r.getString(k)) }.getOrNull()
        val qs = time("quietStart"); val qe = time("quietEnd")
        return ReminderSettings(
            notificationsOn = r.optBoolean("notificationsOn", true),
            blockingAlertsOn = r.optBoolean("blockingAlertsOn", true),
            remindersOn = r.optBoolean("remindersOn", true),
            sessionEventsOn = r.optBoolean("sessionEventsOn", true),
            missedFollowUpOn = r.optBoolean("missedFollowUpOn", true),
            quietStart = if (qs != null && qe != null) qs else null,
            quietEnd = if (qs != null && qe != null) qe else null,
            maxPerHour = r.optInt("maxPerHour", 4).coerceIn(1, 20),
        )
    }

    private fun longs(a: JSONArray?): List<Long> = a?.let { (0 until it.length()).mapNotNull { i -> it.optLong(i, -1).takeIf { v -> v > 0 } } } ?: emptyList()

    private const val MAX_BLOCKED_KEYS = 100
    private const val MAX_LOG = 300
    private const val MAX_STARTS = 40
    private const val MAX_PRESETS = 30

    private fun activeJson(a: ActiveSession): JSONObject = JSONObject().put("id", a.id).put("start", a.start)
                .put("segments", JSONArray(a.segments.map { JSONObject().put("kind", it.kind.name).put("minutes", it.minutes) }))
                .put("intention", a.intention ?: JSONObject.NULL).put("profileId", a.profileId)
                .put("rules", rulesJson(a.rules)).put("commitment", a.commitment.name)
                .put("blocked", JSONObject().apply { a.blocked.forEach { (k, v) -> put(k, v) } })
                .put("emergencyRecoveryAt", a.controls.emergencyRecoveryAt ?: JSONObject.NULL)
                .put("overrides", JSONArray(a.controls.overrides.map {
                    JSONObject().put("id", it.id).put("scope", it.scope.name).put("phaseIndex", it.phaseIndex)
                        .put("requestedAt", it.requestedAt).put("delaySeconds", it.delaySeconds)
                        .put("cancelledAt", it.cancelledAt ?: JSONObject.NULL)
                }))
        .put("occurrenceKey", a.occurrenceKey ?: JSONObject.NULL)
        .put("presetId", a.presetId ?: JSONObject.NULL)
        .put("audio", audioJson(a.audio))

    private fun audioJson(a: ProfileAudio): JSONObject = JSONObject().put("ambient", a.ambient.name).put("level", a.level.toDouble())
        .put("musicLink", a.musicLink ?: JSONObject.NULL).put("autoPlay", a.autoPlay).put("duringBreaks", a.duringBreaks)

    /** Missing or invalid audio reads as none. Links are kept only if they're a supported playlist link. */
    private fun audioOf(o: JSONObject?): ProfileAudio {
        if (o == null) return ProfileAudio()
        return ProfileAudio(
            enumOr(o.optString("ambient"), app.lockdin.audio.Ambient.NONE),
            o.optDouble("level", 0.6).toFloat().let { if (it.isNaN()) 0.6f else it.coerceIn(0f, 1f) },
            if (o.isNull("musicLink") || !o.has("musicLink")) null else app.lockdin.audio.Music.clean(o.getString("musicLink")),
            o.optBoolean("autoPlay", true), o.optBoolean("duringBreaks", false),
        )
    }

    private fun rulesJson(r: AppRules): JSONObject =
        JSONObject().put("mode", r.mode.name).put("packages", JSONArray(r.packages.sorted())).put("sites", JSONArray(r.sites.sorted())).put("siteMode", r.siteMode.name)

    /** Missing or invalid rules read as an empty block list (restricts nothing). */
    private fun rulesOf(o: JSONObject?): AppRules {
        if (o == null) return AppRules()
        val pkgs = o.optJSONArray("packages")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(::validPackage) } }
            ?.take(AppState.MAX_RULE_PACKAGES)?.toSet() ?: emptySet()
        val sites = o.optJSONArray("sites")?.let { a -> (0 until a.length()).mapNotNull { app.lockdin.enforce.BlockPolicy.domainOf(a.optString(it)) } }
            ?.take(300)?.toSet() ?: emptySet()
        return AppRules(enumOr(o.optString("mode"), RuleMode.BLOCK_LIST), pkgs, sites, enumOr(o.optString("siteMode"), RuleMode.BLOCK_LIST))
    }

    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    fun validPackage(p: String): Boolean = p.length <= 255 && PACKAGE.matches(p)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private inline fun <reified T : Enum<T>> enumOr(v: String, d: T): T =
        enumValues<T>().firstOrNull { it.name == v } ?: d
}
