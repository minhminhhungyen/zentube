package vn.zenvid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.zenvid.Services
import vn.zenvid.data.ChannelItem
import vn.zenvid.data.Entry
import vn.zenvid.data.PageResult
import vn.zenvid.data.PlaylistEntry
import vn.zenvid.data.Profile
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import vn.zenvid.ui.components.CenterLoading
import vn.zenvid.ui.components.ChannelRow
import vn.zenvid.ui.components.ErrorView
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.MessageView
import vn.zenvid.ui.components.OnNearEnd
import vn.zenvid.ui.components.PagedList
import vn.zenvid.ui.components.PlaylistRow
import vn.zenvid.ui.components.ProfileAvatar
import vn.zenvid.ui.components.SmallLoading
import vn.zenvid.ui.components.VideoCard
import vn.zenvid.ui.theme.Brand

class HomeVm : ViewModel() {
    var tab by mutableStateOf("rec")
    private val lists = mutableMapOf<String, PagedList<Entry>>()

    fun list(id: String): PagedList<Entry> = lists.getOrPut(id) {
        PagedList(viewModelScope, { it.url }) {
            if (id == "rec") recommendations() else YouTube.kiosk(id)
        }
    }

    private suspend fun recommendations(): Pair<PageResult<Entry>, Nothing?> {
        val lib = Services.library
        val seeds = lib.recentHistory(6).map { it.url }.toMutableList()
        if (seeds.size < 4) {
            val subs = lib.subscriptionUrls().shuffled().take(5)
            if (subs.isNotEmpty()) {
                seeds += YouTube.subscriptionFeed(subs).items.take(6 - seeds.size).map { it.url }
            }
        }
        // Chưa xem gì: hiện video thịnh hành theo quốc gia (mặc định Việt Nam) thay vì trang trống
        if (seeds.isEmpty()) return PageResult<Entry>(YouTube.starter(), null) to null
        val rec = YouTube.recommendations(seeds, lib.watchedUrls())
        return PageResult<Entry>(rec, null) to null
    }
}

@Composable
fun AppTopBar(profile: Profile, title: @Composable () -> Unit = { Logo() }) {
    val actions = LocalActions.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { title() }
        IconButton(onClick = { actions.openSearch() }) { Icon(Icons.Outlined.Search, "Tìm kiếm") }
        IconButton(onClick = { actions.openSettings() }) { ProfileAvatar(profile.name, profile.color, 28.dp) }
    }
}

@Composable
fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).background(Brand, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp).padding(start = 2.dp)) }
        Spacer(Modifier.width(6.dp))
        Text("zenvid", fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.3).sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(profile: Profile) {
    val vm: HomeVm = viewModel()
    val actions = LocalActions.current
    val hideShorts by Services.prefs.hideShorts.flow.collectAsState()
    val tabs = listOf("rec" to "Đề xuất") + YouTube.kiosks
    val list = vm.list(vm.tab)
    LaunchedEffect(vm.tab) { list.ensureLoaded() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(profile)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs, key = { it.first }) { (id, label) ->
                FilterChip(
                    selected = vm.tab == id,
                    onClick = { vm.tab = id },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = null,
                )
            }
        }

        val state = rememberLazyListState()
        state.OnNearEnd { list.loadMore() }
        val items = list.items.filter { !(hideShorts && it is VideoItem && it.isShort) }

        PullToRefreshBox(isRefreshing = list.refreshing, onRefresh = { list.refresh() }, modifier = Modifier.fillMaxSize()) {
            when {
                !list.loaded && list.loading -> CenterLoading()
                list.error != null && items.isEmpty() -> ErrorView(list.error!!) { list.refresh() }
                list.loaded && items.isEmpty() && vm.tab == "rec" -> MessageView(
                    Icons.Outlined.Explore,
                    title = "Chào ${profile.name}!",
                    message = "Đề xuất được tạo ngay trên máy bạn, dựa trên video bạn đã xem và kênh bạn đăng ký – không gửi đi đâu cả. Hãy tìm và xem vài video để bắt đầu.",
                    actionLabel = "Tìm kiếm video",
                    onAction = { actions.openSearch() },
                )
                else -> LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp)) {
                    items(items, key = { it.url }) { e -> EntryItem(e, big = true) }
                    if (list.hasMore) item { SmallLoading() }
                }
            }
        }
    }
}

/** Hiển thị một mục bất kỳ (video / kênh / danh sách phát). */
@Composable
fun EntryItem(e: Entry, big: Boolean = false) {
    when (e) {
        is VideoItem -> if (big) VideoCard(e) else vn.zenvid.ui.components.VideoRow(e)
        is ChannelItem -> ChannelRow(e)
        is PlaylistEntry -> PlaylistRow(e)
    }
}
