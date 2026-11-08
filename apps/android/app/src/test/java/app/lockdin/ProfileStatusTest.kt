package app.lockdin

import app.lockdin.enforce.EnforcerStatus
import app.lockdin.enforce.Protection
import app.lockdin.ui.ProfileStatus
import app.lockdin.ui.profileStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** The summary's pill may say "Blocking active" only for verified, fresh protection of this profile's session. */
class ProfileStatusTest {
    private val now = 1_000_000L
    private fun report(sid: String, p: Protection, age: Long = 0) = EnforcerStatus.Report(sid, p, emptySet(), atElapsed = now - age)

    @Test fun activeOnlyWhenProtectingThisSession() {
        assertEquals(ProfileStatus.ACTIVE, profileStatus("study", "s1", "study", report("s1", Protection.PROTECTING), false, nowElapsed = now))
    }

    @Test fun notActiveForOtherProtectionStates() {
        for (p in Protection.values().filter { it != Protection.PROTECTING })
            assertEquals(ProfileStatus.NOT_IN_USE, profileStatus("study", "s1", "study", report("s1", p), false, nowElapsed = now))
    }

    @Test fun notActiveForStaleReportOrOtherProfileOrNoSession() {
        assertEquals(ProfileStatus.NOT_IN_USE, profileStatus("study", "s1", "study", report("s1", Protection.PROTECTING, age = EnforcerStatus.FRESH_MS + 1), false, nowElapsed = now))
        assertEquals(ProfileStatus.NOT_IN_USE, profileStatus("study", "s1", "work", report("s1", Protection.PROTECTING), false, nowElapsed = now))
        assertEquals(ProfileStatus.NOT_IN_USE, profileStatus("study", null, null, null, false, nowElapsed = now))
    }

    @Test fun missingPermissionsSaySetUp() {
        assertEquals(ProfileStatus.NOT_SET_UP, profileStatus("study", null, null, null, true, nowElapsed = now))
    }
}
