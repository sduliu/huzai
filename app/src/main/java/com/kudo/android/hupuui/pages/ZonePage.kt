package com.kudo.android.hupuui.pages

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.kudo.android.hupu.HupuCategory
import com.kudo.android.hupu.HupuHistoryEntry
import com.kudo.android.hupu.HupuPrefs
import com.kudo.android.hupu.HupuFloorReplies
import com.kudo.android.hupu.HupuReply
import com.kudo.android.hupu.HupuThread
import com.kudo.android.hupu.HupuThreadDetail
import com.kudo.android.hupu.HupuRepository
import com.kudo.android.hupu.HupuTopicInfo
import com.kudo.android.hupu.HupuUiSignals
import com.kudo.android.hupu.HupuTopicPage
import com.kudo.android.hupu.pickTopicSortKey
import com.kudo.android.hupu.SortTab
import com.kudo.android.hupuui.components.ErrorRetry
import com.kudo.android.hupuui.components.FeedList
import com.kudo.android.hupuui.components.HupuIcons
import com.kudo.android.hupuui.components.PageHeader
import com.kudo.android.hupuui.glass.FrostedHeaderLayout
import com.kudo.android.hupuui.components.SecondaryPage
import com.kudo.android.hupuui.components.SortBar
import com.kudo.android.hupuui.components.SORT_BAR_TOTAL_HEIGHT
import com.kudo.android.hupuui.components.SkeletonHome
import com.kudo.android.hupuui.components.tapGuard
import com.kudo.android.hupuui.components.TopicFeedState
import com.kudo.android.hupuui.components.normalizeCover
import com.kudo.android.hupuui.components.tabSwipeSwitch
import com.kudo.android.hupuui.components.CenteredTopBar
import com.kudo.android.hupuui.components.LiquidBackButton
import com.kudo.android.hupuui.components.LiquidIconButton
import com.kudo.android.hupuui.components.HuzaiToast
import com.kudo.android.hupuui.components.HupuIconImage

/**
 * 专区页：13 大类 → 版块网格（3 列）→ 版块话题流（盖入式二级页）。
 * 二级页数据外置持有，返回再进不重载。
 */
