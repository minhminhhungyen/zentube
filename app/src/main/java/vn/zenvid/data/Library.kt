package vn.zenvid.data

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Lists {
    const val LATER = "later"
    const val LIKED = "liked"
}

/** Thư viện cá nhân: tài khoản, lịch sử, Xem sau, Đã thích, kênh đăng ký, danh sách phát. Tất cả nằm trên máy. */
@OptIn(ExperimentalCoroutinesApi::class)
class Library(private val db: AppDb, private val prefs: Prefs) {
    private val dao = db.dao()
    private val pid get() = prefs.profileId.value

    private fun channelIdOf(url: String) = url.substringAfter("/channel/", "").substringBefore("/").substringBefore("?").ifBlank { null }

    private fun <T> perProfile(block: (Long) -> Flow<T>, empty: T): Flow<T> =
        prefs.profileId.flow.flatMapLatest { if (it < 0) flowOf(empty) else block(it) }

    // ================================================================ tài khoản
    val profiles: Flow<List<Profile>> = dao.profiles()

    suspend fun profile(id: Long) = dao.profile(id)

    suspend fun createProfile(name: String, pin: String?): Long {
        val colors = listOf(0xFFE53935, 0xFF8E24AA, 0xFF3949AB, 0xFF039BE5, 0xFF00897B, 0xFF7CB342, 0xFFFB8C00, 0xFF6D4C41)
        val (hash, salt) = if (pin.isNullOrEmpty()) null to null else hashPin(pin)
        return dao.insertProfile(
            Profile(name = name.trim(), pinHash = hash, pinSalt = salt, color = colors.random().toInt())
        )
    }

    suspend fun renameProfile(p: Profile, name: String) = dao.updateProfile(p.copy(name = name.trim()))

    suspend fun setPin(p: Profile, pin: String?) {
        val (hash, salt) = if (pin.isNullOrEmpty()) null to null else hashPin(pin)
        dao.updateProfile(p.copy(pinHash = hash, pinSalt = salt))
    }

    fun checkPin(p: Profile, pin: String): Boolean {
        val salt = p.pinSalt ?: return p.pinHash == null
        return hashPin(pin, Base64.getDecoder().decode(salt)).first == p.pinHash
    }

    suspend fun deleteProfile(id: Long) = db.withTransaction {
        dao.clearSearches(id); dao.clearHistory(id); dao.clearSaved(id); dao.clearSubs(id)
        dao.clearAllPlaylistItems(id); dao.clearPlaylists(id); dao.deleteProfileRow(id)
    }

    /** Mã PIN được băm bằng PBKDF2 kèm muối ngẫu nhiên – không bao giờ lưu dạng rõ. */
    private fun hashPin(pin: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): Pair<String, String> {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val enc = Base64.getEncoder()
        return enc.encodeToString(hash) to enc.encodeToString(salt)
    }

    // ================================================================ tìm kiếm
    val searches: Flow<List<String>> = perProfile({ dao.searches(it) }, emptyList())

    suspend fun addSearch(q: String) {
        if (pid < 0 || prefs.pauseSearchHistory.value || q.isBlank()) return
        dao.upsertSearch(SearchEntry(pid, q.trim(), System.currentTimeMillis()))
    }

    suspend fun deleteSearch(q: String) = dao.deleteSearch(pid, q)
    suspend fun clearSearches() = dao.clearSearches(pid)

    // ================================================================ lịch sử xem
    val history: Flow<List<HistoryEntry>> = perProfile({ dao.history(it) }, emptyList())
    val progress: Flow<List<Progress>> = perProfile({ dao.progress(it) }, emptyList())

    suspend fun recentHistory(n: Int) = if (pid < 0) emptyList() else dao.recentHistory(pid, n)
    suspend fun watchedUrls(): Set<String> = if (pid < 0) emptySet() else dao.watchedUrls(pid).toSet()
    suspend fun positionOf(url: String): Long = dao.historyEntry(pid, url)?.positionMs ?: 0

    suspend fun recordWatch(v: VideoItem, positionMs: Long) {
        if (pid < 0 || prefs.pauseWatchHistory.value) return
        dao.upsertHistory(
            HistoryEntry(pid, v.url, v.title, v.uploader, v.uploaderUrl, v.thumbnail, v.durationSec, positionMs, System.currentTimeMillis())
        )
    }

    suspend fun updatePosition(url: String, pos: Long) {
        if (pid < 0 || prefs.pauseWatchHistory.value) return
        dao.updatePosition(pid, url, pos)
    }

    suspend fun deleteHistory(url: String) = dao.deleteHistory(pid, url)
    suspend fun clearHistory() = dao.clearHistory(pid)

