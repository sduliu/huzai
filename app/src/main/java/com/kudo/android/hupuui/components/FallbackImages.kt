package com.kudo.android.hupuui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil.compose.SubcomposeAsyncImage

/**
 * 批C-3：图片失败兜底。裸 `AsyncImage` 加载失败时是**透明空白洞**——
 * 头像、版块标、队徽在弱网/防盗链 403 下直接消失，列表里看不出这里本该有图。
 * 统一用 `SubcomposeAsyncImage` 的 error 槽渲染「灰底 + 语义图标」空态；
 * 签名与 `AsyncImage` 对齐（model/contentDescription/contentScale/modifier），直接替换调用点。
 *
 * 批E-2：兜底分支的「渲哪个图标」收敛为纯函数 `fallbackIcon`/`fallbackTag`，
 * JVM 单测可直接断言三类映射；error 槽图标带 `hupu-fallback-*` testTag，
 * 供 uiautomator 判定兜底确实渲染（区别于加载成功态）。
 */

/** 三类兜底：头像人形 / 版块与队徽球标 / 缩略图图片标 */
enum class HupuImageFallback { Avatar, Icon, Thumb }

internal fun fallbackIcon(kind: HupuImageFallback): ImageVector = when (kind) {
    HupuImageFallback.Avatar -> Icons.Rounded.Person
    HupuImageFallback.Icon -> Icons.Rounded.SportsBasketball
    HupuImageFallback.Thumb -> Icons.Rounded.Image
}

internal fun fallbackTag(kind: HupuImageFallback): String = when (kind) {
    HupuImageFallback.Avatar -> "hupu-fallback-avatar"
    HupuImageFallback.Icon -> "hupu-fallback-icon"
    HupuImageFallback.Thumb -> "hupu-fallback-thumb"
}

/** 灰底 + 居中图标（占框 55%），三类图共用 */
@Composable
private fun ImageFallbackBox(kind: HupuImageFallback) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            fallbackIcon(kind),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.55f).testTag(fallbackTag(kind)),
        )
    }
}

/** 头像：失败 → 人形占位（官方同款灰底人形语义） */
@Composable
fun HupuAvatarImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        error = { ImageFallbackBox(HupuImageFallback.Avatar) },
    )
}

/** 版块标/队徽：失败 → 灰底小球标 */
@Composable
fun HupuIconImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        error = { ImageFallbackBox(HupuImageFallback.Icon) },
    )
}

/** 列表缩略图：失败 → 灰底图片标（不是空白洞） */
@Composable
fun HupuThumbImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        error = { ImageFallbackBox(HupuImageFallback.Thumb) },
    )
}
