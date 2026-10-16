package com.kudo.android.hupuui.pages

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import kotlin.coroutines.cancellation.CancellationException
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.kudo.android.hupuui.components.AutoFitScore
import com.kudo.android.hupuui.components.normalizeCover
import kotlinx.coroutines.delay
import com.kudo.android.hupu.HupuMatch
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupu.HupuRepository
import com.kudo.android.hupu.HupuTeamApi
import com.kudo.android.hupu.HupuTeamGame
import com.kudo.android.hupu.HupuTeamInfo
import com.kudo.android.hupu.HupuScoreGroup
import com.kudo.android.hupu.HupuScoreItem
import com.kudo.android.hupu.HupuScoreTree
import com.kudo.android.hupuui.components.SecondaryPage
import java.time.LocalDate
import com.kudo.android.hupuui.components.Chip
import com.kudo.android.hupuui.components.ErrorRetry
import com.kudo.android.hupuui.components.HeroBadgeAvatar
import com.kudo.android.hupuui.components.SkeletonHome
import com.kudo.android.hupuui.components.tapGuard
import com.kudo.android.hupuui.components.CenteredTopBar
import com.kudo.android.hupuui.components.LiquidBackButton
import com.kudo.android.hupuui.components.chipBarClip

/**
 * 比赛详情二级页（评分页 → 赛程卡点击进入）。
 * 三层数据一次拉全：比赛 → 对局[]（横滑条切换）→ 选手评分列表。
 * 盖入式转场：进入从右滑入、返回滑回右侧，与专区话题流同款。
 */
