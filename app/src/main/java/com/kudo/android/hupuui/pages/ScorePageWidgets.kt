package com.kudo.android.hupuui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.kudo.android.hupuui.components.animateChipCenterTo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kudo.android.hupu.HupuCommonSubject
import com.kudo.android.hupu.HupuCommonTree
import com.kudo.android.hupu.HupuImage
import com.kudo.android.hupu.HupuMatch
import com.kudo.android.hupu.HupuMatchApi
import com.kudo.android.hupu.HupuPrefs
import com.kudo.android.hupu.isLocalStickerUrl
import com.kudo.android.hupu.HupuMatchDay
import com.kudo.android.hupu.HupuMatchTeam
import com.kudo.android.hupu.HupuPlayerScore
import com.kudo.android.hupu.HupuRepository
import com.kudo.android.hupu.HupuUiSignals
import com.kudo.android.hupu.HupuScoreGroup
import com.kudo.android.hupu.HupuScoreItem
import com.kudo.android.hupu.HupuScoreTree
import com.kudo.android.hupu.HupuAccount
import com.kudo.android.hupu.HupuSelfDetail
import com.kudo.android.hupu.GrandExpandState
import com.kudo.android.hupu.flattenWithDescendants
import com.kudo.android.hupu.ScoreCommentState
import com.kudo.android.hupu.mergeWithOptimistic
import com.kudo.android.hupuui.components.Chip
import com.kudo.android.hupuui.components.ErrorRetry
import com.kudo.android.hupuui.components.PageHeader
import com.kudo.android.hupuui.components.SecondaryPage
import com.kudo.android.hupuui.components.SkeletonHome
import com.kudo.android.hupuui.components.normalizeCover
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.kudo.android.hupuui.components.HupuIconImage

/** 选手评分卡：头像 + 名字 + 分数 + 热评 */
@Composable
internal fun PlayerScoreCard(p: HupuPlayerScore, matchScoreCount: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HupuIconImage(
                model = normalizeCover(p.logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    com.kudo.android.hupu.hupuWanScoreText(p.scoreCountText.ifEmpty { matchScoreCount })
                        .ifEmpty { p.scoreCountText.ifEmpty { matchScoreCount } },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 分数（大字）
            Text(
                p.scoreNum,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor(p.scoreNum),
            )
        }
        // 热评
        if (p.hotComment.isNotBlank()) {
            Text(
                "「${p.hotComment}」",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
        }
    }
}

/** 空赛程 */
@Composable
internal fun EmptySchedule() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "近期暂无赛程",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
