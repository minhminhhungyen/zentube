package vn.zenvid.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Cài đặt lưu trên máy, đọc được dạng StateFlow để giao diện tự cập nhật. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    inner class Pref<T>(
        private val key: String,
        default: T,
        read: SharedPreferences.(String, T) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> Unit,
    ) {
        private val state = MutableStateFlow(sp.read(key, default))
        val flow: StateFlow<T> get() = state
        var value: T
            get() = state.value
            set(v) {
                state.value = v
                sp.edit { write(key, v) }
            }
    }

    private fun bool(key: String, def: Boolean) =
        Pref(key, def, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

    private fun int(key: String, def: Int) =
        Pref(key, def, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) })

    private fun long(key: String, def: Long) =
        Pref(key, def, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })

    private fun str(key: String, def: String) =
        Pref(key, def, { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })

    /** Tài khoản đang đăng nhập (-1 = chưa đăng nhập). */
    val profileId = long("profile_id", -1L)

    /** "system" | "dark" | "light" */
    val theme = str("theme", "system")

    val qualityWifi = int("quality_wifi", 1080)
    val qualityMobile = int("quality_mobile", 480)
    val preferAvc = bool("prefer_avc", true)
    val backgroundPlay = bool("background_play", true)
    val autoplay = bool("autoplay", true)
    val resume = bool("resume", true)
    val subtitles = bool("subtitles", false)
    val hideShorts = bool("hide_shorts", false)

    val pauseWatchHistory = bool("pause_watch_history", false)
    val pauseSearchHistory = bool("pause_search_history", false)

    val country = str("country", "VN")
    val language = str("language", "vi")

    val askedNotifications = bool("asked_notifications", false)

    /** Kiểm tra bản mới trên GitHub (chỉ khi dự án đã cấu hình kho GitHub). */
    val checkUpdates = bool("check_updates", true)
    val lastUpdateCheck = long("last_update_check", 0L)
    val skippedVersion = str("skipped_version", "")
}
