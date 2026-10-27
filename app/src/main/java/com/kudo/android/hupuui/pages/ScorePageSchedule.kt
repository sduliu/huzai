package com.kudo.android.hupuui.pages

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.kudo.android.hupuui.components.AutoFitScore
import com.kudo.android.hupuui.components.animateChipCenterTo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kudo.android.hupu.HupuCommonSubject
import com.kudo.android.hupu.HupuCommonTree
import com.kudo.android.hupu.HupuImage
import com.kudo.android.hupu.HupuMatch
import com.kudo.android.hupu.HupuMatchApi
import com.kudo.android.hupu.HupuPrefs
import com.kudo.android.hupu.isLocalStickerUrl
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupu.HupuMatchTeam
import com.kudo.android.hupu.HupuRepository
import com.kudo.android.hupu.HupuUiSignals
import com.kudo.android.hupu.HupuScoreGroup
import com.kudo.android.hupu.HupuScoreTree
import com.kudo.android.hupu.HupuAccount
import com.kudo.android.hupu.HupuSelfDetail
import com.kudo.android.hupu.GrandExpandState
import com.kudo.android.hupu.flattenWithDescendants
import com.kudo.android.hupu.ScoreCommentState
import com.kudo.android.hupu.mergeWithOptimistic
import com.kudo.android.hupuui.components.Chip
import com.kudo.android.hupuui.components.ErrorRetry
import com.kudo.android.hupuui.components.PageHeader
import com.kudo.android.hupuui.components.SecondaryPage
import com.kudo.android.hupuui.components.SkeletonHome
import com.kudo.android.hupuui.components.normalizeCover
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.kudo.android.hupuui.components.HupuIconImage

/** 赛程列表（按日分组、按日期升序）。默认定位到今天：当天有比赛显示当天，
 *  当天无比赛显示最近的过往比赛日（更早才全部是未来赛程时取第一天）；
 *  上滑自然查看过往、下滑查看未来。刷新后重新定位。 */
@Composable
internal fun ScheduleList(
    days: List<HupuMatchDay>,
    resetKey: String,
    /** 1.198：浮空顶栏时的顶部内边距（关闭该效果时为 0，布局与原来完全一致） */
    topInset: Dp = 0.dp,
    onOpenMatch: (HupuMatch) -> Unit,
) {
    val today = remember { java.time.LocalDate.now().toString() }
    // 批X6-1：item=日分区（组头灰条+当日行），锚点即锚定日的下标
    fun targetItemIndex(): Int {
        var todayIdx = -1
        var lastPastIdx = -1
        days.forEachIndexed { i, d ->
            if (d.dayTime == today) todayIdx = i
            if (d.dayTime <= today) lastPastIdx = i
        }
        return when {
            todayIdx >= 0 -> todayIdx       // 当天有比赛
            lastPastIdx >= 0 -> lastPastIdx // 当天无比赛 → 最近的过往日（含今天之前最后一天）
            else -> 0                       // 全是未来赛程 → 第一天
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetItemIndex())
    // 数据变化（首次加载/切换项目/下拉刷新）→ 重新定位到目标日期
    LaunchedEffect(days, resetKey) {
        listState.scrollToItem(targetItemIndex())
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp + topInset, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        days.forEach { day ->
            item(key = "day-${day.dayTime}") {
                ScheduleDaySection(day, onOpenMatch = onOpenMatch)
            }
        }
    }
}

/** 批X6-1：官方赛程日分区——通栏灰条组头（dateBlock 服务端直出「昨天 周一/
 *  10月29日 周四」）+ 当日比赛行平铺；组头负向 padding 抵消列表两侧 contentPadding
 *  成全宽灰条，行容器无圆角卡片。 */
@Composable
internal fun ScheduleDaySection(
    day: HupuMatchDay,
    onOpenMatch: (HupuMatch) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = (-16).dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                day.dateBlock,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            )
        }
        day.matches.forEach { m ->
            MatchRow(m, onOpen = onOpenMatch)
        }
    }
}

