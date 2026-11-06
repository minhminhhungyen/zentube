package vn.zenvid.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.zenvid.Services
import vn.zenvid.data.Prefs
import vn.zenvid.data.Profile
import vn.zenvid.data.YouTube
import vn.zenvid.player.PlayerManager
import vn.zenvid.ui.components.ConfirmDialog
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.ProfileAvatar
import vn.zenvid.ui.components.TextInputDialog

@Composable
fun SettingsScreen(profile: Profile, onSwitchAccount: () -> Unit) {
    val prefs = Services.prefs
    val lib = Services.library
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var dialog by remember { mutableStateOf<String?>(null) }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    // ---- Nhập / sao lưu / khôi phục (bạn tự chọn file, app không tự gửi đi đâu)
    val importSubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val r = runCatching {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
                lib.importSubscriptions(text)
            }
            toast(r.fold({ "Đã nhập $it kênh." }, { "Không đọc được file: ${it.message}" }))
        }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val r = runCatching {
                val json = lib.exportJson()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(json) } }
            }
            toast(r.fold({ "Đã sao lưu." }, { "Sao lưu thất bại: ${it.message}" }))
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val r = runCatching {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
                lib.importJson(text)
            }
            toast(r.fold({ "Đã khôi phục $it mục." }, { "Không khôi phục được: ${it.message}" }))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text("Cài đặt", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(profile.name, profile.color, 48.dp)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        Text("Lịch sử, Xem sau… lưu trên thiết bị này", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Header("Tài khoản") }
            item { Action("Đổi tên hiển thị", profile.name) { dialog = "rename" } }
            item { Action(if (profile.pinHash == null) "Đặt mã PIN" else "Đổi / xoá mã PIN", "Khoá tài khoản bằng mã số khi mở app") { dialog = "pin" } }
            item { Action("Thêm / chuyển người dùng", "Mỗi người trên máy có lịch sử và danh sách riêng", onClick = onSwitchAccount) }
            item { Action("Xoá tài khoản này", "Xoá toàn bộ dữ liệu của ${profile.name} khỏi máy", danger = true) { dialog = "delete" } }

            item { Header("Phát video") }
            item {
                Choice("Chất lượng khi dùng Wi-Fi", "${prefs.qualityWifi.flow.collectAsState().value}p") { dialog = "q_wifi" }
            }
            item {
                Choice("Chất lượng khi dùng dữ liệu di động", "${prefs.qualityMobile.flow.collectAsState().value}p") { dialog = "q_mobile" }
            }
            item { Toggle(prefs.preferAvc, "Ưu tiên định dạng H.264", "Tương thích tốt, đỡ nóng máy. Tắt để ưu tiên VP9 (nét hơn ở cùng dung lượng).") { PlayerManager.reloadSource() } }
            item { Toggle(prefs.backgroundPlay, "Phát nền", "Tiếp tục nghe khi tắt màn hình hoặc chuyển sang app khác") }
            item { Toggle(prefs.autoplay, "Tự động phát video tiếp theo", null) }
            item { Toggle(prefs.resume, "Xem tiếp từ chỗ đã dừng", null) }
            item { Toggle(prefs.subtitles, "Tải phụ đề", "Bật để chọn phụ đề bằng nút CC trên trình phát") { PlayerManager.reloadSource() } }
            item { Toggle(prefs.hideShorts, "Ẩn Shorts", "Không hiện video ngắn ở Trang chủ và Kênh đăng ký") }

            item { Header("Quyền riêng tư") }
            item { Toggle(prefs.pauseWatchHistory, "Tạm dừng lịch sử xem", null) }
            item { Toggle(prefs.pauseSearchHistory, "Tạm dừng lịch sử tìm kiếm", null) }
            item { Action("Xoá lịch sử xem", null, danger = true) { dialog = "clear_watch" } }
            item { Action("Xoá lịch sử tìm kiếm", null, danger = true) { dialog = "clear_search" } }

            item { Header("Nội dung") }
            item { Choice("Quốc gia nội dung", countries.firstOrNull { it.first == prefs.country.flow.collectAsState().value }?.second ?: prefs.country.value) { dialog = "country" } }
            item { Choice("Ngôn ngữ nội dung", languages.firstOrNull { it.first == prefs.language.flow.collectAsState().value }?.second ?: prefs.language.value) { dialog = "language" } }
            item { Choice("Giao diện", themes.first { it.first == prefs.theme.flow.collectAsState().value }.second) { dialog = "theme" } }

            item { Header("Dữ liệu") }
            item {
                Action("Nhập kênh đăng ký từ YouTube", "File subscriptions.csv từ Google Takeout, hoặc file .json xuất từ NewPipe") {
                    importSubs.launch(arrayOf("text/*", "application/json", "application/octet-stream", "*/*"))
                }
            }
            item { Action("Sao lưu dữ liệu", "Lưu lịch sử, danh sách, kênh đăng ký ra file JSON") { backup.launch("zenvid-${profile.name}.json") } }
            item { Action("Khôi phục từ bản sao lưu", "Gộp dữ liệu từ file JSON vào tài khoản này") { restore.launch(arrayOf("application/json", "*/*")) } }

            item { Header("Cập nhật & hỗ trợ") }
            if (Services.updates.enabled) {
                item { Toggle(prefs.checkUpdates, "Tự kiểm tra bản mới", "Mỗi ngày hỏi GitHub một lần xem có phiên bản mới không. Không gửi thông tin gì về bạn.") }
                item {
                    var checking by remember { mutableStateOf(false) }
                    Action(if (checking) "Đang kiểm tra…" else "Kiểm tra bản mới ngay", "Đang dùng bản ${vn.zenvid.BuildConfig.VERSION_NAME}") {
                        if (checking) return@Action
                        checking = true
                        scope.launch {
                            val r = runCatching { Services.updates.check() }
                            checking = false
                            prefs.lastUpdateCheck.value = System.currentTimeMillis()
                            r.onSuccess { info ->
                                if (info == null) toast("Bạn đang dùng bản mới nhất.")
                                else { toast("Có bản mới ${info.version} – đang mở trang tải."); vn.zenvid.ui.openUrl(ctx, info.apkUrl ?: info.pageUrl) }
                            }.onFailure { toast("Không kiểm tra được: ${it.message}") }
                        }
                    }
                }
                item { Action("Báo lỗi / góp ý", "Mở trang Issues của dự án trên GitHub") { vn.zenvid.ui.openUrl(ctx, Services.updates.issuesUrl) } }
                item { Action("Mã nguồn", "Zenvid là phần mềm tự do theo giấy phép GPLv3") { vn.zenvid.ui.openUrl(ctx, Services.updates.releasesUrl.removeSuffix("/releases")) } }
            }

            item { Header("Giới thiệu") }
            item {
                Text(
                    "Zenvid ${vn.zenvid.BuildConfig.VERSION_NAME} – xem YouTube không quảng cáo, không theo dõi.\n\n" +
                        "• Không quảng cáo: video được lấy trực tiếp, không qua trình phát quảng cáo.\n" +
                        "• Không thu thập dữ liệu: không có thư viện thống kê, theo dõi hay báo lỗi tự động; không ID quảng cáo; không cần đăng nhập.\n" +
                        "• Lịch sử, Xem sau, danh sách… chỉ lưu trên máy này và không sao lưu lên đám mây.\n" +
                        "• Đề xuất được tính ngay trên máy từ video bạn đã xem.\n" +
                        "• Khi app bị lỗi, nội dung lỗi chỉ lưu trên máy; bạn tự quyết định có chia sẻ hay không.\n\n" +
                        "App vẫn phải kết nối tới máy chủ YouTube để tải video và ảnh, nên YouTube thấy địa chỉ IP của bạn như khi dùng trình duyệt.\n\n" +
                        "Zenvid không liên quan tới YouTube hay Google. Việc chặn quảng cáo và tải video có thể trái điều khoản dịch vụ của YouTube – bạn tự chịu trách nhiệm khi sử dụng.\n\n" +
                        "Phần mềm tự do theo giấy phép GPLv3. Dùng thư viện NewPipeExtractor (GPLv3).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ------------------------------------------------------------------ hộp thoại
    when (dialog) {
        "rename" -> TextInputDialog("Đổi tên", "Tên hiển thị", profile.name, onDismiss = { dialog = null }) { n ->
            dialog = null; scope.launch { lib.renameProfile(profile, n) }
        }
        "pin" -> PinDialog(profile, onDismiss = { dialog = null })
        "delete" -> ConfirmDialog(
            "Xoá tài khoản ${profile.name}?", "Toàn bộ lịch sử, danh sách phát và kênh đăng ký của tài khoản này sẽ bị xoá vĩnh viễn.", "Xoá",
            onDismiss = { dialog = null },
        ) {
            scope.launch { val id = profile.id; onSwitchAccount(); lib.deleteProfile(id) }
        }
        "clear_watch" -> ConfirmDialog("Xoá lịch sử xem?", "Không thể hoàn tác.", "Xoá", { dialog = null }) { scope.launch { lib.clearHistory(); toast("Đã xoá lịch sử xem.") } }
        "clear_search" -> ConfirmDialog("Xoá lịch sử tìm kiếm?", "Không thể hoàn tác.", "Xoá", { dialog = null }) { scope.launch { lib.clearSearches(); toast("Đã xoá lịch sử tìm kiếm.") } }
        "q_wifi" -> PickDialog("Chất lượng khi dùng Wi-Fi", qualities, prefs.qualityWifi.value, { dialog = null }) { prefs.qualityWifi.value = it }
        "q_mobile" -> PickDialog("Chất lượng khi dùng dữ liệu di động", qualities, prefs.qualityMobile.value, { dialog = null }) { prefs.qualityMobile.value = it }
        "country" -> PickDialog("Quốc gia nội dung", countries, prefs.country.value, { dialog = null }) {
            prefs.country.value = it; YouTube.setRegion(prefs.language.value, it)
        }
        "language" -> PickDialog("Ngôn ngữ nội dung", languages, prefs.language.value, { dialog = null }) {
            prefs.language.value = it; YouTube.setRegion(it, prefs.country.value)
        }
        "theme" -> PickDialog("Giao diện", themes, prefs.theme.value, { dialog = null }) { prefs.theme.value = it }
    }
}

private val qualities = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144).map { it to "${it}p" }
private val countries = listOf("VN" to "Việt Nam", "US" to "Hoa Kỳ", "JP" to "Nhật Bản", "KR" to "Hàn Quốc", "GB" to "Anh", "FR" to "Pháp", "DE" to "Đức", "TH" to "Thái Lan", "SG" to "Singapore", "AU" to "Úc", "CA" to "Canada")
private val languages = listOf("vi" to "Tiếng Việt (tiêu đề gốc)", "en" to "English (bản dịch tiếng Anh nếu có)")
private val themes = listOf("system" to "Theo hệ thống", "dark" to "Tối", "light" to "Sáng")

@Composable
private fun Header(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
    }
}

