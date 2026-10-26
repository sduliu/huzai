package com.kudo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kudo.android.data.*

import com.kudo.android.ui.account.LocalWebVerification
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val trailingCountdown = Regex("""[0-9]+([^0-9]*)$""")

private fun rateLimitSubtitle(message: String, remaining: Int): String {
    val match = trailingCountdown.find(message)
    if (match == null) {
        return "$message\n\u8bf7\u5728 $remaining \u79d2\u540e\u91cd\u8bd5"
    }
    val prefix = message.substring(0, match.range.first)
    val suffix = match.groupValues[1]
    return if (remaining > 0) "$prefix$remaining$suffix"
    else "$prefix\u6b63\u5728\u91cd\u65b0\u52a0\u8f7d\u2026"
}

fun mediaUrl(value: String): String? {
    val url = "https://linux.do/".toHttpUrlOrNull()?.resolve(value.replace("{size}", "120")) ?: return null
    return url.takeIf { it.isHttps && value.isNotBlank() }?.toString()
}

@Composable
fun PageTopBar(title: String, back: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    CompositionLocalProvider(LocalToolbarActions provides true) {
        Row(Modifier.fillMaxWidth().then(if (LocalToolbarBackdrop.current == null) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
            .statusBarsPadding().heightIn(min = 60.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopBarIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", back)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, icon: ImageVector = Icons.Rounded.Forum, actionLabel: String? = null, action: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(68.dp).background(LogoYellow.copy(alpha = 0.12f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(31.dp), MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null) Button(onClick = action, colors = ButtonDefaults.buttonColors(containerColor = LogoYellow, contentColor = Ink)) { Text(actionLabel) }
    }
}

@Composable
fun ErrorState(error: Throwable, retry: () -> Unit, login: () -> Unit, back: (() -> Unit)? = null) {
    val kind = (error as? ApiFailure)?.kind
    if (kind == FailureKind.NOT_FOUND || kind == FailureKind.FORBIDDEN) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.LinkOff, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.outline)
            Text(if (kind == FailureKind.NOT_FOUND) "内容不存在或暂无权限" else "暂无访问权限", style = MaterialTheme.typography.titleMedium)
            Text(if (kind == FailureKind.NOT_FOUND) "链接可能已失效，内容已删除，或当前账号没有访问权限。" else error.message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (back != null) CommunityButton("返回上一页", back, compact = true)
            Text("刷新登录状态", Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = login).padding(10.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val auth = kind == FailureKind.SIGN_IN || kind == FailureKind.VERIFICATION
    val verification = LocalWebVerification.current
    val action = when (kind) {
        FailureKind.VERIFICATION -> { { if (verification != null) verification(retry) else login() } }
        FailureKind.SIGN_IN -> login
        else -> retry
    }
    val rateLimitRetryAfter = (error as? ApiFailure)
        ?.takeIf { it.kind == FailureKind.RATE_LIMIT }
        ?.retryAfter?.takeIf { it > 0 }
    if (rateLimitRetryAfter != null) {
        var remaining by remember(error) { mutableIntStateOf(rateLimitRetryAfter) }
        var retryStarted by remember(error) { mutableStateOf(false) }
        val triggerRetry = {
            if (!retryStarted) {
                retryStarted = true
                retry()
            }
        }
        LaunchedEffect(error) {
            repeat(rateLimitRetryAfter) {
                delay(1_000)
                remaining = (remaining - 1).coerceAtLeast(0)
            }
            triggerRetry()
        }
        EmptyState(
            "\u6682\u65f6\u6ca1\u80fd\u52a0\u8f7d",
            rateLimitSubtitle(error.message.orEmpty(), remaining),
            Icons.Rounded.CloudOff,
            "\u91cd\u65b0\u52a0\u8f7d",
            triggerRetry,
        )
        return
    }
    EmptyState(if (kind == FailureKind.VERIFICATION) "和社区连接，只差一步" else if (kind == FailureKind.SIGN_IN) "登录后继续" else "暂时没能加载",
        error.message ?: "请稍后重试", if (auth) Icons.Rounded.VerifiedUser else Icons.Rounded.CloudOff,
        if (kind == FailureKind.VERIFICATION) "完成网页验证" else if (auth) "登录虎扑" else "重新加载", action)
}

@Composable
fun LoadingRow() { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CommunityLoadingIndicator() } }

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// 批D：官方平卡复刻——全宽无圆角；作者行(仅 author 存在)→标题 16sp/Medium→图区(≥3 三图行 4:3 / 单图全宽 16:9)→底行 版块chip+👍💬。
// 官方还有神回复预览卡与↗转发数，SSR res[] 无字段不伪造；views(阅读数)官方卡不显示。
@Composable
fun TopicCard(topic: Topic, category: Category?, onClick: () -> Unit, onUser: (String) -> Unit) {
    val resolvedCategory = category ?: LocalTopicPresentation.current.categories.firstOrNull { it.id == topic.categoryId }
    val images = topic.images.ifEmpty { listOfNotNull(mediaUrl(topic.image)) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
        .clickable(onClick = onClick)
        .testTag("topic-card-${topic.id}")
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (topic.author != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(topic.author, 28.dp) { topic.author?.username?.let(onUser) }
                Text(topic.author?.displayName.orEmpty(), Modifier.weight(1f).padding(horizontal = 8.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (topic.pinned) Icon(Icons.Rounded.PushPin, "置顶", Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                Text(relativeTime(topic.bumpedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (topic.pinned && topic.author == null) Icon(Icons.Rounded.PushPin, "置顶", Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
            Text(topic.title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TopicUnreadBadge(topic)
        }
        if (topic.excerpt.isNotBlank()) Text(topic.excerpt, fontSize = 13.sp, lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            images.size >= 3 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                images.take(3).forEachIndexed { i, url ->
                    // 批G-1：视频帖三图行首格叠 ▶ 标识
                    Box(Modifier.weight(1f)) {
                        TopicFeedImage(topic.id, i, url, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        if (topic.hasVideo && i == 0) {
                            com.kudo.android.hupuui.components.VideoPlayBadge(Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
            images.isNotEmpty() -> Box {
                TopicFeedImage(topic.id, 0, images.first(), Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                // 批G-1：视频帖单图叠 ▶ 标识
                if (topic.hasVideo) com.kudo.android.hupuui.components.VideoPlayBadge(Modifier.align(Alignment.Center))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 批G-1：无图视频帖底行给「视频」chip
            if (topic.hasVideo && images.isEmpty()) {
                com.kudo.android.hupuui.components.VideoTagChip()
                Spacer(Modifier.width(6.dp))
            }
            val board = resolvedCategory?.name?.takeIf { it.isNotBlank() }
                ?: topic.tagDetails.firstOrNull()?.name?.takeIf { it.isNotBlank() }
            if (resolvedCategory != null) Box(Modifier.clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 3.dp)) { CategoryInline(resolvedCategory) }
            else if (board != null) Text(board, Modifier.clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            // 虎扑帖子常把所属版块同时放进 topic.name：版块 chip 已显示，标签不再重复
            val visibleTags = topic.tagDetails.filter { it.name != resolvedCategory?.name && it.name != board }
            if (visibleTags.isNotEmpty()) TopicTags(visibleTags, Modifier.padding(start = if (board != null) 6.dp else 0.dp))
            Spacer(Modifier.weight(1f))
            Stat(Icons.Rounded.FavoriteBorder, compactNumber(topic.likes))
            Spacer(Modifier.width(14.dp))
            Stat(Icons.Rounded.ChatBubbleOutline, compactNumber(topic.replies))
        }
    }
}

@Composable
private fun TopicFeedImage(topicId: Int, index: Int, url: String, modifier: Modifier) {
    // 定位契约：index0 仍用 "topic-image-{id}"（androidTest 既有约定），其余带槽位后缀
    val slot = if (index == 0) "$topicId" else "$topicId-$index"
    var loading by remember(url) { mutableStateOf(true) }
    var failure by remember(url) { mutableStateOf<Throwable?>(null) }
    var retry by remember(url) { mutableIntStateOf(0) }
    var ratio by remember(url) { mutableStateOf<Float?>(null) }
    Box(modifier.testTag("topic-image-$slot").clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        key(url, retry) { AsyncImage(url, "话题图片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            onLoading = { loading = true; failure = null; ratio = null },
            onError = { loading = false; failure = it.result.throwable; ratio = null },
            onSuccess = { state ->
                loading = false; failure = null
                val size = state.painter.intrinsicSize
                ratio = if (size.isSpecified && size.height > 0f) size.width / size.height else null
            }) }
        if (loading) CommunityLoadingIndicator(Modifier.align(Alignment.Center).size(18.dp).testTag("topic-image-$slot-loading"))
        val error = failure
        if (error != null) Column(Modifier.align(Alignment.Center).testTag("topic-image-$slot-error")
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "重新加载图片") { loading = true; failure = null; retry++ }
            .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("加载失败，点击重试", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(com.kudo.android.media.imageLoadErrorText(error), fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!loading && error == null) ImageAspectBadge(ratio)
    }
}

internal fun imageAspectLabel(ratio: Float?): String? = when {
    ratio == null || !ratio.isFinite() || ratio <= 0f -> null
    ratio >= 3f -> "宽图"
    ratio <= 1f / 3f -> "长图"
    else -> null
}

@Composable
internal fun BoxScope.ImageAspectBadge(ratio: Float?) {
    val label = imageAspectLabel(ratio) ?: return
    Box(Modifier.matchParentSize().padding(6.dp)) {
        Text(label, Modifier.align(Alignment.BottomEnd).testTag("image-aspect-badge")
            .background(LogoYellow.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
            color = Ink, fontSize = 12.sp, lineHeight = 16.sp,
            style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}

@Composable
fun Stat(icon: ImageVector, text: String) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(icon, null, Modifier.size(17.dp), MaterialTheme.colorScheme.onSurfaceVariant); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
