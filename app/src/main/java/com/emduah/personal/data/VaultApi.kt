package com.emduah.personal.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String): IOException(message)
class VaultApi(private val store: VaultStore? = null, private val client: OkHttpClient = secureClient()) {
    companion object {
        private fun secureClient() = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).followRedirects(false).build()
        private val refreshGate = Any()
        fun normalizeBase(value: String): String {
            val url = value.trim().trimEnd('/').toHttpUrl()
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null && url.encodedPath == "/") { "Enter the HTTPS origin of your web app, e.g. https://mediavault.emduah.xyz" }
            return url.toString().trimEnd('/')
        }
    }
    private fun requestJson(connection: Connection, path: String, payload: JSONObject? = null, method: String = "GET"): JSONObject {
        val request = Request.Builder().url(normalizeBase(connection.baseUrl) + "/api/mobile/v1/" + path).header("Accept", "application/json")
        if (connection.token.isNotBlank()) request.header("Authorization", "Bearer ${connection.token}")
        if (payload != null) request.post(payload.toString().toRequestBody("application/json".toMediaType()))
        if (method == "DELETE") request.delete()
        client.newCall(request.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val j = runCatching { JSONObject(text) }.getOrNull()
            if (!response.isSuccessful) throw ApiException(response.code, j?.optString("message")?.takeIf { it.isNotBlank() } ?: "Server returned ${response.code}. Check the address and mobile API installation.")
            return j ?: throw IOException("The server did not return the mobile API response.")
        }
    }
    private fun credentials(base: String, j: JSONObject) = Connection(base, j.getString("token"), j.getString("name"), j.getString("refreshToken"), j.getString("sessionId"))
    private fun json(c: Connection, path: String, payload: JSONObject? = null, method: String = "GET"): JSONObject {
        try { return requestJson(c, path, payload, method) }
        catch (e: ApiException) {
            if (e.status != 401 || c.refreshToken.isBlank() || store == null) throw e
            val renewed = synchronized(refreshGate) {
                val latest = store.connection()
                if (latest.sessionId != c.sessionId || latest.baseUrl != c.baseUrl) throw ApiException(401, "Session changed. Sign in again.")
                if (latest.token != c.token) latest else {
                    val j = try { requestJson(Connection(c.baseUrl), "session/refresh", JSONObject().put("refresh_token", c.refreshToken)) }
                    catch (failure: ApiException) {
                        if (failure.status == 401 || failure.status == 403) store.invalidateSession()
                        throw failure
                    }
                    credentials(c.baseUrl, j).also { store.saveConnection(it) }
                }
            }
            return requestJson(renewed, path, payload, method)
        }
    }
    fun links(base: String): JSONObject = requestJson(Connection(normalizeBase(base)), "auth/links")
    fun sessions(c: Connection): JSONObject = json(c, "sessions")
    fun revokeDevice(c: Connection, id: String) { json(c, "sessions/$id", method = "DELETE") }
    fun login(base: String, email: String, password: String): Connection {
        val origin = normalizeBase(base)
        val j = requestJson(Connection(origin), "session", JSONObject().put("email", email.trim()).put("password", password).put("device_name", "Emduah Android"))
        return credentials(origin, j)
    }
    fun disconnect(c: Connection) { json(c, "session/revoke", JSONObject()) }
    fun metadata(c: Connection): JSONObject = json(c, "wallpaper/options")
    fun next(c: Connection, p: Profile, current: CurrentMedia): Selection {
        val j = json(c, "wallpaper/next", p.requestFilters().put("position", current.position).put("after_id", current.sequenceId)).getJSONObject("data")
        if (p.requestFilters().optString("sort", "random") == "random") {
            require(j.getString("seed") == p.requestFilters().getString("seed")) { "The server returned a different shuffle seed." }
        }
        return Selection(j.getLong("id"), j.getString("name"), j.getString("type"), j.getString("downloadUrl"), j.getInt("nextPosition"))
    }
    // The storage request is deliberately separate: never send the API token to R2.
    fun download(selection: Selection, file: File, maxBytes: Long, shouldContinue: () -> Boolean = { true }) {
        if (!shouldContinue()) throw IOException("Download paused by network requirements.")
        val url = selection.url.toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "The download must use HTTPS." }
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Media download failed (${response.code}).")
            val body = response.body ?: throw IOException("Empty media download.")
            require(body.contentLength() <= maxBytes) { "Media exceeds your ${maxBytes / 1024 / 1024} MB download limit." }
            body.byteStream().use { source -> file.outputStream().use { target ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    if (!shouldContinue()) throw IOException("Download paused by network requirements.")
                    val read = source.read(buffer)
                    if (read == -1) break
                    total += read
                    require(total <= maxBytes) { "Media exceeds your ${maxBytes / 1024 / 1024} MB download limit." }
                    target.write(buffer, 0, read)
                }
                require(total > 0) { "The downloaded file is empty." }
            } }
        }
    }
}
