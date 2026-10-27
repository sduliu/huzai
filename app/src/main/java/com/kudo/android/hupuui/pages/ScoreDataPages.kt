package com.kudo.android.hupuui.pages

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.kudo.android.hupu.HupuAgainstBracket
import com.kudo.android.hupu.HupuAward
import com.kudo.android.hupu.HupuCareerRankBoard
import com.kudo.android.hupu.HupuDataTab
import com.kudo.android.hupu.HupuDraftBoard
import com.kudo.android.hupu.HupuInjury
import com.kudo.android.hupu.HupuMatch
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupu.HupuPlayerRankBoard
import com.kudo.android.hupu.HupuRankCategory
import com.kudo.android.hupu.HupuRankEntry
import com.kudo.android.hupu.HupuRankItem
import com.kudo.android.hupu.HupuRepository
import com.kudo.android.hupu.HupuSeasonOption
import com.kudo.android.hupu.HupuStandingGroup
import com.kudo.android.hupu.HupuStandingsBoard
import com.kudo.android.hupuui.components.CenteredTopBar
import com.kudo.android.hupuui.components.Chip
import com.kudo.android.hupuui.components.ErrorRetry
import com.kudo.android.hupuui.components.SecondaryPage
import com.kudo.android.hupuui.components.SkeletonHome
import com.kudo.android.hupuui.components.chipBarClip
import com.kudo.android.hupuui.components.normalizeCover
import com.kudo.android.hupuui.components.tapGuard
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 批B-3：赛程中心数据 tab（官方 getDataTabList 轨道：全球|赛程|球队榜|球员榜|选秀|伤病|…）。
 *
 * tab 落地规则（只按官方 tabLinkType/tabUrl 真实给出的形态分流，不编类别）：
 *   · linkType="schedule"      → 主赛程视图（本页不接管，回退频道默认内容）
 *   · linkType="oddsSchedule"  → 全球赛程（oddsScheduleList，复用 ScheduleList 行结构）
 *   · h5 且 url 命中 300003/<league>/<team|player|draft|injury>.html → 原生实现
 *     （同一批 basketballapi 匿名接口数据，页面形态自绘）
 *   · 其余 h5 → 交回宿主开 EmbedWebPage（官方 H5 原页内嵌）
 */

/** tabUrl 命中官方数据 H5 → (leagueType, kind)；kind ∈ team|player|draft|injury|against|honor|history */
internal fun nativeDataTabKind(url: String): Pair<String, String>? {
    val m = Regex("/300003/([a-zA-Z]+)/(team|player|draft|injury|against|honor|history)\\.html").find(url)
        ?: return null
    return m.groupValues[1].lowercase() to m.groupValues[2]
}

/** oddsSchedule tab 的 huputiyu:// 链接里取 code（basketballLottery 等）。 */
private fun oddsCode(tab: HupuDataTab): String =
    Regex("[?&]code=([^&]+)").find(tab.url)?.groupValues?.get(1) ?: ""

/** 官方赛程页数据 tab 的红色下划线（虎扑红，同 FeedScreen 搜索钮用色 0xFFC01E2F）。 */
private val HUPU_TAB_RED = Color(0xFFC01E2F)

/** 数据 tab 轨道（官方顺序原样；接口返回的 tab 集合原样渲染，可横滑）。
 *  批X5-3：官方化——无胶囊纯文字行：选中=深色加粗+红色下划线，未选中=灰字。 */
@Composable
internal fun LeagueDataTabBar(
    tabs: List<HupuDataTab>,
    selectedId: String,
    onSelect: (HupuDataTab) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(tabs, key = { it.id }) { t ->
            val sel = t.id == selectedId
            Column(
                Modifier
                    .selectable(selected = sel, role = Role.Tab) { onSelect(t) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    t.name,
                    fontSize = 15.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 选中下划线：占位常驻（透明=未选中），避免选中切换时行高跳动
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .height(3.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (sel) HUPU_TAB_RED else Color.Transparent),
                )
            }
        }
    }
}

