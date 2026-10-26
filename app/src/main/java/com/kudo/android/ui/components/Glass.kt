package com.kudo.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kudo.android.data.GlassMode
import com.kudo.android.ui.components.liquid.LiquidBottomTab
import com.kudo.android.ui.components.liquid.LiquidBottomTabs
import com.kudo.android.ui.components.liquid.LocalLiquidBottomTabSampling
import com.kudo.android.ui.theme.LogoYellow

@Composable
fun GlassNavigation(selected: Int, onSelect: (Int) -> Unit, backdrop: Backdrop, mode: GlassMode, modifier: Modifier = Modifier, homeHasUpdates: Boolean = false, unreadCount: Int = 0) {
    // 批Q-2：官方形态——底栏三项 首页/专区/我的；发帖仅留版块页 FAB 入口，比赛融进频道顶部比分卡
    val labels = listOf(if (homeHasUpdates) "刷新" else "首页", "专区", "我的")
    val icons = listOf(if (homeHasUpdates) Icons.Rounded.ReplayCircleFilled else Icons.Rounded.Home, Icons.Rounded.GridView, Icons.Rounded.Person)
    LiquidBottomTabs({ selected }, onSelect, backdrop, mode, labels.size, actionIndex = -1, modifier) { previewIndex ->
        val sampling = LocalLiquidBottomTabSampling.current
        labels.forEachIndexed { index, label ->
            val chosen = selected == index
            val highlighted = sampling || chosen || previewIndex == index
            LiquidBottomTab(selected = chosen, onClick = { onSelect(index) }) {
                val color = if (highlighted) LogoYellow else MaterialTheme.colorScheme.onSurface
                Box {
                    Icon(icons[index], null, Modifier.size(22.dp)
                        .then(if (index == 0 && homeHasUpdates) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier), tint = color)
                    if (index == 2) MessageCountBadge(unreadCount, Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp), sampling)
                }
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 14.sp), color = color)
            }
        }
    }
}
