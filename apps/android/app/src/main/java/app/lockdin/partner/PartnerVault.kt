// Where the partner code key and the device's service token live (R49). Both are encrypted with
// an AES key held in Android Keystore (never exported) and kept in no-backup app storage. The key
// is never shown in the app; it's only used to check a code or an admin unlock.
package app.lockdin.partner

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object PartnerVault {
    private const val ALIAS = "lockdin-partner-vault"
    private const val FILE = "partner.vault"

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }

    private fun file(ctx: Context) = java.io.File(ctx.noBackupFilesDir, FILE)

    /** A new random 32-byte code key. */
    fun newKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun save(ctx: Context, key: ByteArray, token: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapKey()) }
        val plain = Base64.encodeToString(key, Base64.NO_WRAP) + "\n" + token
        val out = c.iv + c.doFinal(plain.toByteArray())
        val tmp = java.io.File(file(ctx).path + ".tmp")
        tmp.writeBytes(out); tmp.renameTo(file(ctx))
    }

    private fun read(ctx: Context): Pair<ByteArray, String>? = runCatching {
        val all = file(ctx).takeIf { it.exists() }?.readBytes() ?: return null
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, all, 0, 12)) }
        val (k, t) = String(c.doFinal(all, 12, all.size - 12)).split("\n", limit = 2)
        Base64.decode(k, Base64.NO_WRAP) to t
    }.getOrNull()

    fun key(ctx: Context): ByteArray? = read(ctx)?.first
    fun token(ctx: Context): String? = read(ctx)?.second

    fun clear(ctx: Context) { file(ctx).delete() }
}
