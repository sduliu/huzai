package com.kudo.android.ui.topic

import com.kudo.android.ui.components.TopBarIconButton
import com.kudo.android.ui.components.TopBarTextButton

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.data.*
import com.kudo.android.ui.components.Avatar
import com.kudo.android.ui.components.AvatarPresentation
import com.kudo.android.ui.components.CategoryIcons
import com.kudo.android.ui.components.MemberNameDecorations
import com.kudo.android.ui.components.TopicAuthorMetadata
import com.kudo.android.ui.components.liquid.LiquidButton
import com.kudo.android.ui.theme.LogoYellow
import com.kudo.android.ui.theme.LikeRed
import com.kudo.android.ui.theme.Ink
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import kotlin.math.roundToInt

@Composable
internal fun TopicTopBar(
    state: TopicState, enabled: Boolean, back: () -> Unit, user: (String) -> Unit,
    follow: () -> Unit, more: () -> Unit,
) {
    val author = state.detail?.author
    val profile = state.authorProfile
    Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 60.dp).padding(horizontal = 8.dp, vertical = 6.dp)
        .testTag("topic-top-bar"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TopBarIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", back, enabled = enabled)
        if (author == null) Text("话题详情", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        else Row(Modifier.weight(1f).heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button) { user(author.username) }.testTag("topic-author"),
            verticalAlignment = Alignment.CenterVertically) {
            Avatar(author, 32.dp, presentation = AvatarPresentation.PUBLIC)
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(author.displayName, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                    MemberNameDecorations(author)
                }
                TopicAuthorMetadata(state.detail.originalPost?.updatedAt ?: state.detail.createdAt,
                    category = null, edited = state.detail.originalPost?.edited == true,
                    history = state.detail.originalPost?.takeIf { enabled && it.hasEditHistory }?.let { post ->
                        val actions = LocalTopicActions.current
                        { actions.history(state.detail.id, post) }
                    })
            }
        }
        if (author != null && author.id != state.viewerId &&
            (state.viewerId == null || profile == null || profile.canFollow || profile.isFollowed)) {
            val canClick = enabled && !state.loading && !state.followBusy && (state.viewerId == null || !state.authorLoading)
            TopBarTextButton(if (state.authorError != null && state.viewerId != null) "重试" else if (profile?.isFollowed == true) "已关注" else "关注",
                follow, Modifier.testTag("topic-follow"), enabled = canClick)
        }
        TopBarIconButton(Icons.Rounded.MoreVert, "更多", more, enabled = enabled && state.detail != null)
    }
}

@Composable
internal fun TopicReactionButton(
    post: Post?, enabled: Boolean, choose: (() -> Unit)?, click: () -> Unit,
    modifier: Modifier = Modifier, bottom: Boolean = false,
) {
    val reaction = post?.activeReaction
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f * if (enabled) 1f else .38f)
    val label = if (bottom) {
        if (reaction != null && reaction != "heart") "取消主帖回应" else "赞主帖"
    } else "回应此楼层"
    Row(modifier.clip(RoundedCornerShape(50))
        .combinedClickable(enabled = enabled, role = Role.Button,
            onClickLabel = if (reaction == null) "点赞" else "取消回应", onClick = click,
            onLongClickLabel = "选择回应", onLongClick = choose)
        .heightIn(min = 48.dp).widthIn(min = 48.dp)
        .semantics(mergeDescendants = true) {
            contentDescription = label
            stateDescription = when {
                reaction == null -> "未回应"
                post?.canReact == false -> "已回应 $reaction，无法撤销"
                reaction == "heart" -> "已点赞，短按取消"
                else -> "已回应 $reaction，短按取消"
            }
        }.padding(horizontal = if (bottom) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val size = if (bottom) 22.dp else 19.dp
        if (reaction != null && reaction != "heart") SiteEmoji(reaction, Modifier.size(size))
        else Icon(if (reaction == "heart") Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null,
            Modifier.size(size), tint = if (reaction == "heart") LikeRed else inactiveColor)
        if (!bottom) {
            Spacer(Modifier.width(5.dp))
            Text(post?.reactionCount?.toString().orEmpty(), style = MaterialTheme.typography.labelLarge,
                color = if (reaction == null) inactiveColor else LocalContentColor.current)
        }
    }
}

