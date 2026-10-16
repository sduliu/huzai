package com.kudo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.kudo.android.data.NavigationAction
import com.kudo.android.data.GlassMode
import com.kudo.android.ui.components.liquid.LiquidBottomTab
import com.kudo.android.ui.components.liquid.LiquidBottomTabs
import com.kudo.android.ui.components.liquid.LocalLiquidBottomTabSampling
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow

@Composable
fun GlassNavigation(selected: Int, onSelect: (Int) -> Unit, onCompose: () -> Unit, backdrop: Backdrop, mode: GlassMode, modifier: Modifier = Modifier, homeHasUpdates: Boolean = false, action: NavigationAction = NavigationAction.COMPOSE, unreadCount: Int = 0) {
    val labels = listOf(if (homeHasUpdates) "刷新" else "首页", "分类", "发布", "消息", "我的")
    val icons = listOf(if (homeHasUpdates) Icons.Rounded.ReplayCircleFilled else Icons.Rounded.Home, Icons.Rounded.GridView, if (action == NavigationAction.SEARCH) Icons.Rounded.Search else Icons.Rounded.Add, Icons.Rounded.Explore, Icons.Rounded.Person)
    LiquidBottomTabs({ selected }, onSelect, backdrop, mode, labels.size, actionIndex = 2, modifier) { previewIndex ->
        val sampling = LocalLiquidBottomTabSampling.current
        labels.forEachIndexed { index, label ->
            val chosen = selected == index
            val highlighted = sampling || chosen || previewIndex == index
            LiquidBottomTab(selected = chosen, onClick = { if (index == 2) onCompose() else onSelect(index) }) {
                if (index == 2) {
                    Box(Modifier.size(width = 44.dp, height = 34.dp).background(LogoYellow, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icons[index], if (sampling) null else if (action == NavigationAction.SEARCH) "搜索" else "发布话题", Modifier.size(24.dp), tint = Ink)
                    }
                } else {
                    val color = if (highlighted) LogoYellow else MaterialTheme.colorScheme.onSurface
                    Box {
                        Icon(icons[index], null, Modifier.size(22.dp)
                            .then(if (index == 0 && homeHasUpdates) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier), tint = color)
                        if (index == 3) MessageCountBadge(unreadCount, Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-5).dp), sampling)
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 14.sp), color = color)
                }
            }
        }
    }
}
