package app.lockdin

import app.lockdin.enforce.AppRules
import app.lockdin.enforce.BlockPolicy
import app.lockdin.enforce.Capability
import app.lockdin.enforce.Commitment
import app.lockdin.enforce.Protection
import app.lockdin.enforce.RuleMode
import app.lockdin.engine.Session
import app.lockdin.state.AppState
import app.lockdin.state.StateCodec
import app.lockdin.state.Store
import app.lockdin.state.LastChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementRulesTest {
    private val essentials = setOf("com.example.launcher", "com.example.keyboard", "app.lockdin")
    private val block = AppRules(RuleMode.BLOCK_LIST, setOf("com.video.app", "com.social.app"))
    private val approved = AppRules(RuleMode.APPROVED_ONLY, setOf("com.notes.app"))

    @Test fun blockListCoversOnlyChosenApps() {
        assertTrue(BlockPolicy.isRestricted("com.video.app", block, essentials))
        assertFalse(BlockPolicy.isRestricted("com.notes.app", block, essentials))
    }

    @Test fun approvedOnlyCoversEverythingElse() {
        assertFalse(BlockPolicy.isRestricted("com.notes.app", approved, essentials))
        assertTrue(BlockPolicy.isRestricted("com.video.app", approved, essentials))
    }

    @Test fun essentialsAndSystemAreNeverCovered() {
        // Even if a user puts them on a block list, or approves nothing at all.
        val hostile = AppRules(RuleMode.BLOCK_LIST, essentials + BlockPolicy.ALWAYS_ALLOWED)
        val nothingApproved = AppRules(RuleMode.APPROVED_ONLY, emptySet())
        for (pkg in essentials + BlockPolicy.ALWAYS_ALLOWED) {
            assertFalse(pkg, BlockPolicy.isRestricted(pkg, hostile, essentials))
            assertFalse(pkg, BlockPolicy.isRestricted(pkg, nothingApproved, essentials))
        }
        assertTrue("com.android.settings" in BlockPolicy.ALWAYS_ALLOWED)
        assertTrue("com.google.android.dialer" in BlockPolicy.ALWAYS_ALLOWED)
        assertFalse(BlockPolicy.isRestricted("", nothingApproved, essentials))
    }

    @Test fun protectionIsHonest() {
        val none = emptySet<Capability>()
        assertEquals(Protection.IDLE, BlockPolicy.protection(false, true, block, none))
        assertEquals(Protection.NOT_RESTRICTING, BlockPolicy.protection(true, false, block, none)) // break
        assertEquals(Protection.NOT_RESTRICTING, BlockPolicy.protection(true, true, AppRules(), none)) // timer only
        assertEquals(Protection.PERMISSION_NEEDED, BlockPolicy.protection(true, true, block, setOf(Capability.USAGE_ACCESS)))
        assertEquals(Protection.PERMISSION_NEEDED, BlockPolicy.protection(true, true, approved, setOf(Capability.DISPLAY_OVER_APPS)))
        assertEquals(Protection.PROTECTING, BlockPolicy.protection(true, true, block, none))
        assertEquals(Protection.PROTECTING, BlockPolicy.protection(true, true, AppRules(RuleMode.APPROVED_ONLY), none))
    }

    @Test fun commitmentLevels() {
        assertEquals(0, Commitment.FLEXIBLE.overrideDelaySeconds)
        assertEquals(Session.DEFAULT_OVERRIDE_DELAY_SECONDS, Commitment.DELAYED.overrideDelaySeconds)
        assertEquals(null, Commitment.LOCKED.overrideDelaySeconds)
    }

    @Test fun v1DataMigratesWithDefaults() {
        val v1 = """{"version":1,"theme":"DARK","tone":"DIRECT",
            "profiles":[{"id":"study","name":"Study","description":"d","builtIn":true}],
            "schedule":[],"last":{"focusMinutes":25,"breakMinutes":5,"rounds":2,"profileId":"study"},
            "active":{"id":"s1","start":1000,"segments":[{"kind":"FOCUS","minutes":25}],"intention":"Read","profileId":"study",
              "emergencyRecoveryAt":null,"overrides":[]},
            "recentIntentions":["Read"]}"""
        val s = StateCodec.decode(v1)
        assertEquals("DARK", s.theme.name)
        assertEquals(AppRules(), s.profiles.single().rules)
        assertEquals(Commitment.DELAYED, s.commitment)
        val a = assertNotNullAnd(s.active)
        assertEquals(AppRules(), a.rules)
        assertEquals(Commitment.DELAYED, a.commitment)
        // Re-encoding writes the current version and keeps everything.
        val again = StateCodec.decode(StateCodec.encode(s))
        assertEquals(s, again)
        assertTrue(StateCodec.encode(s).contains("\"version\":${AppState.VERSION}"))
    }

    @Test fun rulesAndCommitmentRoundTrip() {
        val base = AppState()
        val s = base.copy(
            commitment = Commitment.LOCKED,
            profiles = base.profiles.map { if (it.id == "writing") it.copy(rules = approved) else it },
        )
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }

    @Test fun invalidPackagesAreDropped() {
        val raw = """{"version":2,"profiles":[{"id":"study","name":"Study","description":"","builtIn":true,
            "rules":{"mode":"NOPE","packages":["com.ok.app","not a package","../x",""]}}]}"""
        val study = StateCodec.decode(raw).profiles.single()
        assertEquals(AppRules(RuleMode.BLOCK_LIST, setOf("com.ok.app")), study.rules)
    }

    @Test fun futureVersionFallsBackSafely() {
        val raw = StateCodec.encode(AppState(commitment = Commitment.LOCKED)).replace("\"version\":2", "\"version\":99")
        assertEquals(AppState(), StateCodec.decode(raw))
    }

    @Test fun sessionSnapshotsRulesAndCommitment() {
        val base = AppState(commitment = Commitment.FLEXIBLE)
        val store = Store(base.copy(profiles = base.profiles.map { if (it.id == "study") it.copy(rules = block) else it })) {}
        store.startSession(0, Session.roundsPlan(1, 25, 5), "Task", "study", LastChoice(25, 5, 1, "study"))
        // Editing the profile afterwards does not change the running session.
        store.update { st -> st.copy(profiles = st.profiles.map { it.copy(rules = AppRules()) }, commitment = Commitment.LOCKED) }
        val a = assertNotNullAnd(store.state.active)
        assertEquals(block, a.rules)
        assertEquals(Commitment.FLEXIBLE, a.commitment)
    }

    private fun <T> assertNotNullAnd(v: T?): T { assertNotNull(v); return v!! }
}
