package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kudo.android.hupuui.components.HupuAvatarImage
import com.kudo.android.hupuui.components.HupuIconImage
import com.kudo.android.hupuui.components.HupuThumbImage
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Rule
import org.junit.Test

/**
 * 批E-2：三类兜底的真实 error 槽渲染——给 SubcomposeAsyncImage 喂不可达地址
 * （127.0.0.1:1 连接拒绝，即时失败、离线可复现），断言 `hupu-fallback-*` 节点出现。
 */
class FallbackImagesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun failingModel(): String = "http://127.0.0.1:1/unreachable.png"

    @Test fun avatarFailureRendersPersonPlaceholder() {
        compose.setContent { KudoTheme { HupuAvatarImage(failingModel(), "头像", Modifier.size(48.dp)) } }
        compose.waitForIdle()
        compose.onNodeWithTag("hupu-fallback-avatar", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun teamIconFailureRendersBallPlaceholder() {
        compose.setContent { KudoTheme { HupuIconImage(failingModel(), "队徽", Modifier.size(48.dp)) } }
        compose.waitForIdle()
        compose.onNodeWithTag("hupu-fallback-icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun thumbFailureRendersImagePlaceholder() {
        compose.setContent { KudoTheme { HupuThumbImage(failingModel(), "缩略图", Modifier.size(48.dp)) } }
        compose.waitForIdle()
        compose.onNodeWithTag("hupu-fallback-thumb", useUnmergedTree = true).assertIsDisplayed()
    }
}
