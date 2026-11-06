package vn.zenvid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.zenvid.Services
import vn.zenvid.data.Profile
import vn.zenvid.ui.components.ProfileAvatar

/** Tạo tài khoản trên máy (tên + mã PIN tuỳ chọn). Không cần email, không gửi đi đâu. */
@Composable
fun CreateProfileScreen(first: Boolean, onCancel: () -> Unit, onCreated: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    if (!first) BackHandler(onBack = onCancel)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!first) Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
        }
        Spacer(Modifier.height(24.dp))
        Logo()
        Spacer(Modifier.height(24.dp))
        Text(if (first) "Tạo tài khoản" else "Thêm tài khoản", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tài khoản chỉ nằm trên máy này: lịch sử, Xem sau, kênh đăng ký… đều lưu riêng cho từng người. Không cần email, không có quảng cáo, không gửi dữ liệu đi đâu.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it; error = null }, label = { Text("Tên hiển thị") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it; error = null },
                label = { Text("Mã PIN (tuỳ chọn, 4–8 số)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            if (pin.isNotEmpty()) OutlinedTextField(
                pin2, { if (it.length <= 8 && it.all(Char::isDigit)) pin2 = it; error = null },
                label = { Text("Nhập lại mã PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                enabled = !busy,
                onClick = {
                    error = when {
                        name.isBlank() -> "Hãy nhập tên."
                        pin.isNotEmpty() && pin.length < 4 -> "Mã PIN cần ít nhất 4 số."
                        pin.isNotEmpty() && pin != pin2 -> "Hai mã PIN không khớp."
                        else -> null
                    }
                    if (error == null) {
                        busy = true
                        scope.launch {
                            val id = withContext(Dispatchers.Default) { Services.library.createProfile(name, pin.ifEmpty { null }) }
                            onCreated(id)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (first) "Bắt đầu" else "Tạo tài khoản") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "  Mã PIN được mã hoá một chiều, không lưu dạng chữ thường.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Màn hình chọn tài khoản (như "Ai đang xem?"). */
@Composable
fun AccountPickerScreen(profiles: List<Profile>, onPick: (Profile) -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Logo()
        Spacer(Modifier.height(24.dp))
        Text("Ai đang xem?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.id }) { p ->
                Surface(onClick = { onPick(p) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(p.name, p.color, 48.dp)
                        Text(p.name, Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                        if (p.pinHash != null) Icon(Icons.Outlined.Lock, "Có mã PIN", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(48.dp).padding(10.dp))
                    Text("Thêm tài khoản", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Nhập mã PIN để đăng nhập. Sai nhiều lần thì phải chờ. */
@Composable
fun PinScreen(profile: Profile, onBack: () -> Unit, onSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    var lockedFor by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(lockedFor) { if (lockedFor > 0) { delay(1000); lockedFor-- } }

    fun submit() {
        if (pin.length < 4 || checking || lockedFor > 0) return
        checking = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) { Services.library.checkPin(profile, pin) }
            checking = false
            if (ok) onSuccess() else {
                attempts++
                pin = ""
                if (attempts >= 5) { lockedFor = 30; attempts = 0 }
                error = "Mã PIN không đúng."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Đổi tài khoản") }
        }
        Spacer(Modifier.height(48.dp))
        ProfileAvatar(profile.name, profile.color, 88.dp)
        Spacer(Modifier.height(16.dp))
        Text(profile.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            pin,
            { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; error = null } },
            label = { Text("Mã PIN") },
            singleLine = true,
            enabled = lockedFor == 0,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))
        when {
            lockedFor > 0 -> Text("Thử lại sau $lockedFor giây.", color = MaterialTheme.colorScheme.error)
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { submit() }, enabled = pin.length >= 4 && lockedFor == 0 && !checking, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().height(48.dp)) {
            Text(if (checking) "Đang kiểm tra…" else "Đăng nhập")
        }
    }
}
