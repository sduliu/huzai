package com.kudo.android.ui.feed

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.kudo.android.R
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.theme.LogoYellow
import com.kudo.android.ui.theme.LocalDisplaySettings
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

/** 批Nfix-2：官方虎扑品牌红（顶栏「搜索」胶囊底色，与批N-4 HUPU_RED 同值） */
private val HUPU_FEED_SEARCH_RED = Color(0xFFC01E2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(state: FeedState, user: Member?, select: (String) -> Unit, refresh: () -> Unit, more: () -> Unit, openTopic: (Int) -> Unit, openUser: (String) -> Unit, search: () -> Unit, login: () -> Unit, categories: () -> Unit, modifier: Modifier = Modifier, category: Category? = null, back: () -> Unit = {}, listStyle: TopicListStyle = TopicListStyle.FEED, pages: Map<String, FeedState> = emptyMap(), createTopic: (Int) -> Unit = {}, glass: GlassMode = LocalDisplaySettings.current.glass,
    channels: List<Pair<String, String>> = emptyList(), manageChannels: () -> Unit = {},
    channelHeader: (@Composable (key: String, latest: Boolean, onLatestChange: (Boolean) -> Unit) -> Unit)? = null,
    hotContent: (@Composable () -> Unit)? = null,
    prefetch: (String) -> Unit = {}) {
    if (category != null) {
        CategoryFeedScreen(category, state, user, select, refresh, more, openTopic, openUser, search, login, back, modifier, listStyle, pages, createTopic, glass)
        return
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val resetHeader = { scrollBehavior.state.heightOffset = 0f; scrollBehavior.state.contentOffset = 0f }
    val backdrop = rememberLayerBackdrop()
    val surface = MaterialTheme.colorScheme.surface
    val canBlur = glass != GlassMode.TRANSLUCENT && Build.VERSION.SDK_INT >= 31
    val canRefract = glass == GlassMode.LIQUID && Build.VERSION.SDK_INT >= 33
    var headerHeight by remember { mutableIntStateOf(0) }
    val contentTopPadding = with(LocalDensity.current) { headerHeight.toDp() }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scrollBehavior.nestedScrollConnection)) {
        // 首页频道条（官方版式）：热榜 + 推荐 + 用户频道（board:/sport: 键）+ 右端 ☰ 管理
        val tabs = listOf(FeedFilter.HOT.value to FeedFilter.HOT.label,
            FeedFilter.LATEST.value to FeedFilter.LATEST.label) + channels
        key(user?.id, user?.unifiedNew, channels) {
            val pager = rememberPagerState(initialPage = tabs.indexOfFirst { it.first == state.filter }.coerceAtLeast(0), pageCount = { tabs.size })
            val scope = rememberCoroutineScope()
            val currentSelect by rememberUpdatedState(select)
            val currentPrefetch by rememberUpdatedState(prefetch)
            LaunchedEffect(pager) {
                // targetPage 在手势提交翻页的那一刻就更新（不等动画落定）——让 select()
                // 在滑动进行中就开始取数，落定时内容大多已就绪，不再「空页滑入再蹦出」。
                snapshotFlow { pager.targetPage }.distinctUntilChanged().collect { index ->
                    val value = tabs.getOrNull(index)?.first ?: return@collect
                    currentSelect(value)
                }
            }
            LaunchedEffect(pager) {
                var previousPage = pager.settledPage
                snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { index ->
                    if (index != previousPage) {
                        scrollBehavior.state.heightOffset = 0f
                        scrollBehavior.state.contentOffset = 0f
                    }
                    previousPage = index
                    // 落定后预取左右相邻频道：滑过去时缓存已就绪，落地即有内容
                    listOf(index - 1, index + 1).forEach { i ->
                        tabs.getOrNull(i)?.first?.let(currentPrefetch)
                    }
                }
            }
            LaunchedEffect(state.filter) {
                val index = tabs.indexOfFirst { it.first == state.filter }
                if (index >= 0 && pager.currentPage != index && !pager.isScrollInProgress) pager.scrollToPage(index)
            }
            // 赛事频道「资讯 推荐/最新」客户端排序态（按频道键各自记忆）
            val sportLatest = remember { mutableStateMapOf<String, Boolean>() }
            HorizontalPager(pager, Modifier.fillMaxSize().layerBackdrop(backdrop).testTag("home-pager"), key = { tabs[it].first },
                beyondViewportPageCount = 1) { index ->
                val value = tabs[index].first
                if (value == "hot") {
                    Box(Modifier.fillMaxSize().padding(top = contentTopPadding)) { hotContent?.invoke() }
                } else {
                    val page = if (value == state.filter) state else pages[value]?.takeIf { it.accountId == user?.id }
                        ?: FeedState(filter = value, accountId = user?.id, categories = state.categories)
                    val sortLatest = sportLatest[value] == true
                    FeedPage(page, pager.settledPage == index && value == state.filter, resetHeader,
                        refresh, more, openTopic, openUser, login, listStyle,
                        expandHeader = {
                            androidx.compose.animation.core.animate(scrollBehavior.state.heightOffset, 0f) { value, _ -> scrollBehavior.state.heightOffset = value }
                            scrollBehavior.state.contentOffset = 0f
                        }, contentTopPadding = contentTopPadding,
                            sortLatest = sortLatest,
                            sportNews = value.startsWith("sport:"),
                            headerContent = if (value.startsWith("sport:") && channelHeader != null)
                                ({ channelHeader.invoke(value.removePrefix("sport:"), sortLatest) { sportLatest[value] = it } }) else null)
                }
            }
            Column(Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }
                .drawPlainBackdrop(backdrop, shape = { RoundedCornerShape(0.dp) }, effects = {
                    val collapsed = scrollBehavior.state.collapsedFraction
                    if (canBlur && collapsed > 0f) { vibrancy(); blur(4.dp.toPx() * collapsed) }
                    if (canRefract && collapsed > 0f) lens(24.dp.toPx() * collapsed, 24.dp.toPx() * collapsed)
                }, onDrawSurface = {
                    val collapsed = scrollBehavior.state.collapsedFraction
                    drawRect(surface.copy(alpha = 1f - collapsed * if (canBlur) 0.8f else 0.06f))
                }).testTag("home-header").statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    scrollBehavior.state.heightOffsetLimit = -placeable.height.toFloat()
                    val offset = scrollBehavior.state.heightOffset.roundToInt()
                    layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) { placeable.placeRelative(0, offset) }
                }.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(40.dp).clickable(onClick = login), contentAlignment = Alignment.Center) {
                        if (user == null) Image(painterResource(R.drawable.ic_hupu), "虎扑 Logo", Modifier.size(28.dp).clip(CircleShape))
                        else Avatar(user, 28.dp, presentation = AvatarPresentation.ACCOUNT)
                    }
                    Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = search).testTag("home-search"), contentAlignment = Alignment.Center) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 10.dp, end = 3.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp).testTag("home-search-icon"), MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("湿乎乎 · 足球 · 电竞 · 步行街", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // 批Nfix-2：官方红底「搜索」胶囊（品牌红 #C01E2F），原分隔线+灰字样式替换；
                            // 只改样式，入口不变（外层 clickable 仍进 SearchScreen）
                            Box(Modifier.clip(CircleShape).background(HUPU_FEED_SEARCH_RED).testTag("home-search-button").padding(horizontal = 11.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                                Text("搜索", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FeedTabBar(tabs, tabs.getOrNull(pager.currentPage)?.first ?: state.filter, containerColor = Color.Transparent, modifier = Modifier.weight(1f)) { value ->
                        val index = tabs.indexOfFirst { it.first == value }
                        if (index >= 0) scope.launch { pager.animateScrollToPage(index) }
                    }
                    Box(Modifier.size(30.dp).clip(CircleShape).clickable(role = Role.Button, onClick = manageChannels).testTag("home-channel-manage"), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Menu, "全部频道", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedPage(state: FeedState, active: Boolean, scrollToTop: () -> Unit,
    refresh: () -> Unit, more: () -> Unit, openTopic: (Int) -> Unit, openUser: (String) -> Unit,
    login: () -> Unit, listStyle: TopicListStyle, nested: NestedScrollConnection? = null,
    expandHeader: suspend () -> Unit = { scrollToTop() }, contentTopPadding: Dp = 0.dp,
    headerContent: (@Composable () -> Unit)? = null,
    /** 赛事频道「最新」模式：客户端按 bumpedAt 倒序（推荐=服务端原序），置顶保持最前 */
    sortLatest: Boolean = false,
    /** 批T-3：赛事频道资讯区帖行用官方扁平行（左题右图+🔥回复/亮回复），非大卡 */
    sportNews: Boolean = false) {
    val ignored = com.kudo.android.ui.topic.LocalIgnoredUsers.current
    val visibleTopics = state.topics
        .filterNot { it.author?.username?.lowercase(java.util.Locale.ROOT) in ignored }
        .let { l ->
            if (!sortLatest) l else l.sortedWith(
                compareByDescending<Topic> { it.pinned }.thenByDescending { it.bumpedAt })
        }
        val listState = rememberLazyListState()
        val scrollContext = "${state.accountId}:${state.filter}"
        var previousContext by rememberSaveable { mutableStateOf(scrollContext) }
        var consumedScrollRequest by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(scrollContext, state.scrollToTopRequest) {
            if (previousContext != scrollContext || consumedScrollRequest != state.scrollToTopRequest) {
                listState.scrollToItem(0)
                scrollToTop()
                previousContext = scrollContext
                consumedScrollRequest = state.scrollToTopRequest
            }
        }
        val nearEnd by remember { derivedStateOf { val info = listState.layoutInfo; info.totalItemsCount > 3 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3 } }
        LaunchedEffect(active, nearEnd, state.next) { if (active && nearEnd && state.next != null && state.moreError == null) more() }
        val refreshing = state.loading && state.topics.isNotEmpty()
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { if (active) refresh() }, state = pullState,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), indicator = {
                PullToRefreshDefaults.IndicatorBox(state = pullState, isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = contentTopPadding), containerColor = MaterialTheme.colorScheme.surface) {
                    CommunityLoadingIndicator(Modifier.size(16.dp).graphicsLayer {
                        rotationZ = if (refreshing) 0f else pullState.distanceFraction * 180f
                    }.clearAndSetSemantics {
                        if (refreshing) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                        else if (pullState.distanceFraction > 0f) progressBarRangeInfo =
                            ProgressBarRangeInfo(pullState.distanceFraction.coerceIn(0f, 1f), 0f..1f)
                    }, strokeWidth = 2.5.dp, rotating = refreshing)
                }
            }) {
            val traditional = listStyle == TopicListStyle.TITLE
            // 批D：平卡模式列表左右清 0、缝 10dp（官方平卡全宽贴边；sport-header 自带 horizontal=12）
            // 批T-3：sportNews 频道资讯用扁平行（自带行底分隔线）——贴边零间距
            LazyColumn(state = listState, contentPadding = if (traditional || sportNews) PaddingValues(top = contentTopPadding, bottom = 112.dp) else PaddingValues(start = 0.dp, end = 0.dp, top = contentTopPadding + 8.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(if (traditional || sportNews) 0.dp else 10.dp), modifier = Modifier.fillMaxSize().then(if (nested != null) Modifier.nestedScroll(nested) else Modifier).testTag("feed-list-${state.filter}")
                .quickScrollTarget(listState, enabled = active, expandHeader = expandHeader)) {
                if (headerContent != null) item(key = "sport-header", contentType = "header") { headerContent() }
                if (state.loading && state.topics.isEmpty()) item { LoadingRow() }
                if (state.error != null) item { ErrorState(state.error, refresh, login) }
                if (!state.loading && state.error == null && visibleTopics.isEmpty()) item { EmptyState("暂时没有可显示话题", "换个分类，或者稍后再来看看。") }
                items(visibleTopics, key = { "topic-${it.id}" }, contentType = { listStyle }) { topic ->
                    val category = state.categories.firstOrNull { it.id == topic.categoryId }
                    if (sportNews) SportNewsRow(topic) { openTopic(topic.id) }
                    else if (traditional) TopicTitleRow(topic, category, { openTopic(topic.id) }, openUser)
                    else TopicCard(topic, category, { openTopic(topic.id) }, openUser)
                }
                if (state.loadingMore) item { LoadingRow() }
                else if (state.moreError != null) item { ErrorState(state.moreError, more, login) }
                else if (state.next != null) item { TextButton(onClick = more, modifier = Modifier.fillMaxWidth()) { Text("加载更多话题") } }
                else if (state.topics.isNotEmpty()) item { Text("已经看到这里的全部话题", Modifier.fillMaxWidth().padding(14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

}

@Composable
internal fun FeedTabBar(tabs: List<Pair<String, String>>, selected: String, containerColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier, select: (String) -> Unit) {
    BalancedTabRow(tabs.map { it.second }, MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
        modifier.background(containerColor)) { index, slot ->
        val (value, label) = tabs[index]
            key(value) {
                val active = selected == value
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(active) { if (active) bringIntoView.bringIntoView() }
                Box(slot.bringIntoViewRequester(bringIntoView).selectable(active, role = Role.Tab, onClick = { select(value) }).heightIn(min = 34.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(label, Modifier.padding(vertical = 5.dp).drawBehind {
                        if (active) drawRoundRect(
                            brush = Brush.horizontalGradient(listOf(LogoYellow.copy(alpha = 0.25f), LogoYellow.copy(alpha = 0.95f), LogoYellow.copy(alpha = 0.4f))),
                            topLeft = Offset(0f, size.height * 0.58f), size = Size(size.width, size.height * 0.34f),
                            cornerRadius = CornerRadius(3.dp.toPx()))
                    }, fontSize = if (active) 17.sp else 15.sp, lineHeight = if (active) 24.sp else 21.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
    }
}
