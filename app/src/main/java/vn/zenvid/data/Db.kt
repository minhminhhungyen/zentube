package vn.zenvid.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Mọi dữ liệu cá nhân (tài khoản, lịch sử, danh sách) chỉ lưu trong cơ sở dữ liệu này trên máy.

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val color: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "search_history", primaryKeys = ["profileId", "query"])
data class SearchEntry(val profileId: Long, val query: String, val time: Long)

@Entity(tableName = "watch_history", primaryKeys = ["profileId", "url"])
data class HistoryEntry(
    val profileId: Long,
    val url: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String?,
    val thumbnail: String?,
    val durationSec: Long,
    val positionMs: Long,
    val watchedAt: Long,
) {
    fun toItem() = VideoItem(url, title, uploader, uploaderUrl, thumbnail, durationSec = durationSec)
}

/** Danh sách lưu sẵn: "later" = Xem sau, "liked" = Video đã thích. */
@Entity(tableName = "saved", primaryKeys = ["profileId", "list", "url"])
data class SavedEntry(
    val profileId: Long,
    val list: String,
    val url: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String?,
    val thumbnail: String?,
    val durationSec: Long,
    val addedAt: Long,
) {
    fun toItem() = VideoItem(url, title, uploader, uploaderUrl, thumbnail, durationSec = durationSec)
}

@Entity(tableName = "subscriptions", primaryKeys = ["profileId", "channelUrl"])
data class Subscription(
    val profileId: Long,
    val channelUrl: String,
    val name: String,
    val avatar: String?,
    val subscribedAt: Long,
)

@Entity(tableName = "playlists")
data class LocalPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "url"])
data class LocalPlaylistItem(
    val playlistId: Long,
    val url: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String?,
    val thumbnail: String?,
    val durationSec: Long,
    val addedAt: Long,
) {
    fun toItem() = VideoItem(url, title, uploader, uploaderUrl, thumbnail, durationSec = durationSec)
}

/** Video/âm thanh đã tải về máy (dùng chung cho mọi người dùng trên máy). */
@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String?,
    val thumbnail: String?,
    val durationSec: Long,
    /** "video" | "audio" */
    val kind: String,
    /** Chất lượng tối đa người dùng chọn (0 với âm thanh). */
    val height: Int,
    /** queued | running | done | failed */
    val status: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val fileUri: String? = null,
    val mime: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val videoItag: Int = -1,
    val audioItag: Int = -1,
    /** Chất lượng thực tế đã tải (vd. 720). */
    val actualHeight: Int = 0,
) {
    fun toItem() = VideoItem(url, title, uploader, uploaderUrl, thumbnail, durationSec = durationSec)
}

data class PlaylistSummary(
    @Embedded val playlist: LocalPlaylist,
    val count: Int,
    val thumbnail: String?,
)

data class Progress(val url: String, val positionMs: Long, val durationSec: Long)

@Dao
interface AppDao {
    // ---- tài khoản
    @Query("SELECT * FROM profiles ORDER BY createdAt") fun profiles(): Flow<List<Profile>>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun profile(id: Long): Profile?
    @Insert suspend fun insertProfile(p: Profile): Long
    @Update suspend fun updateProfile(p: Profile)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun deleteProfileRow(id: Long)

    // ---- lịch sử tìm kiếm
    @Query("SELECT `query` FROM search_history WHERE profileId = :pid ORDER BY time DESC LIMIT 100")
    fun searches(pid: Long): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSearch(e: SearchEntry)
    @Query("DELETE FROM search_history WHERE profileId = :pid AND `query` = :q") suspend fun deleteSearch(pid: Long, q: String)
    @Query("DELETE FROM search_history WHERE profileId = :pid") suspend fun clearSearches(pid: Long)
    @Query("SELECT * FROM search_history WHERE profileId = :pid") suspend fun allSearches(pid: Long): List<SearchEntry>

    // ---- lịch sử xem
    @Query("SELECT * FROM watch_history WHERE profileId = :pid ORDER BY watchedAt DESC") fun history(pid: Long): Flow<List<HistoryEntry>>
    @Query("SELECT * FROM watch_history WHERE profileId = :pid ORDER BY watchedAt DESC LIMIT :n") suspend fun recentHistory(pid: Long, n: Int): List<HistoryEntry>
    @Query("SELECT * FROM watch_history WHERE profileId = :pid AND url = :url") suspend fun historyEntry(pid: Long, url: String): HistoryEntry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHistory(e: HistoryEntry)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHistory(e: List<HistoryEntry>)
    @Query("UPDATE watch_history SET positionMs = :pos WHERE profileId = :pid AND url = :url") suspend fun updatePosition(pid: Long, url: String, pos: Long)
    @Query("DELETE FROM watch_history WHERE profileId = :pid AND url = :url") suspend fun deleteHistory(pid: Long, url: String)
    @Query("DELETE FROM watch_history WHERE profileId = :pid") suspend fun clearHistory(pid: Long)
    @Query("SELECT url, positionMs, durationSec FROM watch_history WHERE profileId = :pid") fun progress(pid: Long): Flow<List<Progress>>
    @Query("SELECT url FROM watch_history WHERE profileId = :pid") suspend fun watchedUrls(pid: Long): List<String>

    // ---- Xem sau / Đã thích
    @Query("SELECT * FROM saved WHERE profileId = :pid AND list = :list ORDER BY addedAt DESC") fun saved(pid: Long, list: String): Flow<List<SavedEntry>>
    @Query("SELECT * FROM saved WHERE profileId = :pid") suspend fun allSaved(pid: Long): List<SavedEntry>
    @Query("SELECT EXISTS(SELECT 1 FROM saved WHERE profileId = :pid AND list = :list AND url = :url)") fun isSaved(pid: Long, list: String, url: String): Flow<Boolean>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSaved(e: SavedEntry)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSaved(e: List<SavedEntry>)
    @Query("DELETE FROM saved WHERE profileId = :pid AND list = :list AND url = :url") suspend fun deleteSaved(pid: Long, list: String, url: String)
    @Query("DELETE FROM saved WHERE profileId = :pid") suspend fun clearSaved(pid: Long)

