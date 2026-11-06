package vn.zenvid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.zenvid.Services
import vn.zenvid.data.Entry
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import vn.zenvid.ui.components.CenterLoading
import vn.zenvid.ui.components.ErrorView
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.MessageView
import vn.zenvid.ui.components.OnNearEnd
import vn.zenvid.ui.components.PagedList
import vn.zenvid.ui.components.SmallLoading

class SearchVm : ViewModel() {
    var submitted by mutableStateOf<String?>(null); private set
    var filter by mutableStateOf(YouTube.Filter.ALL); private set
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set
    var correction by mutableStateOf<String?>(null); private set
    var results: PagedList<Entry>? by mutableStateOf(null); private set
    private var suggestJob: Job? = null

    fun submit(q: String, f: String = filter) {
        val query = q.trim()
        if (query.isEmpty()) return
        submitted = query
        filter = f
        correction = null
        viewModelScope.launch { Services.library.addSearch(query) }
        results = PagedList(viewModelScope, { it.url }) {
            val r = YouTube.search(query, f)
            correction = r.suggestion
            r.page to r.more
        }.also { it.refresh() }
    }

    fun changeFilter(f: String) { submitted?.let { submit(it, f) } }

    fun typing(q: String) {
        suggestJob?.cancel()
        if (q.isBlank()) { suggestions = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = YouTube.suggestions(q)
        }
    }

    fun editing() { submitted = null }
}

@Composable
fun SearchScreen(initial: String?) {
    val vm: SearchVm = viewModel()
    val actions = LocalActions.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val history by Services.library.searches.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(TextFieldValue(initial ?: vm.submitted ?: "")) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (initial != null && vm.submitted == null) vm.submit(initial)
        else if (vm.submitted == null) focus.requestFocus()
    }

    fun go(q: String) {
        if (q.isBlank()) return
        // Dán liên kết YouTube vào ô tìm kiếm thì mở thẳng
        if (q.startsWith("http") && YouTube.linkKind(q) != YouTube.LinkKind.NONE) {
            actions.openLink(q); return
        }
        text = TextFieldValue(q, TextRange(q.length))
        keyboard?.hide()
        vm.submit(q)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            TextField(
                value = text,
                onValueChange = { text = it; vm.typing(it.text); if (vm.submitted != null && it.text != vm.submitted) vm.editing() },
                placeholder = { Text("Tìm kiếm trên YouTube") },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                trailingIcon = {
                    if (text.text.isNotEmpty()) IconButton(onClick = { text = TextFieldValue(""); vm.editing(); vm.typing(""); focus.requestFocus() }) {
                        Icon(Icons.Default.Close, "Xoá")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go(text.text) }),
                modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus),
            )
        }

        val results = vm.results
        if (vm.submitted == null || results == null) {
            // Gợi ý: lịch sử tìm kiếm (trên máy) + gợi ý từ YouTube
            val q = text.text.trim()
            val fromHistory = history.filter { q.isEmpty() || it.contains(q, ignoreCase = true) }.take(if (q.isEmpty()) 30 else 5)
            val remote = vm.suggestions.filter { it !in fromHistory }.takeIf { q.isNotEmpty() } ?: emptyList()
            LazyColumn(Modifier.fillMaxSize()) {
                items(fromHistory, key = { "h_$it" }) { s ->
                    SuggestionRow(s, Icons.Outlined.History, onClick = { go(s) }, onFill = { text = TextFieldValue(s, TextRange(s.length)); vm.typing(s) }, onLongClick = { confirmDelete = s })
                }
                items(remote, key = { "r_$it" }) { s ->
                    SuggestionRow(s, Icons.Outlined.Search, onClick = { go(s) }, onFill = { text = TextFieldValue("$s ", TextRange(s.length + 1)); vm.typing(s) })
                }
            }
        } else {
            val filters = listOf(
                YouTube.Filter.ALL to "Tất cả", YouTube.Filter.VIDEOS to "Video",
                YouTube.Filter.CHANNELS to "Kênh", YouTube.Filter.PLAYLISTS to "Danh sách phát",
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { (id, label) ->
                    FilterChip(
                        selected = vm.filter == id, onClick = { vm.changeFilter(id) }, label = { Text(label) }, border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            val state = rememberLazyListState()
            state.OnNearEnd { results.loadMore() }
            when {
                !results.loaded && results.loading -> CenterLoading()
                results.error != null && results.items.isEmpty() -> ErrorView(results.error!!) { results.refresh() }
                results.loaded && results.items.isEmpty() -> MessageView(Icons.Outlined.SearchOff, "Không tìm thấy kết quả nào.")
                else -> LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                    vm.correction?.let { c ->
                        item {
                            Text(
                                "Có phải bạn muốn tìm: $c",
                                Modifier.fillMaxWidth().clickable { go(c) }.padding(16.dp),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    items(results.items, key = { it.url }) { e -> EntryItem(e) }
                    if (results.hasMore) item { SmallLoading() }
                }
            }
        }
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(s) },
            text = { Text("Xoá khỏi lịch sử tìm kiếm?") },
            confirmButton = { TextButton(onClick = { scope.launch { Services.library.deleteSearch(s) }; confirmDelete = null }) { Text("Xoá") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Huỷ") } },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SuggestionRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, onFill: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onFill) { Icon(Icons.AutoMirrored.Filled.CallMade, "Điền", Modifier.rotate(-90f), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
