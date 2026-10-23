package com.kudo.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.data.CommunityRepository
import com.kudo.android.hupu.HupuPrefs
import com.kudo.android.ui.account.AccountPageTopBar
import com.kudo.android.ui.components.MaterialPageScaffold

/** 「全部频道」管理页（对齐官方样式）：我的频道/推荐频道 两个 3 列胶囊网格。
 *  热榜|推荐 固定不可动；编辑态下点 ✕ 移除、点两个频道可换位。 */
@Composable
internal fun HomeChannelsScreen(repository: CommunityRepository, back: () -> Unit) {
    val selected = remember { HupuPrefs.effectiveHomeChannels().toMutableStateList() }
    var pool by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    var moveSource by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { pool = repository.channelPool() }

    fun persist() = HupuPrefs.saveHomeChannels(selected.toList())

    fun onSelectedTap(key: String) {
        if (!editing) return
        val src = moveSource
        if (src == null) {
            moveSource = key
        } else {
            val a = selected.indexOfFirst { it.first == src }
            val b = selected.indexOfFirst { it.first == key }
            if (a >= 0 && b >= 0) {
                selected[a] = selected[b].also { selected[b] = selected[a] }
                persist()
            }
            moveSource = null
        }
    }

    val available = pool.filter { p -> selected.none { it.first == p.first } }

    MaterialPageScaffold(modifier = Modifier.testTag("home-channels-screen"), header = {
        AccountPageTopBar("全部频道", back)
    }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "sec-mine", span = { GridItemSpan(3) }) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("我的频道", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (editing) "完成" else "编辑",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { editing = !editing; moveSource = null }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            item(key = "fixed-hot") { ChannelPill("热榜", fixed = true) {} }
            item(key = "fixed-latest") { ChannelPill("推荐", fixed = true) {} }
            items(selected, key = { "sel-${it.first}" }) { (key, label) ->
                ChannelPill(
                    label,
                    editing = editing,
                    highlighted = moveSource == key,
                    onRemove = {
                        val i = selected.indexOfFirst { it.first == key }
                        if (i >= 0) { selected.removeAt(i); persist() }
                        if (moveSource == key) moveSource = null
                    },
                ) { onSelectedTap(key) }
            }
            item(key = "sec-pool", span = { GridItemSpan(3) }) {
                Text("推荐频道", Modifier.padding(top = 8.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(available, key = { "pool-${it.first}" }) { (key, label) ->
                ChannelPill(label, add = true) { selected.add(key to label); persist() }
            }
        }
    }
}

@Composable
private fun ChannelPill(
    label: String,
    fixed: Boolean = false,
    editing: Boolean = false,
    highlighted: Boolean = false,
    add: Boolean = false,
    onRemove: () -> Unit = {},
    onClick: () -> Unit,
) {
    Box {
        Row(
            Modifier.fillMaxWidth().height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (fixed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                )
                .then(
                    if (highlighted) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    else Modifier
                )
                .clickable(enabled = !fixed, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (add) {
                Icon(Icons.Rounded.Add, null, Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(3.dp))
            }
            Text(
                label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (fixed) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (editing && !fixed) {
            Box(
                Modifier.align(Alignment.TopEnd).size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "移除", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
