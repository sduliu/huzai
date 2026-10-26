package com.kudo.android.ui

import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.hupu.HupuDestination
import com.kudo.android.hupu.HupuLink
import com.kudo.android.ui.account.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.editor.*
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import com.kudo.android.ui.web.WebScreen
import kotlinx.coroutines.launch

@Composable
private inline fun <reified T : ViewModel> model(key: String, noinline create: () -> T): T = viewModel(key = key, factory = viewModelFactory { initializer { create() } })

@Composable
internal fun RefreshFeedOnResume(feed: FeedViewModel, user: Member?) {
    LaunchedEffect(user?.id, user?.unifiedNew) { feed.setUser(user) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        feed.setUser(user)
    }
}

@Composable
internal fun ObserveHomeUpdates(feed: FeedViewModel, user: Member?, visible: Boolean) {
    LaunchedEffect(user?.id, user?.unifiedNew) { feed.setUser(user) }
    if (visible) LifecycleResumeEffect(feed, user?.id, user?.unifiedNew) {
        feed.setUser(user)
        feed.setUpdatesActive(true)
        onPauseOrDispose { feed.setUpdatesActive(false) }
    }
}

@Composable
internal fun ObserveTopicUpdates(topic: TopicViewModel) {
    if (!LocalReadingBlocked.current) LifecycleResumeEffect(topic) {
        topic.setUpdatesActive(true)
        onPauseOrDispose { topic.setUpdatesActive(false) }
    }
}

