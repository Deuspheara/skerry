package com.twitchplayer.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.auth.SignInDialog
import com.twitchplayer.phone.feature.browse.BrowseRoute
import com.twitchplayer.phone.feature.browse.CategoryRoute
import com.twitchplayer.phone.feature.channel.ChannelRoute
import com.twitchplayer.phone.feature.discover.DiscoverRoute
import com.twitchplayer.phone.feature.following.FollowingRoute
import com.twitchplayer.phone.feature.home.HomeRoute
import com.twitchplayer.phone.feature.player.PlayerOverlay
import com.twitchplayer.phone.feature.player.PlayerSessionController
import com.twitchplayer.phone.feature.player.PlayerSessionEvent
import com.twitchplayer.phone.feature.player.PlayerSessionState
import com.twitchplayer.phone.feature.player.SheetState
import com.twitchplayer.phone.feature.player.rememberStripHeight
import com.twitchplayer.phone.feature.search.SearchRoute
import com.twitchplayer.phone.feature.settings.SettingsRoute
import com.twitchplayer.phone.navigation.BrowseKey
import com.twitchplayer.phone.navigation.CategoryKey
import com.twitchplayer.phone.navigation.ChannelKey
import com.twitchplayer.phone.navigation.DiscoverKey
import com.twitchplayer.phone.navigation.FollowingKey
import com.twitchplayer.phone.navigation.HomeKey
import com.twitchplayer.phone.navigation.KIND_FADE
import com.twitchplayer.phone.navigation.KIND_TAB
import com.twitchplayer.phone.navigation.NAV_KIND
import com.twitchplayer.phone.navigation.PhoneNavigator
import com.twitchplayer.phone.navigation.PhoneTab
import com.twitchplayer.phone.navigation.PlaybackTarget
import com.twitchplayer.phone.navigation.SearchKey
import com.twitchplayer.phone.navigation.SettingsKey
import com.twitchplayer.phone.navigation.SignedInHint
import com.twitchplayer.phone.navigation.phoneTransition
import com.twitchplayer.phone.navigation.rememberGuardedBackStack
import com.twitchplayer.phone.navigation.rememberScreenFill
import com.twitchplayer.phone.ui.components.HeaderActions
import com.twitchplayer.phone.ui.components.LocalSharedTransitionScope
import com.twitchplayer.phone.ui.components.PhoneBottomBar
import com.twitchplayer.phone.ui.components.PhoneChrome
import com.twitchplayer.phone.ui.components.PhoneRail
import com.twitchplayer.phone.ui.components.isWideWindow
import com.twitchplayer.phone.ui.theme.PhoneTheme
import com.twitchplayer.phone.ui.theme.ThemePreference
import com.twitchplayer.phone.ui.theme.systemAnimationsOff
import com.twitchplayer.tv.app.AuthViewModel
import com.twitchplayer.tv.feature.auth.presentation.AuthEvent
import com.twitchplayer.tv.feature.auth.presentation.SignInResult
import com.twitchplayer.tv.feature.settings.presentation.SettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Wires the theme, the sign-in flow, the adaptive tab scaffold and the navigation display. */
@Composable
fun PhoneRoot(
    modifier: Modifier = Modifier,
    themePreference: ThemePreference = koinInject(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    authViewModel: AuthViewModel = koinViewModel(),
) {
    val themeMode by themePreference.mode.collectAsStateWithLifecycle()
    val settings by settingsViewModel.presenter.state.collectAsStateWithLifecycle()
    val signIn by authViewModel.presenter.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reducedMotion = !settings.animationsEnabled || systemAnimationsOff(context)

    PhoneTheme(themeMode, reducedMotion) {
        val snackbar = remember { SnackbarHostState() }
        val signedInMessage = stringResource(R.string.phone_signin_done)
        val failedMessage = stringResource(R.string.phone_signin_failed)
        // The result lives in the presenter (the activity-scoped holder), so a rotation between the end of the sign-in and this message
        // cannot lose it; it is acknowledged only after the message was shown.
        LaunchedEffect(signIn.result) {
            val message = when (signIn.result) {
                SignInResult.Success -> signedInMessage
                SignInResult.Failed -> failedMessage
                null -> return@LaunchedEffect
            }
            snackbar.showSnackbar(message)
            authViewModel.presenter.onEvent(AuthEvent.ResultShown)
        }
        Box(modifier.fillMaxSize()) {
            PhoneScaffold(
                reducedMotion = reducedMotion,
                snackbar = snackbar,
                account = settings.account,
                sessionLoading = settings.sessionLoading,
                onSignIn = { authViewModel.presenter.onEvent(AuthEvent.SignIn) },
                onSignOut = { authViewModel.presenter.onEvent(AuthEvent.SignOut) },
            )
        }
        signIn.code?.let { code -> SignInDialog(code, onCancel = { authViewModel.presenter.onEvent(AuthEvent.Cancel) }) }
    }
}

private fun searchMorphKey(tab: PhoneTab) = "search-field-${tab.name}"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhoneScaffold(
    reducedMotion: Boolean,
    snackbar: SnackbarHostState,
    account: String?,
    sessionLoading: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    // A saved stack that cannot be read (another build's pages, a damaged bundle) resets that tab to its root instead of crashing.
    val stacks = PhoneTab.entries.associateWith { rememberGuardedBackStack(it.root) }
    var selectedName by rememberSaveable { mutableStateOf(PhoneTab.Home.name) }
    val navigator = remember(stacks) {
        PhoneNavigator(stacks, PhoneTab.entries.firstOrNull { it.name == selectedName } ?: PhoneTab.Home) { selectedName = it.name }
    }
    // The player is a sheet above the tabs and their pages, not a page: it keeps playing while the viewer browses (see PlayerOverlay).
    val session = rememberSaveable(saver = PlayerSessionController.Saver) { PlayerSessionController() }
    val sessionOpen = session.state is PlayerSessionState.Open
    val openChannel = { login: String ->
        session.dispatch(PlayerSessionEvent.Minimise)
        navigator.push(ChannelKey(login))
    }
    // One header per tab: the search pill of each names its own morph into the Search page (see searchMorph).
    val headers = remember(navigator, account) {
        PhoneTab.entries.associateWith { tab ->
            HeaderActions(account, onSearch = { navigator.push(SearchKey) }, onProfile = { navigator.push(SettingsKey) }, morphKey = searchMorphKey(tab))
        }
    }
    val wide = isWideWindow()
    val chromeVisible = navigator.showsNavigation
    val selectTab = { tab: PhoneTab -> navigator.selectTab(tab) }
    // Following needs a viewer. While the session is being read the bar keeps the shape it had last time (no two-then-three tabs at every
    // start) and a restored Following selection is left alone; once it is known, signing out drops Following's pages and returns to Home.
    val context = LocalContext.current
    val hint = remember { SignedInHint(context) }
    val signedIn = account != null
    val tabs = remember(signedIn, sessionLoading) { PhoneTab.bar(signedIn, sessionLoading, hint.signedIn) }
    DisposableEffect(signedIn, sessionLoading) {
        if (!sessionLoading) {
            hint.signedIn = signedIn
            if (!signedIn) navigator.onSignedOut()
        }
        onDispose { }
    }
    DisposableEffect(tabs, sessionLoading) {
        if (PhoneTab.mustLeave(navigator.selected, tabs, sessionLoading)) navigator.selectTab(PhoneTab.Home)
        onDispose { }
    }
    Box(Modifier.fillMaxSize()) {
        // While the player is expanded it covers the page: TalkBack must not be able to reach what is behind it.
        val open = session.state as? PlayerSessionState.Open
        val inPip = open?.inPip == true
        val coveredByPlayer = open?.sheet == SheetState.Expanded || inPip
        Row(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                // The Picture-in-Picture window is the picture alone: the tabs and the page under the sheet are not drawn at all.
                .graphicsLayer { alpha = if (inPip) 0f else 1f }
                .then(if (coveredByPlayer) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            if (chromeVisible && wide) PhoneRail(tabs, navigator.selected, selectTab)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // The bar's or the rail's insets are spent there: the page must not pad for them a second time.
                val spent = when {
                    !chromeVisible -> WindowInsets(0)
                    wide -> WindowInsets.systemBars.only(WindowInsetsSides.Start)
                    else -> WindowInsets.navigationBars
                }
                // While a player is open its strip sits over the bottom of the page: the page ends above it (and above the gesture bar when the
                // tab bar, which normally covers that inset, is not showing).
                val spendsNavigationBar = sessionOpen && (!chromeVisible || wide)
                Box(
                    Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(spent)
                        .then(if (spendsNavigationBar) Modifier.navigationBarsPadding() else Modifier)
                        .padding(bottom = if (sessionOpen) rememberStripHeight() else 0.dp),
                ) {
                    BackHandler(enabled = navigator.canGoBack && navigator.currentStack.size == 1) { navigator.back() }
                    val tab = mapOf<String, Any>(NAV_KIND to KIND_TAB)
                    // Search opens in place (the header's pill grows into it): a fade, no slide.
                    val fade = mapOf<String, Any>(NAV_KIND to KIND_FADE)
                    Box(Modifier.fillMaxSize()) {
                        SharedTransitionLayout(Modifier.fillMaxSize()) {
                            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                                NavDisplay(
                                    backStack = navigator.currentStack,
                                    modifier = Modifier.fillMaxSize(),
                                    onBack = { navigator.back() },
                                    entryDecorators = listOf(
                                        rememberScreenFill(),
                                        rememberSaveableStateHolderNavEntryDecorator(),
                                        rememberViewModelStoreNavEntryDecorator(),
                                    ),
                                    transitionSpec = { phoneTransition(this, reducedMotion, pop = false) },
                                    popTransitionSpec = { phoneTransition(this, reducedMotion, pop = true) },
                                    predictivePopTransitionSpec = { phoneTransition(this, reducedMotion, pop = true) },
                                    entryProvider = entryProvider<NavKey> {
                                        entry<HomeKey>(metadata = tab) {
                                            HomeRoute(
                                                onWatch = { login -> session.open(PlaybackTarget.Live(login)) },
                                                onCategory = { category -> navigator.push(CategoryKey(category.id, category.name)) },
                                                onSignIn = onSignIn,
                                                header = headers.getValue(PhoneTab.Home),
                                            )
                                        }
                                        entry<DiscoverKey>(metadata = tab) {
                                            DiscoverRoute(
                                                onOpen = { login -> session.open(PlaybackTarget.Live(login)) },
                                                onOpenClip = { slug, title -> session.open(PlaybackTarget.Clip(slug, title)) },
                                                onChannel = { login -> navigator.push(ChannelKey(login)) },
                                                onSignIn = onSignIn,
                                                playerOpen = sessionOpen,
                                            )
                                        }
                                        entry<FollowingKey>(metadata = tab) {
                                            FollowingRoute(
                                                onWatch = { login -> session.open(PlaybackTarget.Live(login)) },
                                                onChannel = { login -> navigator.push(ChannelKey(login)) },
                                                onReplay = { target -> session.open(target) },
                                                onSignIn = onSignIn,
                                                header = headers.getValue(PhoneTab.Following),
                                            )
                                        }
                                        entry<BrowseKey>(metadata = tab) {
                                            BrowseRoute(onCategory = { category -> navigator.push(CategoryKey(category.id, category.name)) }, onSignIn = onSignIn, header = headers.getValue(PhoneTab.Browse))
                                        }
                                        entry<SearchKey>(metadata = fade) {
                                            SearchRoute(
                                                morphKey = searchMorphKey(navigator.selected),
                                                onBack = navigator::pop,
                                                onWatch = { login -> session.open(PlaybackTarget.Live(login)) },
                                                onChannel = { login -> navigator.push(ChannelKey(login)) },
                                                onCategory = { category -> navigator.push(CategoryKey(category.id, category.name)) },
                                            )
                                        }
                                        entry<SettingsKey> { SettingsRoute(onBack = navigator::pop, onSignIn = onSignIn, onSignOut = onSignOut) }
                                        entry<CategoryKey> { key ->
                                            CategoryRoute(key, onBack = navigator::pop, onWatch = { login -> session.open(PlaybackTarget.Live(login)) }, onSignIn = onSignIn)
                                        }
                                        entry<ChannelKey> { key ->
                                            ChannelRoute(
                                                login = key.login,
                                                onBack = navigator::pop,
                                                onPlay = session::open,
                                                onChannel = { login -> navigator.push(ChannelKey(login)) },
                                                onSignIn = onSignIn,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        // Inside the scaffold's content area: above the bar, beside the rail, above the keyboard and the gesture bar, whatever the text size.
                        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
                    }
                }
                if (chromeVisible && !wide) PhoneBottomBar(tabs, navigator.selected, selectTab)
            }
        }
        val bottomChrome by animateDpAsState(
            targetValue = if (chromeVisible && !wide) PhoneChrome.BarHeight + PhoneChrome.Hairline else 0.dp,
            animationSpec = if (reducedMotion) snap() else spring(),
            label = "player-bottom-chrome",
        )
        PlayerOverlay(
            session = session,
            bottomChrome = bottomChrome,
            startChrome = if (chromeVisible && wide) PhoneChrome.RailWidth + PhoneChrome.Hairline else 0.dp,
            onOpenChannel = openChannel,
            onSignIn = onSignIn,
        )
    }
}