@Composable
fun MatchDetailOverlay(
    title: String,
    tree: HupuScoreTree?,
    loading: Boolean,
    closing: Boolean,
    onBack: () -> Unit,
    /** 1.183: 退场动画开始即回调——父级据此同步减二级页计数，使 Tab 栏与页面同步 Q 弹回归（与「我的」页二级页同款手感） */
    onExitStart: () -> Unit = {},
    onClosed: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPlayer: (HupuScoreItem) -> Unit,
    /** 赛程卡的两队（memberId/memberName）：与选手 infoJson.teamId 同命名空间，
     *  用于三层结构（LOL）在选手列表上方生成「全部/主队/客队/趣评」分类子 Tab（官方详情页同款） */
    teams: List<Pair<String, String>> = emptyList(),
    /** 服务端分组（getSubGroups）：flat 结构（WNBA/CUBA/VAL/CBA/女篮等）的分类条数据源；
     *  空列表 = 无分组，退回 teamId 本地过滤（LOL 三层结构方案） */
    groups: List<HupuScoreGroup> = emptyList(),
    /** 分组成员缓存（groupId → 该组选手 bizId 集合的持有列表），由 ScorePage 预取 */
    groupMembers: Map<Long, List<HupuScoreItem>> = emptyMap(),
    /** 1.198：顶部记分板数据（来自赛程卡，null = 不显示） */
    scoreboard: MatchScoreboard? = null,
    /** 1.198：对局进行中 → 每 30s 静默拉一次赛程卡与评分树 */
    live: Boolean = false,
    /** 1.198：轮询到点时的静默刷新回调（不动 loading 标志，避免闪骨架） */
    onLiveTick: () -> Unit = {},
    /** 1.200：NBA（basketball_match + NBA 联赛）——非空则显示「数据统计 / 文字实录」入口 */
    nbaQuery: NbaStatsQuery? = null,
    /** 记分板点队名/队标 → 盖出球队页（memberId/name/logo） */
    onOpenTeam: (String, String, String?) -> Unit = { _, _, _ -> },
) {
    // 盖入动画：进入 0→1；返回动画结束后由父级移除本组件
    val progress = remember { Animatable(0f) }
    // 1.223q: 入场动画按标题重播（实例复用时不重播会导致新对局「点了没反应」）
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

    // 当前选中对局（默认第1局）
    var selectedRoundId by remember { mutableStateOf<String?>(null) }
    val currentRound = tree?.rounds?.find { it.bizId == selectedRoundId } ?: tree?.rounds?.firstOrNull()
    // 队伍分类子 Tab（"全部"为默认）：key = memberId 或 "fun"（趣评=中立角色）
    var selectedGroup by remember { mutableStateOf("all") }

    // 1.201：顶层分类——null = 「评分」（默认）；BOX/PLAY = 数据统计 / 文字实录。
    // 非 NBA（nbaQuery == null）时只会停在 null，等价于页面只有「评分」。
    var nbaTab by remember { mutableStateOf<NbaStatsTab?>(null) }
    // 换场（含组件实例复用）后若本场不再是 NBA，把选中项收回「评分」
    LaunchedEffect(nbaQuery) { if (nbaQuery == null) nbaTab = null }
    // 1.201：NBA 数据/实录的加载状态——**页面级持有**，切 tab 不销毁（不重拉、不闪骨架）
    // key 用「比赛身份指纹」而非整个 nbaQuery：nbaQuery 含比分，直播轮询每 30s 可能换新值，
    // 若拿它当 key，比分会把状态实例整个重建 → 闪骨架（「数据更新时整页闪烁」的根因之一）。
    val nbaKey = nbaStatsKey(nbaQuery)
    val nbaState = remember(nbaKey) { NbaStatsState() }
    // 1.201：顶层分类条数据源（评分恒有；NBA 追加数据统计 / 文字实录）
    val detailTabs = remember(nbaKey) {
        buildList {
            add(null to "评分")
            if (nbaQuery != null) NbaStatsTab.entries.forEach { add(it to it.label) }
        }
    }

    // 1.198：实时记分板 —— **仅「进行中」才轮询**，30s 一次，页面离开即随 effect 取消；
    // 对局结束后（isLive 变 false）本 effect 重启后直接返回，自然停止。
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        while (true) {
            delay(30_000)
            onLiveTick()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer {
                translationX = (1f - progress.value) * size.width
            }
            .background(MaterialTheme.colorScheme.background)
            // 穿透守卫：空白点击不落穿到下层页面
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：返回 + 比赛名 + 总评分人数
            CenteredTopBar(
                title = title,
                onBack = { onBack() },
                subtitle = tree?.let { "${formatCount(it.scorePersonCount)}人评分" },
            )

            // 1.198：记分板（赛制 / 状态 / 两队 / 比分或开赛时间）
            // 1.201：上提到内容区之外——各组内容（评分/数据统计/文字实录）共用同一块记分板，且不随列表滚走
            scoreboard?.let { ScoreboardBanner(it, onTeam = onOpenTeam) }

            // 1.201：顶层分类条——「评分」恒有；NBA 追加「数据统计 / 文字实录」
            // （非 NBA 只有一项，整条不显示，外观与旧版一致）
            val detailTab = nbaTab
            if (detailTabs.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 裁剪边界退到 8dp，给首个/末个 chip 的浮起阴影留空间（内容仍从 16dp 起）
                        .chipBarClip()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(detailTabs, key = { it.first?.name ?: "score" }) { (key, label) ->
                        Chip(text = label, selected = detailTab == key) { nbaTab = key }
                    }
                }
            }

            val q = nbaQuery
            if (detailTab == null || q == null) {
                // ---------- ① 评分（原样：加载中 / 失败重试 / 正常） ----------
                val state = if (tree != null) "ok" else if (loading) "loading" else "error"
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = loading && tree != null,
                    onRefresh = onRefresh,
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Crossfade(targetState = state, animationSpec = tween(180), label = "matchDetailSwitch") { s ->
                        when (s) {
                            "loading" -> SkeletonHome()
                            "error" -> ErrorRetry { onRefresh() }
                            else -> {
                                val t = tree
                                if (t == null) {
                                    ErrorRetry { onRefresh() }
                                } else {
                                    TreeContent(t, currentRound?.bizId, teams = teams, groups = groups, groupMembers = groupMembers, selectedGroup = selectedGroup, onGroupChange = { selectedGroup = it }, onSelectRound = { selectedRoundId = it }, onOpenPlayer = onOpenPlayer)
                                }
                            }
                        }
                    }
                }
            } else {
                // ---------- ② 数据统计 / 文字实录（与「评分」同级，直接嵌在本页 tab 下方） ----------
                NbaStatsBody(state = nbaState, query = q, tab = detailTab)
            }
        }

        // 1.224j：球员 PK 换人弹层挂在**页面根节点**（不是 tab 内容区）——遮罩才能盖住顶栏 / 记分板。
        // 此前它挂在「数据统计」正文里，弹层打开只有下半屏变暗，上半屏依旧亮着，很怪。
        NbaPkPickerHost(nbaState)
    }
}