    // ================================================================ Xem sau / Đã thích
    fun saved(list: String): Flow<List<SavedEntry>> = perProfile({ dao.saved(it, list) }, emptyList())
    fun isSaved(list: String, url: String): Flow<Boolean> = perProfile({ dao.isSaved(it, list, url) }, false)

    suspend fun setSaved(list: String, v: VideoItem, saved: Boolean) {
        if (pid < 0) return
        val existing = dao.savedEntry(pid, list, v.url)
        if (saved) {
            if (existing != null) return
            dao.upsertSaved(SavedEntry(pid, list, v.url, v.title, v.uploader, v.uploaderUrl, v.thumbnail, v.durationSec, System.currentTimeMillis()))
        } else {
            if (existing == null) return
            dao.deleteSaved(pid, list, v.url)
        }
    }

    // ================================================================ kênh đăng ký
    val subscriptions: Flow<List<Subscription>> = perProfile({ dao.subs(it) }, emptyList())
    fun isSubscribed(channelUrl: String): Flow<Boolean> =
        perProfile({ dao.isSubscribed(it, YouTube.normalizeChannelUrl(channelUrl)) }, false)

    suspend fun subscriptionUrls(): List<String> = if (pid < 0) emptyList() else dao.subsList(pid).map { it.channelUrl }

    suspend fun setSubscribed(channelUrl: String, name: String, avatar: String?, subscribed: Boolean) {
        if (pid < 0) return
        val url = YouTube.normalizeChannelUrl(channelUrl)
        val existing = dao.sub(pid, url)
        if (subscribed) {
            // Đã đăng ký rồi: chỉ cập nhật tên/ảnh
            dao.upsertSub(Subscription(pid, url, name, avatar ?: existing?.avatar, existing?.subscribedAt ?: System.currentTimeMillis()))
        } else if (existing != null) {
            dao.deleteSub(pid, url)
        }
    }

    // ================================================================ danh sách phát
    val playlists: Flow<List<PlaylistSummary>> = perProfile({ dao.playlists(it) }, emptyList())
    fun playlist(id: Long) = dao.playlist(id)
    fun playlistItems(id: Long) = dao.playlistItems(id)
    fun playlistsContaining(url: String): Flow<List<Long>> = perProfile({ dao.playlistsContaining(it, url) }, emptyList())

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(LocalPlaylist(profileId = pid, name = name.trim()))

    suspend fun renamePlaylist(id: Long, name: String) {
        dao.renamePlaylist(id, name.trim())
    }

    suspend fun deletePlaylist(id: Long) {
        db.withTransaction { dao.clearPlaylistItems(id); dao.deletePlaylistRow(id) }
    }

    suspend fun addToPlaylist(id: Long, v: VideoItem) {
        if (dao.playlistItem(id, v.url) != null) return
        dao.upsertPlaylistItem(v.toPlaylistItem(id, System.currentTimeMillis()))
    }

    suspend fun removeFromPlaylist(id: Long, url: String) {
        dao.deletePlaylistItem(id, url)
    }

    /** Lưu một danh sách phát YouTube vào thư viện (trên máy). */
    suspend fun copyToNewPlaylist(name: String, items: List<VideoItem>): Long = db.withTransaction {
        val id = createPlaylist(name)
        val t = System.currentTimeMillis()
        dao.upsertPlaylistItems(items.mapIndexed { i, v -> v.toPlaylistItem(id, t + i) })
        id
    }

    private fun VideoItem.toPlaylistItem(id: Long, t: Long) =
        LocalPlaylistItem(id, url, title, uploader, uploaderUrl, thumbnail, durationSec, t)

