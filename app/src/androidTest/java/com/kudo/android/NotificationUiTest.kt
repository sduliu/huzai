package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.IncomingNotification
import com.kudo.android.ui.components.*
import com.kudo.android.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotificationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun badgeUsesYellowAndInkHidesZeroAndCapsAt99WithoutDuplicateSamplingNodes() {
        var count by mutableIntStateOf(0)
        compose.setContent { KudoTheme(DisplaySettings()) {
            GlassNavigation(0, {}, rememberLayerBackdrop(), GlassMode.LIQUID, Modifier.fillMaxWidth(), unreadCount = count)
        } }
        compose.onNodeWithTag("message-count-badge", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { count = 126 }
        compose.onAllNodesWithTag("message-count-badge", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("99+").assertIsDisplayed()
        val pixels = compose.onNodeWithTag("message-count-badge", useUnmergedTree = true).captureToImage().toPixelMap()
        var yellow = 0; var dark = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val c = pixels[x, y]
            if (c.red > .9f && c.green > .65f && c.blue < .4f) yellow++
            if (c.red < .3f && c.green < .3f && c.blue < .3f) dark++
        }
        assertTrue(yellow > 30); assertTrue(dark > 5)
        compose.runOnIdle { count = 3 }
        compose.onNodeWithText("3").assertIsDisplayed()
    }
    @Test fun pendingColdTargetWaitsForSessionWarmTargetsConsumeOnceAndMismatchFallsBack() {
        var target by mutableStateOf<NotificationTarget?>(NotificationTarget(42, 10, 100, 8, token = "cold"))
        var ready by mutableStateOf(false)
        var messages = 0; var consumed = 0
        val reads = mutableListOf<NotificationTarget>()
        compose.setContent { KudoTheme(DisplaySettings()) {
            val nav = rememberNavController()
            NavHost(nav, "root") {
                composable("root") { Text("消息页") }
                composable("topic/{id}?post={post}", arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("post") { type = NavType.IntType })) {
                    Text("主题${it.arguments!!.getInt("id")}楼层${it.arguments!!.getInt("post")}")
                }
                composable("chat/{id}?message={message}", arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("message") { type = NavType.IntType })) {
                    Text("聊天${it.arguments!!.getInt("id")}消息${it.arguments!!.getInt("message")}")
                }
            }
            IncomingNotification(nav, target, ready, 42, 0, { messages++ }, reads::add, { consumed++; target = null })
        } }
        compose.onNodeWithText("消息页").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, consumed); ready = true }
        compose.onNodeWithText("主题100楼层8").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, consumed); assertEquals(1, reads.size) }
        compose.runOnIdle { target = NotificationTarget(42, chatChannelId = 7, chatMessageId = 22, token = "warm") }
        compose.onNodeWithText("聊天7消息22").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, consumed); assertEquals(2, reads.size) }
        compose.runOnIdle { target = NotificationTarget(43, 11, 101, 9, token = "other-account") }
        compose.onNodeWithText("消息页").assertIsDisplayed()
        compose.runOnIdle { assertEquals(3, consumed); assertEquals(2, reads.size); assertEquals(3, messages) }
        compose.runOnIdle { target = NotificationTarget(42, token = "no-destination") }
        compose.onNodeWithText("消息页").assertIsDisplayed()
        compose.runOnIdle { assertEquals(4, consumed) }
    }
}