/**
 * 批Nfix-3：帖子详情底部**通栏固定底栏**（官方形态，替代悬浮液态按钮组）。
 * 内容对齐官方「页码 | 我来评论 | 点亮 | 评论 | 转发」+ 保留「收藏」（本端收藏主帖
 * 的可达入口）。楼层编辑态整栏替换为 FloorNavigationBar（动画仅透明度交叉，不再
 * 三段滑出）。正文滚动在栏顶硬切——滚动中途无半透明浮层盖内容。
 */
@Composable
internal fun TopicBottomBar(
    state: TopicState, backdrop: Backdrop, glass: GlassMode, enabled: Boolean,
    reply: () -> Unit, like: (Post) -> Unit, bookmark: (Post) -> Unit, share: () -> Unit,
    choose: (Post) -> Unit, modifier: Modifier = Modifier,
    boost: (Post) -> Unit = {},
    currentFloor: Int = 1, floors: () -> Unit = {},
    floorEditing: Boolean = false, floorRaw: String = currentFloor.toString(),
    floorChange: (String) -> Unit = {}, floorHighest: Int = 1,
    floorJumping: Boolean = false, floorError: String? = null, floorConfirm: (Int) -> Unit = {},
    floorClose: () -> Unit = {}, floorEnabled: Boolean = true,
) {
    val detail = state.detail ?: return
    val first = detail.originalPost
    val actions = LocalTopicActions.current
    val ownFirst = first?.let { state.viewerId != null && it.author.id == state.viewerId } == true
    val available = enabled && !floorEditing && !state.loading && first != null && first.id !in state.busy
    val replying = state.replyingUsers
    val floor = currentFloor.coerceIn(1, detail.highestPostNumber)
    val transition = updateTransition(floorEditing, label = "floor-controls")
    val expansion by transition.animateFloat(transitionSpec = { tween(280, easing = FastOutSlowInEasing) }, label = "expansion") {
        if (it) 1f else 0f
    }
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().then(if (floorEditing) Modifier.imePadding() else Modifier)) {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.fillMaxWidth()) {
                // ---------- 常态栏（编辑态淡出） ----------
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                    .graphicsLayer { alpha = 1f - expansion }
                    .then(if (floorEditing) Modifier.clearAndSetSemantics {} else Modifier),
                    verticalAlignment = Alignment.CenterVertically) {
                    // 页码（官方最左「1/2430」，点击开楼层跳转）
                    if (floorEnabled) Text("$floor / ${detail.highestPostNumber}",
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = enabled && !floorEditing && !state.loading, role = Role.Button) { floors() }
                            .padding(horizontal = 4.dp, vertical = 10.dp).testTag("topic-floor-index")
                            .semantics {
                                contentDescription = "楼层索引，当前第 $floor 楼，共 ${detail.highestPostNumber} 楼"
                                progressBarRangeInfo = ProgressBarRangeInfo(floor.toFloat(), 1f..detail.highestPostNumber.toFloat())
                            },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    // 「我来评论」灰底输入样式框（官方中间项，点击开回复编辑器）
                    Box(Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
                        .clickable(enabled = enabled && !floorEditing && !detail.closed, role = Role.Button) { reply() }
                        .padding(horizontal = 14.dp).testTag("topic-reply").semantics {
                            contentDescription = "我来评论"
                            if (replying.isNotEmpty()) stateDescription = "${replying.size}人正在回复"
                        }, contentAlignment = Alignment.CenterStart) {
                        if (replying.isEmpty()) Text("我来评论", Modifier.testTag("topic-reply-icon"),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        else ReplyingAvatars(replying, false, Modifier)
                    }
                    // 点亮（官方「亮了」= 主帖回应）
                    TopicReactionButton(first, available && (state.viewerId == null || first?.canReact == true),
                        choose = if (first?.reactionsAvailable == true && detail.validReactions.isNotEmpty()) ({ choose(first) }) else null,
                        click = { first?.let(like) }, bottom = true)
                    // 评论数
                    Row(Modifier.testTag("topic-reply-count").padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ModeComment, "回复数", Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f * LocalContentColor.current.alpha))
                        Text((detail.highestPostNumber - 1).coerceAtLeast(0).toString(),
                            Modifier.padding(start = 3.dp), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f * LocalContentColor.current.alpha))
                    }
                    // 转发（数有数据显示数，无数据只显图标；动作为系统分享不变）
                    Row(Modifier.testTag("topic-share").clickable(enabled = enabled, role = Role.Button) { share() }
                        .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Share, "分享帖子", Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f * LocalContentColor.current.alpha))
                        if (detail.shareNum >= 0) Text(detail.shareNum.toString(), Modifier.padding(start = 3.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f * LocalContentColor.current.alpha))
                    }
                    // 收藏保留（官方底栏无此项——本端收藏主帖的可达入口，如实记差）
                    IconButton(onClick = { first?.let(bookmark) }, enabled = available, modifier = Modifier.testTag("topic-bookmark")) {
                        Icon(if (first?.bookmarkId != null) Icons.Rounded.Star else Icons.Rounded.StarBorder, "收藏主帖",
                            Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f * LocalContentColor.current.alpha))
                    }
                    if (ownFirst) {
                        IconButton(onClick = { first?.let { actions.edit(detail.id, it) } },
                            enabled = available && first?.editableBy(state.viewerId) == true, modifier = Modifier.testTag("topic-edit")) {
                            Icon(Icons.Rounded.Edit, "编辑主帖", Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f * LocalContentColor.current.alpha))
                        }
                    }
                }
                // ---------- 楼层编辑态（整栏替换） ----------
                if (transition.currentState || transition.targetState) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).graphicsLayer { alpha = expansion }) {
                        FloorNavigationBar(floorRaw, floorChange, floorHighest, floorJumping, floorError, floorConfirm,
                            backdrop, glass, Modifier.fillMaxWidth(), openingFloor = currentFloor, close = floorClose)
                    }
                }
            }
        }
    }
}

