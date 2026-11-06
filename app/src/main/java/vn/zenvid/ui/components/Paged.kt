package vn.zenvid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import vn.zenvid.data.MoreLoader
import vn.zenvid.data.PageResult
import vn.zenvid.data.YouTube

/** Danh sách tải theo trang (cuộn tới cuối thì tải tiếp). */
class PagedList<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> Any,
    private val initial: suspend () -> Pair<PageResult<T>, MoreLoader<T>?>,
) {
    var items by mutableStateOf<List<T>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var loaded by mutableStateOf(false); private set
    /** Đang tải lại từ đầu (kéo để làm mới) – khác với đang tải trang tiếp theo. */
    var refreshing by mutableStateOf(false); private set
    var hasMore by mutableStateOf(false); private set

    private var next: Page? = null
    private var more: MoreLoader<T>? = null
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        error = null
        loading = true
        refreshing = loaded
        job = scope.launch {
            try {
                val (page, m) = initial()
                items = page.items.distinctBy(key)
                next = page.next
                more = m
                hasMore = next != null && m != null
                loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = YouTube.friendlyError(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun loadMore() {
        val page = next ?: return
        val m = more ?: return
        if (loading) return
        loading = true
        job = scope.launch {
            try {
                val r = m.load(page)
                items = (items + r.items).distinctBy(key)
                next = r.next
                hasMore = next != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                hasMore = false
            } finally {
                loading = false
            }
        }
    }

    fun ensureLoaded() { if (!loaded && !loading && error == null) refresh() }
}

@Composable
fun LazyListState.OnNearEnd(buffer: Int = 6, onNearEnd: () -> Unit) {
    val cb by rememberUpdatedState(onNearEnd)
    LaunchedEffect(this) {
        snapshotFlow {
            val info = layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - buffer
        }.distinctUntilChanged().filter { it }.collect { cb() }
    }
}

@Composable
fun LazyGridState.OnNearEnd(buffer: Int = 6, onNearEnd: () -> Unit) {
    val cb by rememberUpdatedState(onNearEnd)
    LaunchedEffect(this) {
        snapshotFlow {
            val info = layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - buffer
        }.distinctUntilChanged().filter { it }.collect { cb() }
    }
}

@Composable
fun CenterLoading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary) }
}

@Composable
fun SmallLoading() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun ErrorView(message: String, modifier: Modifier = Modifier.fillMaxSize(), onRetry: (() -> Unit)? = null) {
    MessageView(Icons.Outlined.ErrorOutline, message, modifier, actionLabel = onRetry?.let { "Thử lại" }, onAction = onRetry)
}

@Composable
fun MessageView(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
