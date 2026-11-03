// The app's side of the partner service (R49). Only these calls ever leave the phone: creating
// the partner account (the code key and the partner's first name), checking whether the partner
// has opened their link, and messages to admin. Runs on a background thread; callers handle errors.
package app.lockdin.partner

import app.lockdin.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AdminMessage(val id: Long, val fromAdmin: Boolean, val kind: String, val body: String, val sessionId: String?, val at: Long)
data class Thread(val messages: List<AdminMessage>, val partnerClaimed: Boolean)
data class PartnerRequest(val id: String, val url: String)
data class NewAccount(val accountId: String, val token: String, val linkId: String, val linkUrl: String)

class ServiceError(message: String) : Exception(message)

object PartnerService {
    private fun call(method: String, path: String, body: JSONObject? = null, auth: String? = null, userAuth: String? = null): JSONObject {
        val c = URL(BuildConfig.PARTNER_URL + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("content-type", "application/json")
            auth?.let { c.setRequestProperty("authorization", "Bearer $it") }
            userAuth?.let { c.setRequestProperty("x-user-auth", it) }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code !in 200..299) throw ServiceError(json.optString("error").ifBlank { "The Lockd In service didn’t answer (error $code)." })
            return json
        } catch (e: ServiceError) { throw e
        } catch (e: Exception) { throw ServiceError("Couldn’t reach the Lockd In service. Check your internet connection and try again.")
        } finally { c.disconnect() }
    }

    /** `userToken`: the signed-in Lockd In account's device token, so the partner belongs to it. */
    fun createAccount(key: ByteArray, partnerName: String, userToken: String? = null): NewAccount {
        val j = call("POST", "/api/accounts", userAuth = userToken, body = JSONObject()
            .put("key", android.util.Base64.encodeToString(key, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))
            .put("partnerName", partnerName))
        return NewAccount(j.getString("accountId"), j.getString("token"), j.getString("linkId"), j.getString("linkUrl"))
    }

    fun thread(accountId: String, token: String, send: String? = null, sessionId: String? = null): Thread {
        val j = if (send == null) call("GET", "/api/messages", auth = "$accountId.$token")
        else call("POST", "/api/messages", JSONObject().put("body", send).put("sessionId", sessionId ?: JSONObject.NULL), auth = "$accountId.$token")
        val arr: JSONArray = j.optJSONArray("messages") ?: JSONArray()
        val list = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            AdminMessage(it.optLong("id"), it.optInt("fromAdmin") == 1 || it.optBoolean("fromAdmin"), it.optString("kind", "text"),
                it.optString("body"), it.optString("sessionId").takeIf { s -> s.isNotBlank() && s != "null" }, it.optLong("at"))
        }
        return Thread(list, j.optBoolean("partnerClaimed"))
    }

    /** Asks the partner to allow ending this session early (R51). Returns the page they open. */
    fun ask(accountId: String, token: String, sessionId: String, sessionEnd: Long): PartnerRequest {
        val j = call("POST", "/api/requests", JSONObject().put("sessionId", sessionId).put("sessionEnd", sessionEnd), auth = "$accountId.$token")
        return PartnerRequest(j.getString("id"), j.getString("url"))
    }

    /** "pending", "allowed", "denied" or "expired", and the unlock grant once allowed. */
    fun answer(accountId: String, token: String, requestId: String): Pair<String, String?> {
        val j = call("GET", "/api/requests/$requestId", auth = "$accountId.$token")
        return j.optString("status", "pending") to j.optString("grant").takeIf { it.isNotBlank() && it != "null" }
    }

    fun deleteAccount(accountId: String, token: String) { call("DELETE", "/api/account", auth = "$accountId.$token") }
}