    // ================================================================ nhập / sao lưu
    /** Nhập kênh đăng ký từ Google Takeout (subscriptions.csv) hoặc bản xuất của NewPipe (.json). */
    suspend fun importSubscriptions(text: String): Int {
        if (pid < 0) return 0
        val now = System.currentTimeMillis()
        val subs = mutableListOf<Subscription>()
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r')
        if (trimmed.startsWith("{")) {
            val arr = JSONObject(trimmed).optJSONArray("subscriptions") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optInt("service_id", 0) != 0) continue
                val url = o.optString("url").takeIf { it.isNotBlank() } ?: continue
                subs += Subscription(pid, YouTube.normalizeChannelUrl(url), o.optString("name", url), null, now)
            }
        } else {
            // CSV Takeout: "Channel Id,Channel Url,Channel Title" (tiêu đề cột có thể là tiếng Việt)
            trimmed.lineSequence().drop(1).forEach { line ->
                val cols = parseCsvLine(line)
                if (cols.size < 2) return@forEach
                val id = cols[0].trim()
                val url = cols[1].trim().ifBlank { "https://www.youtube.com/channel/$id" }
                if (!url.contains("youtube.com")) return@forEach
                subs += Subscription(pid, YouTube.normalizeChannelUrl(url.replace("http://", "https://")), cols.getOrElse(2) { id }.trim(), null, now)
            }
        }
        dao.insertSubs(subs)
        return subs.size
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    /** Sao lưu toàn bộ dữ liệu của tài khoản hiện tại ra JSON (file do bạn tự chọn nơi lưu). */
    suspend fun exportJson(): String {
        val p = dao.profile(pid) ?: return "{}"
        val root = JSONObject()
        root.put("app", "Zenvid").put("version", 1).put("profileName", p.name)
        root.put("subscriptions", JSONArray(dao.subsList(pid).map {
            JSONObject().put("url", it.channelUrl).put("name", it.name).put("avatar", it.avatar).put("at", it.subscribedAt)
        }))
        root.put("history", JSONArray(dao.recentHistory(pid, Int.MAX_VALUE).map {
            JSONObject().put("url", it.url).put("title", it.title).put("uploader", it.uploader).put("uploaderUrl", it.uploaderUrl)
                .put("thumbnail", it.thumbnail).put("duration", it.durationSec).put("pos", it.positionMs).put("at", it.watchedAt)
        }))
        root.put("saved", JSONArray(dao.allSaved(pid).map {
            JSONObject().put("list", it.list).put("url", it.url).put("title", it.title).put("uploader", it.uploader)
                .put("uploaderUrl", it.uploaderUrl).put("thumbnail", it.thumbnail).put("duration", it.durationSec).put("at", it.addedAt)
        }))
        root.put("searches", JSONArray(dao.allSearches(pid).map { JSONObject().put("q", it.query).put("at", it.time) }))
        root.put("playlists", JSONArray(dao.playlistList(pid).map { pl ->
            JSONObject().put("name", pl.name).put("at", pl.createdAt).put("items", JSONArray(dao.playlistItemsList(pl.id).map {
                JSONObject().put("url", it.url).put("title", it.title).put("uploader", it.uploader).put("uploaderUrl", it.uploaderUrl)
                    .put("thumbnail", it.thumbnail).put("duration", it.durationSec).put("at", it.addedAt)
            }))
        }))
        return root.toString(2)
    }

    /** Khôi phục bản sao lưu (gộp vào dữ liệu hiện có của tài khoản đang dùng). */
    suspend fun importJson(text: String): Int = db.withTransaction {
        val root = JSONObject(text)
        // Nhận cả bản sao lưu tạo từ tên cũ của app
        if (root.optString("app") !in setOf("Zenvid", "Clean" + "Tube")) {
            return@withTransaction importSubscriptions(text) // có thể là file NewPipe
        }
        var n = 0
        fun JSONObject.s(k: String) = if (isNull(k)) null else optString(k)
        root.optJSONArray("subscriptions")?.let { a ->
            dao.insertSubs((0 until a.length()).map { a.getJSONObject(it) }.map {
                Subscription(pid, it.getString("url"), it.optString("name"), it.s("avatar"), it.optLong("at"))
            }.also { n += it.size })
        }
        root.optJSONArray("history")?.let { a ->
            dao.upsertHistory((0 until a.length()).map { a.getJSONObject(it) }.map {
                HistoryEntry(pid, it.getString("url"), it.optString("title"), it.optString("uploader"), it.s("uploaderUrl"),
                    it.s("thumbnail"), it.optLong("duration", -1), it.optLong("pos"), it.optLong("at"))
            }.also { n += it.size })
        }
        root.optJSONArray("saved")?.let { a ->
            dao.upsertSaved((0 until a.length()).map { a.getJSONObject(it) }.map {
                SavedEntry(pid, it.getString("list"), it.getString("url"), it.optString("title"), it.optString("uploader"),
                    it.s("uploaderUrl"), it.s("thumbnail"), it.optLong("duration", -1), it.optLong("at"))
            }.also { n += it.size })
        }
        root.optJSONArray("searches")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { dao.upsertSearch(SearchEntry(pid, it.getString("q"), it.optLong("at"))) }
        }
        root.optJSONArray("playlists")?.let { a ->
            for (i in 0 until a.length()) {
                val pl = a.getJSONObject(i)
                val id = dao.insertPlaylist(LocalPlaylist(profileId = pid, name = pl.optString("name"), createdAt = pl.optLong("at")))
                val items = pl.optJSONArray("items") ?: continue
                dao.upsertPlaylistItems((0 until items.length()).map { items.getJSONObject(it) }.map {
                    LocalPlaylistItem(id, it.getString("url"), it.optString("title"), it.optString("uploader"), it.s("uploaderUrl"),
                        it.s("thumbnail"), it.optLong("duration", -1), it.optLong("at"))
                })
                n++
            }
        }
        n
    }
}