@Composable
fun KudoApp(app: AppContainer, incomingUrl: String? = null, linkSequence: Long = 0L, consumeLink: () -> Unit = {},
    incomingNotification: NotificationTarget? = null, consumeNotification: () -> Unit = {},
    incomingDownloads: Boolean = false, consumeDownloads: () -> Unit = {}) {
    val display by app.settings.display.collectAsStateWithLifecycle()
    var darkStatusIcons by remember { mutableStateOf<Boolean?>(null) }
    KudoTheme(display, darkStatusIcons) {
        VerificationHost(app.client, app.userAgent) {
        ArticleFootnoteHost {
        QuickScrollToTopHost {
        val account = model("account") { AccountViewModel(app) }
        val homeUpdate = model("home-update") { HomeUpdateViewModel(check = { GitHubUpdates().latest() }) }
        val nav = rememberNavController()
        LaunchedEffect(incomingDownloads, linkSequence) {
            if (incomingDownloads) { nav.navigate("downloads") { launchSingleTop = true }; consumeDownloads() }
        }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val appLinks = remember(context) { ExternalAppLinkLauncher(context) }
        fun showMessage(text: String) { showKudoToast(context, text) }
        var refreshEpoch by rememberSaveable { mutableIntStateOf(0) }
        var postEditResult by remember(account.user?.id) { mutableStateOf<PostEditResult?>(null) }
        var readingEpoch by rememberSaveable { mutableIntStateOf(0) }
        val readingSync = remember(app.repository, scope) {
            TopicReadingSync(scope, app.repository) { readingEpoch++ }
        }
        SideEffect { readingSync.setAccount(account.user?.id) }
        DisposableEffect(readingSync) { onDispose { readingSync.close() } }
        var selected by rememberSaveable { mutableIntStateOf(0) }
        // 首页赛事频道 → 「比赛」Tab 跳转请求（nonce 区分连续点击）
        var scoreRequest by remember { mutableStateOf<com.kudo.android.hupuui.pages.ScoreNavRequest?>(null) }
        val homeChannels = remember(com.kudo.android.hupu.HupuPrefs.homeChannelsVersion) {
            com.kudo.android.hupu.HupuPrefs.effectiveHomeChannels()
        }
        fun openBrowser(url: String) {
            if (Uri.parse(url).scheme !in listOf("https", "http")) return
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                val browser = context.packageManager.resolveActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")), PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName?.takeUnless { it == "android" || it == context.packageName }
                if (browser != null) context.startActivity(intent.setPackage(browser))
                else context.startActivity(Intent.createChooser(intent, "浏览器打开").putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, com.kudo.android.MainActivity::class.java))))
            }
            catch (_: Exception) { showMessage("没有可打开此链接的浏览器") }
        }
        fun login() { nav.navigate("login") { launchSingleTop = true } }
        fun authenticated(action: () -> Unit) { if (account.user == null) login() else action() }
        fun topic(id: Int, number: Int? = null) { if (id > 0) nav.navigate("topic/$id?post=${number ?: 0}") }
        fun user(username: String) { if (username.isNotBlank()) nav.navigate("user/${Uri.encode(username)}") }
        fun openTag(tag: TopicTag) {
            val slug = tag.slug.ifBlank { tag.name }
            if (slug.isNotBlank()) nav.navigate("tag/${Uri.encode(slug)}?id=${tag.id ?: 0}&name=${Uri.encode(tag.name)}") { launchSingleTop = true }
        }
        fun invalidLink() { showMessage("无法打开此链接") }
        fun openWebPage(url: String) { nav.openWebPage(url, ::invalidLink) }
        fun openLink(url: String) {
            if (app.downloads.isLikelyDownload(url)) {
                app.downloads.enqueue(url)?.let { showMessage("已加入下载队列") }
                return
            }
            if (appLinks.open(url, display.externalLinkMode)) return
            // 批P-2：虎扑站内链接优先应用内打开（正文话题词/站内链）；归不进目的地的走既有分流
            when (val dest = HupuLink.destination(url)) {
                is HupuDestination.Thread -> { topic(HupuIds.topicId(dest.tid)); return }
                is HupuDestination.Board -> { nav.navigate("category/${HupuIds.boardId(dest.boardUrl)}"); return }
                is HupuDestination.User -> { user(dest.euid); return }
                is HupuDestination.Tag -> {
                    nav.navigate("tag/${Uri.encode("话题")}?id=${HupuIds.id("tagpage:${dest.tagId}")}") { launchSingleTop = true }
                    return
                }
                null -> Unit
            }
            val fallback = ExternalAppLinkPolicy.fallbackUrl(url) ?: return invalidLink()
            nav.openContentLink(fallback, { selected = 0 }, ::invalidLink)
        }
        fun editor(id: Int? = null, number: Int? = null, categoryId: Int? = null) { authenticated { nav.navigate("compose?topic=${id ?: 0}&reply=${number ?: 0}&category=${categoryId ?: 0}") } }
        ApkInstallHost {
        UpdateDownloadEnvironment(app.downloads, ::openBrowser) {
        LaunchedEffect(account) { account.events.collect { showMessage(it) } }
        val downloadLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(app.downloads, downloadLifecycle) {
            downloadLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.downloads.events.collect { event ->
                    showMessage(when (event) {
                        is com.kudo.android.media.DownloadEvent.Completed -> "${event.fileName} 下载完成"
                        is com.kudo.android.media.DownloadEvent.Failed -> "${event.fileName} 下载失败"
                    })
                }
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { account.checkSession() } }
        IncomingLinuxDoLink(nav, incomingUrl, linkSequence, { selected = 0 }, consumeLink)
        com.kudo.android.notifications.NotificationPermission(account.user?.id)
        IncomingNotification(nav, incomingNotification, account.sessionReady, account.user?.id, linkSequence,
            { selected = 3 }, account::readNotificationTarget, { app.notifications.consume(it); consumeNotification() })
        TopicPresentationEnvironment(app.repository, account.user?.id) {
        AvatarEnvironment(app.repository, account.user?.id) {
            ImageGalleryHost(loadAlbum = { id ->
                val detail = app.repository.detail(id, 1)
                val first = detail.originalPost ?: detail.stream.firstOrNull()?.let { firstId ->
                    app.repository.posts(id, listOf(firstId)).firstOrNull { it.number == 1 }
                }
                parseArticle(first?.cooked ?: error("暂时无法读取主帖图片")).articleImages()
            }, openLink = ::openLink, accountKey = account.user?.id) {
            TopicActionsHost(app.repository, account.user, display.glass, ::login,
                ignored = { id, username -> account.recordIgnore(id, username, true); refreshEpoch++ },
                message = { showMessage(it) }, editPost = { id, post ->
                    authenticated { nav.navigate("edit-post/${post.id}?topic=$id") }
                }, openHistory = { _, post -> nav.navigate("post-history/${post.id}") }, changed = {
                    refreshEpoch++
                    nav.currentBackStackEntry?.savedStateHandle?.set("refresh", System.nanoTime())
                }) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
                NavHost(nav, "root", Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    enterTransition = { fadeIn(tween(280)) }, exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) }, popExitTransition = { fadeOut(tween(220)) }) {
                    composable("root") {
                        val directory = model("community-directory") { CommunityDirectoryViewModel(app.repository) }
                        val feed = model("feed") { FeedViewModel(app.repository, initialUser = account.user) }
                        val state by feed.state.collectAsStateWithLifecycle()
                        val pages by feed.pages.collectAsStateWithLifecycle()
                        val backdrop = rememberLayerBackdrop()
                        ObserveHomeUpdates(feed, account.user, selected == 0)
                        LaunchedEffect(feed) {
                            feed.refreshEvents.collect { count ->
                                if (count > 0) showMessage("已为你加载${count}条新内容")
                            }
                        }
                        LaunchedEffect(refreshEpoch) { feed.refreshIfRequested(refreshEpoch) }
                        // 批N-2：详情页「版块名 >」→ 切到专区 Tab 由 ZonePage 打开版块流
                        LaunchedEffect(com.kudo.android.hupu.HupuUiSignals.zoneBoardRequest) {
                            if (com.kudo.android.hupu.HupuUiSignals.zoneBoardRequest != null) selected = 1
                        }
                        // 关注/频道变化 → 「综合」按关注重新过滤（只作废该页缓存，其他频道页不受影响）
                        LaunchedEffect(com.kudo.android.hupu.HupuPrefs.homeChannelsVersion,
                            com.kudo.android.hupu.HupuPrefs.favoriteTopicsVersion) {
                            feed.invalidateLatest()
                        }
                        LaunchedEffect(readingEpoch, account.user?.id) {
                            feed.refreshReadListsIfRequested(readingEpoch, account.user?.id)
                        }

                        CompositionLocalProvider(LocalQuickScrollBottomPadding provides
                            (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp)) {
                        HomeUpdateHost(homeUpdate, selected == 0, ::openBrowser) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background)) {
                                when (selected) {
                                    0 -> FeedScreen(state.forUser(account.user), account.user, feed::select, feed::refresh, feed::more, { topic(it) }, ::user, { nav.navigate("search") }, { if (account.user == null) login() else user(account.user!!.username) }, { directory.useAccount(account.user?.id); if (account.sessionReady) directory.loadIfStale() }, listStyle = display.topicListStyle, pages = pages, prefetch = feed::prefetch,
                                        channels = homeChannels,
                                        manageChannels = { nav.navigate("home-channels") },
                                        channelHeader = { key, latest, onLatestChange ->
                                            com.kudo.android.ui.feed.SportChannelHeader(key, app.repository,
                                                // 二级页打开赛程中心（可返回），不再切底栏 Tab
                                                onOpenSchedule = { g -> nav.navigate("score/$g") },
                                                onOpenMatch = { m -> nav.navigate("score/$key?match=${m.matchId}") },
                                                onOpenStandings = { g -> nav.navigate("standings/$g") },
                                                // 批B-4：数据 tab 深链（积分榜/球员榜/选秀/伤病 → 赛程中心对应 tab）
                                                onOpenDataTab = { g, kind -> nav.navigate("score/$g?dtab=$kind") },
                                                latest = latest, onLatestChange = onLatestChange)
                                        },
                                        hotContent = {
                                            com.kudo.android.ui.feed.HotChannelPage(app.repository,
                                                openTopic = { topic(it) },
                                                openBoard = { url -> nav.navigate("category/${com.kudo.android.data.HupuIds.boardId(url)}") },
                                                openSearch = { q -> nav.navigate("search?query=${Uri.encode(q)}") },
                                                openRatings = { nav.navigate("score/common") })
                                        })
                                    1 -> com.kudo.android.hupuui.pages.ZonePage()
                                    3 -> com.kudo.android.hupuui.pages.ScorePage(request = scoreRequest)
                                    4 -> ProfileScreen(account.user, ::login, { nav.navigate("settings") }, { nav.navigate("bookmarks") }, { account.user?.username?.let(::user) }, repository = app.repository, about = { nav.navigate("about") }, downloads = { nav.navigate("downloads") },
                                        destination = { destination ->
                                            when (destination) {
                                                AccountDestination.BOOKMARKS -> nav.navigate("bookmarks")
                                                AccountDestination.TRUST -> nav.navigate("trust")
                                                AccountDestination.TOPICS, AccountDestination.HISTORY, AccountDestination.MESSAGES -> nav.navigate("personal/${destination.name}")
                                                AccountDestination.DRAFTS -> nav.navigate("drafts")
                                                AccountDestination.LIKES -> nav.navigate("liked-posts")
                                                AccountDestination.CHAT, AccountDestination.PENDING, AccountDestination.BADGES, AccountDestination.INVITES -> nav.navigate("account-records/${destination.name}")
                                            }
                                        }, statusBarStyle = { darkStatusIcons = it })
                                }
                            }
                            // 批Nfix-4：专区详情盖入期间隐藏主导航药丸——详情通栏底栏与
                            // 药丸同底边重叠（批N-3 缺陷）；官方详情页本无底栏导航。
                            if (!com.kudo.android.hupu.HupuUiSignals.zoneDetailOpen) GlassNavigation(selected, {
                                selected = it
                                if (it == 0 && state.forUser(account.user).hasUpdates) feed.refreshFromNavigation()
                            }, { if (display.navigationAction == NavigationAction.SEARCH) nav.navigate("search") else editor() }, backdrop, display.glass, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).widthIn(max = 530.dp).fillMaxWidth(), homeHasUpdates = state.forUser(account.user).hasUpdates, action = display.navigationAction, unreadCount = account.unreadCount)
                        }
                        }
                        }
                    }
                    composable("category/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                        val id = entry.arguments!!.getInt("id")
                        val vm = model("category-$id") { FeedViewModel(app.repository, id, account.user) }
                        val state by vm.state.collectAsStateWithLifecycle()
                        val pages by vm.pages.collectAsStateWithLifecycle()
                        RefreshFeedOnResume(vm, account.user)
                        val category = account.categories.firstOrNull { it.id == id } ?: state.categories.firstOrNull { it.id == id }
                            ?: Category(id, "社区分类", "", "", "", 0)
                        FeedScreen(state.forUser(account.user, inCategory = true), account.user, vm::select, vm::refresh, vm::more, { topic(it) }, ::user, { nav.navigate("search") }, ::login, {}, category = category, back = { nav.popBackStack() }, listStyle = display.topicListStyle, pages = pages, createTopic = { editor(categoryId = it) }, glass = display.glass)
                    }
                    composable("tag/{slug}?id={id}&name={name}", arguments = listOf(navArgument("slug") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType; defaultValue = 0 }, navArgument("name") { type = NavType.StringType; defaultValue = "" })) { entry ->
                        val slug = entry.arguments!!.getString("slug").orEmpty()
                        val id = entry.arguments!!.getInt("id").takeIf { it > 0 }
                        val vm = model("tag-$slug-${id ?: 0}") { FeedViewModel(app.repository, initialUser = account.user, tagSlug = slug, tagId = id) }
                        val state by vm.state.collectAsStateWithLifecycle()
                        val pages by vm.pages.collectAsStateWithLifecycle()
                        RefreshFeedOnResume(vm, account.user)
                        TagFeedScreen(slug, state.forUser(account.user, inCategory = true), account.user, vm::select, vm::refresh, vm::more,
                            { topic(it) }, ::user, { nav.navigate("search") }, ::login, { nav.popBackStack() }, display.topicListStyle, pages, name = entry.arguments!!.getString("name").orEmpty())
                    }
                    composable("home-channels") {
                        com.kudo.android.ui.feed.HomeChannelsScreen(app.repository) { nav.popBackStack() }
                    }
                    // 赛事频道「查看全部赛程/比赛卡」→ 赛程中心二级页（预选该赛事，可返回）
                    composable(
                        "score/{game}?match={match}&dtab={dtab}",
                        arguments = listOf(
                            navArgument("game") { defaultValue = "" },
                            navArgument("match") { nullable = true; defaultValue = null },
                            navArgument("dtab") { nullable = true; defaultValue = null },
                        ),
                    ) { entry ->
                        val g = entry.arguments?.getString("game").orEmpty()
                        val m = entry.arguments?.getString("match")
                        val d = entry.arguments?.getString("dtab")
                        com.kudo.android.hupuui.pages.ScorePage(
                            request = com.kudo.android.hupuui.pages.ScoreNavRequest(g, m, System.nanoTime(), dataTab = d),
                            onBack = { nav.popBackStack() },
                        )
                    }
                    // 赛事频道快捷行「积分榜」→ 原生积分榜二级页
                    composable("standings/{game}") { entry ->
                        val g = entry.arguments?.getString("game").orEmpty()
                        com.kudo.android.hupuui.pages.StandingsRoutePage(g) { nav.popBackStack() }
                    }
                    composable("search?query={query}", arguments = listOf(navArgument("query") { defaultValue = "" })) { entry ->
                        val q = entry.arguments?.getString("query").orEmpty()
                        // 虎扑原生搜索页（bbs.hupu.com/search SSR，匿名可用）
                        com.kudo.android.hupuui.pages.SearchPage(
                            initialQuery = q,
                            onOpenThread = { s -> topic(HupuIds.topicId(s.tid)) },
                            onClose = { nav.popBackStack() },
                        )
                    }
                    composable("personal/{destination}") { entry ->
                        val member = account.user
                        val destination = AccountDestination.entries.firstOrNull { it.name == entry.arguments?.getString("destination") }
                        if (member != null && destination != null) {
                            val vm = model("personal-${member.id}-$destination") { PersonalTopicsViewModel(app.repository, member.username, destination) }
                            PersonalTopicsScreen(vm, account.categories, display.topicListStyle, { nav.popBackStack() }, ::login, ::topic, ::user,
                                { nav.navigate("new-message") }, drafts = { nav.navigate("drafts") })
                        } else MaterialPagePlaceholder("个人内容", { nav.popBackStack() }) { EmptyState("登录后查看", "登录后查看自己的社区内容。", actionLabel = "登录", action = ::login) }
                    }
                    composable("liked-posts") {
                        val member = account.user
                        if (member != null) {
                            val vm = model("liked-posts-${member.id}") { LikedPostsViewModel(app.repository, member.username) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            LikedPostsScreen(state, { nav.popBackStack() }, ::login, ::openLink,
                                { vm.load() }, { vm.load(true) }, vm::retry)
                        } else MaterialPagePlaceholder("我的点赞", { nav.popBackStack() }) {
                            EmptyState("登录后查看点赞", "你送出的赞会出现在这里。", actionLabel = "登录", action = ::login)
                        }
                    }
                    composable("trust") {
                        account.user?.let { member ->
                            TrustScreen(member, app.connect, { nav.popBackStack() }, { openWebPage("https://connect.linux.do/") })
                        } ?: MaterialPagePlaceholder("等级要求", { nav.popBackStack() }) { EmptyState("登录后查看等级", "查看当前账号的等级条件与进度。", actionLabel = "登录", action = ::login) }
                    }
                    composable("account-records/{destination}") { entry ->
                        val member = account.user
                        val destination = AccountDestination.entries.firstOrNull { it.name == entry.arguments?.getString("destination") }
                        if (member != null && destination != null) key(member.id, destination) {
                            AccountRecordsScreen(destination, member, app.repository, app.settings, { nav.popBackStack() }, ::login,
                                ::openWebPage, ::topic, { id -> nav.navigate("chat/$id") }, ::editor) { text ->
                                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("邀请链接", text))
                                showMessage("已复制邀请链接")
                            }
                        } else MaterialPagePlaceholder("个人功能", { nav.popBackStack() }) { EmptyState("请先登录", "登录后查看个人内容。", actionLabel = "登录", action = ::login) }
                    }
                    composable("drafts") {
                        val member = account.user
                        if (member != null) DraftsScreen(member, app.repository, app.settings, { nav.popBackStack() }, ::login) { draft ->
                            if (draft.remote) nav.navigate("cloud-draft/${Uri.encode(draft.key)}")
                            else if (draft.privateMessage) nav.navigate("new-message?draft=${Uri.encode(draft.key)}")
                            else editor(draft.topicId, draft.replyTo, draft.categoryContext)
                        } else MaterialPagePlaceholder("我的草稿", { nav.popBackStack() }) { EmptyState("登录后查看草稿", "草稿按账号分别保存。", actionLabel = "登录", action = ::login) }
                    }
                    composable("cloud-draft/{key}") { entry ->
                        account.user?.let { member ->
                            LaunchedEffect(member.id) { if (account.categories.isEmpty()) account.loadCategories() }
                            CloudDraftScreen(entry.arguments!!.getString("key")!!, member, app.repository, app.settings, account.categories,
                                { nav.popBackStack() }, ::login) { result ->
                                nav.popBackStack(); refreshEpoch++
                                if (!result.queued) result.topicId?.let { topic(it) }
                                showMessage(if (result.queued) "已提交审核" else "发布成功")
                            }
                        } ?: MaterialPagePlaceholder("云端草稿", { nav.popBackStack() }) { EmptyState("请先登录", "登录后继续编辑。", actionLabel = "登录", action = ::login) }
                    }
                    composable("new-message?recipient={recipient}&draft={draft}", arguments = listOf(navArgument("recipient") { defaultValue = "" },
                        navArgument("draft") { defaultValue = "" })) { entry ->
                        account.user?.let { member -> key(member.id) {
                            PrivateMessageScreen(member, app.repository, app.settings, { nav.popBackStack() }, published = { result ->
                                nav.popBackStack()
                                if (!result.queued) result.topicId?.let { topic(it) }
                                showMessage(if (result.queued) "私信已提交审核" else "私信已发送")
                            }, recipient = entry.arguments?.getString("recipient").orEmpty(), existingDraftKey = entry.arguments?.getString("draft").orEmpty())
                        } } ?: MaterialPagePlaceholder("写私信", { nav.popBackStack() }) { EmptyState("请先登录", "登录后发送私信。", actionLabel = "登录", action = ::login) }
                    }
                    composable("chat/{id}?message={message}", arguments = listOf(navArgument("id") { type = NavType.IntType },
                        navArgument("message") { type = NavType.IntType; defaultValue = 0 })) { entry ->
                        account.user?.let { member ->
                            val id = entry.arguments!!.getInt("id")
                            val initialMessage = entry.arguments?.getInt("message")?.takeIf { it > 0 }
                            val vm = model("chat-${member.id}-$id-$initialMessage") { ChatViewModel(app.repository, id, initialMessage) }
                            ChatScreen(vm, member, app.settings, { nav.popBackStack() }, ::login,
                                { openWebPage("https://linux.do/chat/c/-/$id") }, ::openLink, ::user,
                                read = { if (account.user?.id == member.id) account.chatRead(id, it) })
                        } ?: MaterialPagePlaceholder("聊天", { nav.popBackStack() }) { EmptyState("请先登录", "登录后查看聊天。", actionLabel = "登录", action = ::login) }
                    }
                    composable("web?url={url}", arguments = listOf(navArgument("url") { type = NavType.StringType })) { entry ->
                        WebScreen(entry.arguments!!.getString("url")!!, { nav.popBackStack() }, ::openBrowser,
                            openApp = { appLinks.open(it) }, externalLinkMode = display.externalLinkMode)
                    }
                    composable("topic/{id}?post={post}",
                        arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("post") { type = NavType.IntType; defaultValue = 0 }),
                        enterTransition = { fadeIn(tween(280)) + scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.97f) },
                        popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.98f) },
                        popExitTransition = { fadeOut(tween(220)) + scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 0.98f) }) { entry ->
                        val id = entry.arguments!!.getInt("id")
                        val number = entry.arguments!!.getInt("post").takeIf { it > 0 }
                        val vm = model("topic-$id-$number-${account.user?.id}") {
                            TopicViewModel(app.repository, id, number, account.user?.id,
                                updates = DiscourseMessageBus(app.client, userAgent = app.userAgent, route = app.network::client), updateUser = account.user)
                        }
                        ObserveTopicUpdates(vm)
                        val state by vm.state.collectAsStateWithLifecycle()
                        val nonce by entry.savedStateHandle.getStateFlow("refresh", 0L).collectAsStateWithLifecycle()
                        LaunchedEffect(vm, postEditResult) {
                            postEditResult?.takeIf { it.topicId == id }?.let { vm.applyEdit(it); postEditResult = null }
                        }
                        LaunchedEffect(nonce) { if (nonce != 0L) vm.refresh() }
                        LaunchedEffect(vm) { vm.events.collect { showMessage(it) } }
                        var replyTarget by rememberSaveable(account.user?.id) { mutableStateOf<Int?>(null) }
                        var replyQuote by rememberSaveable(account.user?.id) { mutableStateOf<String?>(null) }
                        val replyVisibility = remember(account.user?.id) { MutableTransitionState(replyTarget != null) }
                        val keyboard = LocalSoftwareKeyboardController.current
                        val replyShowing = replyVisibility.currentState || replyVisibility.targetState
                        LaunchedEffect(replyVisibility.currentState, replyVisibility.targetState) {
                            if (!replyVisibility.currentState && !replyVisibility.targetState) replyTarget = null
                        }
                        Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().then(if (replyShowing) Modifier.clearAndSetSemantics {} else Modifier)
                            .focusProperties { onEnter = { if (replyShowing) cancelFocusChange() } }.focusGroup()) {
                        TopicScreen(state, display.glass, { nav.popBackStack() }, vm::refresh, vm::more, ::login, { authenticated { vm.like(it) } }, { authenticated { vm.bookmark(it) } }, { post -> authenticated { replyQuote = null; replyTarget = post?.number ?: 0; replyVisibility.targetState = true } }, ::user, {}, ::openLink,
                            { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${state.detail?.title.orEmpty()}\nhttps://linux.do/t/$id") }, "分享话题")) },
                            follow = { authenticated { vm.follow() } }, react = { post, reaction -> authenticated { vm.react(post, reaction) } }, openBrowser = ::openBrowser, loadReplies = vm::loadReplies, resolveContext = vm::resolveContext,
                            readingEnabled = !replyShowing, onReadingBatch = { readingSync.submit(state.viewerId, it) }, onPostsRead = vm::markRead,
                            loadBoostRules = vm::loadBoostRules, boost = vm::boost, jumpTo = vm::jumpTo, previous = vm::previous,
                            vote = { post, name, options -> authenticated { vm.vote(post, name, options) } },
                            openCategory = { categoryId -> if (categoryId > 0) nav.navigate("category/$categoryId") { launchSingleTop = true } },
                            openTag = ::openTag, quoteReply = { post, markdown -> authenticated {
                                replyQuote = markdown; replyTarget = post.number; replyVisibility.targetState = true
                            } })
                        }
                        val currentUser = account.user
                        val target = replyTarget
                        if (currentUser != null && target != null) {
                            val editorVm = model("reply-${currentUser.id}-$id-$target") { EditorViewModel(app, currentUser.id, id, target.takeIf { it > 0 }, categoryId = state.detail?.categoryId) }
                            LaunchedEffect(editorVm, replyQuote) {
                                replyQuote?.let { editorVm.edit(editorVm.draft.copy(raw = appendPostQuote(editorVm.draft.raw, it))); replyQuote = null }
                            }
                            LaunchedEffect(editorVm) { editorVm.published.collect { result ->
                                keyboard?.hide(); replyVisibility.targetState = false
                                vm.refresh(); refreshEpoch++
                                showMessage(if (result.queued) "内容已提交，等待社区审核" else "发布成功")
                            } }
                            ReplyEditorSheet(replyVisibility, editorVm.sending, { replyVisibility.targetState = false }) {
                                val replyAuthor = if (target > 0) state.allPosts.firstOrNull { it.number == target }?.author
                                    else state.authorProfile ?: state.detail?.author
                                EditorScreen(editorVm, account.categories, account.categoryError, account::loadCategories,
                                    { keyboard?.hide(); replyVisibility.targetState = false }, replyAuthor = replyAuthor)
                            }
                        }
                        }
                    }
                    composable("post-history/{post}", arguments = listOf(navArgument("post") { type = NavType.IntType })) { entry ->
                        val postId = entry.arguments!!.getInt("post")
                        val vm = model("post-history-${account.user?.id}-$postId") { PostHistoryViewModel(app.repository, postId) }
                        val state by vm.state.collectAsStateWithLifecycle()
                        PostHistoryScreen(postId, state, vm::reload, vm::more, { nav.popBackStack() }, ::login, ::user, ::openLink)
                    }
                    composable("edit-post/{post}?topic={topic}", arguments = listOf(
                        navArgument("post") { type = NavType.IntType }, navArgument("topic") { type = NavType.IntType })) { entry ->
                        val postId = entry.arguments!!.getInt("post")
                        val topicId = entry.arguments!!.getInt("topic")
                        val currentUser = account.user
                        if (currentUser == null) ErrorState(ApiFailure(FailureKind.SIGN_IN, "登录后可编辑自己的内容"), {}, ::login)
                        else {
                            val vm = model("post-edit-${currentUser.id}-$postId") { PostEditViewModel(
                                loadPost = {
                                    app.repository.editablePost(postId, currentUser.id).also { require(it.topicId == topicId) { "编辑话题不匹配" } }
                                }, savePost = { source, draft -> app.repository.editPost(source, currentUser.id, draft.raw, draft.title, draft.reason) },
                                restore = { app.settings.postEditDraft(currentUser.id, postId) },
                                persist = { app.settings.savePostEditDraft(currentUser.id, postId, it) },
                            ) }
                            LaunchedEffect(vm) { vm.saved.collect {
                                postEditResult = it; refreshEpoch++; nav.popBackStack(); showMessage("修改已保存")
                            } }
                            PostEditScreen(vm, display.glass, { uri -> uploadEditorImage(app.repository, context, uri) }, { nav.popBackStack() }, ::login)
                        }
                    }
                    composable("login") { LoginScreen(account, { refreshEpoch++; nav.popBackStack() }, { refreshEpoch++; nav.popBackStack(); showMessage("欢迎回来，${account.user?.displayName}") }) }
                    composable("settings") {
                        val network by app.network.state.collectAsStateWithLifecycle()
                        val limits by app.requestSettings.configuration.collectAsStateWithLifecycle()
                        SettingsScreen(display, app.settings::update, account.user, account::logout, { nav.popBackStack() }, ::openWebPage,
                            { nav.navigate("ignored-users") }, "${if (network.enabled) network.summary else "DoH 已关闭"} · ${limits.concurrent} 路并发",
                            { nav.navigate("doh-settings") })
                    }
                    composable("downloads") { DownloadsScreen(app.downloads) { nav.popBackStack() } }
                    composable("doh-settings") { DohSettingsScreen(app.network, app.requestSettings) { nav.popBackStack() } }
                    composable("about") { AboutScreen(openLink = ::openWebPage, openExternal = ::openBrowser) { nav.popBackStack() } }
                    composable("ignored-users") {
                        LaunchedEffect(Unit) { account.checkSession() }
                        key(account.user?.id) { IgnoredUsersScreen(account.user, app.repository, { nav.popBackStack() }, ::login) { id, username ->
                            account.recordIgnore(id, username, false); refreshEpoch++
                        } }
                    }
                    composable("bookmarks") { LaunchedEffect(account.user?.id) { account.loadSaved() }; BookmarksScreen(account, { nav.popBackStack() }, ::login, ::topic) }
                    composable("unavailable") {
                        MaterialPagePlaceholder("内容不可访问", { nav.popBackStack() }) {
                            ErrorState(ApiFailure(FailureKind.NOT_FOUND, "内容不存在或暂无权限"), {}, ::login, { nav.popBackStack() })
                        }
                    }
                    composable("user-search/{username}") { entry ->
                        val username = entry.arguments!!.getString("username")!!
                        val vm = model("user-search-$username") { SearchViewModel(app.repository, account.user?.id, "@$username") }
                        val state by vm.state.collectAsStateWithLifecycle()
                        LaunchedEffect(vm, account.user?.id) { vm.useAccount(account.user?.id) }
                        SearchScreen(state.forAccount(account.user?.id), vm::search, { nav.popBackStack() }, ::topic, ::user, ::login, listStyle = display.topicListStyle,
                            openCategory = { nav.navigate("category/$it") }, openTag = ::openTag,
                            changeKind = vm::changeKind, changeSort = vm::changeSort, clear = vm::clear, refreshHistory = vm::refreshHistory,
                            loadMetadata = vm::loadMetadata, suggestTags = vm::suggestTags, applyFilters = vm::applyFilters, clearHistory = vm::clearHistory,
                            initialScrollIndex = vm.scrollIndex, initialScrollOffset = vm.scrollOffset, saveScrollPosition = vm::saveScrollPosition)
                    }
                    composable("user/{username}") { entry ->
                        val username = entry.arguments!!.getString("username")!!
                        // 虎扑帖子的作者 username 承载的是 euid（纯数字长串，见 HupuBridge.toMember）；
                        // Discourse 用户名不会纯数字 —— 借此分流到虎扑用户主页（SSR 匿名可读）。
                        if (username.all(Char::isDigit)) {
                            com.kudo.android.hupuui.pages.UserProfilePage(euid = username, onClose = { nav.popBackStack() })
                        } else key(username, account.user?.id) {
                            UserScreen(username, app.repository, { nav.popBackStack() }, ::login, ::openLink,
                                viewerId = account.user?.id, statusBarStyle = { darkStatusIcons = it },
                                search = { nav.navigate("user-search/${Uri.encode(username)}") },
                                message = { authenticated { nav.navigate("new-message?recipient=${Uri.encode(username)}") } }, openWeb = ::openWebPage)
                        }
                    }
                    composable("compose?topic={topic}&reply={reply}&category={category}", arguments = listOf(navArgument("topic") { type = NavType.IntType; defaultValue = 0 }, navArgument("reply") { type = NavType.IntType; defaultValue = 0 }, navArgument("category") { type = NavType.IntType; defaultValue = 0 })) { entry ->
                        val topicId = entry.arguments!!.getInt("topic").takeIf { it > 0 }
                        val replyTo = entry.arguments!!.getInt("reply").takeIf { it > 0 }
                        val categoryId = entry.arguments!!.getInt("category").takeIf { it > 0 }
                        val currentUser = account.user
                        if (currentUser == null) {
                            MaterialPagePlaceholder("发布", { nav.popBackStack() }) { EmptyState("登录后发布", "草稿会自动保存在本机。", actionLabel = "登录", action = ::login) }
                        } else {
                            val vm = model("editor-${currentUser.id}-$topicId-$replyTo-$categoryId") { EditorViewModel(app, currentUser.id, topicId, replyTo, categoryId = categoryId) }
                            LaunchedEffect(Unit) { if (account.categories.isEmpty()) account.loadCategories() }
                            LaunchedEffect(vm) { vm.published.collect { result ->
                                nav.previousBackStackEntry?.savedStateHandle?.set("refresh", System.nanoTime())
                                nav.popBackStack(); refreshEpoch++
                                if (!result.queued && topicId == null) result.topicId?.let { topic(it) }
                                showMessage(if (result.queued) "内容已提交，等待社区审核" else "发布成功")
                            } }
                            EditorScreen(vm, account.categories, account.categoryError, account::loadCategories, { nav.popBackStack() })
                        }
                    }
                }
            }
            }
            }
        }
        }
        }
        }
        }
        }
        }
    }
}
