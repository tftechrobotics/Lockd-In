// Partner-code sessions (R49): the pure rules, unit tested without a phone.
package app.lockdin.partner

import app.lockdin.engine.SessionControls
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PartnerRules {
    /**
     * Without the partner's code, an emergency end waits 3/4 of the time left in the session
     * (owner 2026-11-02). It can never be longer than that, so there is no indefinite lockout.
     */
    fun emergencyWaitMs(sessionEnd: Long, now: Long): Long = ((sessionEnd - now).coerceAtLeast(0) * 3) / 4

    /** An emergency end has been asked for and is still waiting. */
    fun emergencyPending(controls: SessionControls, now: Long): Boolean = controls.emergencyRecoveryAt?.let { it > now } == true
}

/**
 * The same codes as the partner service (services/partner/src/crypto.js): RFC 6238 TOTP
 * (HMAC-SHA1, 30-second steps, 6 digits) and the admin unlock grant (first 16 hex of
 * HMAC-SHA256(key, "unlock:" + sessionId)). Checked on the phone, offline.
 */
object PartnerCode {
    const val STEP_MS = 30_000L

    fun totp(key: ByteArray, nowMs: Long): String {
        var counter = nowMs / STEP_MS
        val msg = ByteArray(8)
        for (i in 7 downTo 0) { msg[i] = (counter and 0xff).toByte(); counter = counter shr 8 }
        val h = mac("HmacSHA1", key, msg)
        val off = h[h.size - 1].toInt() and 0x0f
        val bin = ((h[off].toInt() and 0x7f) shl 24) or ((h[off + 1].toInt() and 0xff) shl 16) or
            ((h[off + 2].toInt() and 0xff) shl 8) or (h[off + 3].toInt() and 0xff)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }

    /** True for the code shown now or in the previous or next 30-second step (clock drift). */
    fun check(key: ByteArray, code: String, nowMs: Long): Boolean {
        val c = code.filter { it.isDigit() }
        if (c.length != 6) return false
        return listOf(-1L, 0L, 1L).any { java.security.MessageDigest.isEqual(totp(key, nowMs + it * STEP_MS).toByteArray(), c.toByteArray()) }
    }

    fun unlockGrant(key: ByteArray, sessionId: String): String =
        mac("HmacSHA256", key, "unlock:$sessionId".toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    fun checkGrant(key: ByteArray, sessionId: String, grant: String): Boolean =
        java.security.MessageDigest.isEqual(unlockGrant(key, sessionId).toByteArray(), grant.lowercase().toByteArray())

    private fun mac(alg: String, key: ByteArray, msg: ByteArray): ByteArray =
        Mac.getInstance(alg).apply { init(SecretKeySpec(key, alg)) }.doFinal(msg)
}
