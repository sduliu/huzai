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
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

/**
 * 批C-3：图片失败兜底。裸 `AsyncImage` 加载失败时是**透明空白洞**——
 * 头像、版块标、队徽在弱网/防盗链 403 下直接消失，列表里看不出这里本该有图。
 * 统一用 `SubcomposeAsyncImage` 的 error 槽渲染「灰底 + 语义图标」空态；
 * 签名与 `AsyncImage` 对齐（model/contentDescription/contentScale/modifier），直接替换调用点。
 */

/** 灰底 + 居中图标（占框 55%），三类图共用 */
@Composable
private fun ImageFallbackBox(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { content() }
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
        error = {
            ImageFallbackBox {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.55f),
                )
            }
        },
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
        error = {
            ImageFallbackBox {
                Icon(
                    Icons.Rounded.SportsBasketball,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.55f),
                )
            }
        },
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
        error = {
            ImageFallbackBox {
                Icon(
                    Icons.Rounded.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.55f),
                )
            }
        },
    )
}
