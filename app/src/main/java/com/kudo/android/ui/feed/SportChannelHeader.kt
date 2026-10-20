package com.kudo.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.data.CommunityRepository
import com.kudo.android.hupu.HupuMatch
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupuui.pages.BoardMatchRow
import java.time.LocalDate

/** 首页赛事频道头部：近期比赛（进行中→今天/最近比赛日→未来，≤4 场）+ 「查看全部赛程」。
 *  赛程来源与「比赛」Tab 同一条 matchSchedule；行复用板块页的紧凑比赛行。
 *  点击行/全部赛程 → 回调由上层落到「比赛」Tab（详情/积分榜等能力全部在那边）。 */
@Composable
fun SportChannelHeader(
    sportKey: String,
    repository: CommunityRepository,
    onOpenSchedule: (String) -> Unit,
    onOpenMatch: (HupuMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days by produceState<List<HupuMatchDay>?>(initialValue = null, sportKey) {
        value = repository.sportMatches(sportKey)
    }
    val ds = days ?: return
    if (ds.isEmpty()) return

    val today = remember { LocalDate.now().toString() }
    val hotMatches = remember(ds) {
        val live = ds.flatMap { it.matches }.filter { it.status.equals("INPROGRESS", true) }
        val todayIdx = ds.indexOfFirst { it.dayTime == today }
        val lastPast = ds.indexOfLast { it.dayTime <= today }
        val start = when {
            todayIdx >= 0 -> todayIdx
            lastPast >= 0 -> lastPast
            else -> 0
        }
        (live + ds.drop(start).flatMap { it.matches }).distinctBy { it.matchId }.take(4)
    }
    if (hotMatches.isEmpty()) return

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "近期比赛",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenSchedule(sportKey) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("查看全部赛程", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            hotMatches.forEach { m -> BoardMatchRow(m) { onOpenMatch(m) } }
        }
    }
}
