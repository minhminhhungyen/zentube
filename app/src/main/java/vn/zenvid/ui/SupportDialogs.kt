package vn.zenvid.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.zenvid.Services
import vn.zenvid.support.CrashReporter
import vn.zenvid.support.UpdateInfo

fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Khi mở app: báo lỗi lần trước (nếu có) và bản cập nhật mới (nếu bật kiểm tra). */
@Composable
fun SupportDialogs() {
    val ctx = LocalContext.current
    val prefs = Services.prefs
    val updates = Services.updates
    var crash by remember { mutableStateOf(CrashReporter.pending()) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        val due = System.currentTimeMillis() - prefs.lastUpdateCheck.value > 20 * 3600_000L
        if (updates.enabled && prefs.checkUpdates.value && due) {
            runCatching { updates.check() }.onSuccess { info ->
                prefs.lastUpdateCheck.value = System.currentTimeMillis()
                if (info != null && info.version != prefs.skippedVersion.value) update = info
            }
        }
    }

    crash?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Lần trước Zenvid bị lỗi") },
            text = {
                Column {
                    Text(
                        "Thông tin dưới đây chỉ nằm trên máy bạn. Nếu muốn giúp sửa lỗi, hãy chia sẻ cho người phát triển.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SelectionContainer {
                        Text(
                            text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                            modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        .putExtra(Intent.EXTRA_SUBJECT, "Báo lỗi Zenvid")
                    ctx.startActivity(Intent.createChooser(send, "Chia sẻ báo lỗi"))
                    if (updates.enabled) openUrl(ctx, updates.issuesUrl + "/new")
                    CrashReporter.clear(); crash = null
                }) { Text(if (updates.enabled) "Chia sẻ & mở trang báo lỗi" else "Chia sẻ") }
            },
            dismissButton = { TextButton(onClick = { CrashReporter.clear(); crash = null }) { Text("Bỏ qua") } },
        )
    }

    if (crash == null) update?.let { u ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Có bản mới: ${u.version}") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        u.notes.ifBlank { "Bản cập nhật sửa lỗi và cải thiện. YouTube thay đổi thường xuyên nên nên cập nhật để xem video ổn định." },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { openUrl(ctx, u.apkUrl ?: u.pageUrl); update = null }) { Text("Tải bản mới") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { update = null }) { Text("Để sau") }
                    TextButton(onClick = { prefs.skippedVersion.value = u.version; update = null }) { Text("Bỏ qua bản này") }
                }
            },
        )
    }
}
