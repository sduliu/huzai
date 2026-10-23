package com.kudo.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kudo.android.data.CommunityRepository
import com.kudo.android.data.HupuIds
import com.kudo.android.hupu.HupuBoardPage
import com.kudo.android.hupuui.pages.formatCount

private val RankRed = Color(0xFFE60023)
private val RankOrange = Color(0xFFFF6B35)
private val RankGray = Color(0xFF9BA1A7)

/** 「热榜」频道：热议帖榜 + 热门版块 + 大家都在搜。
 *  数据源即官方移动站板块页内嵌 SSR（recommend/hot/trending）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HotChannelPage(
    repository: CommunityRepository,
    openTopic: (Int) -> Unit,
    openBoard: (String) -> Unit,
    openSearch: (String) -> Unit,
    /** 评分榜卡片点击 → 打开「比赛」Tab 的虎扑评分频道（score/common 二级页） */
    openRatings: () -> Unit = {},
) {
    val page by produceState<HupuBoardPage?>(initialValue = null) {
        value = repository.boardPage("/all-gambia")
    }
    // 评分榜：虎扑通用评分主题（score-home SSR，与「比赛」Tab 虎扑评分同源）
    val subjects by produceState<List<com.kudo.android.hupu.HupuCommonSubject>>(initialValue = emptyList()) {
        value = runCatching { com.kudo.android.hupu.HupuRepository().commonSubjects() }.getOrDefault(emptyList())
    }
    val p = page
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (p == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        if (subjects.isNotEmpty()) {
            SectionTitle("评分榜")
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                items(subjects.size) { i ->
                    val s = subjects[i]
                    Column(
                        Modifier.width(140.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                s.bgColorDay?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                    ?: MaterialTheme.colorScheme.surface
                            )
                            .clickable { openRatings() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.score.ifBlank { "-" }, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                            color = s.scoreColorDay?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                ?: Color(0xFFFF6B35),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatCount(s.scoreCountNum)}人评分", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (p.recommendations.isNotEmpty()) {
            SectionTitle("热帖榜")
            SectionCard {
                p.recommendations.take(10).forEachIndexed { i, t ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { openTopic(HupuIds.topicId(t.tid)) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = when (i) {
                                0 -> RankRed
                                1, 2 -> RankOrange
                                else -> RankGray
                            },
                            modifier = Modifier.width(22.dp),
                        )
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(t.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            t.topic?.name?.let {
                                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "👍${t.lights}  💬${t.replies}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (p.hotTopics.isNotEmpty()) {
            SectionTitle("热门版块")
            SectionCard {
                p.hotTopics.take(12).chunked(3).forEachIndexed { row, group ->
                    if (row > 0) Divider()
                    Row(Modifier.fillMaxWidth()) {
                        group.forEach { info ->
                            Column(
                                Modifier.weight(1f)
                                    .clickable { openBoard(info.url) }
                                    .padding(horizontal = 6.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AsyncImage(
                                    model = info.logo, contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop,
                                )
                                Text(
                                    info.name, fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                                if (info.hotText.isNotBlank()) {
                                    Text(info.hotText, fontSize = 10.sp, color = RankOrange)
                                }
                            }
                        }
                        repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (p.trending.isNotEmpty()) {
            SectionTitle("大家都在搜")
            SectionCard {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
                    p.trending.forEach { w ->
                        Box(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { openSearch(w) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(w, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🔥", fontSize = 15.sp)
        Spacer(Modifier.width(5.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
        content = { content() },
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier.fillMaxWidth().padding(start = 34.dp).height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}