@Composable
fun ZonePage(modifier: Modifier = Modifier) {
    val repo = remember { HupuRepository() }

    // ---------- 一级页状态 ----------
    var categories by remember { mutableStateOf<List<HupuCategory>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cateRefreshTick by remember { mutableIntStateOf(0) }
    // 1.191: 收藏专区操作的轻提示（顶部胶囊，点击立即消失）
    // 当前选中大类（默认第 1 个；批L-1 起收藏板块走顶层「关注」tab，不再入左栏）
    var selectedCateId by remember { mutableStateOf("") }
    // 批L-1: 收藏板块列表（顶层「关注」tab 数据源，本地 HupuPrefs，匿名可用）
    val favTopics = remember(HupuPrefs.favoriteTopicsVersion) { HupuPrefs.loadFavoriteTopics() }
    // 批L-1：「广场/关注」顶层切换（官方形态）。关注=本地收藏板块（匿名可用，
    // 官方云端关注需登录态的部分不在此实现——等登录批次）。
    var zoneTab by remember { mutableStateOf("plaza") }
    // 批L-1：本页版块搜索盖入页（search2 type=topic，匿名可读）
    var boardSearchOpen by remember { mutableStateOf(false) }
    var boardSearchClosing by remember { mutableStateOf(false) }

    // ---------- 二级页状态（外置，关闭再进不重载） ----------
    var openedTopic by remember { mutableStateOf<HupuTopicInfo?>(null) }
    // 1.192: 本层「已计数」标记——连点多个条目时 enter 只发生一次，防 Tab 栏计数泄漏
    var topicEntered by remember { mutableStateOf(false) }
    var overlayClosing by remember { mutableStateOf(false) }
    val feedStates = remember { mutableStateMapOf<String, TopicFeedState>() }
    // 批L-2: 各版块 JRs 成员数（zoneData.followedUserNum，按 topicId 惰性拉取）
    val zoneMemberTexts = remember { mutableStateMapOf<String, String>() }
    // 各专区的排序 tabs（服务器返回）；1.2xx：用跨会话缓存预热，避免排序条"晚一拍"出现
    val topicSorts = remember {
        mutableStateMapOf<String, List<SortTab>>().apply {
            putAll(HupuPrefs.loadTopicSortTabs())
        }
    }
    var loadingTopicKey by remember { mutableStateOf<String?>(null) }
    /**
     * 1.2xx：最近一次真正展示过的排序条 —— 新话题 tab 未知时先借用它。
     * 多数话题排序分类相同，借来的往往就是最终那一套；真实 tab 到位若相同则排序条不动。
     */
    var lastSortBar by remember { mutableStateOf<List<SortTab>>(emptyList()) }
    LaunchedEffect(openedTopic?.url, openedTopic?.url?.let { topicSorts[it] }) {
        val t = openedTopic?.url?.let { topicSorts[it] }
        if (!t.isNullOrEmpty()) lastSortBar = t
    }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var selectedSort by remember { mutableStateOf<String?>(null) }

    // 大类加载（与首页共享 /all-gambia 缓存，进过首页后秒开）
    LaunchedEffect(cateRefreshTick) {
        // 批Q-1：目录 SWR——冷启动先吃 HupuPrefs 持久化目录立即上屏（不再骨架等网络），
        // 随后原通道（fetchHtml 缓存/网络）后台刷新合回自然替换；
        // 下拉刷新（tick>0）不读缓存直接强制网络。
        if (cateRefreshTick == 0 && categories == null) {
            // 批X9-1：缓存仅接受官方目录形态（首项=推荐聚合组 cateId "z0"）；
            // 旧 web 形态缓存（首项综合体育）不恢复——SWR 一次合回即写入新形态。
            val cached = HupuPrefs.loadZoneCategories()
                .takeIf { it.firstOrNull()?.cateId == "z0" }
            if (!cached.isNullOrEmpty()) {
                categories = cached
                if (cached.none { it.cateId == selectedCateId }) selectedCateId = cached.first().cateId
            }
        }
        loading = cateRefreshTick == 0 && categories == null
        // 1.133: 与首页/评分页一致的容错——空结果递增退避重试，避免首屏偶发失败后长期空态
        // 批X9-1：目录源=m.hupu.com/zone SSR（官方分组/排序/关注数），失败内部回落 web 目录
        var list = repo.zoneDirectory(refresh = cateRefreshTick > 0)
        var tries = 0
        while (list.isEmpty() && tries < 2) {
            tries++
            kotlinx.coroutines.delay(600L * tries)
            list = repo.zoneDirectory(refresh = cateRefreshTick > 0)
        }
        if (list.isNotEmpty()) {
            categories = list
            // 批L-1: 收藏改走顶层「关注」tab——选中项只在服务器大类里保持/回落
            if (list.none { it.cateId == selectedCateId }) {
                selectedCateId = list.first().cateId
            }
        }
        loading = false
    }

    /** 关注/取关一个板块：写 HupuPrefs（同步首页频道），轻提示 */
    fun toggleFav(t: HupuTopicInfo) {
        val r = HupuPrefs.toggleFavoriteTopic(t)
        com.kudo.android.hupuui.components.HuzaiToast.show(
            when (r) {
                HupuPrefs.FavoriteToggle.ADDED -> "已关注「${t.name}」"
                HupuPrefs.FavoriteToggle.REMOVED -> "已取消关注"
                HupuPrefs.FavoriteToggle.LIMIT ->
                    "关注板块已达上限（${HupuPrefs.MAX_FAVORITE_TOPICS}）"
            }
        )
    }

    fun openTopic(t: HupuTopicInfo) {
        // 1.2xx：应用「专区默认排序」（按标题匹配已缓存的排序 tab；未配置/未命中 → 服务器默认）
        selectedSort = pickTopicSortKey(
            topicSorts[t.url] ?: emptyList(),
            HupuPrefs.loadDefaultTopicSortTitle(),
        )
        overlayClosing = false
        if (!topicEntered) { topicEntered = true; SecondaryPage.enter() }
        openedTopic = t
    }

    fun closeTopic() {
        // 先播返回动画（overlay 滑回右侧），动画结束后真正移除
        overlayClosing = true
    }

    // 批N-2：跨页「版块名 >」导航——详情页置信号 → KudoApp 切到本 Tab → 这里消费开版块流
    LaunchedEffect(HupuUiSignals.zoneBoardRequest) {
        val t = HupuUiSignals.consumeZoneBoard() ?: return@LaunchedEffect
        openTopic(t)
    }

    // ---------- 帖子详情三级页（盖入式；从话题流内点帖进入） ----------
    var openedThread by remember { mutableStateOf<HupuThread?>(null) }
    // 1.192: 本层「已计数」标记——连点多个条目时 enter 只发生一次，防 Tab 栏计数泄漏
    var threadEntered by remember { mutableStateOf(false) }
    var threadClosing by remember { mutableStateOf(false) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadLoadingMore by remember { mutableStateOf(false) }
    var threadSortSeq by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val threadDetails = remember { mutableStateMapOf<String, HupuThreadDetail>() }

    // ---------- 楼中楼（数据外置缓存；打开瞬间同步置 loading） ----------
    val floorStates = remember { mutableStateMapOf<String, HupuFloorReplies>() }
    val floorLoading = remember { mutableStateMapOf<String, Boolean>() }
    val floorLoadingMore = remember { mutableStateMapOf<String, Boolean>() }

    fun openThread(t: HupuThread) {
        HupuPrefs.addHistory(
            HupuHistoryEntry(
                tid = t.tid,
                title = t.title,
                topicName = t.topic?.name ?: "",
                lights = t.lights,
                replies = t.replies,
                read = t.read,
                visitedAt = System.currentTimeMillis(),
            )
        )
        threadClosing = false
        if (!threadEntered) { threadEntered = true; SecondaryPage.enter() }
        openedThread = t
        // 同步置位（频道页同款）：无缓存→骨架屏第一帧
        threadLoading = !threadDetails.containsKey(t.tid)
    }

    fun closeThread() {
        threadClosing = true
    }

    // 打开的帖子：无缓存则拉取（错峰到盖入动画完成后）
    // 1.123: 详情页时效性——每次进入都强刷；有缓存秒开第一帧，新数据到达后无缝替换
    LaunchedEffect(openedThread?.tid) {
        val t = openedThread ?: return@LaunchedEffect
        val tid = t.tid
        val mySeq = threadSortSeq
        if (threadDetails.containsKey(tid)) {
            val old = threadDetails[tid]
            val fresh = repo.threadDetail(tid, refresh = true)
            if (fresh != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                threadDetails[tid] = repo.mergeThreadDetail(old, fresh)
            }
        } else {
            threadLoading = true
            val cached = repo.threadDetailCached(tid)
            if (cached != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                threadDetails[tid] = cached
                threadLoading = false
                val fresh = repo.threadDetail(tid, refresh = true)
                if (fresh != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                    threadDetails[tid] = repo.mergeThreadDetail(threadDetails[tid], fresh)
                }
            } else {
                delay(300)
                if (threadDetails.containsKey(tid)) return@LaunchedEffect
                val d = repo.threadDetail(tid)
                if (d != null && mySeq == threadSortSeq) threadDetails[tid] = d
                if (openedThread?.tid == tid) threadLoading = false
            }
        }
    }

    // 批Nfix-4：详情盖入期间隐藏主导航药丸（详情通栏底栏与药丸同底边会重叠；
    // 官方详情为推入页本无底栏导航）。本可组合销毁时兜底复位。
    LaunchedEffect(openedThread != null) {
        HupuUiSignals.zoneDetailOpen = openedThread != null
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { HupuUiSignals.zoneDetailOpen = false }
    }

    val scope = rememberCoroutineScope()
    val current = categories?.find { it.cateId == selectedCateId }

    fun openFloor(tid: String, r: HupuReply) {
        if (floorStates.containsKey(r.pid)) return
        floorLoading[r.pid] = true
        scope.launch {
            delay(300)
            if (floorStates.containsKey(r.pid)) {
                floorLoading[r.pid] = false
                return@launch
            }
            val fr = repo.floorReplies(tid, r)
            if (fr != null) floorStates[r.pid] = fr
            floorLoading[r.pid] = false
        }
    }

    fun loadFloorMore(tid: String, pid: String, fr: HupuFloorReplies) {
        if (floorLoadingMore[pid] == true || !fr.hasMore) return
        val lastPid = fr.subReplies.lastOrNull { it.depth == 0 }?.pid ?: return
        floorLoadingMore[pid] = true
        scope.launch {
            val next = repo.floorReplies(tid, HupuReply(pid = pid), maxpid = lastPid)
            val cur = floorStates[pid]
            if (next != null && cur != null) {
                val merged = (cur.subReplies + next.subReplies).distinctBy { it.pid }
                floorStates[pid] = cur.copy(subReplies = merged, hasMore = next.hasMore)
            }
            floorLoadingMore[pid] = false
        }
    }
    val opened = openedTopic

    // 1.186: 顶部大类 Tab 左右滑动切换（仅本大页面内；批L-1 起收藏走「关注」tab 不入滑轨）
    val cateIds = remember(categories) {
        categories?.map { it.cateId } ?: emptyList()
    }
    fun swipeCate(delta: Int) {
        val cur = cateIds.indexOf(selectedCateId)
        if (cur < 0) return
        val ni = cur + delta
        if (ni !in cateIds.indices) return
        selectedCateId = cateIds[ni]
    }

    Box(modifier.fillMaxSize()) {
        // ---------- 一级页 ----------
        // 1.198：顶栏浮空（可选效果，默认关；关闭时退回原形态，零额外开销）
        FrostedHeaderLayout(
            modifier = Modifier
                .fillMaxSize()
                .tabSwipeSwitch(
                    onPrevious = { swipeCate(-1) },
                    onNext = { swipeCate(1) },
                ),
            header = {
                PageHeader(title = "专区") {
                    // 批L-1：本页版块搜索入口（官方专区页同款位置）
                    LiquidIconButton(
                        icon = HupuIcons.SearchOutlined,
                        contentDescription = "搜索版块",
                        onClick = { boardSearchOpen = true },
                        iconSize = 24.dp,
                    )
                }
                // 批L-1：官方「广场/关注」切换（关注=本地收藏板块，匿名可用）
                SortBar(
                    sorts = listOf(
                        SortTab(id = 0, title = "广场", url = "plaza"),
                        SortTab(id = 1, title = "关注", url = "follow"),
                    ),
                    selected = zoneTab,
                ) { zoneTab = it }
            },
        ) { topInset ->
            when {
                // 1.198：骨架也从浮空栏下开始（否则首行骨架块会被顶栏盖住）
                categories == null && loading -> SkeletonHome(Modifier.padding(top = topInset))
                categories == null -> ErrorRetry { cateRefreshTick++ }
                categories!!.isEmpty() -> ErrorRetry { cateRefreshTick++ }
                zoneTab == "follow" -> {
                    // 批L-1: 「关注」页=本地收藏板块全宽网格（无左栏；官方云端关注需登录态）
                    if (favTopics.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(top = topInset), contentAlignment = Alignment.Center) {
                            Text(
                                "还没有关注板块\n在广场里点亮版块右上角的星标即可关注",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(
                                start = 10.dp, end = 10.dp,
                                top = topInset + 10.dp, bottom = 140.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            gridItems(favTopics, key = { it.topicId + it.url }) { t ->
                                TopicCell(t,
                                    isFavorite = true,
                                    onClick = { openTopic(t) },
                                    onToggleFavorite = { toggleFav(t) },
                                )
                            }
                        }
                    }
                }
                else -> {
                    // 官方布局：左栏分类竖排导航（灰底）+ 右栏版块网格
                    Row(Modifier.fillMaxSize().padding(top = topInset)) {
                        CateRail(
                            categories = categories!!,
                            selected = selectedCateId,
                            onSelect = { selectedCateId = it },
                            showFavorites = false,
                            onSelectFavorites = {},
                        )
                        val topics = current?.topics.orEmpty()
                        if (topics.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "该分类暂无版块",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 140.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            ) {
                                gridItems(topics, key = { it.topicId + it.url }) { t ->
                                    TopicCell(t,
                                        isFavorite = favTopics.any { it.url == t.url },
                                        onClick = { openTopic(t) },
                                        onToggleFavorite = { toggleFav(t) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1.191: 收藏专区操作反馈（顶部胶囊，点击立即消失）
        // ---------- 二级页：版块话题流（盖入式转场） ----------
        if (opened != null) {
            // 1.192: 本层离开组合时兜底回收计数（正常退场已由 onExitStart 提前回收）
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { if (topicEntered) { topicEntered = false; SecondaryPage.exit() } }
            }
            TopicFeedOverlay(
                topic = opened,
                isFavorite = favTopics.any { it.url == opened.url },
                onToggleFavorite = { toggleFav(opened) },
                closing = overlayClosing,
                feedStates = feedStates,
                topicSorts = topicSorts,
                fallbackSorts = lastSortBar,
                loadingTopicKey = loadingTopicKey,
                refreshVersion = refreshVersion,
                selectedSort = selectedSort,
                onSelectSort = { selectedSort = it },
                // 批L-2: JRs 成员数（zoneData 惰性补充，拉过一次不重拉）
                memberText = zoneMemberTexts[opened.topicId].orEmpty(),
                onLoadZoneInfo = { id ->
                    if (!zoneMemberTexts.containsKey(id)) {
                        scope.launch {
                            val z = repo.zoneInfo(id)
                            if (z != null && z.memberText.isNotBlank()) {
                                zoneMemberTexts[id] = z.memberText
                            }
                        }
                    }
                },
                onBack = { closeTopic() },
                onExitStart = { if (topicEntered) { topicEntered = false; SecondaryPage.exit() } },
                onClosed = {
                    overlayClosing = false
                    selectedSort = null
                    openedTopic = null
                },
                onFirstLoad = { url, sort ->
                    // 首次进入或切排序：无缓存才加载
                    val key = "$url#${sort ?: url}"
                    if (feedStates.containsKey(key)) return@TopicFeedOverlay
                    scope.launch {
                        loadingTopicKey = key
                        val p = topicFeed(repo, sort ?: url, 1)
                        if (p != null) {
                            // 1.2xx：发现「有默认排序要用」时**不落地**这份默认排序的内容 ——
                            // 否则 Crossfade 会拿它当退场层，先闪一下「最新回复」列表再切成目标排序。
                            val want = if (selectedSort == null) {
                                pickTopicSortKey(p.sortTabs, HupuPrefs.loadDefaultTopicSortTitle())
                            } else {
                                null
                            }
                            if (want != null) {
                                topicSorts[url] = p.sortTabs
                                HupuPrefs.saveTopicSortTabs(url, p.sortTabs)
                                loadingTopicKey = null
                                selectedSort = want // 键变化 → 触发目标排序自己加载
                                return@launch
                            }
                            feedStates[key] = TopicFeedState(
                                threads = p.threads,
                                page = p.page,
                                totalPages = p.totalPages,
                                topicInfo = p.topic,
                                boardInfo = p.boardInfo,
                            )
                            topicSorts[url] = p.sortTabs
                            HupuPrefs.saveTopicSortTabs(url, p.sortTabs)
                        } else {
                            feedStates[key] = TopicFeedState() // 空=失败
                        }
                        loadingTopicKey = null
                    }
                },
                onRefresh = {
                    val url = opened.url
                    val sort = selectedSort
                    val key = "$url#${sort ?: url}"
                    scope.launch {
                        loadingTopicKey = key
                        val p = topicFeed(repo, sort ?: url, 1, refresh = true)
                        if (p != null) {
                            feedStates[key] = TopicFeedState(
                                threads = p.threads,
                                page = p.page,
                                totalPages = p.totalPages,
                                topicInfo = p.topic,
                                boardInfo = p.boardInfo,
                            )
                            topicSorts[url] = p.sortTabs
                            refreshVersion++
                        }
                        loadingTopicKey = null
                    }
                },
                onLoadMore = { url, sort, page ->
                    scope.launch {
                        val key = "$url#${sort ?: url}"
                        val state = feedStates[key] ?: return@launch
                        if (state.loadingMore || state.page >= state.totalPages) return@launch
                        feedStates[key] = state.copy(loadingMore = true)
                        val p = topicFeed(repo, sort ?: url, state.page + 1)
                        val cur = feedStates[key]
                        if (p != null && cur != null) {
                            val merged = (cur.threads + p.threads).distinctBy { it.tid }
                            feedStates[key] = cur.copy(
                                threads = merged,
                                page = p.page,
                                totalPages = p.totalPages,
                                loadingMore = false,
                            )
                        } else if (cur != null) {
                            feedStates[key] = cur.copy(loadingMore = false)
                        }
                    }
                },
                onOpenThread = { openThread(it) },
            )
        }

        // ---------- 帖子详情三级页：盖入式转场（盖过话题流二级页） ----------
        val ot = openedThread
        if (ot != null) {
            // 1.192: 本层离开组合时兜底回收计数（正常退场已由 onExitStart 提前回收）
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { if (threadEntered) { threadEntered = false; SecondaryPage.exit() } }
            }
            val d = threadDetails[ot.tid]
            ThreadDetailOverlay(
                tid = ot.tid,
                titleText = ot.title,
                detail = d,
                loading = threadLoading,
                loadingMore = threadLoadingMore,
                closing = threadClosing,
                onBack = { closeThread() },
                onExitStart = { if (threadEntered) { threadEntered = false; SecondaryPage.exit() } },
                onClosed = {
                    threadClosing = false
                    openedThread = null
                },
                onRefresh = {
                    val mySeq = threadSortSeq
                    scope.launch {
                        threadLoading = true
                        val cur = threadDetails[ot.tid]
                        val desc = cur?.descReplies == true
                        val nd = repo.threadDetailDirected(ot.tid, desc, refresh = true)
                        if (nd != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) threadDetails[ot.tid] = if (desc) nd else repo.mergeThreadDetail(cur, nd)
                        threadLoading = false
                    }
                },
                onLoadMore = {
                    val cur = threadDetails[ot.tid] ?: return@ThreadDetailOverlay
                    if (threadLoadingMore || !repo.hasMoreReplies(cur)) return@ThreadDetailOverlay
                    val mySeq = threadSortSeq
                    scope.launch {
                        threadLoadingMore = true
                        val next = repo.threadRepliesNext(ot.tid, cur)
                        if (next != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) {
                            val merged = (cur.replies + next.replies).distinctBy { it.pid }
                            threadDetails[ot.tid] = next.copy(replies = merged)
                        }
                        threadLoadingMore = false
                    }
                },
                onSortChanged = { desc ->
                    // 1.185c: 每次意图推进序号——迟到的旧响应据此被丢弃（连点不再来回闪）
                    val mySeq = ++threadSortSeq
                    val cur = threadDetails[ot.tid]
                    if (cur != null && cur.descReplies != desc) {
                        scope.launch {
                            val d = repo.repliesDirected(ot.tid, cur, desc)
                            if (d != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) threadDetails[ot.tid] = d
                        }
                    }
                },
                floorStates = floorStates,
                floorLoading = floorLoading.filterValues { it }.keys,
                floorLoadingMore = floorLoadingMore.filterValues { it }.keys,
                 onOpenFloor = { r -> openFloor(ot.tid, r) },
                 onFloorLoadMore = { pid, fr -> loadFloorMore(ot.tid, pid, fr) },
             )
        }

        // ---------- 批L-1：版块搜索盖入页（盖过话题流/详情页） ----------
        if (boardSearchOpen) {
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { boardSearchClosing = false }
            }
            ZoneSearchOverlay(
                closing = boardSearchClosing,
                categories = categories,
                favTopics = favTopics,
                onBack = { boardSearchClosing = true },
                onClosed = {
                    boardSearchClosing = false
                    boardSearchOpen = false
                },
                onOpenTopic = { t ->
                    boardSearchClosing = true
                    openTopic(t)
                },
            )
        }
    }
}

/**
 * 二级页：版块话题流。
 * 转场：盖入式——进入时从右侧滑入（下层完全静止），返回时旧页滑回右侧后移除。
 */
@Composable
private fun TopicFeedOverlay(
    topic: HupuTopicInfo,
    /** 1.191: 该专区是否已收藏（右上角收藏按钮的选中态） */
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    closing: Boolean,
    feedStates: Map<String, TopicFeedState>,
    topicSorts: Map<String, List<SortTab>>,
    /** 1.2xx：本话题排序 tab 未知时借用的「上一个话题的排序条」（多数话题分类相同） */
    fallbackSorts: List<SortTab>,
    loadingTopicKey: String?,
    refreshVersion: Int,
    selectedSort: String?,
    onSelectSort: (String) -> Unit,
    /** 批L-2：JRs 成员数（zoneData.followedUserNum）；空串=未取到/无数据不显示 */
    memberText: String = "",
    /** 批L-2：按 topicId 惰性补充成员数（父级缓存，拉过不重拉） */
    onLoadZoneInfo: (String) -> Unit = {},
    onBack: () -> Unit,
    /** 1.183: 退场动画开始即回调——父级据此同步减二级页计数，使 Tab 栏与页面同步 Q 弹回归（与「我的」页二级页同款手感） */
    onExitStart: () -> Unit = {},
    onClosed: () -> Unit,
    onFirstLoad: (url: String, sort: String?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: (url: String, sort: String?, page: Int) -> Unit,
    onOpenThread: (HupuThread) -> Unit,
) {
    // 盖入动画：进入 0→1；返回动画结束后由父级移除本组件
    val progress = remember { Animatable(0f) }
    // 1.223q: 入场动画按**话题 url** 重播 —— 实例被复用（A 退场中点开 B）时不重播，
    // progress 会停在退场中途的接近 0 值，新话题被画成几乎不可见（表现为「点了没反应」）。
    LaunchedEffect(closing) {
    if (closing) {
            onExitStart()
            progress.animateTo(0f, tween(280))
            onClosed()
        } else {
        // 1.223r: 退场途中再次打开（含同一帖子）→ 重播入场，避免「看不见」
        progress.snapTo(0f)
        progress.animateTo(1f, tween(280))
        }
    }
    // 系统返回手势：预测性返回（单一 handler——多个 handler 共存时后组合者永远优先）。
    // - 退场动画播放中：吞掉手势（防止漏到系统直接退出应用）
    // - 其余：页面跟随手指实时滑出（progress: 0→1 跟手），松手未过阈值弹回、过阈值退出
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { } // 吞掉
            return@PredictiveBackHandler
        }
        try {
            events.collect { event ->
                // 手势进度实时驱动盖出动画（页面跟手右滑）
                progress.snapTo(1f - event.progress)
            }
            // 越过系统提交阈值 → 播完剩余动画并退出
            progress.animateTo(0f, tween(120))
            onBack()
        } catch (e: CancellationException) {
            // 未过阈值松手 → 弹回原位
            progress.animateTo(1f, tween(200))
            throw e
        }
    }

    val key = "${topic.url}#${selectedSort ?: topic.url}"
    val state = feedStates[key]
    val sorts = topicSorts[topic.url]

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer {
                translationX = (1f - progress.value) * size.width
            }
            .background(MaterialTheme.colorScheme.background)
            // 穿透守卫：排序条空隙/失败态空白点击由本层兜底消费，不落穿到下层
            .tapGuard(),
    ) {
        // 1.198：顶栏浮空（可选效果，默认关；关闭时退回原有观感）
        FrostedHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            // ---------- 顶栏（浮空：标题行 + 排序条） ----------
            header = {
            // 顶栏：返回 + 版块名 + 热度
            CenteredTopBar(
                title = topic.name,
                onBack = { onBack() },
                // 副标题随热度有无变化；没有热度时传 null（组件会自动省略副标题）
                subtitle = if (topic.hotText.isNotBlank()) "${topic.hotText}热度" else null,
                actions = {
                    // 1.191: 收藏该专区
                    LiquidIconButton(
                        icon = HupuIcons.StarRate,
                        contentDescription = if (isFavorite) "取消关注" else "关注板块",
                        onClick = { onToggleFavorite() },
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            // 排序子 Tab（服务器返回）
            // 1.2xx：真实 tab 未知时先借用上一个话题的排序条（多数话题分类相同 → 几乎零跳变）；
            // 借用期忽略点击（借来的 url 属于上一个话题，用了会请求错页面）
            val realTabs = sorts?.takeIf { it.isNotEmpty() }
            val barTabs = realTabs ?: fallbackSorts
            if (barTabs.isNotEmpty()) {
                val barSel = if (realTabs != null) {
                    selectedSort ?: topic.url
                } else {
                    pickTopicSortKey(barTabs, HupuPrefs.loadDefaultTopicSortTitle())
                        ?: barTabs.firstOrNull()?.url.orEmpty()
                }
                SortBar(barTabs, barSel) { if (realTabs != null) onSelectSort(it) }
            } else {
                // 无任何可借用的排序条 → 占位同高，避免整行消失导致列表上移再弹回
                Spacer(Modifier.height(SORT_BAR_TOTAL_HEIGHT))
            }
            },
        ) { topInset ->
        Column(Modifier.fillMaxSize()) {
            // 列表区：排序切换用 Crossfade 防闪（每层用自己的目标态，不被污染）
            androidx.compose.animation.Crossfade(
                targetState = selectedSort,
                animationSpec = tween(180),
                label = "zoneFeedSwitch",
            ) { sel ->
                val selKey = "${topic.url}#${sel ?: topic.url}"
                val selState = feedStates[selKey]
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = loadingTopicKey == selKey,
                    onRefresh = onRefresh,
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    // 1.198：指示器落在浮空顶栏下方（关闭该效果时为 0，位置不变）
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = loadingTopicKey == selKey,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = topInset),
                        )
                    },
                ) {
                    when {
                        selState == null -> SkeletonHome(Modifier.padding(top = topInset))
                        selState.threads.isEmpty() -> ErrorRetry { onRefresh() }
                        else -> FeedList(
                            threads = selState.threads,
                            useBigCards = false,
                            flatRows = true,
                            headerContent = {
                                BoardProfileHeader(
                                    topic = selState.topicInfo ?: topic,
                                    isFavorite = isFavorite,
                                    onToggleFavorite = onToggleFavorite,
                                    memberText = memberText,
                                    boardInfo = selState.boardInfo,
                                )
                            },
                            canLoadMore = selState.page < selState.totalPages,
                            loadingMore = selState.loadingMore,
                            onLoadMore = { onLoadMore(topic.url, sel, selState.page) },
                            resetKey = "$selKey-$refreshVersion",
                            topInset = topInset,
                            // 批L-3：末行避让「立即发布」FAB 与全局导航药丸叠层
                            bottomInset =
                                com.kudo.android.ui.components.LocalQuickScrollBottomPadding.current + 72.dp,
                            onOpenThread = onOpenThread,
                        )
                    }
                }
            }
        }
        }
        // 官方版块页红色「立即发布」FAB——发帖需登录；未登录点击提示去「我的」页登录
        // 批L-3：底距避让全局导航药丸（原 24dp 时 FAB 下半被药丸遮住一半）
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 18.dp,
                    bottom = com.kudo.android.ui.components.LocalQuickScrollBottomPadding.current + 8.dp,
                )
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFE60023))
                .clickable {
                    if (com.kudo.android.hupu.HupuAccount.isLoggedIn) {
                        HuzaiToast.show("发帖暂未开放，敬请期待")
                    } else {
                        HuzaiToast.show("请先在「我的」页登录后再发帖")
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("立即发布", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
    // 首次进入 / 切换排序：无缓存才加载
    LaunchedEffect(topic.url, selectedSort) {
        val key = "${topic.url}#${selectedSort ?: topic.url}"
        if (!feedStates.containsKey(key)) onFirstLoad(topic.url, selectedSort)
    }
    // 批L-2：进入即惰性补充 JRs 成员数（zoneData 独立接口；topicId 为空串时跳过）
    LaunchedEffect(topic.topicId) {
        if (topic.topicId.isNotBlank() && memberText.isBlank()) onLoadZoneInfo(topic.topicId)
    }
}

/** 官方版块页档案头：logo + 名称 + 「N 帖子」+ 简介 + 红色关注胶囊钮。数据取自话题页 SSR（topicInfo）。 */
@Composable
private fun BoardProfileHeader(
    topic: HupuTopicInfo,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    /** 批L-2：JRs 成员数（zoneData.followedUserNum），空串不显示 */
    memberText: String = "",
    /** 批L-2：SSR 专区信息（版主团队 + 招募标记），空对象不显示 */
    boardInfo: com.kudo.android.hupu.HupuBoardInfo = com.kudo.android.hupu.HupuBoardInfo(),
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HupuIconImage(
                model = normalizeCover(topic.logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(topic.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                val meta = buildList {
                    if (topic.hotText.isNotBlank()) add("${topic.hotText} 帖子")
                    if (memberText.isNotBlank()) add("$memberText JRs")
                    if (topic.desc.isNotBlank()) add(topic.desc)
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isFavorite) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE60023))
                    .clickable { onToggleFavorite() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    if (isFavorite) "已关注" else "关注",
                    color = if (isFavorite) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        // 批L-2: 官方「专区信息」行——版主头像组 + 招募版主标记（SSR admins 节点，匿名可读）
        if (boardInfo.admins.isNotEmpty() || boardInfo.adminApplicable == 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                boardInfo.admins.take(6).forEach { a ->
                    HupuIconImage(
                        model = normalizeCover(a.header),
                        contentDescription = "${a.roleName} ${a.userName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Text(
                    boardInfo.admins.joinToString("、") { it.userName }.let {
                        if (boardInfo.admins.isNotEmpty()) "版主：$it" else ""
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (boardInfo.adminApplicable == 1) {
                    Text(
                        "招募版主",
                        fontSize = 11.sp,
                        color = Color(0xFFE60023),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE60023).copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** 1.191: 「收藏专区」虚拟 tab 的 id（不是真实大类，仅本页内使用） */
private const val FAV_CATE_ID = "__fav_topics__"

/** 官方式左栏分类导航：竖排条目，选中项白底+左红条，跟随选中自动滚动。 */
@Composable
private fun CateRail(
    categories: List<HupuCategory>,
    selected: String,
    onSelect: (String) -> Unit,
    /** 1.191: 有收藏专区时，最前面插一个「关注」项（默认选中） */
    showFavorites: Boolean = false,
    onSelectFavorites: () -> Unit = {},
) {
    val railState = rememberLazyListState()
    val allIds = if (showFavorites) listOf(FAV_CATE_ID) + categories.map { it.cateId }
                 else categories.map { it.cateId }
    val selIdx = allIds.indexOf(selected)
    LaunchedEffect(selected, showFavorites) { if (selIdx >= 0) railState.animateScrollToItem(selIdx) }
    androidx.compose.foundation.lazy.LazyColumn(
        state = railState,
        modifier = Modifier
            .width(92.dp)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        if (showFavorites) {
            item(key = FAV_CATE_ID) {
                RailItem("关注", selected == FAV_CATE_ID, onSelectFavorites)
            }
        }
        items(categories, key = { it.cateId }) { c ->
            RailItem(c.name, selected == c.cateId) { onSelect(c.cateId) }
        }
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp)
            .background(if (selected) MaterialTheme.colorScheme.background else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(3.dp).height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Color(0xFFE60023) else Color.Transparent)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 话题/板块统一取数：话题页解析失败回落板块页。
 * 两类页面并存：专区话题页（parseTopicPage，有分页）/老式板块页（parseBoardPage，无分页），
 * parseTopicPage 对板块页结构返回 null；板块页无分页——page>1 不回落，直接视为没有更多。
 */
private suspend fun topicFeed(
    repo: HupuRepository,
    url: String,
    page: Int,
    refresh: Boolean = false,
): HupuTopicPage? {
    repo.topicPage(url, page, refresh)?.let { return it }
    if (page > 1) return null
    val b = repo.board(url, refresh) ?: return null
    return HupuTopicPage(
        topic = b.category ?: HupuTopicInfo(topicId = "", name = "", url = url),
        threads = b.threads,
        page = 1,
        totalPages = 1,
        sortTabs = emptyList(),
    )
}

/** 版块格子：圆形 logo + 名称 + 热度；右上角星标即「关注」（同步首页频道与综合过滤） */
@Composable
private fun TopicCell(t: HupuTopicInfo, isFavorite: Boolean, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HupuIconImage(
                model = normalizeCover(t.logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                t.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            // 批X9-2：指标官方化——关注数「👍N万」（m 站 zone topicList.count 真实字段）；
            // 字段缺失（英雄联盟等 m 站未下发版块）回落既有 hotText 热度文案，不换算冒充
            if (t.followText.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        HupuIcons.Light,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        t.followText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (t.hotText.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${t.hotText} 热度",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                HupuIcons.StarRate,
                contentDescription = if (isFavorite) "取消关注" else "关注",
                modifier = Modifier.size(18.dp),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * 批L-1：专区版块搜索盖入页（官方专区页搜索入口的对应物）。
 * 数据走 m.hupu.com/api/v2/search2 type=topic（匿名可读）；
 * 结果点击 → 优先按 topicId 映射回本地类目 url，否则用 /{topicId} 数字路径兜底。
 */
@Composable
private fun ZoneSearchOverlay(
    closing: Boolean,
    categories: List<HupuCategory>?,
    favTopics: List<HupuTopicInfo>,
    onBack: () -> Unit,
    onClosed: () -> Unit,
    onOpenTopic: (HupuTopicInfo) -> Unit,
) {
    val repo = remember { HupuRepository() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<HupuTopicInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    // 盖入动画 + 二级页计数（与 TopicFeedOverlay 同套）
    val progress = remember { Animatable(0f) }
    var pageEntered by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onClosed()
        } else {
            progress.animateTo(1f, tween(280))
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        pageEntered = true
        SecondaryPage.enter()
        onDispose { if (pageEntered) { pageEntered = false; SecondaryPage.exit() } }
    }
    LaunchedEffect(closing) {
        if (closing && pageEntered) { pageEntered = false; SecondaryPage.exit() }
    }
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { }
            return@PredictiveBackHandler
        }
        events.collect { }
        onBack()
    }

    // 400ms 防抖实时搜（接口侧另有 1s 全局节流兜底）
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            searched = false
            loading = false
            return@LaunchedEffect
        }
        loading = true
        kotlinx.coroutines.delay(400)
        results = repo.searchTopics(query.trim())
        loading = false
        searched = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(3f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // 顶栏：返回 + 输入框
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidBackButton(onBack)
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { keyboard?.hide() },
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "搜索版块",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                    Icon(
                        HupuIcons.SearchOutlined,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                loading -> SkeletonHome()
                searched && results.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "没有找到相关版块",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        results,
                        key = { it.topicId },
                    ) { t ->
                        val fav = favTopics.any {
                            it.topicId == t.topicId || it.url == t.url
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 优先用本地类目里的正式 url（数字路径也有效，但正式 url 更稳）
                                    val real = categories
                                        ?.flatMap { it.topics }
                                        ?.firstOrNull { it.topicId == t.topicId }
                                    onOpenTopic(real ?: t)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HupuIconImage(
                                model = normalizeCover(t.logo),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (t.desc.isNotBlank()) {
                                    Text(
                                        t.desc,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (fav) {
                                Icon(
                                    HupuIcons.StarRate,
                                    contentDescription = "已关注",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
