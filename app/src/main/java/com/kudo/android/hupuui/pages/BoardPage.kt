package com.kudo.android.hupuui.pages

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import coil.compose.AsyncImage
import com.kudo.android.hupu.HupuBoardNews
import com.kudo.android.hupu.HupuEmbed
import com.kudo.android.hupu.HupuHistoryEntry
import com.kudo.android.hupu.HupuMatch
import com.kudo.android.hupu.HupuMatchApi
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupu.HupuPrefs
import com.kudo.android.hupu.HupuReply
import com.kudo.android.hupu.HupuRepository
import com.kudo.android.hupu.HUPU_BOARDS
import com.kudo.android.hupu.HupuBoardCfg
import com.kudo.android.hupu.HupuBoardStanding
import com.kudo.android.hupu.HupuStandingRow
import com.kudo.android.hupu.HupuStandings
import com.kudo.android.hupu.HupuThread
import com.kudo.android.hupu.HupuThreadDetail
import com.kudo.android.hupu.HupuFloorReplies
import com.kudo.android.hupuui.components.CenteredTopBar
import com.kudo.android.hupuui.components.Chip
import com.kudo.android.hupuui.components.ErrorRetry
import com.kudo.android.hupuui.components.HupuIcons
import com.kudo.android.hupuui.components.formatCount
import com.kudo.android.hupuui.components.normalizeCover
import com.kudo.android.hupuui.components.thumbnailUrl
import com.kudo.android.hupuui.components.SecondaryPage
import com.kudo.android.hupuui.components.SkeletonHome
import com.kudo.android.hupuui.components.tapGuard
import java.net.URLDecoder
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kudo.android.hupuui.components.HupuIconImage
import com.kudo.android.hupuui.components.HupuThumbImage

/** 批T-4：官方「JRs评分」蓝（与批S titleFont 官方蓝同值 0xFF3B82F6）。 */
private val JrsBlue = androidx.compose.ui.graphics.Color(0xFF3B82F6)

/**
 * board：赛事板块视图（官方板块形态的复刻）——**内嵌在「比赛」Tab 频道内容区**。
 *
 * 结构对齐官方 App 赛事板块（频道本体即板块页，无中间入口）：
 *   「近期比赛」标题行（右侧：积分榜 chip? + 查看全部赛程 ›，就地展开/收起）
 *   近期比赛紧凑行（≤4 场热门：进行中优先+锚定日填充）→ 点比赛走宿主的 MatchDetailOverlay
 *   资讯流（basketballapi/news/v2/newsList 或 soccer SSR / 专区话题兜底；
 *          tid 帖 → ThreadDetailOverlay，LINK 运营位 → 解 huputiyu://webview url 进 EmbedWebPage）
 *
 * 内部 overlay（帖子详情/嵌入页/积分榜）照常盖出并计数 SecondaryPage；
 * 比赛区「常驻占位」——赛程异步落地后原地替换，不把视口顶下去。
 */
/**
 * board：板块视图状态。拆出原因——板块视图内嵌在「比赛」Tab 内容区后，其内部
 * overlay（帖子详情/嵌入页/积分榜）必须挂到宿主根层渲染（频道页头是覆盖式浮层，
 * 内容区内的 overlay 会被压住）；状态与加载逻辑集中于此，[BoardContent] 渲染列表、
 * [BoardOverlays] 在根层渲染盖层。
 */