    // ---- Kênh đăng ký
    @Query("SELECT * FROM subscriptions WHERE profileId = :pid ORDER BY name COLLATE NOCASE") fun subs(pid: Long): Flow<List<Subscription>>
    @Query("SELECT * FROM subscriptions WHERE profileId = :pid") suspend fun subsList(pid: Long): List<Subscription>
    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE profileId = :pid AND channelUrl = :url)") fun isSubscribed(pid: Long, url: String): Flow<Boolean>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSub(s: Subscription)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSubs(s: List<Subscription>)
    @Query("DELETE FROM subscriptions WHERE profileId = :pid AND channelUrl = :url") suspend fun deleteSub(pid: Long, url: String)
    @Query("DELETE FROM subscriptions WHERE profileId = :pid") suspend fun clearSubs(pid: Long)

    // ---- Danh sách phát của bạn
    @Query(
        """SELECT pl.*,
           (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = pl.id) AS count,
           (SELECT i.thumbnail FROM playlist_items i WHERE i.playlistId = pl.id ORDER BY i.addedAt LIMIT 1) AS thumbnail
           FROM playlists pl WHERE pl.profileId = :pid ORDER BY pl.createdAt DESC"""
    )
    fun playlists(pid: Long): Flow<List<PlaylistSummary>>
    @Query("SELECT * FROM playlists WHERE profileId = :pid") suspend fun playlistList(pid: Long): List<LocalPlaylist>
    @Query("SELECT * FROM playlists WHERE id = :id") fun playlist(id: Long): Flow<LocalPlaylist?>
    @Insert suspend fun insertPlaylist(p: LocalPlaylist): Long
    @Query("UPDATE playlists SET name = :name WHERE id = :id") suspend fun renamePlaylist(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylistRow(id: Long)
    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY addedAt") fun playlistItems(id: Long): Flow<List<LocalPlaylistItem>>
    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY addedAt") suspend fun playlistItemsList(id: Long): List<LocalPlaylistItem>
    @Query("SELECT playlistId FROM playlist_items WHERE url = :url AND playlistId IN (SELECT id FROM playlists WHERE profileId = :pid)")
    fun playlistsContaining(pid: Long, url: String): Flow<List<Long>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlaylistItem(i: LocalPlaylistItem)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlaylistItems(i: List<LocalPlaylistItem>)
    @Query("DELETE FROM playlist_items WHERE playlistId = :id AND url = :url") suspend fun deletePlaylistItem(id: Long, url: String)
    @Query("DELETE FROM playlist_items WHERE playlistId = :id") suspend fun clearPlaylistItems(id: Long)
    @Query("DELETE FROM playlist_items WHERE playlistId IN (SELECT id FROM playlists WHERE profileId = :pid)") suspend fun clearAllPlaylistItems(pid: Long)
    @Query("DELETE FROM playlists WHERE profileId = :pid") suspend fun clearPlaylists(pid: Long)



    @Query("SELECT * FROM subscriptions WHERE profileId = :pid AND channelUrl = :url") suspend fun sub(pid: Long, url: String): Subscription?

    @Query("SELECT * FROM saved WHERE profileId = :pid AND list = :list AND url = :url") suspend fun savedEntry(pid: Long, list: String, url: String): SavedEntry?
    @Query("SELECT * FROM saved WHERE profileId = :pid AND list = :list") suspend fun savedList(pid: Long, list: String): List<SavedEntry>

    @Query("SELECT * FROM playlists WHERE id = :id") suspend fun playlistRow(id: Long): LocalPlaylist?
    @Query("SELECT * FROM playlist_items WHERE playlistId = :id AND url = :url") suspend fun playlistItem(id: Long, url: String): LocalPlaylistItem?

    // ---- tải xuống
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun downloads(): Flow<List<DownloadEntry>>
    @Query("SELECT * FROM downloads WHERE url = :url ORDER BY createdAt DESC") fun downloadsFor(url: String): Flow<List<DownloadEntry>>
    @Query("SELECT * FROM downloads WHERE url = :url AND status = 'done' ORDER BY (kind = 'video') DESC, actualHeight DESC LIMIT 1")
    suspend fun doneDownload(url: String): DownloadEntry?
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun download(id: Long): DownloadEntry?
    @Query("SELECT * FROM downloads WHERE status = 'queued' ORDER BY createdAt LIMIT 1") suspend fun nextQueued(): DownloadEntry?
    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'queued'") suspend fun queuedCount(): Int
    @Query("UPDATE downloads SET status = 'queued' WHERE status = 'running'") suspend fun requeueRunning()
    @Insert suspend fun insertDownload(d: DownloadEntry): Long
    @Update suspend fun updateDownload(d: DownloadEntry)
    @Query("UPDATE downloads SET downloadedBytes = :done, totalBytes = :total WHERE id = :id") suspend fun downloadProgress(id: Long, done: Long, total: Long)
    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id") suspend fun downloadStatus(id: Long, status: String, error: String?)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun deleteDownload(id: Long)
}

@Database(
    entities = [Profile::class, SearchEntry::class, HistoryEntry::class, SavedEntry::class,
        Subscription::class, LocalPlaylist::class, LocalPlaylistItem::class, DownloadEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun create(ctx: Context): AppDb =
            Room.databaseBuilder(ctx, AppDb::class.java, "zenvid.db").build()
    }
}
