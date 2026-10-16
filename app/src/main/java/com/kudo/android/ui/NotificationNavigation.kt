package com.kudo.android.ui

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kudo.android.data.NotificationTarget

internal fun notificationRoute(target: NotificationTarget, accountId: Int?): String? {
    if (target.accountId != accountId) return null
    return when {
        (target.chatChannelId ?: 0) > 0 -> "chat/${target.chatChannelId}?message=${target.chatMessageId ?: 0}"
        (target.topicId ?: 0) > 0 -> "topic/${target.topicId}?post=${target.postNumber ?: 0}"
        else -> null
    }
}

@Composable
internal fun IncomingNotification(nav: NavHostController, target: NotificationTarget?, sessionReady: Boolean,
    accountId: Int?, sequence: Long, messages: () -> Unit, read: (NotificationTarget) -> Unit,
    consume: (NotificationTarget) -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    LaunchedEffect(target?.token, target, sessionReady, accountId, sequence, entry != null) {
        if (target == null || !sessionReady || entry == null) return@LaunchedEffect
        messages()
        if (!nav.popBackStack("root", false)) nav.navigate("root") { launchSingleTop = true }
        val route = notificationRoute(target, accountId)
        if (route != null) {
            try { nav.navigate(route) { launchSingleTop = true } }
            catch (_: IllegalArgumentException) { /* Root message page remains available. */ }
        }
        if (target.accountId == accountId) read(target)
        consume(target)
    }
}