internal class BoardUiState(
    val cfg: HupuBoardCfg,
    internal val repo: HupuRepository,
    internal val scope: CoroutineScope,
) {
    // ---------- 页面态 ----------
    var openedThread by mutableStateOf<HupuThread?>(null)
    var embedPage by mutableStateOf<HupuEmbed?>(null)
    var standingsOpen by mutableStateOf(false)
    var showAllMatches by mutableStateOf(false)

    // ---------- 赛程（复用频道数据源） ----------
    var days by mutableStateOf<List<HupuMatchDay>?>(null)

    // ---------- 资讯流 ----------
    var newsItems by mutableStateOf<List<HupuBoardNews>>(emptyList())
    var newsLoading by mutableStateOf(true)
    var newsFailed by mutableStateOf(false)
    var newsLoadingMore by mutableStateOf(false)
    var newsAnchor by mutableStateOf("")
    var newsPreCount by mutableIntStateOf(0)
    var newsHasMore by mutableStateOf(false)
    var refreshTick by mutableIntStateOf(0)

    // ---------- 积分榜 ----------
    var standings by mutableStateOf<HupuStandings?>(null)
    var standingsLoading by mutableStateOf(false)
    var standingsFailed by mutableStateOf(false)

    // ---------- 帖子详情 ----------
    var threadClosing by mutableStateOf(false)
    var threadEntered by mutableStateOf(false)
    var threadLoading by mutableStateOf(false)
    var threadLoadingMore by mutableStateOf(false)
    var threadSortSeq by mutableIntStateOf(0)
    val threadDetails = mutableStateMapOf<String, HupuThreadDetail>()
    // 详情页点头像 → 用户主页盖入式叠层（匿名可读，查看不要求登录）
    val userPageStack = mutableStateListOf<String>()
    val floorStates = mutableStateMapOf<String, HupuFloorReplies>()
    val floorLoading = mutableStateMapOf<String, Boolean>()
    val floorLoadingMore = mutableStateMapOf<String, Boolean>()

    fun loadSoccerNews(force: Boolean) {
        scope.launch {
            newsLoading = true
            val list = repo.soccerNews(refresh = force)
            newsItems = list
            newsFailed = list.isEmpty()
            newsHasMore = false      // SSR 单页，无翻页
            newsLoading = false
        }
    }

    fun loadNews(first: Boolean, force: Boolean) {
        val tag = cfg.tagCode
        val topic = cfg.newsTopic
        if (tag == null && topic.isEmpty()) { loadSoccerNews(force); return }
        if (first) newsLoading = true else newsLoadingMore = true
        scope.launch {
            val page = if (topic.isNotEmpty()) {
                // 专区兜底：preCount 槽位复用为页码（topicNews 回写 nextPreCount=page+1）
                repo.topicNews(topic, page = if (first) 1 else newsPreCount, refresh = force)
            } else {
                repo.boardNews(
                    tag!!,
                    preCount = if (first) 0 else newsPreCount,
                    anchorNid = if (first) "" else newsAnchor,
                    refresh = force,
                )
            }
            // 失败/首页空 → 配置了 SSR 回退的板块（国际足球）再试一次
            if ((page == null || (first && page.items.isEmpty())) && cfg.soccerSsr) {
                loadSoccerNews(force)
                return@launch
            }
            if (page == null) {
                if (first) newsFailed = true else newsLoadingMore = false
                newsLoading = false
                return@launch
            }
            if (first) {
                newsItems = page.items
                newsAnchor = page.anchorNid
            } else {
                // feed 会更新：按 nid 去重防重复插入
                val seen = newsItems.mapTo(HashSet<String>()) { it.nid }
                newsItems = newsItems + page.items.filter { it.nid !in seen }
            }
            newsPreCount = page.nextPreCount
            // 空页 = 到底；LINK 位不算翻页依据，只要返回满页就继续认为有下一页
            newsHasMore = page.items.isNotEmpty()
            newsFailed = false
            newsLoading = false
            newsLoadingMore = false
        }
    }

    fun loadStandings() {
        scope.launch {
            standingsLoading = true
            standingsFailed = false
            val s = when (cfg.standing) {
                HupuBoardStanding.NBA -> repo.nbaStandings()
                HupuBoardStanding.SOCCER_API -> repo.soccerStandings(cfg.standingCompId)
                HupuBoardStanding.NONE -> null
            }
            if (s != null) standings = s else standingsFailed = true
            standingsLoading = false
        }
    }

    /** 「积分榜」chip：有原生实现开原生页，否则回落 standingsUrl 的 WebView。
     *  批B-3：NBA 走 LeagueStandingsOverlay（自拉 basketballapi），不再预载 SSR 榜。 */
    fun openStandings() {
        if (cfg.standing != HupuBoardStanding.NONE) {
            standingsOpen = true
            if (cfg.standing != HupuBoardStanding.NBA && standings == null && !standingsLoading) loadStandings()
        } else if (cfg.standingsUrl.isNotEmpty()) {
            embedPage = HupuEmbed(type = "board", url = cfg.standingsUrl, matchId = "", label = "积分榜")
        }
    }

    fun loadSchedule(force: Boolean) {
        scope.launch {
            val ds = repo.matchSchedule(cfg.scheduleId, refresh = force)
            days = ds
        }
    }

    fun openThread(t: HupuThread) {
        HupuPrefs.addHistory(
            HupuHistoryEntry(
                tid = t.tid, title = t.title, topicName = t.topic?.name ?: "",
                lights = t.lights, replies = t.replies, read = t.read,
                visitedAt = System.currentTimeMillis(),
            )
        )
        threadClosing = false
        if (!threadEntered) { threadEntered = true; SecondaryPage.enter() }
        openedThread = t
        threadLoading = !threadDetails.containsKey(t.tid)
    }

    fun openFloor(tid: String, r: HupuReply) {
        if (floorStates.containsKey(r.pid)) return
        floorLoading[r.pid] = true
        scope.launch {
            delay(300)
            if (floorStates.containsKey(r.pid)) { floorLoading[r.pid] = false; return@launch }
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

    // 资讯点击：有 tid → 帖子详情；无 tid → 解 link 内嵌 webview
    fun openNews(n: HupuBoardNews) {
        if (n.openableAsThread) {
            openThread(
                HupuThread(
                    tid = n.tid, title = n.title, cover = n.img,
                    replies = n.replies, lights = n.lights,
                    createdAtText = n.publishTime.take(16).drop(5),
                )
            )
            return
        }
        boardLinkUrl(n)?.let { url ->
            embedPage = HupuEmbed(type = "board", url = url, matchId = "", label = n.title.take(20))
        }
    }
}

/**
 * 创建/复用板块状态（key=boardId，切频道即整套重建——滚动/资讯/赛程互不串台）。
 * 非板块频道返回 null。
 */
@Composable
internal fun rememberBoardState(boardId: String): BoardUiState? {
    val repo = remember { HupuRepository() }
    val scope = rememberCoroutineScope()
    return remember(boardId) { HUPU_BOARDS[boardId]?.let { BoardUiState(it, repo, scope) } }
}

/**
 * 板块视图：「近期比赛 ≤4 场紧凑行（查看全部赛程就地展开）」+ 下方资讯流。
 * 内嵌在「比赛」Tab 频道内容区；内部 overlay 由 [BoardOverlays] 挂宿主根层。
 */
@Composable
internal fun BoardContent(
    state: BoardUiState,
    onOpenMatch: (HupuMatch) -> Unit,
    /** 宿主频道页已加载的赛程（schedules[boardId]）——首帧即用，本页 fetch 仅作兜底/刷新 */
    hostSchedule: List<HupuMatchDay>? = null,
    /** 浮空顶栏（页头+频道条）占位——内容从这里开始才不钻进栏底 */
    topInset: androidx.compose.ui.unit.Dp,
) = with(state) {
    LaunchedEffect(state, refreshTick) {
        days = null
        loadSchedule(force = refreshTick > 0)
        loadNews(first = true, force = refreshTick > 0)
    }

    // 批A-3：「查看全部赛程」是就地展开态（非导航层），返回键先收起再退页——
    // 否则展开态按返回会直接弹出整个二级页，实测落回频道首页
    BackHandler(enabled = showAllMatches) { showAllMatches = false }

    // 顶部热门比赛（官方式：≤4 场）——进行中优先，锚定日（今天/最近过往日/首日）起填充；
    // 数据源：本页拉到的 days 优先，未落地前用宿主频道的 hostSchedule 首帧直出
    val today = remember { LocalDate.now().toString() }
    val effDays = days ?: hostSchedule
    val hotMatches = remember(days, hostSchedule) {
        val ds = effDays.orEmpty()
        if (ds.isEmpty()) emptyList() else {
            val live = ds.flatMap { it.matches }.filter { it.status == "INPROGRESS" }
            val todayIdx = ds.indexOfFirst { it.dayTime == today }
            val lastPast = ds.indexOfLast { it.dayTime <= today }
            val start = when {
                todayIdx >= 0 -> todayIdx
                lastPast >= 0 -> lastPast
                else -> 0
            }
            (live + ds.drop(start).flatMap { it.matches }).distinctBy { it.matchId }.take(4)
        }
    }

    // 帖子详情数据加载（懒拉 + 缓存 + 后台刷新）
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
                threadLoading = false
            }
        }
    }

    // ---------- 视图 ----------
    Box(Modifier.fillMaxSize()) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = newsLoading && newsItems.isNotEmpty(),
            onRefresh = { refreshTick++ },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = newsLoading && newsItems.isNotEmpty(),
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                // 列表状态随 boardState 实例重建（切频道=全新滚动位置）+ 首帧强制置顶：
                // 「近期比赛」永远是最先看到的内容，异步赛程落位不会把视口顶下去
                val listState = remember(state) { LazyListState() }
                LaunchedEffect(state) { listState.scrollToItem(0) }
                // 「查看全部赛程」展开 → 定位到当前比赛日（频道版 ScheduleList 同款锚定：
                // 今天有比赛→今天；无→最近过往日；全是未来→首日），上滑过去、下滑未来。
                // item 布局：0=标题行，1..n=日块（每日一个 item）→ 锚点 = 1 + 锚定日下标
                LaunchedEffect(showAllMatches, effDays) {
                    // 收起回顶：深滚动位置收起后无效，回到「近期比赛」开头
                    if (!showAllMatches) { listState.scrollToItem(0); return@LaunchedEffect }
                    val ds = effDays.orEmpty().filter { it.matches.isNotEmpty() }
                    if (ds.isEmpty()) return@LaunchedEffect
                    val todayIdx = ds.indexOfFirst { it.dayTime == today }
                    val lastPast = ds.indexOfLast { it.dayTime <= today }
                    val anchor = when {
                        todayIdx >= 0 -> todayIdx
                        lastPast >= 0 -> lastPast
                        else -> 0
                    }
                    listState.scrollToItem(1 + anchor)
                }
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        newsHasMore && !newsLoadingMore &&
                            last >= listState.layoutInfo.totalItemsCount - 5
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) loadNews(first = false, force = false)
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = topInset, bottom = 140.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 比赛区标题行：「近期比赛」 … [积分榜 chip] 「查看全部赛程 ›」（展开后变「全部赛程/收起」）
                    item(key = "schedule-header") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BoardSectionHeader(if (showAllMatches) "全部赛程" else "近期比赛")
                            Spacer(Modifier.weight(1f))
                            if (cfg.standing != HupuBoardStanding.NONE ||
                                cfg.standingsUrl.isNotEmpty()
                            ) {
                                Chip(text = "积分榜", selected = false) { openStandings() }
                                Spacer(Modifier.width(6.dp))
                            }
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showAllMatches = !showAllMatches }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (showAllMatches) "收起" else "查看全部赛程",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    if (showAllMatches) {
                        // 全部赛程：按日分块平铺（每日一张圆角面，行仍是紧凑行）
                        effDays.orEmpty().forEach { d ->
                            if (d.matches.isNotEmpty()) {
                                item(key = "day-${d.dayTime}") {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        d.matches.forEach { m ->
                                            BoardMatchRow(m) { onOpenMatch(m) }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (hotMatches.isNotEmpty()) {
                        // 顶部热门比赛（官方式大卡：赛事名+大比分+评分人数，≤4 场，点卡进详情）
                        item(key = "schedule-strip") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                hotMatches.forEachIndexed { i, m ->
                                    if (i > 0) Box(
                                        Modifier.fillMaxWidth().height(0.5.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                    BoardMatchCard(m) { onOpenMatch(m) }
                                }
                            }
                        }
                    } else {
                        // 占位常驻：赛程异步落地后原地替换，不会因插入条目把视口顶下去
                        item(key = "schedule-empty") {
                            Text(
                                if (effDays == null) "赛程加载中…" else "近期暂无比赛",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                    // 资讯
                    item(key = "news-header") {
                        BoardSectionHeader("资讯")
                    }
                    when {
                        newsLoading && newsItems.isEmpty() -> {
                            item(key = "news-skeleton") { SkeletonHome() }
                        }
                        newsFailed && newsItems.isEmpty() -> {
                            item(key = "news-failed") { ErrorRetry { refreshTick++ } }
                        }
                        newsItems.isEmpty() -> {
                            item(key = "news-empty") {
                                Text(
                                    "暂无资讯",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        else -> {
                            items(newsItems, key = { it.nid }) { n ->
                                BoardNewsRow(n, onOpen = { openNews(n) })
                            }
                            if (newsHasMore) {
                                item(key = "news-loading-more") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

/**
 * 板块内部 overlay（帖子详情/嵌入页/积分榜）——必须挂在宿主**根层**渲染：
 * 频道页头是覆盖式浮层，若放在内容区里会被「比赛」页头压住。
 */
@Composable
internal fun BoardOverlays(state: BoardUiState) = with(state) {
        // ---------- 资讯帖子详情（盖在本页内容之上） ----------
        val ot = openedThread
        if (ot != null) {
            DisposableEffect(Unit) {
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
                onBack = { threadClosing = true },
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
                        if (nd != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) {
                            threadDetails[ot.tid] = if (desc) nd else repo.mergeThreadDetail(cur, nd)
                        }
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
                    val mySeq = ++threadSortSeq
                    val cur = threadDetails[ot.tid]
                    if (cur != null && cur.descReplies != desc) {
                        scope.launch {
                            val nd = repo.repliesDirected(ot.tid, cur, desc)
                            if (nd != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) {
                                threadDetails[ot.tid] = nd
                            }
                        }
                    }
                },
                floorStates = floorStates,
                floorLoading = floorLoading.filterValues { it }.keys,
                floorLoadingMore = floorLoadingMore.filterValues { it }.keys,
                onOpenFloor = { r -> openFloor(ot.tid, r) },
                onFloorLoadMore = { pid, fr -> loadFloorMore(ot.tid, pid, fr) },
                onOpenUser = { pu -> if (pu.isNotEmpty()) userPageStack.add(pu) },
            )
        }
        // 用户主页叠层（盖在详情/楼层 sheet 之上；自身带盖入动画与二级页计数）
        userPageStack.forEachIndexed { si, subEuid ->
            androidx.compose.runtime.key(si) {
                UserProfilePage(
                    euid = subEuid,
                    zIndex = 3f + si,
                    onClose = { userPageStack.removeAt(si) },
                )
            }
        }
        // LINK 运营位 / 积分榜落地页
        embedPage?.let { e ->
            androidx.compose.runtime.key(e.url) {
                EmbedWebPage(e) { embedPage = null }
            }
        }
        // 积分榜原生页（批B-3：NBA 升级为 basketballapi 版——赛季选择器 + 多类别左栏）
        if (standingsOpen) {
            if (cfg.standing == HupuBoardStanding.NBA) {
                LeagueStandingsOverlay("nba", "NBA 积分榜") { standingsOpen = false }
            } else {
                BoardStandingsOverlay(
                    data = standings,
                    loading = standingsLoading,
                    failed = standingsFailed,
                    onBack = { standingsOpen = false },
                    onRetry = { loadStandings() },
                )
            }
        }
}

/** 板块内节标题。 */
@Composable
private fun BoardSectionHeader(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * 资讯条目：FeedItem 布局 + 置顶/角标支持。
 * tid 帖点击进帖子详情；无 tid 的 LINK 运营位进内嵌 webview。
 */
@Composable
private fun BoardNewsRow(n: HupuBoardNews, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (n.top || n.badges.isNotEmpty()) {
                    Text(
                        n.badges.firstOrNull() ?: "置顶",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    n.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            // meta 行对齐官方：纯文字「亮 N · 回复 N · 时间」（官方为「顶 N 回复 N 亮回复」，
            // 我们的字段是 lights=点亮数，用「亮」对应）
            BoardMetaText(
                buildString {
                    append("亮 ").append(formatCount(n.lights))
                    append(" · 回复 ").append(formatCount(n.replies))
                    if (n.publishTime.length >= 16) append(" · ").append(n.publishTime.substring(5, 16))
                }
            )
        }
        normalizeCover(n.img)?.let { img ->
            HupuThumbImage(
                model = thumbnailUrl(img, 320),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 100.dp, height = 76.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun BoardMetaText(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * LINK 运营位落地链接解码：
 *   huputiyu://webview/openencodeurl?...&url=<urlencoded https> → 解出内层 https
 *   直接 http(s) 链接原样返回；其余 scheme（kanqiu 等）返回 null（不可承载）。
 */
private fun boardLinkUrl(n: HupuBoardNews): String? {
    val link = n.link
    if (link.startsWith("http://") || link.startsWith("https://")) return link
    val inner = link.substringAfter("url=", "")
    if (inner.isEmpty()) return null
    return runCatching { URLDecoder.decode(inner.substringBefore('&'), "UTF-8") }
        .getOrNull()
        ?.takeIf { it.startsWith("http") }
}

/**
 * 积分榜盖入页（EmbedWebPage 同款转场）：组标题 + 列头 + 排名行。
 * NBA（SSR 表）与足球（competition/ranking）共用渲染；足球行的
 * [HupuStandingRow.color] 是服务端给的升降级区底色，直接做排名角标底。
 */
@Composable
private fun BoardStandingsOverlay(
    data: HupuStandings?,
    loading: Boolean,
    failed: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    DisposableEffect(Unit) {
        onDispose { SecondaryPage.exit() }
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onBack()
        }
    }
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { }
            return@PredictiveBackHandler
        }
        try {
            events.collect { event -> progress.snapTo(1f - event.progress) }
            progress.animateTo(0f, tween(120))
            closing = true
        } catch (e: CancellationException) {
            progress.animateTo(1f, tween(200))
            throw e
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(1.6f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            CenteredTopBar(
                title = data?.league?.takeIf { it.isNotEmpty() }?.let { "$it 积分榜" } ?: "积分榜",
                onBack = { closing = true },
            )
            when {
                data != null -> StandingList(data)
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                failed -> ErrorRetry { onRetry() }
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StandingList(data: HupuStandings) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        data.groups.forEach { g ->
            item(key = "g-${g.title}") {
                BoardSectionHeader(g.title)
                Spacer(Modifier.height(6.dp))
            }
            // 列头
            item(key = "h-${g.title}") {
                StandingRowLayout(
                    rank = "#", team = "队名", cells = g.columns,
                    isHeader = true, color = "",
                )
            }
            items(g.rows, key = { "${g.title}-${it.rank}-${it.team}" }) { r ->
                StandingRowLayout(
                    rank = r.rank, team = r.team, cells = r.cells,
                    isHeader = false, color = r.color, teamLogo = r.teamLogo,
                )
            }
            item(key = "sp-${g.title}") { Spacer(Modifier.height(14.dp)) }
        }
    }
}

@Composable
private fun StandingRowLayout(
    rank: String,
    team: String,
    cells: List<String>,
    isHeader: Boolean,
    color: String,
    teamLogo: String = "",
) {
    val textColor = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface
    val badgeBg = runCatching {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color))
    }.getOrNull() ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isHeader) MaterialTheme.colorScheme.surface else badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                rank, fontSize = 11.sp,
                fontWeight = if (isHeader) FontWeight.Normal else FontWeight.SemiBold,
                color = textColor,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (teamLogo.isNotEmpty()) {
            AsyncImage(
                model = teamLogo,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            team, fontSize = 12.sp,
            fontWeight = if (isHeader) FontWeight.Normal else FontWeight.Medium,
            color = textColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        cells.forEach { c ->
            Text(
                c, fontSize = if (isHeader) 10.sp else 12.sp, color = textColor,
                maxLines = 1, overflow = TextOverflow.Clip,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
        }
    }
}

/**
 * 板块页/首页频道顶部热门比赛的官方大卡：赛事名居顶，主队名[队标] 大比分 [队标]客队名
 * 居中一行，下方状态行（进行中→红「进行中」/已结束→「N人评分」/未开始→开赛时间）。
 * 有详情钥匙的比赛整卡可点进详情（openable 判据同 BoardMatchRow）。
 */
@Composable
internal fun BoardMatchCard(m: HupuMatch, onOpen: () -> Unit) {
    val openable = m.scoreBizNo != null || m.scoreBizType == HupuMatchApi.FOOTBALL_MATCH
    val multiTeam = (m.home == null && m.away == null) ||
        (m.home?.name.isNullOrBlank() && m.away?.name.isNullOrBlank())
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (openable) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            m.introduction.ifBlank { m.matchName },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        if (multiTeam) {
            Text(
                m.matchName.ifBlank { m.introduction },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.home?.name.orEmpty(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                HupuIconImage(
                    model = normalizeCover(m.home?.logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
                Text(
                    if (m.home?.baseScore?.isNotEmpty() == true &&
                        m.away?.baseScore?.isNotEmpty() == true
                    ) "${m.home?.baseScore} - ${m.away?.baseScore}" else "VS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (m.status == "INPROGRESS") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                HupuIconImage(
                    model = normalizeCover(m.away?.logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    m.away?.name.orEmpty(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 批T-4：官方底行规则——有评分→蓝「{万制}JRs评分」；无评分已结束→灰「已结束」；
        // 未开始→开赛时间；进行中→状态文案。JRs 蓝与批S titleFont 官方蓝同值。
        val jrsText = com.kudo.android.hupu.hupuJrsScoreText(m.scoreCountText)
        val bottomText = when {
            m.status == "NOTSTARTED" -> m.startTimeText
            m.status == "INPROGRESS" -> m.statusDesc.ifBlank { "进行中" }
            jrsText.isNotEmpty() -> jrsText
            else -> m.statusDesc.ifBlank { "已结束" }
        }
        Text(
            bottomText,
            fontSize = 12.sp,
            color = when {
                m.status == "INPROGRESS" -> MaterialTheme.colorScheme.primary
                jrsText.isNotEmpty() && m.status != "NOTSTARTED" -> JrsBlue
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/**
 * 板块页顶部热门比赛的紧凑行：时间 | 主队名[logo] 比分 [logo]客队名 | 状态。
 * 官方式样——单行最多 4 条；有详情钥匙的比赛可点进详情（同 MatchCard 的 openable 判据）。
 */
@Composable
internal fun BoardMatchRow(m: HupuMatch, onOpen: () -> Unit) {
    val openable = m.scoreBizNo != null || m.scoreBizType == HupuMatchApi.FOOTBALL_MATCH
    val multiTeam = (m.home == null && m.away == null) ||
        (m.home?.name.isNullOrBlank() && m.away?.name.isNullOrBlank())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (openable) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 开赛时间（"10月16日 07:30" 由服务端格式化直出）
        Text(
            m.startTimeText,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(72.dp),
        )
        if (multiTeam) {
            // 多队赛事（团体赛/大逃杀）：无队伍槽位，赛事名居中
            Text(
                m.matchName.ifBlank { m.introduction },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    m.home?.name.orEmpty(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(5.dp))
                HupuIconImage(
                    model = normalizeCover(m.home?.logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(18.dp).clip(CircleShape),
                )
                Text(
                    if (m.home?.baseScore?.isNotEmpty() == true &&
                        m.away?.baseScore?.isNotEmpty() == true
                    ) "${m.home?.baseScore}:${m.away?.baseScore}" else "vs",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 7.dp),
                )
                HupuIconImage(
                    model = normalizeCover(m.away?.logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(18.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    m.away?.name.orEmpty(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            m.statusDesc,
            fontSize = 10.sp,
            color = if (m.status == "INPROGRESS") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp),
        )
    }
}

/** 「积分榜」独立路由页（首页赛事频道快捷行入口）：与板块内 overlay 同一套 StandingList 渲染。
 *  批B-3：NBA 升级为 basketballapi 版（赛季选择器 + 东/西/分区/技术统计左栏）。 */
@Composable
internal fun StandingsRoutePage(sportKey: String, back: () -> Unit) {
    val repo = remember { HupuRepository() }
    val cfg = HUPU_BOARDS[sportKey]
    if (cfg?.standing == HupuBoardStanding.NBA) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CenteredTopBar(title = "NBA 积分榜", onBack = back)
            StandingsBoardView("nba")
        }
        return
    }
    var data by remember { mutableStateOf<HupuStandings?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(sportKey, tick) {
        loading = true
        failed = false
        val s = when (cfg?.standing) {
            HupuBoardStanding.NBA -> repo.nbaStandings()
            HupuBoardStanding.SOCCER_API -> repo.soccerStandings(cfg.standingCompId)
            else -> null
        }
        if (s != null) data = s else failed = true
        loading = false
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CenteredTopBar(
            title = data?.league?.takeIf { it.isNotEmpty() }?.let { "$it 积分榜" }
                ?: "${cfg?.name.orEmpty()}积分榜",
            onBack = back,
        )
        when {
            data != null -> StandingList(data!!)
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            failed -> ErrorRetry { tick++ }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}
