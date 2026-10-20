package com.kudo.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
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
import com.kudo.android.data.CommunityRepository
import com.kudo.android.hupu.HupuPrefs
import com.kudo.android.ui.account.AccountPageTopBar
import com.kudo.android.ui.components.CommunityIconButton
import com.kudo.android.ui.components.MaterialPageScaffold

/** 首页频道管理：综合固定第一不可动；其余频道可加/删/上下移。
 *  候选池 = 全部赛事频道（HUPU_BOARDS，去掉「热门赛事」聚合）+ 板块目录。 */
@Composable
internal fun HomeChannelsScreen(repository: CommunityRepository, back: () -> Unit) {
    val selected = remember { HupuPrefs.effectiveHomeChannels().toMutableStateList() }
    var pool by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { pool = repository.channelPool() }

    fun persist() {
        HupuPrefs.saveHomeChannels(selected.toList())
    }
    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in selected.indices) return
        val item = selected.removeAt(index)
        selected.add(target, item)
        persist()
    }

    val available = pool.filter { p -> selected.none { it.first == p.first } }

    MaterialPageScaffold(modifier = Modifier.testTag("home-channels-screen"), header = {
        AccountPageTopBar("频道管理", back)
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "sec-fixed") {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("我的频道", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("综合", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("固定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(selected, key = { "sel-${it.first}" }) { (key, label) ->
                val index = selected.indexOfFirst { it.first == key }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CommunityIconButton(Icons.Rounded.KeyboardArrowUp, "上移", { move(index, -1) }, enabled = index > 0)
                    CommunityIconButton(Icons.Rounded.KeyboardArrowDown, "下移", { move(index, 1) }, enabled = index < selected.lastIndex)
                    CommunityIconButton(Icons.Rounded.Remove, "移除", { selected.removeAt(index); persist() })
                }
            }
            item(key = "sec-pool") {
                Text("可添加频道", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(available, key = { "pool-${it.first}" }) { (key, label) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
                        .clickable { selected.add(key to label); persist() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.Add, "添加", Modifier.padding(10.dp), MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