// 批A-1：回复排序条（对齐官方「最亮/最早/最晚/楼主」四键）。
// 官方是服务端分页排序；这里只重排/过滤已加载楼层——选中非默认排序时显式提示，不伪造结果。
internal val REPLY_SORTS = listOf("最亮", "最早", "最晚", "楼主")

@Composable
internal fun ReplySortBar(sort: Int, replies: Int, onSort: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("reply-sort-bar")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("回复 $replies", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            REPLY_SORTS.forEachIndexed { index, label ->
                val selected = sort == index
                Text(label, Modifier.clip(RoundedCornerShape(50))
                    .background(if (selected) LogoYellow.copy(alpha = .22f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                    .clickable(role = Role.Button) { onSort(index) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "回复排序：$label"
                        stateDescription = if (selected) "已选择" else "未选择"
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("reply-sort-$label"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Ink else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (sort != 1) Text("排序仅作用于已加载楼层", Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReplyingAvatars(users: List<Member>, compact: Boolean, modifier: Modifier) {
    val visible = users.take(3)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
    val size = minOf(if (compact) 20.dp else 24.dp, maxHeight, maxWidth)
    val step = if (visible.size == 1) 0.dp else minOf(if (compact) 10.dp else 16.dp,
        ((maxWidth - size) / (visible.size - 1)).coerceAtLeast(0.dp))
    Box(Modifier.width(size + step * (visible.size - 1)).height(size).testTag("topic-replying-avatars")) {
        visible.forEachIndexed { index, member ->
            Box(Modifier.offset(x = step * index).size(size).clip(CircleShape)
                .testTag("topic-replying-avatar-${member.id}")) {
                Avatar(member, size, presentation = AvatarPresentation.PLAIN)
                if (index == 2 && users.size > 3) {
                    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .72f)), contentAlignment = Alignment.Center) {
                        BasicText("+${users.size - 3}", Modifier.padding(horizontal = 1.dp).testTag("topic-replying-overflow"),
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Normal),
                            maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 10.sp, stepSize = .5.sp))
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun TopicReactionChoices(post: Post, choices: List<String>, enabled: Boolean, select: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { reaction ->
            val selected = post.activeReaction == reaction
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .background(if (selected) LogoYellow.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                .clickable(enabled = enabled, role = Role.Button) { select(reaction) }
                .semantics(mergeDescendants = true) {
                    contentDescription = "回应 $reaction"
                    stateDescription = if (selected) "已选择，短按取消" else "未选择"
                }.testTag("reaction-choice-$reaction"), contentAlignment = Alignment.Center) {
                SiteEmoji(reaction, Modifier.size(28.dp))
            }
        }
    }
}