/** 数据 tab 内容分派（schedule/h5-embed 以外的形态都在这里渲染）。 */
@Composable
internal fun DataTabContent(
    tab: HupuDataTab,
    topInset: Dp,
    onOpenMatch: (HupuMatch) -> Unit,
) {
    when {
        tab.linkType == "oddsSchedule" -> GlobalScheduleView(oddsCode(tab), topInset, onOpenMatch)
        else -> {
            val kind = nativeDataTabKind(tab.url)
            when (kind?.second) {
                "team" -> StandingsBoardView(kind.first)
                "player" -> PlayerRankView(kind.first)
                "draft" -> DraftView(kind.first)
                "injury" -> InjuryView(kind.first)
                "honor" -> AwardView(kind.first)
                "history" -> CareerRankView(kind.first)
                "against" -> AgainstView(kind.first)
                else -> Box(Modifier.fillMaxSize()) // 不应到达：h5 在宿主层已开 embed
            }
        }
    }
}

// ==================== 全球赛程（oddsScheduleList） ====================

@Composable
private fun GlobalScheduleView(code: String, topInset: Dp, onOpenMatch: (HupuMatch) -> Unit) {
    val repo = remember { HupuRepository() }
    var days by remember { mutableStateOf<List<HupuMatchDay>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(code, tick) {
        loading = true
        val ds = repo.globalSchedule(code.ifEmpty { "basketballLottery" }, refresh = tick > 0)
        days = ds
        loading = false
    }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = loading && days != null,
        onRefresh = { tick++ },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            days == null -> if (loading) SkeletonHome(Modifier.padding(top = topInset)) else ErrorRetry { tick++ }
            days!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无赛程", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            else -> ScheduleList(days!!, resetKey = "global-$code-$tick", topInset = topInset, onOpenMatch = onOpenMatch)
        }
    }
}

// ==================== 球队榜（teamStandingList + teamSeasonStatsAllRank + 赛季选择器） ====================

/** 赛季 chip 条：rankType 枚举派生自联赛（NBA_TEAM_RANK / CBA_TEAM_RANK / …）。 */
@Composable
private fun SeasonChips(
    seasons: List<HupuSeasonOption>,
    selected: HupuSeasonOption?,
    onSelect: (HupuSeasonOption) -> Unit,
) {
    if (seasons.size <= 1) return // 无可选赛季不渲染选择器（官方无数据不编）
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .chipBarClip()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons, key = { "${it.season}-${it.seasonType}" }) { s ->
            Chip(
                text = "${s.shortSeason} ${s.seasonTypeName}",
                selected = s == selected,
            ) { onSelect(s) }
        }
    }
}

/**
 * 球队榜整页：赛季选择器（switchableRankSeasonList）+ 左栏类别 + 右侧分组表。
 * 作为数据 tab 内容、积分榜路由页与板块盖入页三处共用。
 */
