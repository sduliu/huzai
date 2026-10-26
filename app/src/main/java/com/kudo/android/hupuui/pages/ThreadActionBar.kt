package com.kudo.android.hupuui.pages

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.hupuui.components.HupuIcons
import com.kudo.android.hupuui.components.formatCount

/**
 * 批N-3：帖子详情底部**通栏固定底栏**（官方形态，替代 1.189 的悬浮毛玻璃药丸）。
 *
 * 官方底栏内容：「页码 | 我来评论 | 👍点赞数 | 💬评论数 | ↗转发」。
 * 本端差异（如实）：
 * - 转发计数走 `api/v2/bbs-thread/{tid}` 惰性补齐（shareCount<0 时只显图标不显数）；
 * - 「收藏」官方不在底栏，但本端收藏仅此一处可达 → 保留为转发右侧一项（功能不丢）。
 *
 * 视觉：满宽不悬浮、不透明 surface + 顶部 0.5dp 细分隔线，贴底（上层调用方负责
 * navBars/药丸避让 padding 与出入场动画，均沿用原悬浮条同一套）。
 */
@Composable
internal fun ThreadActionBar(
    /** 「1/2430」页码（replyPage/replyTotalPages 由调用方格式化传入） */
    pageText: String = "",
    replyCount: Int,
    recommendCount: Int,
    /** 批N-3：转发数（bbs-thread 补齐；-1=未取到，只显图标） */
    shareCount: Int = -1,
    isRecommended: Boolean,
    isCollected: Boolean,
    onWrite: () -> Unit,
    onJumpComments: () -> Unit,
    onRecommend: () -> Unit,
    onCollect: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth()) {
        // 顶部分隔线（官方通栏与内容的分界）
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 页码（官方底栏左端「1/2430」）
            if (pageText.isNotEmpty()) {
                Text(
                    pageText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.width(10.dp))
            }
            // 「我来评论」输入框样式入口（官方通栏中段灰底圆角框）
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .clickable { onWrite() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    HupuIcons.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "我来评论",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(10.dp))
            BarCount(HupuIcons.Light, formatCount(recommendCount), isRecommended, activeColor, onRecommend)
            BarCount(HupuIcons.Comment, formatCount(replyCount), false, Color.Unspecified, onJumpComments)
            // 转发：bbs-thread 补齐计数；未取到只显图标
            BarCount(
                HupuIcons.IosShare,
                if (shareCount >= 0) formatCount(shareCount) else "",
                false, Color.Unspecified, onShare,
            )
            // 收藏保留（本端唯一可达入口；官方底栏无此项，见头注释）
            BarCount(
                HupuIcons.StarRate,
                "",
                isCollected,
                activeColor,
                onCollect,
            )
        }
    }
}

/** 底栏单个图标项：图标 + 可选计数（下方小字，空串则不显示） */
@Composable
private fun BarCount(
    icon: ImageVector,
    caption: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    iconSize: Dp = 20.dp,
) {
    val normal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val tint = if (active) activeColor else normal
    Column(
        Modifier
            .width(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        if (caption.isNotEmpty()) {
            Text(
                caption,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = tint,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
