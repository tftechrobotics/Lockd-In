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
enum class Tone { CALM, DIRECT, PLAYFUL, CUSTOM, QUIET }

/** `rules` are this phone's own app rules for the profile (each device keeps its own). */
data class Profile(val id: String, val name: String, val description: String, val builtIn: Boolean, val rules: AppRules = AppRules(), val audio: ProfileAudio = ProfileAudio())

/** A profile's study audio and music-app access (R48); defined in app.lockdin.audio. */
typealias ProfileAudio = app.lockdin.audio.ProfileAudio

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

data class CheckInState(
    val askedAt: Long? = null,
    val answeredAt: Long? = null,
    val snoozedUntil: Long? = null,
    /** Answers in this session, e.g. "paper" -> 2. */
    val answers: Map<String, Int> = emptyMap(),
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
    /** The profile's study audio when the session started (with any choice made at setup). */
    val audio: ProfileAudio = ProfileAudio(),
    /** Music browsing used so far in this session (LIMITED access), counted by FocusService. */
    val musicUsedMs: Long = 0,
    /** The 30-second warning was given. */
    val musicWarned: Boolean = false,
    /** Question timing for this session (D), if the person used it. */
    val questions: app.lockdin.engine.QuestionLog = app.lockdin.engine.QuestionLog(),
    /** Inactivity check-ins in this session (D). */
    val checkIn: CheckInState = CheckInState(),
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
    /** First-run introduction seen (new installs start with false; see Store.get). */
    val onboarded: Boolean = true,
    /** Location routines (E): places, and a switch that pauses all of them. */
    val places: List<app.lockdin.engine.Place> = emptyList(),
    val placesPaused: Boolean = false,
    /** Coaching (D): the person's own messages (Custom tone) and a message for specific apps' block screens. */
    val customMessages: List<String> = emptyList(),
    val appMessages: Map<String, String> = emptyMap(),
    /** Durations (ms) of recently finished timed questions, for the "taking longer than usual" note. */
    val questionTimes: List<Long> = emptyList(),
    /** Inactivity check-in after this many idle minutes during focus; 0 = off (default). */
    val checkInMinutes: Int = 0,
    /** Optional look (C2), both off by default: a background behind Focus, and a quote per session. */
    val backdrop: String = "NONE",
    /** The line drawn large by the "Words to work by" background. */
    val backdropWords: String = "One thing at a time",
    val quotesOn: Boolean = false,
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
        put("onboarded", s.onboarded)
        put("places", JSONArray(s.places.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("lat", p.lat).put("lon", p.lon).put("radiusM", p.radiusM)
                .put("profileId", p.profileId).put("days", JSONArray(p.days.map { it.value }.sorted()))
                .put("from", p.from.toString()).put("to", p.to.toString()).put("focusMinutes", p.focusMinutes)
                .put("autoStart", p.autoStart).put("arriveGraceMin", p.arriveGraceMin).put("enabled", p.enabled)
        }))
        put("placesPaused", s.placesPaused)
        put("customMessages", JSONArray(s.customMessages))
        put("appMessages", JSONObject().apply { s.appMessages.forEach { (k, v) -> put(k, v) } })
        put("questionTimes", JSONArray(s.questionTimes.takeLast(50)))
        put("checkInMinutes", s.checkInMinutes)
        put("backdrop", s.backdrop)
        put("backdropWords", s.backdropWords)
        put("quotesOn", s.quotesOn)
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
                onboarded = o.optBoolean("onboarded", true),
                places = o.optJSONArray("places")?.objects()?.mapNotNull(::placeOf)?.take(20) ?: emptyList(),
                placesPaused = o.optBoolean("placesPaused", false),
                customMessages = o.optJSONArray("customMessages")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().take(140).ifEmpty { null } } }?.take(10) ?: emptyList(),
                appMessages = o.optJSONObject("appMessages")?.let { m ->
                    m.keys().asSequence().filter(::validPackage).take(50).associateWith { m.optString(it).trim().take(140) }.filterValues { it.isNotEmpty() }
                } ?: emptyMap(),
                questionTimes = longs(o.optJSONArray("questionTimes")).takeLast(50),
                checkInMinutes = o.optInt("checkInMinutes", 0).let { if (it in app.lockdin.engine.CheckIns.IDLE_CHOICES) it else 0 },
                backdrop = o.optString("backdrop", "NONE").takeIf { it in BACKDROPS } ?: "NONE",
                backdropWords = o.optString("backdropWords", "One thing at a time").trim().take(40).ifEmpty { "One thing at a time" },
                quotesOn = o.optBoolean("quotesOn", false),
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
            a.optLong("musicUsedMs", 0).coerceIn(0, 24 * 3_600_000L),
            a.optBoolean("musicWarned", false),
            questionsOf(a.optJSONObject("questions")),
            a.optJSONObject("checkIn")?.let { c ->
                fun t(k: String) = if (c.isNull(k) || !c.has(k)) null else c.getLong(k)
                CheckInState(t("askedAt"), t("answeredAt"), t("snoozedUntil"),
                    c.optJSONObject("answers")?.let { m -> m.keys().asSequence().take(8).associateWith { m.optInt(it, 0).coerceIn(0, 1000) } } ?: emptyMap())
            } ?: CheckInState(),
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
    // Earlier pattern backgrounds (PAPER, LINEN, CONTOURS, HILLS) were replaced by scenes; they read as NONE.
    private val BACKDROPS = setOf("NONE", "LIBRARY", "MOUNTAINS", "WORDS", "BLUEPRINT", "PHOTO")

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
        .put("audio", audioJson(a.audio)).put("musicUsedMs", a.musicUsedMs).put("musicWarned", a.musicWarned)
        .put("questions", questionsJson(a.questions))
        .put("checkIn", JSONObject().put("askedAt", a.checkIn.askedAt ?: JSONObject.NULL).put("answeredAt", a.checkIn.answeredAt ?: JSONObject.NULL)
            .put("snoozedUntil", a.checkIn.snoozedUntil ?: JSONObject.NULL).put("answers", JSONObject().apply { a.checkIn.answers.forEach { (k, v) -> put(k, v) } }))

    private fun placeOf(p: JSONObject): app.lockdin.engine.Place? = runCatching {
        val lat = p.getDouble("lat"); val lon = p.getDouble("lon")
        require(lat in -90.0..90.0 && lon in -180.0..180.0)
        app.lockdin.engine.Place(
            p.getString("id").take(64), p.getString("name").trim().take(40).ifEmpty { "Place" }, lat, lon,
            p.optInt("radiusM", 150).coerceIn(50, 1000), p.getString("profileId"),
            p.optJSONArray("days")?.let { a -> (0 until a.length()).mapNotNull { runCatching { java.time.DayOfWeek.of(a.getInt(it)) }.getOrNull() }.toSet() } ?: emptySet(),
            java.time.LocalTime.parse(p.optString("from", "08:00")), java.time.LocalTime.parse(p.optString("to", "22:00")),
            p.optInt("focusMinutes", 40).coerceIn(5, 240), p.optBoolean("autoStart", false),
            p.optInt("arriveGraceMin", 3).coerceIn(0, 30), p.optBoolean("enabled", true),
        )
    }.getOrNull()

    private fun questionsJson(q: app.lockdin.engine.QuestionLog): JSONObject = JSONObject()
        .put("items", JSONArray(q.items.takeLast(200).map {
            JSONObject().put("n", it.n).put("startedAt", it.startedAt).put("endedAt", it.endedAt ?: JSONObject.NULL).put("pausedMs", it.pausedMs).put("stuck", it.stuck)
        }))
        .put("pausedAt", q.pausedAt ?: JSONObject.NULL).put("quietUntil", q.quietUntil ?: JSONObject.NULL).put("finished", q.finished)

    private fun questionsOf(o: JSONObject?): app.lockdin.engine.QuestionLog {
        if (o == null) return app.lockdin.engine.QuestionLog()
        fun t(j: JSONObject, k: String) = if (j.isNull(k) || !j.has(k)) null else j.getLong(k)
        val items = o.optJSONArray("items")?.objects()?.mapNotNull { j ->
            runCatching { app.lockdin.engine.QuestionItem(j.getInt("n"), j.getLong("startedAt"), t(j, "endedAt"), j.optLong("pausedMs", 0).coerceAtLeast(0), j.optBoolean("stuck")) }.getOrNull()
        }?.take(200) ?: emptyList()
        return app.lockdin.engine.QuestionLog(items, t(o, "pausedAt"), t(o, "quietUntil"), o.optBoolean("finished"))
    }

    private fun audioJson(a: ProfileAudio): JSONObject = JSONObject()
        .put("builtIn", a.builtIn).put("collection", a.collection.name).put("volume", a.volume.toDouble())
        .put("playOnStart", a.playOnStart).put("breaks", a.breaks.name)
        .put("access", a.access.name).put("musicApps", JSONArray(a.musicApps.sorted())).put("allowanceMin", a.allowanceMin)

    /**
     * Missing or invalid audio reads as off. Older saves (B2: generated ambient sound and a
     * playlist link) carry over as built-in Rain with the same start and break choices; the
     * playlist link is dropped (R48 replaces it).
     */
    private fun audioOf(o: JSONObject?): ProfileAudio {
        if (o == null) return ProfileAudio()
        if (!o.has("builtIn") && o.has("ambient")) {
            val old = o.optString("ambient", "NONE")
            return ProfileAudio(
                builtIn = old != "NONE", collection = app.lockdin.audio.AudioCollection.RAIN,
                volume = o.optDouble("level", 0.6).toFloat().let { if (it.isNaN()) 0.6f else it.coerceIn(0f, 1f) },
                playOnStart = old != "NONE" && o.optBoolean("autoPlay", true),
                breaks = if (o.optBoolean("duringBreaks", false)) app.lockdin.audio.BreakAudio.CONTINUE else app.lockdin.audio.BreakAudio.PAUSE,
            )
        }
        return ProfileAudio(
            builtIn = o.optBoolean("builtIn", false),
            collection = enumOr(o.optString("collection"), app.lockdin.audio.AudioCollection.PIANO),
            volume = o.optDouble("volume", 0.6).toFloat().let { if (it.isNaN()) 0.6f else it.coerceIn(0f, 1f) },
            playOnStart = o.optBoolean("playOnStart", false),
            breaks = enumOr(o.optString("breaks"), app.lockdin.audio.BreakAudio.PAUSE),
            access = enumOr(o.optString("access"), app.lockdin.audio.MusicAccess.NORMAL),
            musicApps = o.optJSONArray("musicApps")?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.filter(::validPackage).take(20).toSet() } ?: emptySet(),
            allowanceMin = o.optInt("allowanceMin", app.lockdin.audio.MusicAllowance.DEFAULT_MIN).coerceIn(1, 60),
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
