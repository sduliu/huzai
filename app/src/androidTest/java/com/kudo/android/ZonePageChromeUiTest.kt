package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kudo.android.data.DisplaySettings
import com.kudo.android.hupu.HupuTopicInfo
import com.kudo.android.hupuui.pages.TopicCell
import com.kudo.android.hupuui.pages.ZoneTopTab
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 批X9：专区页版式官方化回归——
 * 顶栏「广场/关注」文字 tab（选中态可点切）；
 * 网格扁平项 = 版块名 + 关注数「👍N万」真实字段直出，缺字段回落热度文案。
 */
class ZonePageChromeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun zoneTopTabsRenderAndSwitch() {
        var selected by mutableStateOf("plaza")
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                Row {
                    ZoneTopTab(title = "广场", selected = selected == "plaza") { selected = "plaza" }
                    ZoneTopTab(title = "关注", selected = selected == "follow") { selected = "follow" }
                }
            }
        }
        compose.onNodeWithText("广场").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("关注").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithText("关注").performClick().assertIsSelected()
        compose.onNodeWithText("广场").assertIsNotSelected()
        compose.runOnIdle { assertEquals("follow", selected) }
    }

    @Test fun topicCellShowsFollowCountAndHotFallback() {
        var opened = ""
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                Column {
                    TopicCell(
                        HupuTopicInfo(
                            topicId = "1", name = "步行街主干道", url = "/bxj",
                            followText = "132.0万",
                        ),
                    ) { opened = "/bxj" }
                    TopicCell(
                        HupuTopicInfo(
                            topicId = "40", name = "英雄联盟", url = "/lol",
                            hotText = "6.5w",
                        ),
                    ) { opened = "/lol" }
                }
            }
        }
        // 官方「👍N万」：关注数文案直出（无「热度」后缀）
        compose.onNodeWithText("步行街主干道").assertIsDisplayed()
        compose.onNodeWithText("132.0万").assertIsDisplayed()
        // 缺关注数字段（m 站未下发版块）→ 回落既有热度文案
        compose.onNodeWithText("英雄联盟").assertIsDisplayed()
        compose.onNodeWithText("6.5w 热度").assertIsDisplayed()
        // 点击仍走版块打开回调
        compose.onNodeWithText("步行街主干道").performClick()
        compose.runOnIdle { assertEquals("/bxj", opened) }
    }
}
