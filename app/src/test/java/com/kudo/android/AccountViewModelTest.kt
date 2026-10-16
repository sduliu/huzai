package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.account.AccountViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    @Test fun changingAccountDiscardsOldNotificationResponses() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("""{"current_user":{"id":7,"username":"former"}}"""))
        val vm = AccountViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), {}, {})
        try {
            runCurrent(); vm.viewModelScope.coroutineContext.job.children.toList().joinAll()
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"notifications":[{"id":1,"notification_type":2,"data":{"title":"former-account"}}]}""").setBodyDelay(300, TimeUnit.MILLISECONDS))
            vm.loadNotices(); runCurrent()
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
            server.enqueue(MockResponse().setBody("""{"current_user":{"id":8,"username":"current"}}"""))
            assertTrue(vm.checkSession())
            vm.viewModelScope.coroutineContext.job.children.toList().joinAll()
            assertEquals(8, vm.user?.id)
            assertTrue("切换账号后旧通知响应必须丢弃", vm.notices.isEmpty())
        } finally { vm.viewModelScope.cancel(); server.shutdown(); Dispatchers.resetMain() }
    }
    @Test fun lateSessionResponseCannotRestoreAccountAfterLogout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("""{"current_user":{"id":7,"username":"former-user"}}""").setBodyDelay(300, TimeUnit.MILLISECONDS))
        var cleared = false
        val vm = AccountViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), {}, { cleared = true })
        try {
            runCurrent()
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
            vm.logout()
            vm.viewModelScope.coroutineContext.job.children.toList().joinAll()
            assertTrue(cleared)
            assertNull("退出登录后，较早的会话检查不得重新写回用户", vm.user)
        } finally { vm.viewModelScope.cancel(); server.shutdown(); Dispatchers.resetMain() }
    }
}
