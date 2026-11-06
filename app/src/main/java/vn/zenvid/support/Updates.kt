package vn.zenvid.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
)

/**
 * Kiểm tra bản mới trên GitHub Releases của dự án.
 * Chỉ gửi một yêu cầu tới api.github.com (không kèm thông tin nào về bạn), tắt được trong Cài đặt.
 */
class Updates(private val http: OkHttpClient, private val repo: String, private val currentVersion: String) {

    val enabled get() = repo.isNotBlank()
    val issuesUrl get() = "https://github.com/$repo/issues"
    val releasesUrl get() = "https://github.com/$repo/releases"

    /** Trả về bản mới nếu có, null nếu đang dùng bản mới nhất. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Zenvid")
            .build()
        http.newCall(req).execute().use { res ->
            if (res.code == 404) return@withContext null // chưa có bản phát hành nào
            if (!res.isSuccessful) throw java.io.IOException("GitHub trả lỗi ${res.code}")
            parse(res.body.string(), currentVersion)
        }
    }

    companion object {
        fun parse(json: String, currentVersion: String): UpdateInfo? {
            val o = JSONObject(json)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
            val version = o.optString("tag_name").removePrefix("v").removePrefix("V")
            if (version.isBlank() || !isNewer(version, currentVersion)) return null
            val assets = o.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val url = assets.getJSONObject(i).optString("browser_download_url")
                if (url.endsWith(".apk")) { apk = url; break }
            }
            return UpdateInfo(version, o.optString("body").trim(), o.optString("html_url"), apk)
        }

        /** So sánh phiên bản dạng 1.10.2 > 1.9.9 (bỏ qua hậu tố như -beta). */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
