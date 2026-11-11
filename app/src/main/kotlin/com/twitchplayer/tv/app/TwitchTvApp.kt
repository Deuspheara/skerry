package com.twitchplayer.tv.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.twitchplayer.tv.R
import com.twitchplayer.tv.core.designsystem.component.NavRail
import com.twitchplayer.tv.core.designsystem.component.RailEdgeGuard
import com.twitchplayer.tv.core.designsystem.component.NavRailItem
import com.twitchplayer.tv.core.designsystem.component.ToastState
import com.twitchplayer.tv.core.designsystem.component.TvToastHost
import com.twitchplayer.tv.core.designsystem.component.LocalToast
import com.twitchplayer.tv.core.designsystem.icon.TvIcons
import com.twitchplayer.tv.core.designsystem.theme.LocalMotion
import com.twitchplayer.tv.core.designsystem.theme.TvColors
import com.twitchplayer.tv.core.ui.AppActions
import com.twitchplayer.tv.core.ui.LocalAppActions
import com.twitchplayer.tv.feature.browse.ui.BrowseRoute
import com.twitchplayer.tv.feature.category.ui.CategoryRoute
import com.twitchplayer.tv.feature.channel.ui.ChannelRoute
import com.twitchplayer.tv.feature.follows.ui.FollowsRoute
import com.twitchplayer.tv.feature.home.ui.HomeRoute
import com.twitchplayer.tv.feature.player.presentation.PlayerViewModel
import com.twitchplayer.tv.feature.player.ui.PlayerHost
import com.twitchplayer.tv.feature.search.ui.SearchRoute
import com.twitchplayer.tv.feature.settings.ui.SettingsRoute
import com.twitchplayer.tv.navigation.AppNavigator
import com.twitchplayer.tv.navigation.BrowseKey
import com.twitchplayer.tv.navigation.CategoryKey
import com.twitchplayer.tv.navigation.ChannelKey
import com.twitchplayer.tv.navigation.ClipKey
import com.twitchplayer.tv.navigation.VodKey
import com.twitchplayer.tv.feature.vod.ui.VodRoute
import com.twitchplayer.tv.feature.clip.ui.ClipRoute
import com.twitchplayer.tv.navigation.FollowsKey
import com.twitchplayer.tv.navigation.HomeKey
import com.twitchplayer.tv.navigation.PlayerKey
import com.twitchplayer.tv.navigation.ProfileKey
import com.twitchplayer.tv.navigation.SearchKey
import com.twitchplayer.tv.navigation.TopLevelKeys
import com.twitchplayer.tv.navigation.railIndexOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.twitchplayer.tv.core.ui.ChannelKeyBus
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.derivedStateOf

private val TAB = mapOf<String, Any>(NAV_KIND to KIND_TAB)
private val PLAYER = mapOf<String, Any>(NAV_KIND to KIND_PLAYER)