/** 树内容：对局横滑条（MOBA 三层结构）+ 队伍分类子 Tab + 选手评分列表；flat 结构直接列选手 */
@Composable
private fun TreeContent(
    tree: HupuScoreTree,
    selectedRoundId: String?,
    teams: List<Pair<String, String>>,
    /** 服务端分组定义（getSubGroups）：非空时走服务端分组方案（flat 结构） */
    groups: List<HupuScoreGroup>,
    /** groupId → 该组成员（groupAndSubNodes 解析结果，bizId 与树 players 同命名空间） */
    groupMembers: Map<Long, List<HupuScoreItem>>,
    selectedGroup: String,
    onGroupChange: (String) -> Unit,
    onSelectRound: (String) -> Unit,
    onOpenPlayer: (HupuScoreItem) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // 1.198：记分板与 1.201 的顶层分类条都已上提到本组件之外（页面级固定头部）
        // 对局横滑条（第1局…第N局，正序）——仅 MOBA 三层结构显示；
        // 传统体育（NBA 等）children 直接是选手（flat），没有对局层
        if (!tree.flat) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    // 1.197：裁剪边界退到 8dp，给首个/末个 chip 的浮起阴影留空间（内容仍从 16dp 起）
                    .chipBarClip()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tree.rounds, key = { it.bizId }) { round ->
                    Chip(
                        text = round.name,
                        selected = round.bizId == selectedRoundId,
                    ) { onSelectRound(round.bizId) }
                }
            }
        }
        // 选手评分列表（flat 结构直接用唯一虚拟轮；MOBA 用选中的对局）
        val round = tree.rounds.find { it.bizId == selectedRoundId } ?: tree.rounds.firstOrNull()
        if (round == null || round.players.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "该对局暂无评分",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // 分类子 Tab，两种方案：
            // ① 服务端分组（getSubGroups，官方对所有赛事通用）：flat 结构（WNBA/CUBA/VAL/CBA/女篮等），
            //    Tab = 全部 + 各分组名（如「全部/美国女篮/中国女篮」，含教练/裁判等中立角色按官方归属入组）
            // ② teamId 本地过滤：三层结构（LOL）——赛程卡两队 + 趣评（无 teamId 中立角色）
            // 无数据分组不显示：CS2 三层结构每张地图一套独立分组与选手节点（bizId 逐图不同），
            // 其他地图的分组与当前对局选手无交集、点开必为空态——只保留有成员交集（有数据）的分组，
            // 并按分组名去重（地图间同队分组重复时只留一个，与官方单地图分类条一致）
            val roundPlayerIds = round.players.map { it.bizId }.toSet()
            val serverGroups = groups
                .filter { g ->
                    val mem = groupMembers[g.groupId]
                    !mem.isNullOrEmpty() && mem.any { it.bizId in roundPlayerIds }
                }
                .distinctBy { it.name }
            // 切换对局（地图）后旧选中的分组可能已不在当前对局分组里 → 回退「全部」，避免命中空态
            val selected = if (selectedGroup.startsWith("g-") && serverGroups.none { "g-${it.groupId}" == selectedGroup }) "all" else selectedGroup
            val teamTabs = if (serverGroups.isEmpty()) teams.filter { (id, _) -> round.players.any { it.teamId == id } } else emptyList()
            val hasNeutral = serverGroups.isEmpty() && round.players.any { it.teamId == null }
            val showGroupBar = serverGroups.isNotEmpty() || teamTabs.isNotEmpty() && (teamTabs.size >= 2 || hasNeutral)
            if (showGroupBar) {
                val tabs = buildList {
                    add("all" to "全部")
                    if (serverGroups.isNotEmpty()) {
                        serverGroups.forEach { g -> add("g-${g.groupId}" to g.name) }
                    } else {
                        teamTabs.forEach { add(it) }
                        if (hasNeutral) add("fun" to "趣评")
                    }
                }
                // 分类条可横向滚动（和平精英等战队多的赛事 Tab 超宽会挤爆固定 Row）
                // 1.192: 由「文字 + 下划线」改为玻璃胶囊横滑条（与三大页顶部横滑条同款质感）
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 1.197：裁剪边界退到 8dp，给首个/末个 chip 的浮起阴影留空间（内容仍从 16dp 起）
                        .chipBarClip(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(tabs, key = { it.first }) { (key, title) ->
                        Chip(text = title, selected = key == selected) { onGroupChange(key) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            val players = when {
                !showGroupBar -> round.players
                selected == "all" -> round.players
                serverGroups.isNotEmpty() && selected.startsWith("g-") -> {
                    // 服务端分组过滤：按该组成员 bizId 集合反查（成员含教练/裁判等全部节点）
                    val memberIds = selected.removePrefix("g-").toLongOrNull()
                        ?.let { gid -> groupMembers[gid]?.map { it.bizId }?.toSet() }
                    if (memberIds.isNullOrEmpty()) round.players
                    else round.players.filter { it.bizId in memberIds }
                }
                selected == "fun" -> round.players.filter { it.teamId == null }
                else -> round.players.filter { it.teamId == selected }
            }
            if (players.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "该分组暂无评分",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(players, key = { it.bizId }) { p ->
                        PlayerScoreRow(p, onClick = { onOpenPlayer(p) })
                    }
                }
            }
        }
    }
}

/** 选手评分行：头像 + 名字/说明 | 大字分数（居中对齐）| 热评（头像下方另起一行）；点击进入选手详情 */
@Composable
private fun PlayerScoreRow(p: HupuScoreItem, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        // 1.224k：整行**顶部对齐**（此前是 CenterVertically，说明文本没显示时名称被压到行中间，
// 看着比"原来"低了一截）。顶部对齐后名称与头像顶边齐平，说明文本紧随其下。
        Row(verticalAlignment = Alignment.Top) {
            HeroBadgeAvatar(avatar = p.image, heroIcon = p.heroIcon, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    p.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 1.224k：名称**左对齐**（不居中）——只有右侧「分数 / N人」才做居中对齐。
                    textAlign = TextAlign.Start,
                )
                // 1.224k：名称下方改为**评分对象说明文本**（此前这里是热评）。
                // 没有说明文本的对象不占位。
                if (p.intro.isNotBlank()) {
                    Text(
                        p.intro,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // 1.224k：分数与评分人数**居中对齐**（此前右对齐，小字被顶到右边、与分数中轴不齐）。
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    p.scoreAvg,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor(p.scoreAvg),
                )
                Text(
                    "${formatCount(p.scorePersonCount)}人",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 1.224k：热评文本**另起一行，位于头像下方**（与通用评分二级页同款）。
        if (p.hotComment.isNotBlank()) {
            Text(
                "「${p.hotComment}」",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 人数格式化：117494 → 11.7万 */
internal fun formatCount(n: Long): String = when {
    n >= 10000 -> String.format(java.util.Locale.CHINA, "%.1f万", n / 10000.0)
    else -> n.toString()
}

/** 分数配色：>=7 绿 / 5-6.9 橙 / <5 红（虎扑评分惯例，供本页与 ScorePage 共用） */
internal fun scoreColor(scoreNum: String): androidx.compose.ui.graphics.Color {
    val v = scoreNum.toDoubleOrNull() ?: return androidx.compose.ui.graphics.Color.Unspecified
    return when {
        v >= 7.0 -> androidx.compose.ui.graphics.Color(0xFF30A46C)
        v >= 5.0 -> androidx.compose.ui.graphics.Color(0xFFF5A524)
        else -> androidx.compose.ui.graphics.Color(0xFFE5484D)
    }
}


/** 1.198：评分二级页的记分板数据。
 *
 * 全部来自赛程卡 [com.kudo.android.hupu.HupuMatch]（比分来自 HupuMatchTeam.baseScore，
 * 赛制来自 bigScore，状态来自 status/statusDesc）—— **不需要任何新接口**；
 * 进行中的对局由页面每 30s 静默刷新一次，结束后自动停。
 */
data class MatchScoreboard(
    /** 阶段/赛事名，如「LPL第三赛段组内赛」 */
    val stage: String,
    /** 已结束 / 进行中 / 未开始 */
    val statusDesc: String,
    /** 进行中 → 呼吸点 + 轮询 */
    val live: Boolean,
    /** 赛制大分（HupuMatchTeam.bigScore，"5" → 显示 BO5） */
    val bestOf: String?,
    val homeName: String,
    val homeLogo: String?,
    val homeScore: String,
    val awayName: String,
    val awayLogo: String?,
    val awayScore: String,
    /** 队名点击 → 球队页（HupuMatchTeam.memberId）；空串 = 不可点 */
    val homeId: String = "",
    val awayId: String = "",
    val homeWon: Boolean,
    val awayWon: Boolean,
    /** 开赛时间文案（未开始/无比分时占位显示） */
    val timeText: String,
)

/** 1.198：记分板 —— 状态/赛制 + 两队 + 比分 + 日期时间。
 *
 * 布局（1.198b 真机反馈逐条修正）：
 *   · 赛事名**不再重复**（顶栏已有），只留赛制与状态；
 *   · 赛制 + 状态**居中**，与下方比分同一条中轴；
 *   · 两队队标分别在自己那半区**居中**（左右对称拱着比分），队名在队标正下方居中；
 *   · 底部固定显示**日期时间**（原来只在未开始时借位显示）。
 */
@Composable
private fun ScoreboardBanner(sb: MatchScoreboard, onTeam: (String, String, String?) -> Unit) {
    val hasScore = sb.homeScore.isNotBlank() && sb.homeScore != "-" &&
        sb.awayScore.isNotBlank() && sb.awayScore != "-"

    // 比分变化 → 中间数字 Q 弹放大一次（真机反馈：要动效）
    val pop = remember { Animatable(1f) }
    val scoreText = if (hasScore) "${sb.homeScore} - ${sb.awayScore}" else ""
    var last by remember { mutableStateOf(scoreText) }
    LaunchedEffect(scoreText) {
        if (scoreText != last) {
            last = scoreText
            if (scoreText.isNotEmpty()) {
                pop.animateTo(1.22f, tween(110))
                pop.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 赛制 + 状态：居中（与比分同一条中轴）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!sb.bestOf.isNullOrBlank()) {
                Text(
                    "BO${sb.bestOf}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            StatusPill(sb)
        }
        // 两队 + 比分：队标在各自半区居中，比分居中
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamSide(
                sb.homeName, sb.homeLogo, sb.homeWon, modifier = Modifier.weight(1f),
                onClick = if (sb.homeId.isNotBlank()) ({ onTeam(sb.homeId, sb.homeName, sb.homeLogo) }) else null,
            )
            Column(Modifier.width(118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (hasScore) {
                    // 1.199b：比分自适应字号（空间不够就缩小，不换行、不裁切）
                    AutoFitScore(
                        text = scoreText,
                        maxFontSize = 26f,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        },
                    )
                } else {
                    // 未开始：比分位置留空，避免与下方时间重复
                    Text(
                        "VS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            TeamSide(
                sb.awayName, sb.awayLogo, sb.awayWon, modifier = Modifier.weight(1f),
                onClick = if (sb.awayId.isNotBlank()) ({ onTeam(sb.awayId, sb.awayName, sb.awayLogo) }) else null,
            )
        }
        // 日期时间（始终显示）
        if (sb.timeText.isNotBlank()) {
            Text(
                sb.timeText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一侧队伍：队标 + 队名 —— 两者**水平居中对齐**（队名在队标正下方） */
@Composable
private fun TeamSide(
    name: String,
    logo: String?,
    won: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = normalizeCover(logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = if (won) FontWeight.Bold else FontWeight.Normal,
            color = if (won) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** 状态胶囊：进行中 → 主题色呼吸点；其余 → 中性底显示状态文案 */
@Composable
private fun StatusPill(sb: MatchScoreboard) {
    if (sb.live) {
        val transition = rememberInfiniteTransition(label = "livePill")
        val dotAlpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "liveDotAlpha",
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(5.dp))
            Text("进行中", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Text(
            sb.statusDesc.ifBlank { "—" },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 球队页请求：打开方用当前频道上下文构造（赛程来源 + 榜单类型）。 */
internal class TeamPageRequest(
    val memberId: String,
    val name: String,
    val logo: String?,
    /** matchSchedule 的 businessId（板块频道 = cfg.scheduleId，其余 = 频道 id） */
    val scheduleId: String,
    /** 原生榜类型（无榜源 = NONE，只显示赛程） */
    val standingKind: HupuBoardStanding,
    val standingCompId: String,
)

/**
 * 球队页（比赛详情 → 记分板点队名/队标进入）。
 * 零新接口：赛程 = 频道赛程按 memberId 过滤；榜单摘要 = NBA/足球原生榜按队名匹配。
 * 盖入页与积分榜同款转场；zIndex 高于比赛详情（2f）使其盖在详情之上。
 */
@Composable
internal fun TeamDetailOverlay(
    req: TeamPageRequest,
    onOpenMatch: (HupuMatch) -> Unit,
    onBack: () -> Unit,
) {
    val repo = remember { HupuRepository() }
    var loading by remember { mutableStateOf(true) }
    var days by remember { mutableStateOf<List<HupuMatchDay>>(emptyList()) }
    var standingLine by remember { mutableStateOf<String?>(null) }
    /** NBA 球队专属数据（nba.hupu.com/teams|schedule/{slug} SSR）：信息+阵容+全季赛程窗 */
    var teamInfo by remember { mutableStateOf<HupuTeamInfo?>(null) }
    var teamGames by remember { mutableStateOf<List<HupuTeamGame>?>(null) }

    LaunchedEffect(req.memberId) {
        loading = true
        // NBA 队：官方站球队页（信息+阵容）与全季赛程窗——比频道赛程窗口更全
        val slug = HupuTeamApi.nbaSlug(req.name)
        if (slug != null) {
            teamInfo = repo.nbaTeamInfo(slug)
            val gs = repo.nbaTeamSchedule(slug)
            if (gs.isNotEmpty()) teamGames = gs
        }
        // 兜底（非 NBA 或官方页失败）：频道赛程按 memberId 过滤
        if (teamInfo == null && teamGames == null) {
            val ds = repo.matchSchedule(req.scheduleId)
            days = ds.mapNotNull { d ->
                val ms = d.matches.filter {
                    it.home?.memberId == req.memberId || it.away?.memberId == req.memberId
                }
                if (ms.isEmpty()) null else d.copy(matches = ms)
            }
        }
        if (req.standingKind != HupuBoardStanding.NONE) {
            val s = when (req.standingKind) {
                HupuBoardStanding.NBA -> repo.nbaStandings()
                HupuBoardStanding.SOCCER_API -> repo.soccerStandings(req.standingCompId)
                else -> null
            }
            s?.groups?.forEach { g ->
                g.rows.find { it.team == req.name }?.let { r ->
                    standingLine = buildString {
                        if (g.title.isNotBlank()) append(g.title).append('·')
                        append("第").append(r.rank).append("名")
                        g.columns.zip(r.cells).take(4).forEach { (c, v) -> append(' ').append(c).append(v) }
                    }
                }
            }
        }
        loading = false
    }

    // NBA 模式：按月分组的赛程段（锚定=首个含今日之后比赛的月）
    val gameMonths = remember(teamGames) {
        teamGames?.groupBy { it.dateTime.take(7) }?.toList() ?: emptyList()
    }
    // 锚定当前比赛日/月（今日→最近过往日→首日），与「全部赛程」同款
    val listState = rememberLazyListState()
    val effDays = days
    LaunchedEffect(effDays, gameMonths) {
        val today = LocalDate.now().toString()
        if (gameMonths.isNotEmpty()) {
            val mIdx = gameMonths.indexOfFirst { (_, gs) ->
                gs.any { it.dateTime.take(10) >= today }
            }.takeIf { it >= 0 } ?: (gameMonths.size - 1)
            // item 序：0=信息卡(NBA) → 赛程按月各一项
            listState.scrollToItem(1 + mIdx)
        } else if (effDays.isNotEmpty()) {
            val anchor = effDays.indexOfFirst { it.dayTime >= today }
                .takeIf { it >= 0 } ?: (effDays.size - 1)
            listState.scrollToItem(1 + anchor)
        }
    }

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
            .zIndex(2.6f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            CenteredTopBar(title = req.name, onBack = { closing = true })
            // 队头卡：队徽 + 队名 + 榜单摘要（有榜源且匹配到才显示）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = normalizeCover(req.logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(req.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    standingLine?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val nbaMode = teamInfo != null || teamGames != null
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                !nbaMode && days.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("近期无赛程数据", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    // ---- NBA：球队信息卡（进入NBA/主场/分区/主教练 + 简介） ----
                    teamInfo?.let { ti ->
                        item(key = "info") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                ti.infos.forEach { (k, v) ->
                                    Row(Modifier.padding(vertical = 2.dp)) {
                                        Text(k, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(10.dp))
                                        Text(v, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                if (ti.intro.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        ti.intro,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                        }
                    }
                    // ---- NBA：全季赛程窗（按月分组，本月锚定） ----
                    gameMonths.forEach { (month, gs) ->
                        item(key = "m-$month") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    month.replace('-', '年') + "月",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                                gs.forEach { g -> TeamGameRow(g) }
                            }
                        }
                    }
                    // ---- 兜底：频道赛程按 memberId 过滤（非 NBA/官方页失败） ----
                    if (!nbaMode) {
                        effDays.forEach { day ->
                            item(key = "d-${day.dayTime}") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        day.dateBlock,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    )
                                    // 点场 → 本页滑出 + 详情层换上该场（详情 zIndex 2 在本页之下）
                                    day.matches.forEach { m -> BoardMatchRow(m) { closing = true; onOpenMatch(m) } }
                                }
                            }
                        }
                    }
                    // ---- NBA：阵容表（号码/姓名/位置/年龄/身高/体重） ----
                    val roster = teamInfo?.roster.orEmpty()
                    if (roster.isNotEmpty()) {
                        item(key = "roster") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    "阵容",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                                TeamRosterRow("#", "球员", "位置", "年龄", "身高", "体重", header = true)
                                roster.forEach { p ->
                                    TeamRosterRow(p.no, p.name, p.pos, p.age, p.height, p.weight, header = false)
                                }
                            }
                        }
                    }
                    item(key = "tail") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** 球队赛程行（NBA 官方页 /schedule/{slug}）：日期·时间 | 主/客 vs 对手 | 比分 | 胜负（本队视角） */
@Composable
private fun TeamGameRow(g: HupuTeamGame) {
    val played = g.result == "胜" || g.result == "负"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 北京时间 → 「MM-dd HH:mm」
        Text(
            g.dateTime.drop(5).take(11).replace('-', '.'),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
            maxLines = 1,
        )
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (g.isHome) "主" else "客",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                g.opponent,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (played) g.score else "- : -",
            fontSize = 13.sp,
            fontWeight = if (played) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when (g.result) { "胜" -> "胜"; "负" -> "负"; else -> "未赛" },
            fontSize = 11.sp,
            color = when (g.result) {
                "胜" -> MaterialTheme.colorScheme.primary
                "负" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** 阵容表行：# | 球员 | 位置 | 年龄 | 身高 | 体重 */
@Composable
private fun TeamRosterRow(
    no: String, name: String, pos: String,
    age: String, height: String, weight: String,
    header: Boolean,
) {
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val fs = if (header) 11.sp else 12.sp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = if (header) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(no, fontSize = fs, color = color, modifier = Modifier.width(26.dp), maxLines = 1)
        Text(
            name, fontSize = fs, color = color, modifier = Modifier.weight(1f),
            fontWeight = if (header) FontWeight.Normal else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(pos, fontSize = fs, color = color, modifier = Modifier.width(30.dp), maxLines = 1)
        Text(age, fontSize = fs, color = color, modifier = Modifier.width(30.dp), maxLines = 1)
        Text(height, fontSize = fs, color = color, modifier = Modifier.width(48.dp), maxLines = 1)
        Text(weight, fontSize = fs, color = color, modifier = Modifier.width(52.dp), maxLines = 1)
    }
}
