package pl.wolter.usos.data.usos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class UsosException(message: String, val code: Int = 0) : Exception(message)

/** Instalacje USOS z publicznym USOS API. */
data class University(val code: String, val name: String, val baseUrl: String)

val UNIVERSITIES = listOf(
    University("Vistula", "Akademia Finansów i Biznesu Vistula", "https://usosapps.vistula.edu.pl/"),
    University("UW", "Uniwersytet Warszawski", "https://usosapps.uw.edu.pl/"),
    University("UJ", "Uniwersytet Jagielloński", "https://apps.usos.uj.edu.pl/"),
    University("PW", "Politechnika Warszawska", "https://apps.usos.pw.edu.pl/"),
    University("PWr", "Politechnika Wrocławska", "https://apps.usos.pwr.edu.pl/"),
    University("UAM", "Uniwersytet im. A. Mickiewicza", "https://usosapps.amu.edu.pl/"),
    University("UMK", "Uniwersytet Mikołaja Kopernika", "https://usosapps.umk.pl/"),
    University("UŁ", "Uniwersytet Łódzki", "https://usosapps.uni.lodz.pl/"),
    University("UWr", "Uniwersytet Wrocławski", "https://usosapps.uwr.edu.pl/"),
    University("SGH", "Szkoła Główna Handlowa", "https://usosapps.sgh.waw.pl/"),
)

/**
 * Minimalny klient USOS API z podpisem OAuth 1.0a (HMAC-SHA1).
 * Wszystkie parametry (łącznie z oauth_*) idą w ciele POST.
 */
class UsosClient(
    baseUrl: String,
    private val consumerKey: String,
    private val consumerSecret: String,
    var token: String? = null,
    var tokenSecret: String? = null,
) {
    val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val rnd = SecureRandom()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        .replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    private fun signed(url: String, params: Map<String, String>): Map<String, String> {
        val all = params.toMutableMap()
        all["oauth_consumer_key"] = consumerKey
        all["oauth_nonce"] = java.lang.Long.toHexString(rnd.nextLong()) + java.lang.Long.toHexString(rnd.nextLong())
        all["oauth_signature_method"] = "HMAC-SHA1"
        all["oauth_timestamp"] = (System.currentTimeMillis() / 1000).toString()
        all["oauth_version"] = "1.0"
        token?.let { all["oauth_token"] = it }
        val norm = all.entries.map { enc(it.key) to enc(it.value) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { "${it.first}=${it.second}" }
        val baseString = "POST&${enc(url)}&${enc(norm)}"
        val key = "${enc(consumerSecret)}&${enc(tokenSecret ?: "")}"
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA1")) }
        all["oauth_signature"] = Base64.getEncoder().encodeToString(mac.doFinal(baseString.toByteArray()))
        return all
    }

    private suspend fun post(method: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val url = base + method
        val body = FormBody.Builder().apply { signed(url, params).forEach { (k, v) -> add(k, v) } }.build()
        http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { (Json.parseToJsonElement(text) as JsonObject)["message"]?.jsonPrimitive?.content }
                    .getOrNull() ?: text.take(200)
                throw UsosException("${r.code}: $msg", r.code)
            }
            text
        }
    }

    suspend fun call(method: String, params: Map<String, String> = emptyMap()): JsonElement =
        Json.parseToJsonElement(post("services/$method", params))

    private fun form(s: String) = s.split('&').mapNotNull {
        val i = it.indexOf('='); if (i < 0) null else
            java.net.URLDecoder.decode(it.substring(0, i), "UTF-8") to java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
    }.toMap()

    /** Krok 1: token żądania. Zwraca URL autoryzacji w przeglądarce. */
    suspend fun requestToken(callback: String, scopes: String): String {
        token = null; tokenSecret = null
        val r = form(post("services/oauth/request_token", mapOf("oauth_callback" to callback, "scopes" to scopes)))
        token = r["oauth_token"] ?: throw UsosException("Brak oauth_token")
        tokenSecret = r["oauth_token_secret"]
        return "${base}services/oauth/authorize?oauth_token=${enc(token!!)}"
    }

    /** Krok 3: wymiana verifiera na token dostępu. */
    suspend fun accessToken(verifier: String) {
        val r = form(post("services/oauth/access_token", mapOf("oauth_verifier" to verifier)))
        token = r["oauth_token"] ?: throw UsosException("Brak tokenu dostępu")
        tokenSecret = r["oauth_token_secret"]
    }
}