@Composable
private fun Action(title: String, subtitle: String?, danger: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun Choice(title: String, value: String, onClick: () -> Unit) = Action(title, value, onClick = onClick)

@Composable
private fun Toggle(pref: Prefs.Pref<Boolean>, title: String, subtitle: String?, onChanged: () -> Unit = {}) {
    val v by pref.flow.collectAsState()
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = v, onCheckedChange = { pref.value = it; onChanged() }) },
        modifier = Modifier.clickable { pref.value = !v; onChanged() },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun <T> PickDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(value); onDismiss() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

@Composable
private fun PinDialog(profile: Profile, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val hasPin = profile.pinHash != null

    fun digits(s: String) = s.length <= 8 && s.all(Char::isDigit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPin) "Đổi mã PIN" else "Đặt mã PIN") },
        text = {
            Column {
                if (hasPin) OutlinedTextField(
                    current, { if (digits(it)) current = it }, label = { Text("Mã PIN hiện tại") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    pin, { if (digits(it)) pin = it }, label = { Text(if (hasPin) "Mã PIN mới (để trống = xoá PIN)" else "Mã PIN mới (4–8 số)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val lib = Services.library
                    val ok = !hasPin || withContext(Dispatchers.Default) { lib.checkPin(profile, current) }
                    when {
                        !ok -> error = "Mã PIN hiện tại không đúng."
                        pin.isNotEmpty() && pin.length < 4 -> error = "Mã PIN cần ít nhất 4 số."
                        !hasPin && pin.isEmpty() -> error = "Hãy nhập mã PIN."
                        else -> {
                            withContext(Dispatchers.Default) { lib.setPin(profile, pin.ifEmpty { null }) }
                            Toast.makeText(ctx, if (pin.isEmpty()) "Đã xoá mã PIN." else "Đã lưu mã PIN.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                }
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}
