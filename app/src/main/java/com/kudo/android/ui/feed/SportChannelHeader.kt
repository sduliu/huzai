package com.kudo.android.ui.feed

import androidx.compose.foundation.background
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
    val hotMatches = remember(ds) {
        val live = ds.flatMap { it.matches }.filter { it.status.equals("INPROGRESS", true) }
        (live + ds.drop(start).flatMap { it.matches }).distinctBy { it.matchId }.take(4)
    }
    val recentCount = remember(ds) { ds.drop(start).flatMap { it.matches }.size }
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
            // 官方同款：🔥 查看近期N场热门比赛 ›
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onOpenSchedule(sportKey) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "查看近期${recentCount}场热门比赛"
                    }
                    .testTag("schedule-all-link")
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    "🔥 查看近期${recentCount}场热门比赛",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.weight(1f))
            }
        }
        // 官方快捷图标行：圆形图标 + 下方文字（赛程必有；积分榜按频道配置出现）
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
            ShortcutIcon("赛程", { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary) }) {
                onOpenSchedule(sportKey)
            }
            if (hasStandings) {
                ShortcutIcon("积分榜", { Icon(Icons.Rounded.BarChart, null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary) }) {
                    onOpenStandings(sportKey)
                }
            }
        }
        // 「资讯」+ 推荐/最新切换（对齐官方频道资讯区）
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "资讯",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(2.dp),
            ) {
                listOf(false to "推荐", true to "最新").forEach { (v, label) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (latest == v) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(role = Role.Tab) { onLatestChange(v) }
                            .semantics(mergeDescendants = true) { contentDescription = "资讯排序：$label" }
                            .testTag("feed-sort-$label")
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text(
                            label, fontSize = 12.sp,
                            fontWeight = if (latest == v) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = if (latest == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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