@Composable
fun TwitchTvApp(
    onQuit: () -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
    channelKeys: ChannelKeyBus = koinInject(),
    // One player for the whole app: it keeps playing (as a mini player) while other screens are shown.
    player: PlayerViewModel = koinViewModel(key = "player") { parametersOf("") },
) {
    val backStack = rememberNavBackStack(HomeKey)
    val navigator = remember(backStack) { AppNavigator(backStack) }
    val toast = remember { ToastState() }
    var showQuit by remember { mutableStateOf(false) }
    val signIn by authViewModel.state.collectAsState()
    val motion = LocalMotion.current

    val actions = remember(authViewModel) {
        object : AppActions {
            override fun signIn() = authViewModel.signIn()
            override fun signOut() = authViewModel.signOut()
            override fun requestQuit() { showQuit = true }
        }
    }

    val top = backStack.lastOrNull()
    // A channel page keeps the rail of the screen it was opened from (Home when it came from the player).
    val railIndex = railIndexOf(top)
        ?: if (top is ChannelKey) backStack.dropLast(1).asReversed().firstNotNullOfOrNull(::railIndexOf) ?: 0 else null
    val playerFullScreen = top is PlayerKey
    val edgeGuard = remember { RailEdgeGuard() }
    val playerState = player.state.collectAsState()
    val miniActive by remember { derivedStateOf { playerState.value.active && !playerFullScreen } }
    val hasPlayerScreen = backStack.any { it is PlayerKey }
    LaunchedEffect(top) { if (top is PlayerKey) player.attach(top.login) }
    LaunchedEffect(hasPlayerScreen) { if (!hasPlayerScreen) player.detach() }
    // Leaving the player screen with Back ends the session; going to another screen via the dock keeps it as a mini player.
    val leavePlayer = { player.close(); navigator.pop() }
    // Overlay Back is handled by each overlay's own window; this only runs on a bare top-level screen.
    BackHandler(enabled = backStack.size <= 1) { if (navigator.back()) showQuit = true }

    val railItems = listOf(
        NavRailItem(TvIcons.Home, stringResource(R.string.nav_home)),
        NavRailItem(TvIcons.Heart, stringResource(R.string.nav_follows)),
        NavRailItem(TvIcons.Grid, stringResource(R.string.nav_browse)),
        NavRailItem(TvIcons.Search, stringResource(R.string.nav_search)),
        NavRailItem(TvIcons.User, stringResource(R.string.nav_profile)),
    )

    // "X is live now" for followed channels (not the one already on screen), only while the app is in front.
    val liveWatcher: com.twitchplayer.tv.core.data.repository.LiveWatcher = koinInject()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val nowLive = stringResource(R.string.toast_now_live)
    LaunchedEffect(liveWatcher, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            liveWatcher.wentLive().collect { stream ->
                val watching = player.state.value.takeIf { it.active }?.login
                if (!stream.login.equals(watching, ignoreCase = true)) toast.show(String.format(nowLive, stream.name))
            }
        }
    }

    val returnToStream = stringResource(R.string.player_return)
    val railWithPlayer = if (miniActive) railItems + NavRailItem(TvIcons.Maximize, returnToStream, TvColors.Purple) else railItems

    val streamPreview = com.twitchplayer.tv.feature.player.playback.rememberStreamPreview(koinInject())
    CompositionLocalProvider(
        LocalAppActions provides actions,
        LocalToast provides toast,
        com.twitchplayer.tv.feature.player.playback.LocalStreamPreview provides streamPreview,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(TvColors.Body)
                // CHANNEL +/- and page keys switch channel from anywhere (consumed so they never reach a list).
                .onPreviewKeyEvent { event ->
                    edgeGuard.onKey(event)
                    com.twitchplayer.tv.core.ui.DirectionKeys.onKey(event)
                    val delta = when (event.key) {
                        Key.ChannelUp, Key.PageUp -> -1
                        Key.ChannelDown, Key.PageDown -> 1
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) channelKeys.emit(delta)
                    true
                },
        ) {
            Row(Modifier.fillMaxSize()) {
                if (railIndex != null) {
                    NavRail(
                        railWithPlayer, railIndex,
                        edgeGuard = edgeGuard,
                        onSelect = {
                            if (it < TopLevelKeys.size) navigator.openTopLevel(TopLevelKeys[it])
                            else navigator.push(PlayerKey(player.state.value.login))
                        },
                    )
                }
                androidx.compose.animation.SharedTransitionLayout(Modifier.weight(1f)) {
                CompositionLocalProvider(com.twitchplayer.tv.core.ui.LocalSharedTransition provides this) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = {
                        if (top is PlayerKey) leavePlayer() else navigator.back().also { if (it) showQuit = true }
                    },
                    entryDecorators = listOf(
                        rememberScreenFill(),
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    transitionSpec = { screenTransition(this, motion, pop = false) },
                    popTransitionSpec = { screenTransition(this, motion, pop = true) },
                    predictivePopTransitionSpec = { screenTransition(this, motion, pop = true) },
                    entryProvider = entryProvider<NavKey> {
                        entry<HomeKey>(metadata = TAB) {
                            HomeRoute(
                                onWatch = { navigator.watch(it.login) },
                                onCategory = { navigator.push(CategoryKey(it.id, it.name, it.artUrl)) },
                            )
                        }
                        entry<FollowsKey>(metadata = TAB) {
                            FollowsRoute(onWatch = { navigator.watch(it.login) }, onChannel = { navigator.push(ChannelKey(it.login)) })
                        }
                        entry<BrowseKey>(metadata = TAB) { BrowseRoute(onCategory = { navigator.push(CategoryKey(it.id, it.name, it.artUrl)) }) }
                        entry<SearchKey>(metadata = TAB) {
                            SearchRoute(
                                onChannel = { navigator.push(ChannelKey(it.login)) },
                                onCategory = { navigator.push(CategoryKey(it.id, it.name, it.artUrl)) },
                            )
                        }
                        entry<ProfileKey>(metadata = TAB) { SettingsRoute() }
                        entry<CategoryKey> { key ->
                            CategoryRoute(key.id, key.name, key.artUrl, onBack = navigator::pop, onWatch = { navigator.watch(it.login) })
                        }
                        entry<ChannelKey> { key ->
                            ChannelRoute(
                                login = key.login,
                                onBack = navigator::pop,
                                onWatch = navigator::watch,
                                onChannel = { navigator.push(ChannelKey(it)) },
                                // One video at a time: the live session (and its chat) ends when a clip starts.
                                onClip = { clip -> player.close(); navigator.push(ClipKey(clip.slug, clip.title)) },
                                onReplay = { replay -> player.close(); navigator.push(VodKey(replay.id, replay.title, replay.durationSeconds)) },
                            )
                        }
                        entry<ClipKey> { key -> ClipRoute(key.slug, key.title, onBack = navigator::pop) }
                        entry<VodKey> { key -> VodRoute(key.id, key.title, key.durationSeconds, onBack = navigator::pop) }
                        // The player itself is drawn by PlayerHost (above this stack); this entry only marks "player on top".
                        // No fill of its own: PlayerHost, the app root and the window already paint the same colour, and every
                        // extra full-screen fill is one more pass of the TV's GPU over the video area (overdraw).
                        entry<PlayerKey>(metadata = PLAYER) { Box(Modifier.fillMaxSize()) }
                    },
                )
                }
                }
            }
            PlayerHost(
                viewModel = player,
                fullScreen = playerFullScreen,
                dockItems = railItems,
                onDockSelect = { navigator.openTopLevel(TopLevelKeys[it]) },
                onBack = leavePlayer,
                onOpenChannel = { navigator.push(ChannelKey(it)) },
                onExpand = { navigator.push(PlayerKey(player.state.value.login)) },
            )
            TvToastHost(toast)
            signIn.code?.let { SignInDialog(it, onCancel = authViewModel::cancel) }
            if (showQuit) QuitDialog(onStay = { showQuit = false }, onQuit = onQuit)
        }
    }
}