@Composable
internal fun StandingsBoardView(league: String, showSeasonBar: Boolean = true) {
    val repo = remember { HupuRepository() }
    val rankType = "${league.uppercase()}_TEAM_RANK"
    var seasons by remember { mutableStateOf<List<HupuSeasonOption>>(emptyList()) }
    var seasonSel by remember { mutableStateOf<HupuSeasonOption?>(null) }
    var board by remember { mutableStateOf<HupuStandingsBoard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var catSel by remember { mutableStateOf("") }

    LaunchedEffect(league) {
        seasons = repo.rankSeasons(rankType)
        seasonSel = seasons.firstOrNull { it.selected }
    }
    LaunchedEffect(league, seasonSel, tick) {
        loading = true
        failed = false
        val b = repo.standingsBoard(
            league,
            season = seasonSel?.season.orEmpty(),
            stageType = seasonSel?.seasonType.orEmpty(),
            refresh = tick > 0,
        )
        if (b != null) {
            board = b
            if (catSel.isEmpty() || allCategories(b).none { it.id == catSel }) {
                catSel = allCategories(b).firstOrNull()?.id.orEmpty()
            }
        } else failed = true
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        if (showSeasonBar && seasons.size > 1) {
            SeasonChips(seasons, seasonSel) { s ->
                if (s != seasonSel) { seasonSel = s; catSel = "" }
            }
        }
        val b = board
        when {
            b == null && loading -> SkeletonHome()
            b == null && failed -> ErrorRetry { tick++ }
            b == null -> Box(Modifier.fillMaxSize())
            else -> {
                val cats = allCategories(b)
                Row(Modifier.fillMaxSize()) {
                    // 左栏类别（官方左栏：东/西/分区/技术统计，接口给什么放什么）
                    Column(
                        Modifier
                            .width(96.dp)
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                    ) {
                        cats.forEach { c ->
                            val sel = c.id == catSel
                            Text(
                                c.title,
                                fontSize = 14.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                    )
                                    .clickable { catSel = c.id }
                                    .padding(horizontal = 10.dp, vertical = 11.dp),
                            )
                        }
                    }
                    // 右侧分组表
                    val groups = cats.firstOrNull { it.id == catSel }?.groups.orEmpty()
                    LazyColumn(
                        contentPadding = PaddingValues(end = 16.dp, bottom = 100.dp),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) {
                        groups.forEach { g ->
                            item(key = "gt-${g.title}") {
                                Text(
                                    g.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                            item(key = "gtb-${g.title}") { StandingGroupTable(g) }
                            item(key = "gs-${g.title}") { Spacer(Modifier.height(14.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/** categories + statCategories 合成统一类别表（技术统计作为最后一个类别）。 */
private fun allCategories(b: HupuStandingsBoard): List<HupuRankCategory> =
    if (b.statCategories.isEmpty()) b.categories
    else b.categories + HupuRankCategory("stats", "技术统计", b.statCategories)

/** 一张分组表：列头 + 排名行（列头随 group.columns）。 */
@Composable
private fun StandingGroupTable(g: HupuStandingGroup) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("#", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
            Text("队名", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            g.columns.forEach { c ->
                Text(
                    c, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(34.dp),
                )
            }
        }
        g.rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    r.rank, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(24.dp),
                )
                if (r.teamLogo.isNotEmpty()) {
                    AsyncImage(
                        model = r.teamLogo, contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    r.team, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                r.cells.forEach { c ->
                    Text(
                        c, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.width(34.dp),
                    )
                }
            }
        }
    }
}

// ==================== 球员榜（playerSeasonRank） ====================

@Composable
internal fun PlayerRankView(league: String) {
    val repo = remember { HupuRepository() }
    val rankType = "${league.uppercase()}_PLAYER_RANK"
    var seasons by remember { mutableStateOf<List<HupuSeasonOption>>(emptyList()) }
    var seasonSel by remember { mutableStateOf<HupuSeasonOption?>(null) }
    var board by remember { mutableStateOf<HupuPlayerRankBoard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var dimSel by remember { mutableStateOf("") }
    // 批F-1：点「查看完整榜单」展开全量榜 overlay（官方 allUrl 指向的 rank/index 页同源数据）
    var fullRank by remember { mutableStateOf<HupuRankItem?>(null) }

    LaunchedEffect(league) {
        seasons = repo.rankSeasons(rankType)
        seasonSel = seasons.firstOrNull { it.selected }
    }
    LaunchedEffect(league, seasonSel, tick) {
        loading = true
        failed = false
        val b = repo.playerRankBoard(
            league,
            season = seasonSel?.season.orEmpty(),
            stageType = seasonSel?.seasonType.orEmpty(),
            refresh = tick > 0,
        )
        if (b != null) {
            board = b
            if (dimSel.isEmpty() || b.dimensions.none { it.key == dimSel }) {
                dimSel = b.dimensions.firstOrNull()?.key.orEmpty()
            }
        } else failed = true
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        if (seasons.size > 1) {
            SeasonChips(seasons, seasonSel) { s -> if (s != seasonSel) seasonSel = s }
        }
        val b = board
        when {
            b == null && loading -> SkeletonHome()
            b == null && failed -> ErrorRetry { tick++ }
            b == null -> Box(Modifier.fillMaxSize())
            else -> {
                // 维度条（基础/进阶）
                LazyRow(
                    modifier = Modifier.fillMaxWidth().chipBarClip().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(b.dimensions, key = { it.key }) { d ->
                        Chip(text = d.name, selected = d.key == dimSel) { dimSel = d.key }
                    }
                }
                val dim = b.dimensions.firstOrNull { it.key == dimSel }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    dim?.items?.forEach { item ->
                        item(key = "ri-${item.key}") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                item.leaders.forEach { e ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${e.rank}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(24.dp),
                                        )
                                        AsyncImage(
                                            model = normalizeCover(e.photo), contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(26.dp).clip(CircleShape),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                e.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            if (e.teamName.isNotEmpty()) {
                                                Text(
                                                    e.teamName, fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        Text(
                                            e.value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (item.hasMore) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { fullRank = item }
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "查看完整榜单",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp),
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
    fullRank?.let { item ->
        PlayerRankFullOverlay(
            league = league,
            item = item,
            season = seasonSel?.season.orEmpty(),
            stageType = seasonSel?.seasonType.orEmpty(),
            onBack = { fullRank = null },
        )
    }
    }
}

/** 批F-1：单维度球员全量榜盖入页（LeagueStandingsOverlay 同款滑入骨架）。 */
@Composable
private fun PlayerRankFullOverlay(
    league: String,
    item: HupuRankItem,
    season: String,
    stageType: String,
    onBack: () -> Unit,
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
            CenteredTopBar(title = "${item.name}榜", onBack = { closing = true })
            PlayerRankFullList(league, item, season, stageType)
        }
    }
}

/** 全量榜列表：pageNo 20 行/页滚动续载，空页即止（官方 rank/index 滚动加载同款）。 */
@Composable
private fun PlayerRankFullList(league: String, item: HupuRankItem, season: String, stageType: String) {
    val repo = remember { HupuRepository() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val rows = remember { mutableStateListOf<HupuRankEntry>() }
    var nextPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun loadPage() {
        if (loading || !hasMore || failed) return
        loading = true
        scope.launch {
            val p = repo.playerRankPage(league, item.key, season, stageType, nextPage)
            if (p == null) {
                failed = true
            } else {
                rows += p.leaders
                hasMore = p.leaders.size >= 20
                nextPage++
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadPage() }
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !loading && !failed &&
                last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) loadPage() }

    when {
        rows.isEmpty() && loading -> SkeletonHome()
        rows.isEmpty() && failed -> ErrorRetry { failed = false; loadPage() }
        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(rows, key = { e -> e.playerId.ifEmpty { "${e.rank}-${e.name}" } }) { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${e.rank}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(30.dp),
                    )
                    AsyncImage(
                        model = normalizeCover(e.photo), contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(26.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (e.teamName.isNotEmpty()) {
                            Text(
                                e.teamName, fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        e.value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item(key = "footer") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        failed -> Text(
                            "加载失败，点击重试",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { failed = false; loadPage() },
                        )
                        !hasMore -> Text(
                            "已加载全部 ${rows.size} 人",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ==================== 选秀（players/draft + draftYearConfig） ====================

@Composable
internal fun DraftView(league: String) {
    val repo = remember { HupuRepository() }
    var board by remember { mutableStateOf<HupuDraftBoard?>(null) }
    var yearSel by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(league, yearSel, tick) {
        loading = true
        failed = false
        val b = repo.draftBoard(league, yearSel, refresh = tick > 0)
        if (b != null) {
            board = b
            if (yearSel == null) yearSel = b.year
        } else failed = true
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        val years = board?.years.orEmpty()
        if (years.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().chipBarClip().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(years, key = { it }) { y ->
                    Chip(text = "${y}年", selected = y == yearSel) { if (y != yearSel) yearSel = y }
                }
            }
        }
        val b = board
        when {
            b == null && loading -> SkeletonHome()
            b == null && failed -> ErrorRetry { tick++ }
            b == null -> Box(Modifier.fillMaxSize())
            b.rounds.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("该年暂无选秀数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                b.rounds.forEach { (round, picks) ->
                    item(key = "rd-$round") {
                        Text(
                            "第${round}轮", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    item(key = "rdt-$round") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            picks.forEach { p ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${p.rank}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(28.dp),
                                    )
                                    AsyncImage(
                                        model = normalizeCover(p.photo), contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(30.dp).clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            buildString {
                                                if (p.position.isNotEmpty()) append(p.position)
                                                if (p.heightCm > 0) {
                                                    if (isNotEmpty()) append(" · ")
                                                    append("${p.heightCm}cm")
                                                }
                                            },
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    Text(
                                        p.teamName, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
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

// ==================== 伤病（allInjuryList，按队分组） ====================

@Composable
internal fun InjuryView(league: String) {
    val repo = remember { HupuRepository() }
    var list by remember { mutableStateOf<List<HupuInjury>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(league, tick) {
        loading = true
        list = repo.injuries(league, refresh = tick > 0)
        loading = false
    }
    when {
        list == null && loading -> SkeletonHome()
        list == null -> ErrorRetry { tick++ }
        list!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无伤病报告", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        else -> {
            // 按队分组（保留接口顺序），队名头 + 伤病行
            val grouped = list!!.groupBy { it.teamName }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                grouped.forEach { (team, rows) ->
                    item(key = "it-$team") {
                        Text(
                            team.ifEmpty { "未标注球队" },
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    item(key = "itb-$team") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            rows.forEach { r ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = normalizeCover(r.photo), contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(34.dp).clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            r.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        val sub = listOf(r.part, r.detail).filter { it.isNotEmpty() }.joinToString(" · ")
                                        if (sub.isNotEmpty()) {
                                            Text(
                                                sub, fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            r.status, fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                        if (r.date.isNotEmpty()) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                r.date, fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}

// ==================== 荣誉榜（allPlayerAwardList，奖项 × 近年 top5 获奖者） ====================

/** 批Y：官方 honor.html 同源——奖项卡 + 获奖者行（头像/姓名/队名/赛季/分板助）。 */
@Composable
internal fun AwardView(league: String) {
    val repo = remember { HupuRepository() }
    var awards by remember { mutableStateOf<List<HupuAward>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(league, tick) {
        loading = true
        awards = repo.playerAwards(league, refresh = tick > 0)
        loading = false
    }
    when {
        awards == null && loading -> SkeletonHome()
        awards == null -> ErrorRetry { tick++ }
        awards!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无荣誉数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            awards!!.forEach { a ->
                item(key = "aw-${a.awardName}") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            a.awardDesc, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        a.winners.forEach { w ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = normalizeCover(w.photo), contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(30.dp).clip(CircleShape),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        w.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    val sub = listOf(w.teamName, w.season).filter { it.isNotEmpty() }
                                        .joinToString(" · ")
                                    if (sub.isNotEmpty()) {
                                        Text(
                                            sub, fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                val stats = listOf(
                                    w.pts.takeIf { it.isNotEmpty() }?.let { "${it}分" },
                                    w.reb.takeIf { it.isNotEmpty() }?.let { "${it}板" },
                                    w.asts.takeIf { it.isNotEmpty() }?.let { "${it}助" },
                                ).filterNotNull().joinToString(" ")
                                if (stats.isNotEmpty()) {
                                    Text(
                                        stats, fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
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

// ==================== 历史榜（playerAllDimensionCareerRank，stage×grain 开关 + 统计项卡） ====================

/** 批Y：官方 history.html 同源——常规赛/季后赛 × 场均/总数 开关 + 生涯榜统计项卡（各项 top5）。 */
@Composable
internal fun CareerRankView(league: String) {
    val repo = remember { HupuRepository() }
    var stage by remember { mutableStateOf("REGULAR") }
    var grain by remember { mutableStateOf("pergame") }
    var board by remember { mutableStateOf<HupuCareerRankBoard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(league, stage, grain, tick) {
        loading = true
        failed = false
        val b = repo.careerRankBoard(league, stage, grain, refresh = tick > 0)
        if (b != null) board = b else failed = true
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        // 官方开关：常规赛|季后赛、场均|总数（各自一行 chip）
        Row(
            Modifier.fillMaxWidth().chipBarClip().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("REGULAR" to "常规赛", "PLAYOFF" to "季后赛").forEach { (k, n) ->
                Chip(text = n, selected = k == stage) { if (k != stage) stage = k }
            }
            Spacer(Modifier.width(8.dp))
            listOf("pergame" to "场均", "totals" to "总数").forEach { (k, n) ->
                Chip(text = n, selected = k == grain) { if (k != grain) grain = k }
            }
        }
        val b = board
        when {
            b == null && loading -> SkeletonHome()
            b == null && failed -> ErrorRetry { tick++ }
            b == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无历史榜数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                b.dimensions.forEach { dim ->
                    dim.items.forEach { item ->
                        item(key = "cr-${dim.key}-${item.key}") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                item.leaders.forEach { e ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${e.rank}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(24.dp),
                                        )
                                        AsyncImage(
                                            model = normalizeCover(e.photo), contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(26.dp).clip(CircleShape),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                e.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            if (e.teamName.isNotEmpty()) {
                                                Text(
                                                    e.teamName, fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        Text(
                                            e.value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
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
}

// ==================== 对阵图（against-plan-chart，季后赛 bracket；官方数据空→显式空态） ====================

/** 批Y：官方 against.html 同源——七列 bracket 系列赛格；数据源空时显式空态（不编图）。 */
@Composable
internal fun AgainstView(league: String) {
    val repo = remember { HupuRepository() }
    var bracket by remember { mutableStateOf<HupuAgainstBracket?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(league, tick) {
        loading = true
        bracket = repo.againstChart(league, refresh = tick > 0)
        loading = false
    }
    when {
        bracket == null && loading -> SkeletonHome()
        bracket == null -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "暂无季后赛对阵图数据",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "官方对阵图源当前为空（against-plan-chart result=null）",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "重试", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { tick++ },
            )
        }
        else -> {
            // 按 planChartNumber 分组列（官方 NBA：东 R1/东 R2/东决/总决/西决/西 R2/西 R1）
            val cols = bracket!!.cells.groupBy { it.col }.toSortedMap()
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                cols.forEach { (col, cells) ->
                    item(key = "acol-$col") {
                        Column(
                            Modifier.width(170.dp).fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            cells.sortedBy { it.row }.forEach { c -> AgainstCellCard(c) }
                        }
                    }
                }
            }
        }
    }
}

/** 一个系列赛格：两行（队徽+种子位+队名+系列赛胜场），晋级方高亮。 */
@Composable
private fun AgainstCellCard(c: com.kudo.android.hupu.HupuAgainstCell) {
    @Composable
    fun teamRow(name: String, logo: String, seed: String, wins: String, winner: Boolean) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (logo.isNotEmpty()) {
                AsyncImage(
                    model = normalizeCover(logo), contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                name.ifEmpty { "待定" },
                fontSize = 12.sp,
                fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
                color = if (winner) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (seed.isNotEmpty()) {
                Text(
                    seed, fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                wins, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (winner) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        teamRow(c.teamName, c.teamLogo, c.rank, c.teamScore,
            winner = c.winnerTeamId.isNotEmpty() && c.winnerTeamId == c.teamId)
        teamRow(c.oppoTeamName, c.oppoTeamLogo, "", c.oppoScore,
            winner = c.winnerTeamId.isNotEmpty() && c.winnerTeamId == c.oppoTeamId)
    }
}

// ==================== 积分榜盖入页（板块内「积分榜」入口用，EmbedWebPage 同款转场） ====================

@Composable
internal fun LeagueStandingsOverlay(league: String, title: String, onBack: () -> Unit) {
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
            CenteredTopBar(title = title, onBack = { closing = true })
            StandingsBoardView(league)
        }
    }
}
