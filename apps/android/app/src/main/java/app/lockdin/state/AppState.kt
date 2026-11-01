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
data class Profile(val id: String, val name: String, val description: String, val builtIn: Boolean, val rules: AppRules = AppRules())

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
                .put("rules", rulesJson(it.rules))
        }))
        put("commitment", s.commitment.name)
        put("schedule", JSONArray(s.schedule.map {
            JSONObject().put("id", it.id).put("title", it.title)
                .put("days", JSONArray(it.days.map { d -> d.value }.sorted()))
                .put("startTime", it.startTime.toString()).put("focusMinutes", it.focusMinutes)
                .put("breakMinutes", it.breakMinutes).put("rounds", it.rounds).put("profileId", it.profileId)
                .put("date", it.date?.toString() ?: JSONObject.NULL).put("enabled", it.enabled)
        }))
        put("last", JSONObject().put("focusMinutes", s.last.focusMinutes).put("breakMinutes", s.last.breakMinutes)
            .put("rounds", s.last.rounds).put("profileId", s.last.profileId))
        s.active?.let { a ->
            put("active", JSONObject().put("id", a.id).put("start", a.start)
                .put("segments", JSONArray(a.segments.map { JSONObject().put("kind", it.kind.name).put("minutes", it.minutes) }))
                .put("intention", a.intention ?: JSONObject.NULL).put("profileId", a.profileId)
                .put("rules", rulesJson(a.rules)).put("commitment", a.commitment.name)
                .put("emergencyRecoveryAt", a.controls.emergencyRecoveryAt ?: JSONObject.NULL)
                .put("overrides", JSONArray(a.controls.overrides.map {
                    JSONObject().put("id", it.id).put("scope", it.scope.name).put("phaseIndex", it.phaseIndex)
                        .put("requestedAt", it.requestedAt).put("delaySeconds", it.delaySeconds)
                        .put("cancelledAt", it.cancelledAt ?: JSONObject.NULL)
                })))
        }
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
                    else Profile(id, name.take(40), p.optString("description").take(120), p.optBoolean("builtIn"), rulesOf(p.optJSONObject("rules")))
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
        ).takeIf { (it.date != null || it.days.isNotEmpty()) && it.focusMinutes in 1..240 && it.rounds in 1..12 && it.breakMinutes in 0..120 }
    } catch (e: Exception) {
        null // DateTimeException, JSONException, bad day numbers: drop the item
    }

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
        )
    } catch (e: Exception) {
        null
    }

    private fun rulesJson(r: AppRules): JSONObject =
        JSONObject().put("mode", r.mode.name).put("packages", JSONArray(r.packages.sorted()))

    /** Missing or invalid rules read as an empty block list (restricts nothing). */
    private fun rulesOf(o: JSONObject?): AppRules {
        if (o == null) return AppRules()
        val pkgs = o.optJSONArray("packages")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(::validPackage) } }
            ?.take(AppState.MAX_RULE_PACKAGES)?.toSet() ?: emptySet()
        return AppRules(enumOr(o.optString("mode"), RuleMode.BLOCK_LIST), pkgs)
    }

    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    fun validPackage(p: String): Boolean = p.length <= 255 && PACKAGE.matches(p)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private inline fun <reified T : Enum<T>> enumOr(v: String, d: T): T =
        enumValues<T>().firstOrNull { it.name == v } ?: d
}
