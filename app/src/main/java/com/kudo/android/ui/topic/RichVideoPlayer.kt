package com.kudo.android.ui.topic

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kudo.android.hupuui.player.FullscreenVideo
import com.kudo.android.hupuui.player.VideoHost
import com.kudo.android.hupuui.player.VideoPlayer

/**
 * 信息流详情路径（TopicScreen/RichContent）的内嵌视频块。
 *
 * 复用 hupuui 播放器三件套（VideoHost / VideoPlayer / FullscreenVideo）：
 *  · 小窗 = 封面 + 播放键，点播放后 ExoPlayer 起播，带暂停/进度/倍速控件
 *  · 全屏 = FullscreenVideo 自管旋转与沉浸式系统栏，装在全屏 Dialog 里盖出页面
 *    （TopicScreen 是 NavHost destination，没有页级 overlay 挂载位；Dialog 即满屏容器）
 *  · host/player 放块内：LazyItem 滚出即释放（不占解码器），滚回从头起播——
 *    视频帖在信息流里占比很低，不为滚动保活维护页级映射
 */
@Composable
internal fun RichVideoBlock(block: ArticleBlock.Video) {
    val host = remember { VideoHost() }
    var fullscreen by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }

    BackHandler(enabled = fullscreen) { fullscreen = false }
    DisposableEffect(Unit) {
        onDispose {
            host.player?.release()
            host.player = null
            host.started = false
        }
    }

    VideoPlayer(
        url = block.url,
        cover = block.poster.orEmpty(),
        host = host,
        isFullscreen = fullscreen && revealed,
        onToggleFullscreen = { v ->
            fullscreen = v
            if (v) revealed = false
        },
    )

    val player = host.player
    if (fullscreen && player != null) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            FullscreenVideo(
                player = player,
                onExit = { fullscreen = false },
                onRevealed = { revealed = true },
            )
        }
    }
}
