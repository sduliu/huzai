package com.kudo.android

import com.kudo.android.data.*
import com.kudo.android.ui.notificationRoute
import org.junit.Assert.*
import org.junit.Test

class NotificationLedgerTest {
    private val member = Member(42, "fixture", "", "", unreadNotifications = 120)
    private fun notice(id: Int = 1, read: Boolean = false) = Notice(id, "主题", "有新回复", 10, id, read, "")
    @Test fun initialBaselineAndReplayedMessagesNeverAlert() {
        val ledger = NotificationLedger(); ledger.account(member)
        val snapshot = NotificationSnapshot(120, notice(), mapOf(1 to false))
        assertTrue(ledger.accept(NotificationBatch(listOf(snapshot), initial = true)).isEmpty())
        assertTrue(ledger.accept(NotificationBatch(listOf(snapshot), reconnect = true)).isEmpty())
        assertEquals(120, ledger.count)
        assertEquals(1, ledger.accept(NotificationBatch(listOf(NotificationSnapshot(121, notice(2), emptyMap())))).size)
        assertTrue(ledger.accept(NotificationBatch(listOf(NotificationSnapshot(121, notice(2), emptyMap())))).isEmpty())
    }
    @Test fun liveCountsBeatStaleSessionAndAccountSwitchClearsReadAndDedupState() {
        val ledger = NotificationLedger(); ledger.account(member)
        ledger.accept(NotificationBatch(listOf(NotificationSnapshot(130, notice(), mapOf(1 to true)))))
        ledger.account(member)
        assertEquals(130, ledger.count)
        ledger.account(member.copy(id = 43, unreadNotifications = 2))
        assertEquals(2, ledger.count); assertTrue(ledger.reads.isEmpty())
        ledger.account(null); assertEquals(0, ledger.count)
    }
    @Test fun forumAndAlertChannelCoalesceAndAlreadyReadNeverAlert() {
        val ledger = NotificationLedger(); ledger.account(member)
        val alert = NotificationAlert("/notification-alert/42:1", "主题", "回复", NotificationTarget(42, topicId = 10, postNumber = 1))
        val batch = NotificationBatch(listOf(NotificationSnapshot(121, notice(), emptyMap())), listOf(alert))
        assertEquals(1, ledger.accept(batch, 1_000).size)
        assertTrue(ledger.accept(batch.copy(reconnect = true), 2_000).isEmpty())
        assertTrue(ledger.accept(NotificationBatch(listOf(NotificationSnapshot(120, notice(2), mapOf(2 to true))))).isEmpty())
        assertEquals(1, ledger.accept(NotificationBatch(alerts = listOf(alert.copy(key = "new-cursor"))), 60_000).size)
    }
    @Test fun readAcknowledgementDecrementsOnceAndCannotEraseNewConcurrentMessage() {
        val ledger = NotificationLedger(); ledger.account(member)
        ledger.confirmRead(setOf(1), false, ledger.revision)
        assertEquals(119, ledger.count)
        ledger.confirmRead(setOf(1), false, ledger.revision)
        assertEquals(119, ledger.count)
        val started = ledger.revision
        ledger.accept(NotificationBatch(listOf(NotificationSnapshot(120, notice(2), emptyMap()))))
        ledger.confirmRead(setOf(1), true, started)
        assertEquals(120, ledger.count)
        ledger.freshCount(member.copy(unreadNotifications = 1), ledger.revision)
        assertEquals(1, ledger.count)
    }
    @Test fun lateCountRequestCannotOverwriteNewerRealtimeValue() {
        val ledger = NotificationLedger(); ledger.account(member)
        val started = ledger.revision
        ledger.accept(NotificationBatch(listOf(NotificationSnapshot(130, null, emptyMap()))))
        ledger.freshCount(member.copy(unreadNotifications = 120), started)
        assertEquals(130, ledger.count)
    }
    @Test fun inFlightPageCannotUndoReadAcknowledgementButNewRequestCanRefreshOlderStatus() {
        val ledger = NotificationLedger(); ledger.account(member)
        val started = ledger.revision
        ledger.confirmRead(setOf(1), false, started)
        assertTrue(ledger.reconcilePage(listOf(notice()), started).single().read)
        assertFalse(ledger.reconcilePage(listOf(notice()), ledger.revision).single().read)
    }
    @Test fun routesKeepFloorPrivateTopicChatMessageAndSafeFallback() {
        assertEquals("topic/10?post=8", notificationRoute(NotificationTarget(42, topicId = 10, postNumber = 8), 42))
        assertEquals("chat/9?message=23", notificationRoute(NotificationTarget(42, chatChannelId = 9, chatMessageId = 23), 42))
        assertNull(notificationRoute(NotificationTarget(42), 42))
        assertNull(notificationRoute(NotificationTarget(42, topicId = 10), 43))
        assertNull(notificationRoute(NotificationTarget(42, topicId = 10), null))
    }
}
