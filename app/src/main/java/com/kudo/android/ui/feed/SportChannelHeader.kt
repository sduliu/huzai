package com.kudo.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.data.CommunityRepository
import com.kudo.android.hupu.HUPU_BOARDS
import com.kudo.android.hupu.HupuBoardStanding
import com.kudo.android.hupu.HupuMatch
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupuui.pages.BoardMatchCard
import java.time.LocalDate

/** 官方「JRs评分」蓝（与批S titleFont 官方蓝/批T-4 比分卡蓝同值）。 */
private val JrsBlue = androidx.compose.ui.graphics.Color(0xFF3B82F6)

/** 首页赛事频道头部（对齐官方频道页结构）：
 *  近期比赛大卡 → 「查看近期N场热门比赛 ›」→ 圆形快捷图标行（赛程/积分榜）
 *  →「资讯」+ 推荐/最新切换。赛程/积分榜入口均推开二级页，不切底栏。 */
@Composable
fun SportChannelHeader(
    sportKey: String,
    repository: CommunityRepository,
    onOpenSchedule: (String) -> Unit,
    onOpenMatch: (HupuMatch) -> Unit,
    onOpenStandings: (String) -> Unit = {},
    /** 批B-4：数据 tab 深链（sportKey → kind：player/draft/injury/team/global） */
    onOpenDataTab: (String, String) -> Unit = { _, _ -> },
    /** 批T-2：主队入口（登录态功能；本批仅提示，登录批次实现） */
    onMyTeam: () -> Unit = {},
    latest: Boolean = false,
    onLatestChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val days by produceState<List<HupuMatchDay>?>(initialValue = null, sportKey) {
        value = repository.sportMatches(sportKey)
    }
    val ds = days ?: return

    val today = remember { LocalDate.now().toString() }
    val start = remember(ds) {
        val todayIdx = ds.indexOfFirst { it.dayTime == today }
        val lastPast = ds.indexOfLast { it.dayTime <= today }
        when {
            todayIdx >= 0 -> todayIdx
            lastPast >= 0 -> lastPast
            else -> 0
        }
    }
    // 批T-1：「近期热门比赛」口径 = 进行中 ∪ 锚定日场次（官方实证：NBA 锚定日 7 场全量
    // 即热门；足球官方为服务端精选子集——匿名数据无 hot 标记，取锚定日全量为近似，
    // 偏差入架构笔记批T-0 节）。原实现取 drop(start) 全部未来日 → 计数虚高。
    val hotSet = remember(ds) {
        val live = ds.flatMap { it.matches }.filter { it.status.equals("INPROGRESS", true) }
        (live + ds.getOrNull(start)?.matches.orEmpty()).distinctBy { it.matchId }
    }
    // 卡列 = 热门集合按评分人数降序取前 4（官方卡序即评分降序；进行中优先置顶）
    val hotMatches = remember(hotSet) {
        hotSet.sortedWith(
            compareByDescending<HupuMatch> { it.status.equals("INPROGRESS", true) }
                .thenByDescending { com.kudo.android.hupu.hupuScoreCount(it.scoreCountText) }
        ).take(4)
    }
    val recentCount = hotSet.size
    val cfg = HUPU_BOARDS[sportKey]
    val hasStandings = cfg != null &&
        (cfg.standing != HupuBoardStanding.NONE || cfg.standingsUrl.isNotEmpty())

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (hotMatches.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth()
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
            // 官方同款：🔥 查看近期N场热门比赛 ›（左文右箭头，红色火焰）
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onOpenSchedule(sportKey) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "查看近期${recentCount}场热门比赛"
                    }
                    .testTag("schedule-all-link")
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🔥 查看近期${recentCount}场热门比赛",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
                )
            }
        }
        // 官方快捷图标行（批T-2 对齐官方位序）：
        //   NBA/篮球系：赛程 / 数据 / 主队 / AI工坊* / NBA游戏*（*排除类不渲染）
        //   足球系：    赛程 / 积分榜 / 主队 / 冠军经理* / 足球游戏*
        // 第二位规则：有原生数据 tab 轨道的频道给「数据」（默认落球员榜 tab）；
        // 无数据轨道（soccer getDataTabList 服务端报错为空）回落「积分榜」。
        // 主队 = 登录态聚合页，本批只留入口位 + 提示（登录批次欠账）。
        val dataKinds by produceState<Set<String>>(emptySet(), sportKey) {
            value = repository.leagueDataTabs(sportKey)
                .mapNotNull { com.kudo.android.hupuui.pages.nativeDataTabKind(it.url)?.second }
                .toSet()
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
            ShortcutIcon("赛程", { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary) }) {
                onOpenSchedule(sportKey)
            }
            if (dataKinds.isNotEmpty()) {
                val kind = listOf("player", "team", "draft", "injury").first { it in dataKinds }
                ShortcutIcon("数据", { Icon(Icons.Rounded.Leaderboard, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary) }) {
                    onOpenDataTab(sportKey, kind)
                }
            } else if (hasStandings) {
                ShortcutIcon("积分榜", { Icon(Icons.Rounded.BarChart, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary) }) {
                    onOpenStandings(sportKey)
                }
            }
            ShortcutIcon("主队", { Icon(Icons.Rounded.Shield, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary) }) {
                onMyTeam()
            }
        }
        // 「资讯」+ 推荐/最新切换（对齐官方频道资讯区：左大标题右分段胶囊）
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "资讯",
                fontSize = 17.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            // 批U-0b：官方分段胶囊——浅蓝底+蓝边框+选中蓝字（非金色无边框）
            Row(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(JrsBlue.copy(alpha = 0.10f))
                    .padding(2.dp),
            ) {
                listOf(false to "推荐", true to "最新").forEach { (v, label) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(13.dp))
                            .then(
                                if (latest == v) Modifier.background(JrsBlue.copy(alpha = 0.16f))
                                    .border(1.dp, JrsBlue, RoundedCornerShape(13.dp))
                                else Modifier
                            )
                            .clickable(role = Role.Tab) { onLatestChange(v) }
                            .semantics(mergeDescendants = true) { contentDescription = "资讯排序：$label" }
                            .testTag("feed-sort-$label")
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text(
                            label, fontSize = 12.sp,
                            fontWeight = if (latest == v) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = if (latest == v) JrsBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 批T-3：频道「资讯」帖行（官方形态）——左标题（2 行截断）+ 右缩略图 72×54，
 * 标题下 meta：`🔥 {replies}回复  {lights}亮回复`；行底 0.5dp 分隔线。
 * 数据源为 newsList（无图条目自然退化为纯文本行，与官方一致）。
 */
@Composable
internal fun SportNewsRow(topic: com.kudo.android.data.Topic, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = topic.title }
            .padding(horizontal = 12.dp)
            .testTag("sport-news-${topic.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = if (topic.image.isNotBlank()) 10.dp else 0.dp)) {
                Text(
                    topic.title,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 批U-0c：🔥 只在亮回复满 50（官方「50亮回复」封顶值=热帖口径）时出现；
                    // newsList 无显式 hot 字段——官方带🔥行共同点=亮回复 50，低亮行不带
                    if (topic.likes >= 50) {
                        Text("🔥", fontSize = 11.sp)
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(
                        "${topic.replies}回复",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${topic.likes}亮回复",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (topic.image.isNotBlank()) {
                com.kudo.android.hupuui.components.HupuIconImage(
                    model = com.kudo.android.hupuui.components.normalizeCover(topic.image),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(width = 96.dp, height = 72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ShortcutIcon(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        // 批A-4：合并语义——让 uiautomator 只看到一个「赛程/积分榜」可点节点且
        // bounds=整列触控区，而不是文字字形小框/部分可见时的 [0,0] 节点
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(vertical = 4.dp)
            .testTag("shortcut-$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
    }
}