/** 官方赛程中心紧凑行：左「开赛时间+赛事名」、中「队徽+队名+比分」两行、右「状态+评分人数」。 */
@Composable
internal fun MatchRow(m: HupuMatch, onOpen: (HupuMatch) -> Unit) {
    val openable = m.scoreBizNo != null || m.scoreBizType == HupuMatchApi.FOOTBALL_MATCH
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (openable) Modifier.clickable { onOpen(m) } else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左列：开赛时间（HH:mm）+ 赛事名
        Column(Modifier.width(60.dp)) {
            Text(
                m.startTimeText.substringAfter(' ', m.startTimeText),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                m.introduction.ifBlank { m.matchName },
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // 中列：两队迷你行（多队赛事显示单场赛事名）
        val multiTeam = (m.home == null && m.away == null) ||
            (m.home?.name.isNullOrBlank() && m.away?.name.isNullOrBlank())
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (multiTeam) {
                Text(
                    m.matchName.ifBlank { m.introduction },
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                MiniTeamScore(m.home, m.winnerMemberId == m.home?.memberId)
                Spacer(Modifier.height(6.dp))
                MiniTeamScore(m.away, m.winnerMemberId == m.away?.memberId)
            }
        }
        // 右列：状态 + 评分人数
        val subscribable = m.subscribe?.show == true
        Column(horizontalAlignment = Alignment.End) {
            // 批V-2：官方未开始行右列=「🔔预约」单入口（订阅位），不再重复显示「未开始」状态字；
            // 进行中仍显示状态蓝字。
            if (!subscribable || m.status == "INPROGRESS") {
                Text(
                    m.statusDesc, fontSize = 11.sp,
                    color = if (m.status == "INPROGRESS") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 批U-5：官方赛程行右侧评分文案为万制「N万人评分」——"9223评分"→"9223人评分"、
            // "15.6万人评分"→"15.6万人评分"；非计数文案原样显示。
            val countText = com.kudo.android.hupu.hupuWanScoreText(m.scoreCountText)
                .ifEmpty { m.scoreCountText }
            if (countText.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(countText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 批B-2：预约占位——官方在未开赛行给「预约」入口（subscribeInfo.showSubscribe）；
            // 订阅是写接口（需登录态），这里只做展示占位，不伪造已约态。
            // 批F-2/批G-3：点 chip 弹只读对话框，标题/正文=官方 subScribeTitle/subScribeDesc
            // 真实字段，不发任何订阅/写请求（匿名可达范围只读）。
            // 批V-2：官方形态=铃铛图标+「预约」蓝字（非填充胶囊）。
            if (subscribable) {
                var showSubscribe by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showSubscribe = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("预约", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
                if (showSubscribe) {
                    AlertDialog(
                        onDismissRequest = { showSubscribe = false },
                        title = { Text(m.subscribe.title.ifBlank { "预约提醒" }, fontSize = 16.sp) },
                        text = { Text(m.subscribe.desc.ifBlank { "暂无预约说明" }, fontSize = 14.sp) },
                        confirmButton = {
                            Text(
                                "知道了", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showSubscribe = false }.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 紧凑行内的队伍行：小logo + 队名 + 右对齐比分（胜方加粗） */
@Composable
private fun MiniTeamScore(t: HupuMatchTeam?, isWinner: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HupuIconImage(
            model = normalizeCover(t?.logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(18.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            t?.name ?: "", fontSize = 13.sp,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            t?.baseScore ?: "", fontSize = 14.sp,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 单场比赛卡：队伍比分行（官方式：只比分，不带选手评分卡）。
 * 有评分钥匙的已完赛/进行中比赛仍可点进详情——详情页内保留评分体系。 */
@Composable
internal fun MatchCard(m: HupuMatch, onOpen: (HupuMatch) -> Unit) {
    // 电竞：scoreBizNo 由赛程直接给出；国际足球：赛程无 outBizNo，开赛后（进行中/已结束）
    // 才挂 football_match 类型，点击时再换取钥匙——两类都视为可点
    val openable = m.scoreBizNo != null || m.scoreBizType == HupuMatchApi.FOOTBALL_MATCH
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (openable) Modifier.clickable { onOpen(m) } else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 队伍比分行（左队靠左、右队靠右，与比分等距对称）；
        // 多队赛事（和平精英/绝地求生/奥运团体赛等）：memberInfos 是空占位 → home/away 为 null，
        // 走「赛事名居中」形态，不渲染空白队伍槽
        // 多队赛事（和平精英/绝地求生/奥运团体赛等）：memberInfos 是空占位 → 无有效队伍，
        // 走「赛事名居中」形态，不渲染空白队伍槽；双保险：即使队伍对象存在但双方都无名字，
        // 同样按多队形态处理
        val multiTeam = (m.home == null && m.away == null) ||
            (m.home?.name.isNullOrBlank() && m.away?.name.isNullOrBlank())
        if (multiTeam) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(2.dp))
                Text(
                    m.matchName.ifBlank { m.introduction },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // 状态行（多队赛事显示完整开始时间更有信息量）
                Text(
                    m.startTimeText.ifBlank { m.statusDesc },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamScore(m.home, isWinner = m.winnerMemberId == m.home?.memberId && m.home != null, alignEnd = false, modifier = Modifier.weight(1f))
            // 中间比分区
            // 1.199b（真机反馈）：比分在空间不够时会被挤到第二行 →
            // 这里给比分区一个**宽度上限**（长队名不再被它挤没），并让比分**自适应字号**
            // （放不下就逐级缩小，绝不换行、不裁切）。
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 132.dp),
            ) {
                if (m.home?.baseScore?.isNotEmpty() == true && m.away?.baseScore?.isNotEmpty() == true) {
                    AutoFitScore(
                        text = "${m.home.baseScore} : ${m.away.baseScore}",
                        maxFontSize = 20f,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        m.startTimeText,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                // 状态行并入开始时间（仅取 HH:mm 部分；未开赛的卡中间已显示 - : -，时间不重复）
                val timeOnly = m.startTimeText.substringAfter(' ', "")
                val statusText = if (timeOnly.isNotBlank()) "$timeOnly · ${m.statusDesc}" else m.statusDesc
                Text(
                    statusText,
                    fontSize = 11.sp,
                    // 只有「进行中」用主题色标识；未开始 / 已结束 / 取消一律弱化灰，
                    // 保持「未开始」与「已结束」观感一致
                    color = if (m.status == "INPROGRESS") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            TeamScore(m.away, isWinner = m.winnerMemberId == m.away?.memberId && m.away != null, alignEnd = true, modifier = Modifier.weight(1f))
        }
        } // end else (1v1 队伍比分行)
        // 赛事名（如 LPL第三赛段组内赛）
        if (m.introduction.isNotBlank()) {
            Text(
                m.introduction,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 队伍：logo + 名称（胜方加粗；镜像参数控制左/右槽位对齐方式） */
@Composable
internal fun TeamScore(
    t: HupuMatchTeam?,
    isWinner: Boolean,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        // 右槽位镜像：logo 移到名字右侧
        if (alignEnd) {
            Text(
                t?.name ?: "",
                fontSize = 15.sp,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            HupuIconImage(
                model = normalizeCover(t?.logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
        } else {
            HupuIconImage(
                model = normalizeCover(t?.logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                t?.name ?: "",
                fontSize = 15.sp,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
