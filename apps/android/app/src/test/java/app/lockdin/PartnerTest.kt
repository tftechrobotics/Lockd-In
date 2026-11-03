package app.lockdin

import app.lockdin.engine.SessionControls
import app.lockdin.partner.PartnerCode
import app.lockdin.partner.PartnerRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerTest {
    private val key = "12345678901234567890".toByteArray()

    @Test fun totpMatchesRfc6238Vectors() {
        // RFC 6238 appendix B, SHA-1, last 6 of the 8-digit values.
        assertEquals("287082", PartnerCode.totp(key, 59_000))
        assertEquals("081804", PartnerCode.totp(key, 1_111_111_109_000))
        assertEquals("050471", PartnerCode.totp(key, 1_111_111_111_000))
        assertEquals("005924", PartnerCode.totp(key, 1_234_567_890_000))
        assertEquals("279037", PartnerCode.totp(key, 2_000_000_000_000))
    }

    @Test fun checkAcceptsOneStepOfDriftOnly() {
        val now = 1_234_567_890_000
        val code = PartnerCode.totp(key, now)
        assertTrue(PartnerCode.check(key, code, now))
        assertTrue(PartnerCode.check(key, code, now + 30_000))
        assertFalse(PartnerCode.check(key, code, now + 90_000))
        assertFalse(PartnerCode.check(key, "12345", now))
        assertFalse(PartnerCode.check("another key 1234567".toByteArray(), code, now))
    }

    @Test fun unlockGrantMatchesTheServiceAndIsPerSession() {
        // Computed by services/partner/src/crypto.js unlockGrant(key, "sess-abc123").
        assertEquals("e3bd7f0dd9e3f226", PartnerCode.unlockGrant(key, "sess-abc123"))
        assertTrue(PartnerCode.checkGrant(key, "sess-abc123", "E3BD7F0DD9E3F226"))
        assertFalse(PartnerCode.checkGrant(key, "sess-other", "e3bd7f0dd9e3f226"))
    }

    @Test fun emergencyWaitIsThreeQuartersOfTimeLeftAndNeverNegative() {
        assertEquals(45 * 60_000L, PartnerRules.emergencyWaitMs(sessionEnd = 60 * 60_000L, now = 0))
        assertEquals(0L, PartnerRules.emergencyWaitMs(sessionEnd = 0, now = 10_000))
        assertTrue(PartnerRules.emergencyPending(SessionControls(emergencyRecoveryAt = 5_000), now = 1_000))
        assertFalse(PartnerRules.emergencyPending(SessionControls(emergencyRecoveryAt = 5_000), now = 5_000))
        assertFalse(PartnerRules.emergencyPending(SessionControls(), now = 1_000))
    }
}
