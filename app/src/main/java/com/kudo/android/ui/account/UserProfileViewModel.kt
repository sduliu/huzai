package com.kudo.android.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class ProfileFeedState(val entries: List<ProfileEntry> = emptyList(), val next: String? = null,
    val loaded: Boolean = false, val loading: Boolean = false, val error: Throwable? = null)
data class PublicProfileState(val profile: PublicProfile? = null, val loading: Boolean = true, val error: Throwable? = null,
    val cardBackground: String = "", val cardError: Throwable? = null, val cardLoading: Boolean = true,
    val summary: ProfileSummary? = null, val summaryLoading: Boolean = true, val summaryError: Throwable? = null,
    val feeds: Map<ProfileTab, ProfileFeedState> = emptyMap(), val followBusy: Boolean = false,
    val follows: List<Member>? = null, val followsLoading: Boolean = false, val followsError: Throwable? = null,
    val showingFollowers: Boolean? = null)

class UserProfileViewModel(private val repository: CommunityRepository, val username: String, val viewerId: Int?) : ViewModel() {
    private val mutable = MutableStateFlow(PublicProfileState())
    val state = mutable.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var generation = 0
    private val jobs = mutableMapOf<String, Job>()
    private var stopped = false
    init { refresh() }

    fun stop() { stopped = true; generation++; jobs.values.forEach { it.cancel() }; jobs.clear() }
    fun resume() {
        if (!stopped) return
        stopped = false
        val current = mutable.value
        if (current.followBusy) { mutable.update { it.copy(followBusy = false) }; refresh(); return }
        if (current.loading) { refresh(); return }
        if (current.summaryLoading) retrySummary()
        if (current.cardLoading) retryCard()
        current.feeds.filterValues { it.loading }.forEach { (tab, feed) ->
            mutable.update { it.copy(feeds = it.feeds + (tab to feed.copy(loading = false))) }
            load(tab, feed.entries.isNotEmpty() && feed.next != null, true)
        }
        if (current.followsLoading && current.showingFollowers != null) showFollows(current.showingFollowers)
    }

    fun refresh() {
        if (mutable.value.followBusy) return
        generation++
        jobs.values.forEach { it.cancel() }; jobs.clear()
        mutable.update { PublicProfileState(profile = it.profile) }
        read("profile", { repository.publicProfile(username) },
            { value -> copy(profile = value, loading = false) }, { copy(loading = false, error = it) })
        retryCard(); retrySummary()
    }

    private fun <T> read(key: String, request: suspend () -> T, success: PublicProfileState.(T) -> PublicProfileState,
        failure: PublicProfileState.(Throwable) -> PublicProfileState) {
        jobs[key]?.cancel()
        val version = generation
        jobs[key] = viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) { request() }
                if (version == generation) mutable.update { it.success(value) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (version == generation) mutable.update { it.failure(e) } }
        }
    }

    fun retryCard() {
        mutable.update { it.copy(cardLoading = true, cardError = null) }
        read("card", { repository.profileCardBackground(username) },
            { copy(cardBackground = it, cardLoading = false) }, { copy(cardError = it, cardLoading = false) })
    }
    fun retrySummary() {
        mutable.update { it.copy(summaryLoading = true, summaryError = null) }
        read("summary", { repository.profileSummary(username) },
            { copy(summary = it, summaryLoading = false) }, { copy(summaryError = it, summaryLoading = false) })
    }
    fun load(tab: ProfileTab, more: Boolean = false, refresh: Boolean = false) {
        if (tab == ProfileTab.SUMMARY) return
        val current = mutable.value.feeds[tab] ?: ProfileFeedState()
        if (current.loading || (!more && !refresh && current.loaded && current.error == null) || (more && current.next == null)) return
        val cursor = if (more) current.next else null
        mutable.update { it.copy(feeds = it.feeds + (tab to current.copy(loading = true, error = null))) }
        read("feed-$tab", { repository.profilePage(username, tab, cursor) }, { page ->
            val entries = ((if (more) feeds[tab]?.entries.orEmpty() else emptyList()) + page.entries).distinctBy(ProfileEntry::key)
            copy(feeds = feeds + (tab to ProfileFeedState(entries, page.next?.takeIf { it != cursor }, loaded = true)))
        }, { error -> copy(feeds = feeds + (tab to (feeds[tab] ?: current).copy(loading = false, error = error))) })
    }
    fun follow() {
        val current = mutable.value.profile ?: return
        if (viewerId == null || current.member.id == viewerId || mutable.value.followBusy) return
        mutable.update { it.copy(followBusy = true) }
        read("follow", { repository.follow(current.member) }, { followed ->
            copy(profile = profile?.copy(member = current.member.copy(isFollowed = followed),
                followers = current.followers?.let { (it + if (followed) 1 else -1).coerceAtLeast(0) }), followBusy = false)
        }, { error -> messages.trySend(error.message ?: "关注状态需刷新确认"); copy(followBusy = false) })
    }
    fun showFollows(followers: Boolean) {
        mutable.update { it.copy(showingFollowers = followers, follows = null, followsLoading = true, followsError = null) }
        read("follows", { repository.profileFollows(username, followers) },
            { copy(follows = it, followsLoading = false) }, { copy(followsError = it, followsLoading = false) })
    }
    fun closeFollows() {
        jobs["follows"]?.cancel()
        mutable.update { it.copy(showingFollowers = null, follows = null, followsLoading = false, followsError = null) }
    }
}
