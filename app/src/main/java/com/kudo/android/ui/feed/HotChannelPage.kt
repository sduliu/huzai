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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.data.CommunityRepository
import com.kudo.android.data.HupuIds
import com.kudo.android.hupu.HupuBoardPage
import com.kudo.android.hupu.HupuHotTag
import com.kudo.android.hupu.HupuTagPage
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
    // 批Z-2：整页与热榜分别给显式失败/空态（原 boardPage 失败=无限 spinner、hotTags 空=静默缺席）
    var retryTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var pageFailed by remember { mutableStateOf(false) }
    val page by produceState<HupuBoardPage?>(initialValue = null, retryTick) {
        val p = runCatching { repository.boardPage("/all-gambia") }.getOrNull()
        pageFailed = (p == null)
        value = p
    }
    // 评分榜：虎扑通用评分主题（score-home SSR，与「比赛」Tab 虎扑评分同源）
    val subjects by produceState<List<com.kudo.android.hupu.HupuCommonSubject>>(initialValue = emptyList()) {
        value = runCatching { com.kudo.android.hupu.HupuRepository().commonSubjects() }.getOrDefault(emptyList())
    }
    // 批F-4：官方热榜（m.hupu.com/hot——微博式热搜 tag 榜，与官方「热榜」内容形态对齐）
    // 批Z-2：null=加载中；空表=为空或取数失败（仓储失败即空表，显式空态+重试）
    val hotTags by produceState<List<HupuHotTag>?>(initialValue = null, retryTick) {
        value = runCatching { repository.hotTags() }.getOrNull()
    }
    // 批F-4：tag 点击 → 聚合页盖入层（m.hupu.com/tag/{id} 匿名 SSR + tagThreads 分页）
    var openTag by remember { mutableStateOf<HupuHotTag?>(null) }
    val p = page
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (p == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                if (pageFailed) {
                    // 批Z-2：整页取数失败显式态+重试（原无限 spinner）
                    Text(
                        "加载失败，点击重试", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { retryTick++ },
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Column
        }
        // 批F-4：虎扑热榜（官方同形态：排名 + 话题词 + 🔥热度；点击进 tag 聚合页）
        val tags = hotTags
        if (tags != null && tags.isEmpty()) {
            // 批Z-2：榜单为空或取数失败 → 显式空态+重试（原静默缺席）
            SectionTitle("虎扑热榜")
            SectionCard {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "热榜暂不可用，点击重试", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { retryTick++ },
                    )
                }
            }
        }
        if (!tags.isNullOrEmpty()) {
            SectionTitle("虎扑热榜")
            SectionCard {
                tags.forEachIndexed { i, tag ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { openTag = tag }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${tag.rank}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = when (tag.rank) {
                                1 -> RankRed
                                2, 3 -> RankOrange
                                else -> RankGray
                            },
                            modifier = Modifier.width(26.dp),
                        )
                        Text(
                            tag.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                        )
                        Text(
                            "🔥${formatCount(tag.heat)}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
                        if (s.score.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                s.score, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                color = s.scoreColorDay?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                    ?: Color(0xFFFF6B35),
                            )
                        }
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
                        // 批Z-2：meta 对齐官方扁平行「{replies}回复 {lights}亮回复」；
                        // 🔥 沿用批U-0c 口径——亮回复≥50（官方「50亮回复」封顶=热帖标志）才显示
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (t.lights >= 50) {
                                Text("🔥", fontSize = 11.sp)
                            }
                            Text(
                                "${t.replies}回复  ${t.lights}亮回复",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                                com.kudo.android.hupuui.components.HupuIconImage(
                                    model = info.logo, contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(CircleShape),
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
    // 批F-4：tag 聚合页盖入层（全屏，盖在频道内容之上；返回手势/返回键收起）
    openTag?.let { tag ->
        TagFeedOverlay(
            tag = tag,
            repository = repository,
            onClose = { openTag = null },
            openThread = { tid -> tid.toIntOrNull()?.let(openTopic) },
        )
    }
    }
}

/**
 * 批F-4：tag 聚合页（官方 m.hupu.com/tag/{id} 同款信息流——
 * banner 头 + 帖数/浏览 + 帖列表 + 滚动分页）。匿名数据：SSR 首屏 + tagThreads API。
 */
@Composable
private fun TagFeedOverlay(
    tag: HupuHotTag,
    repository: CommunityRepository,
    onClose: () -> Unit,
    openThread: (String) -> Unit,
) {
    BackHandler { onClose() }
    var page by remember(tag.tagId) { mutableStateOf<HupuTagPage?>(null) }
    var loading by remember(tag.tagId) { mutableStateOf(true) }
    var failed by remember(tag.tagId) { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(tag.tagId, retryTick) {
        loading = true; failed = false
        val tp = runCatching { repository.tagPage(tag.tagId) }.getOrNull()
        if (tp != null) page = tp else failed = true
        loading = false
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶栏：返回 + 话题名
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                "#${page?.info?.name?.ifBlank { tag.name } ?: tag.name}",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败，点击重试", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { retryTick++ })
            }
            else -> {
                val info = page!!.info
                val listState = rememberLazyListState()
                val threads = page!!.threads
                val canLoadMore = page!!.nextPage
                val shouldLoadMore by remember(threads.size, canLoadMore, loadingMore) {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        canLoadMore && !loadingMore && last >= listState.layoutInfo.totalItemsCount - 4
                    }
                }
                androidx.compose.runtime.LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) {
                        loadingMore = true
                        val next = runCatching { repository.tagThreads(tag.tagId, page!!.page + 1) }.getOrNull()
                        if (next != null) {
                            page = next.copy(
                                info = info,
                                threads = (threads + next.threads).distinctBy { it.tid },
                            )
                        }
                        loadingMore = false
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                ) {
                    // banner 头（官方 banner + bannerRgb 兜底色）+ 帖数/浏览
                    item(key = "banner") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .background(
                                    info.bannerRgb.takeIf { it.isNotBlank() }
                                        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                        ?: MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            if (info.banner.isNotBlank()) {
                                com.kudo.android.hupuui.components.HupuThumbImage(
                                    model = info.banner, contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(
                                Modifier.align(Alignment.BottomStart).padding(14.dp),
                            ) {
                                Text(
                                    "#${info.name.ifBlank { tag.name }}",
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${formatCount(info.tNum)}帖子 · ${formatCount(info.pv)}浏览",
                                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f),
                                )
                            }
                        }
                    }
                    itemsIndexed(threads, key = { _, t -> t.tid }) { _, t ->
                        TagThreadRow(t) { openThread(t.tid) }
                        Divider()
                    }
                    if (loadingMore || canLoadMore) {
                        item(key = "more") {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loadingMore) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Text("上拉加载更多", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 批F-4：tag 帖行——头像+昵称+时间 / 标题 / 摘要2行 / 右小图 / 计数（👍💬↗） */
@Composable
private fun TagThreadRow(t: com.kudo.android.hupu.HupuTagThread, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (t.header.isNotBlank()) {
                com.kudo.android.hupuui.components.HupuAvatarImage(
                    model = t.header, contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(t.userName.ifBlank { "虎扑JR" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (t.time.isNotBlank()) {
                Text(t.time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (t.summary.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(t.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            t.pics.firstOrNull()?.let { pic ->
                Spacer(Modifier.width(10.dp))
                com.kudo.android.hupuui.components.HupuThumbImage(
                    model = pic, contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
            Text("👍${formatCount(t.recommendCount.toLong())}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("💬${formatCount(t.replies.toLong())}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("↗${formatCount(t.shareNum.toLong())}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
