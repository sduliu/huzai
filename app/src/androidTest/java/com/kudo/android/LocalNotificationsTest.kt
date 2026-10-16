package com.kudo.android

import android.Manifest
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.notifications.LocalNotifications
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

class LocalNotificationsTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private class FixtureContext(base: Context, val deny: Boolean = false) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("fixture_$name", mode)
        override fun checkSelfPermission(permission: String): Int = if (deny && permission == Manifest.permission.POST_NOTIFICATIONS)
            PackageManager.PERMISSION_DENIED else super.checkSelfPermission(permission)
    }
    private val fixture = FixtureContext(base)
    @Before fun prepare() { LocalNotifications(fixture).account(null) }
    @After fun cleanup() { LocalNotifications(fixture).account(null) }
    @Test fun postsImmutableNotificationRestoresColdTargetAndCancelsOnlyAfterReadOrAccountChange() {
        val service = LocalNotifications(fixture)
        assumeTrue("系统未授权时不伪称已经展示通知", service.enabled())
        service.account(424242)
        service.show(NotificationAlert("offline", "离线通知夹具", "只验证本机通知，不写社区", NotificationTarget(424242, 123, 456, 8)))
        val prefs = fixture.getSharedPreferences("runtime_notifications", 0)
        val token = prefs.all.keys.single()
        val manager = base.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.single { it.tag == "kudo.message.$token" }.notification
        if (Build.VERSION.SDK_INT >= 31) assertTrue(notification.contentIntent.isImmutable)
        val intent = Intent("com.kudo.android.OPEN_NOTIFICATION").putExtra("notification_token", token)
        val target = LocalNotifications(fixture).fromIntent(intent)!!
        assertEquals(424242, target.accountId); assertEquals(456, target.topicId); assertEquals(8, target.postNumber)
        service.consume(target)
        assertNotNull(LocalNotifications(fixture).fromIntent(intent))
        service.readForum(setOf(123))
        assertNull(LocalNotifications(fixture).fromIntent(intent))
        service.show(NotificationAlert("chat", "聊天夹具", "本机", NotificationTarget(424242, noticeId = 124, chatChannelId = 7, chatMessageId = 20)))
        service.readForum(setOf(124))
        assertTrue("具体通知已读必须取消对应聊天提醒", prefs.all.isEmpty())
        service.show(NotificationAlert("second", "换号夹具", "本机", NotificationTarget(424242)))
        service.account(434343)
        assertTrue(prefs.all.isEmpty())
        assertNull(service.fromIntent(Intent("com.kudo.android.OPEN_NOTIFICATION").putExtra("notification_token", "forged")))
    }
    @Test fun deniedPermissionDoesNotPostOrPersistAnAlert() {
        if (Build.VERSION.SDK_INT < 33) return
        val denied = FixtureContext(base, deny = true)
        val service = LocalNotifications(denied); service.account(424242)
        assertFalse(service.enabled())
        service.show(NotificationAlert("denied", "权限拒绝", "本机", NotificationTarget(424242)))
        assertTrue(denied.getSharedPreferences("runtime_notifications", 0).all.isEmpty())
    }
}
