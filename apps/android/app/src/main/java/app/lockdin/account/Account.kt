// Lockd In accounts (R50): sign up / sign in on the Lockd In service so this phone can later be
// paired with a computer. Only the email, first name and password (sent once over HTTPS; stored by
// the service as a salted hash) leave the phone. The device token is kept encrypted on the phone.
package app.lockdin.account

import android.content.Context
import app.lockdin.BuildConfig
import app.lockdin.partner.KeystoreFile
import app.lockdin.partner.ServiceError
import app.lockdin.state.Account
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AccountVault {
    private val store = KeystoreFile("lockdin-account-vault", "account.vault")
    fun save(ctx: Context, token: String) = store.write(ctx, token)
    fun token(ctx: Context): String? = store.read(ctx)
    fun clear(ctx: Context) = store.clear(ctx)
}

/** Checks done on the phone before anything is sent (the service checks again). */
object AccountRules {
    private val EMAIL = Regex("^[^\\s@]{1,64}@[^\\s@]{1,190}\\.[^\\s@]{2,}$")
    fun emailProblem(email: String): String? = if (EMAIL.matches(email.trim()) && email.trim().length <= 254) null else "That email doesn’t look right."
    fun passwordProblem(pw: String): String? = when {
        pw.length < 8 -> "Use at least 8 characters for your password."
        pw.length > 200 -> "That password is too long."
        else -> null
    }
}

object AccountService {
    private fun call(method: String, path: String, body: JSONObject? = null, auth: String? = null): JSONObject {
        val c = URL(BuildConfig.PARTNER_URL + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("content-type", "application/json")
            auth?.let { c.setRequestProperty("authorization", "Bearer $it") }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code !in 200..299) throw ServiceError(json.optString("error").ifBlank { "The Lockd In service didn’t answer (error $code)." })
            return json
        } catch (e: ServiceError) { throw e
        } catch (e: Exception) { throw ServiceError("Couldn’t reach Lockd In. Check your internet connection and try again.")
        } finally { c.disconnect() }
    }

    private fun device() = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim().take(40)

    private fun accountOf(j: JSONObject) = Account(j.getString("userId"), j.getString("email"), j.optString("name"))

    /** Creates the account; returns it with this phone's device token. */
    fun signUp(name: String, email: String, password: String): Pair<Account, String> {
        val j = call("POST", "/api/users", JSONObject().put("name", name).put("email", email.trim()).put("password", password).put("device", device()))
        return accountOf(j) to j.getString("deviceToken")
    }

    fun signIn(email: String, password: String): Pair<Account, String> {
        val j = call("POST", "/api/signin", JSONObject().put("email", email.trim()).put("password", password).put("device", device()))
        return accountOf(j) to j.getString("deviceToken")
    }

    fun signOut(token: String) { call("DELETE", "/api/me/device", auth = token) }
    fun delete(token: String) { call("DELETE", "/api/me", auth = token) }
}
