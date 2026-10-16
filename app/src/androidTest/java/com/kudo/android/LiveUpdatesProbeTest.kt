package com.kudo.android

import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.feed.FeedViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onEach
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in, read-only online probe. Normal regression runs do not depend on the live community. */
class LiveUpdatesProbeTest {
    @Test fun anonymousLiveUpdatesReachFeed() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveReadOnlyProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val hints = AtomicInteger()
        lateinit var vm: FeedViewModel
        instrumentation.runOnMainSync {
            val app = AppContainer(instrumentation.targetContext)
            val bus = DiscourseMessageBus(app.client, userAgent = app.userAgent)
            vm = FeedViewModel(app.repository, updates = FeedUpdates { user ->
                bus.observe(user).onEach { println("Live MessageBus hint: ${hints.incrementAndGet()}") }
            }, updateIntervalMillis = 3000)
            vm.setUpdatesActive(true)
        }
        try {
            withTimeout(60_000) {
                while (!vm.state.value.hasUpdates) {
                    delay(3000)
                    println("Live feed: hints=${hints.get()}, size=${vm.state.value.topics.size}, loading=${vm.state.value.loading}, failure=${vm.state.value.error?.javaClass?.simpleName}, updates=${vm.state.value.hasUpdates}")
                }
            }
        } finally { instrumentation.runOnMainSync { vm.viewModelScope.cancel() } }
    }
}
