package ir.shipaa.mobile

import android.webkit.CookieManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Cookies stay in WebView's private store; nonce and media URLs are memory-only. */
class LearningApi {
    class Failure(val status: Int) : Exception("HTTP $status")
    data class Session(val user: JSONObject, val course: JSONObject)
    private var nonce = ""
    private var restCookie = ""
    private var ajaxCookie = ""
    private fun request(path: String, body: JSONObject? = null): String {
        val address = "https://shipaa.ir$path"
        val c = URL(address).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000; c.readTimeout = 20000
            c.useCaches = false
            c.setRequestProperty("Cache-Control", "no-store")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Cookie", if (path.startsWith("/wp-admin/")) ajaxCookie else restCookie)
            if (nonce.isNotEmpty()) c.setRequestProperty("X-WP-Nonce", nonce)
            if (body != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            if (status !in 200..299) throw Failure(status)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
    fun session(): Session {
        nonce = ""
        restCookie = CookieManager.getInstance().getCookie("https://shipaa.ir/wp-json/") ?: ""
        ajaxCookie = CookieManager.getInstance().getCookie("https://shipaa.ir/wp-admin/admin-ajax.php") ?: ""
        val fresh = request("/wp-admin/admin-ajax.php?action=rest-nonce").trim()
        if (!fresh.matches(Regex("[a-fA-F0-9]{10}"))) throw Failure(401)
        nonce = fresh
        val user = JSONObject(request("/wp-json/wp/v2/users/me?context=view&_fields=id,name,avatar_urls"))
        if (user.optLong("id") <= 0) throw Failure(401)
        val course = JSONObject(request("/wp-json/shipaa-learning/v1/course/174/session"))
        require(course.getInt("course_id") == 174)
        return Session(user, course)
    }
    fun progress(id: String, seconds: Long, completed: Boolean) {
        request("/wp-json/shipaa-learning/v1/course/174/progress", JSONObject()
            .put("lesson_id", id).put("seconds", seconds).put("completed", completed))
    }
}
