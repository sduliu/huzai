package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kudo.android.hupuui.components.HupuAvatarImage
import com.kudo.android.hupuui.components.HupuIconImage
import com.kudo.android.hupuui.components.HupuThumbImage
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Rule
import org.junit.Test

/**
 * 批E-2：三类兜底的真实 error 槽渲染——给 SubcomposeAsyncImage 喂不可达地址
 * （127.0.0.1:1 连接拒绝，即时失败、离线可复现），断言 `hupu-fallback-*` 节点出现。
 *
 * 批M：批K 真机上三例报 "is not displayed"。定性为测试时序——Coil 请求经
 * Main.immediate→IO→Main 投递 error 态，这段后台往返不在 waitForIdle 的空闲
 * 判定内（checkIsDisplayed 对空选集也报同一句）。改为 waitUntil 显式等兜底
 * 节点进语义树：tag 只由 error 槽发射，节点出现即证明 error 态到达。
 */
class FallbackImagesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun failingModel(): String = "http://127.0.0.1:1/unreachable.png"

    /** 等 error 槽把 tag 节点发射进语义树（=error 态到达），再等布局落位 */
    private fun awaitFallback(tag: String) {
        compose.waitUntil("$tag 兜底节点出现", timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    @Test fun avatarFailureRendersPersonPlaceholder() {
        compose.setContent { KudoTheme(DisplaySettings()) { HupuAvatarImage(failingModel(), "头像", Modifier.size(48.dp)) } }
        awaitFallback("hupu-fallback-avatar")
        compose.onNodeWithTag("hupu-fallback-avatar", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun teamIconFailureRendersBallPlaceholder() {
        compose.setContent { KudoTheme(DisplaySettings()) { HupuIconImage(failingModel(), "队徽", Modifier.size(48.dp)) } }
        awaitFallback("hupu-fallback-icon")
        compose.onNodeWithTag("hupu-fallback-icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun thumbFailureRendersImagePlaceholder() {
        compose.setContent { KudoTheme(DisplaySettings()) { HupuThumbImage(failingModel(), "缩略图", Modifier.size(48.dp)) } }
        awaitFallback("hupu-fallback-thumb")
        compose.onNodeWithTag("hupu-fallback-thumb", useUnmergedTree = true).assertIsDisplayed()
    }
}
