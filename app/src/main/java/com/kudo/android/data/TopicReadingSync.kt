package com.kudo.android.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Navigation-independent, account-bound queue. Unknown writes are never retried. */
class TopicReadingSync(
    private val scope: CoroutineScope,
    private val repository: CommunityRepository,
    private val confirmed: (Int) -> Unit,
) {
    private var accountId: Int? = null
    private var queue: Channel<TopicReadingBatch>? = null
    private var worker: Job? = null

    fun setAccount(id: Int?) {
        if (accountId == id) return
        worker?.cancel()
        queue?.close()
        accountId = id
        queue = null
        if (id == null) return
        val channel = Channel<TopicReadingBatch>(Channel.BUFFERED)
        queue = channel
        worker = scope.launch {
            for (batch in channel) {
                try {
                    withContext(Dispatchers.IO) { repository.recordReading(batch) }
                    if (accountId == id && isActive) confirmed(id)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Best effort. Never duplicate an uncertain timing write. */ }
            }
        }
    }

    fun submit(viewerId: Int?, batch: TopicReadingBatch) {
        if (viewerId != null && viewerId == accountId) queue?.trySend(batch)
    }

    fun close() {
        worker?.cancel()
        queue?.close()
        accountId = null
        queue = null
    }
}
