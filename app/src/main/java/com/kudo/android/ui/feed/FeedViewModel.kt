package com.kudo.android.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first

data class FeedState(val topics: List<Topic> = emptyList(), val categories: List<Category> = emptyList(), val filter: String = "latest", val loading: Boolean = true, val loadingMore: Boolean = false, val next: String? = null, val error: Throwable? = null, val moreError: Throwable? = null, val accountId: Int? = null, val newCount: Int? = null, val hasUpdates: Boolean = false, val scrollToTopRequest: Long = 0, val tag: TopicTag? = null) {
    fun forUser(user: Member?, inCategory: Boolean = false): FeedState =
        if (accountId == user?.id) this else FeedState(filter = defaultFeedFilter(user, inCategory), accountId = user?.id)
}

private fun defaultFeedFilter(user: Member?, inCategory: Boolean) = if (user != null && !inCategory) "unread" else "latest"

class FeedViewModel(private val repository: CommunityRepository, private val categoryId: Int? = null, initialUser: Member? = null, private val updates: FeedUpdates? = null, private val updateIntervalMillis: Long = 15_000, private val tagSlug: String? = null, private val tagId: Int? = null) : ViewModel() {
    private var user = initialUser
    private val _state = MutableStateFlow(FeedState(filter = defaultFeedFilter(user, categoryId != null || tagSlug != null), accountId = user?.id))
    val state = _state.asStateFlow()
    private val _pages = MutableStateFlow<Map<String, FeedState>>(emptyMap())
    val pages = _pages.asStateFlow()
    private var job: Job? = null
    private var moreJob: Job? = null
    private var generation = 0L
    private var updateJob: Job? = null
    private var updatesActive = false
    private var signals: Channel<Unit>? = null
    private var arrivalRevision = 0L
    private var baseline: Map<Int, Topic>? = null
    private var acknowledgeOnProbe = false
    private var lastRefreshEpoch = 0
    private var lastReadingEpoch = 0
    private var readingCountJob: Job? = null
    private val refreshResults = Channel<Int>(Channel.BUFFERED)
    val refreshEvents = refreshResults.receiveAsFlow()
    init { refresh(manual = false) }
    fun setUser(value: Member?): Boolean {
        if (value?.id == user?.id && value?.unifiedNew == user?.unifiedNew) return false
        user = value
        _pages.value = emptyMap()
        prefetchJobs.values.forEach { it.cancel() }; prefetchJobs.clear()
        baseline = null
        acknowledgeOnProbe = false
        _state.value = FeedState(filter = defaultFeedFilter(value, categoryId != null || tagSlug != null), accountId = value?.id)
        refresh(manual = false)
        restartUpdates()
        return true
    }
    fun select(filter: String) {
        // 频道键直通：board:<url> 板块 / sport:<id> 赛事频道都是合法页键
        if (FeedFilter.available(user != null).none { it.value == filter }
            && !filter.startsWith("board:") && !filter.startsWith("sport:")) return
        if (filter == _state.value.filter) return
        val previous = _state.value
        _pages.update { it + (previous.filter to previous.copy(loading = previous.loading && previous.topics.isEmpty(), loadingMore = false)) }
        job?.cancel(); moreJob?.cancel(); readingCountJob?.cancel(); generation++
        acknowledgeOnProbe = false
        val cached = _pages.value[filter]?.takeIf { !it.loading }
        baseline = cached?.topics?.associateBy(Topic::id)
        _state.value = cached?.copy(categories = previous.categories, loadingMore = false)
            ?: FeedState(categories = previous.categories, filter = filter, accountId = user?.id, tag = previous.tag)
        if (cached == null) refresh(manual = false)
        restartUpdates()
    }
    /**
     * 频道预取：pager 落定某页后，后台把相邻频道的首页数据装进 _pages。
     * 用户滑过去时 select() 命中 cached 直接出内容，不再出现「空页滑入→转圈→内容蹦出」。
     * 预取结果走 _pages 缓存，不触碰 _state；失败移除占位让 select() 走正常拉取。
     */
    private val prefetchJobs = mutableMapOf<String, Job>()
    fun prefetch(filter: String) {
        if (FeedFilter.available(user != null).none { it.value == filter }
            && !filter.startsWith("board:") && !filter.startsWith("sport:")) return
        if (filter == _state.value.filter) return
        val existing = _pages.value[filter]
        if (existing != null && !existing.loading) return
        if (prefetchJobs[filter]?.isActive == true) return
        val account = user
        _pages.update { it + (filter to (existing ?: FeedState(filter = filter, accountId = account?.id))) }
        prefetchJobs[filter] = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { loadTopics(filter) }
                _pages.update { pages ->
                    val cur = pages[filter] ?: return@update pages
                    pages + (filter to FeedState(
                        topics = page.topics.distinctBy(Topic::id),
                        categories = cur.categories,
                        filter = filter,
                        next = page.next,
                        loading = false,
                        accountId = account?.id,
                        tag = page.tag,
                    ))
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                _pages.update { it - filter }
            }
        }
    }
    fun refreshFromNavigation() {
        _state.update { it.copy(scrollToTopRequest = it.scrollToTopRequest + 1) }
        if (!_state.value.loading) refresh(manual = true)
    }

    fun refreshIfRequested(epoch: Int) {
        if (epoch <= 0 || epoch == lastRefreshEpoch) return
        lastRefreshEpoch = epoch
        _pages.value = emptyMap()
        prefetchJobs.values.forEach { it.cancel() }; prefetchJobs.clear()
        refresh(manual = false)
    }

    fun refreshReadListsIfRequested(epoch: Int, accountId: Int?) {
        val account = user ?: return
        if (account.id != accountId || epoch <= 0 || epoch == lastReadingEpoch) return
        lastReadingEpoch = epoch
        val dependent = setOf("unread", "new", "read")
        _pages.update { pages -> pages.filterKeys { it !in dependent }.mapValues { it.value.copy(newCount = null) } }
        if (_state.value.filter in dependent) {
            // Keep the current reading snapshot, including later pages, until an explicit refresh.
            _state.update { it.copy(hasUpdates = true) }
        }
        if (categoryId == null && tagSlug == null) {
            // Reading must not replace public pages, pagination or their saved scroll positions.
            readingCountJob?.cancel()
            val token = generation
            _state.update { it.copy(newCount = null) }
            readingCountJob = viewModelScope.launch {
                try {
                    val count = withContext(Dispatchers.IO) { repository.newTopicCount(account) }
                    if (token == generation && user?.id == account.id) {
                        _state.update { it.copy(newCount = count) }
                        _pages.update { pages -> pages.mapValues { it.value.copy(newCount = count) } }
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* A missing count must not replace a successful public page. */ }
            }
        }
    }

    fun setUpdatesActive(active: Boolean) {
        if (updatesActive == active) return
        updatesActive = active
        restartUpdates()
    }

    private fun restartUpdates() {
        updateJob?.cancel()
        signals?.close()
        signals = null
        val source = updates ?: return
        if (!updatesActive) return
        val channel = Channel<Unit>(Channel.CONFLATED)
        signals = channel
        val account = user
        updateJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    try {
                        source.observe(account).collect {
                            arrivalRevision++
                            channel.trySend(Unit)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ApiFailure) {
                        if (e.kind == FailureKind.VERIFICATION) {
                            // The independent push origin cannot diagnose main-site clearance.
                            updatesActive = false
                            updateJob?.cancel()
                        }
                    }
                }
                // Reconcile gaps after backgrounding, even when private push authentication is unavailable.
                channel.trySend(Unit)
                for (signal in channel) {
                    delay(updateIntervalMillis)
                    while (channel.tryReceive().isSuccess) { /* coalesce a busy site's messages */ }
                    state.first { !it.loading }
                    val previous = baseline ?: continue
                    val token = generation
                    val filter = _state.value.filter
                    try {
                        val page = withContext(Dispatchers.IO) { loadTopics(filter) }
                        if (token != generation) { channel.trySend(Unit); continue }
                        val changed = page.topics.any { topic ->
                            val old = previous[topic.id]
                            old == null || topic.hasNewContentComparedTo(old)
                        }
                        val statuses = page.topics.associateBy(Topic::id)
                        fun updateBadges(state: FeedState) = state.copy(topics = state.topics.map { old ->
                            statuses[old.id]?.let { old.copy(unseen = it.unseen, unreadPosts = it.unreadPosts) } ?: old
                        })
                        _state.update { updateBadges(it).copy(hasUpdates = changed || (!acknowledgeOnProbe && it.hasUpdates)) }
                        _pages.update { cached -> cached.mapValues { (key, value) -> if (key == filter) updateBadges(value) else value } }
                        acknowledgeOnProbe = false
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        // A failed background check must never erase an existing refresh prompt.
                        val retry = (e as? ApiFailure)?.retryAfter?.toLong()?.times(1000) ?: updateIntervalMillis
                        delay(retry.coerceAtLeast(updateIntervalMillis))
                        channel.trySend(Unit)
                    }
                }
            }
        }
    }

    fun refresh() = refresh(manual = true)

    private fun refresh(manual: Boolean) {
        job?.cancel()
        moreJob?.cancel()
        readingCountJob?.cancel()
        val token = ++generation
        val refreshRevision = arrivalRevision
        val account = user
        val filter = _state.value.filter
        _state.update { it.copy(loading = true, loadingMore = false, error = null, moreError = null, newCount = null) }
        job = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { loadTopics(filter) }
                if (token != generation) return@launch
                val loadedNewCount = if (manual) {
                    baseline?.let { old -> page.topics.count { topic -> old[topic.id]?.let { topic.hasNewContentComparedTo(it) } ?: true } } ?: 0
                } else null
                baseline = page.topics.associateBy(Topic::id)
                acknowledgeOnProbe = arrivalRevision != refreshRevision
                _state.update { it.copy(topics = page.topics.distinctBy(Topic::id), next = page.next, loading = false, hasUpdates = it.hasUpdates && acknowledgeOnProbe, tag = page.tag ?: it.tag) }
                if (categoryId == null && tagSlug == null) loadedNewCount?.takeIf { it > 0 }?.let(refreshResults::trySend)
                if (acknowledgeOnProbe) signals?.trySend(Unit)
                coroutineScope {
                    if (_state.value.categories.isEmpty()) launch {
                        try {
                            val categories = withContext(Dispatchers.IO) { repository.categories() }
                            if (token == generation) _state.update { it.copy(categories = categories) }
                        } catch (e: CancellationException) { throw e } catch (_: Exception) {
                            // Optional metadata must not replace a successful feed with an error.
                        }
                    }
                    if (account != null && categoryId == null && tagSlug == null) launch {
                        try {
                            val count = withContext(Dispatchers.IO) { repository.newTopicCount(account) }
                            if (token == generation) _state.update { it.copy(newCount = count) }
                        } catch (e: CancellationException) { throw e } catch (_: Exception) {
                            // Unknown is different from zero; leave the badge absent.
                        }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (token == generation) _state.update { it.copy(loading = false, error = e) } }
        }
    }
    fun more() {
        val snapshot = _state.value
        val next = snapshot.next ?: return
        if (snapshot.loading || snapshot.loadingMore) return
        val token = generation
        _state.update { it.copy(loadingMore = true, moreError = null) }
        moreJob = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { loadTopics(snapshot.filter, next) }
                if (token == generation) _state.update { it.copy(topics = (it.topics + page.topics).distinctBy(Topic::id), next = page.next?.takeUnless { path -> path == next }, loadingMore = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (token == generation) _state.update { it.copy(loadingMore = false, moreError = e) } }
        }
    }

    private suspend fun loadTopics(filter: String, next: String? = null): TopicPage =
        if (tagSlug != null) repository.tagTopics(tagSlug, tagId, filter, next)
        else repository.topics(filter, categoryId, next)
}

private fun Topic.hasNewContentComparedTo(old: Topic): Boolean = bumpedAt != old.bumpedAt || replies != old.replies ||
    title != old.title || excerpt != old.excerpt || image != old.image || categoryId != old.categoryId || tags != old.tags || closed != old.closed

