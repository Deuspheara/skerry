package com.twitchplayer.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.twitchplayer.phone.R
import com.twitchplayer.phone.feature.auth.SignInDialog
import com.twitchplayer.phone.feature.browse.BrowseRoute
import com.twitchplayer.phone.feature.browse.CategoryRoute
import com.twitchplayer.phone.feature.channel.ChannelRoute
import com.twitchplayer.phone.feature.home.HomeRoute
import com.twitchplayer.phone.feature.search.SearchRoute
import com.twitchplayer.phone.feature.settings.SettingsRoute
import com.twitchplayer.phone.navigation.BrowseKey
import com.twitchplayer.phone.navigation.CategoryKey
import com.twitchplayer.phone.navigation.ChannelKey
import com.twitchplayer.phone.navigation.HomeKey
import com.twitchplayer.phone.navigation.KIND_TAB
import com.twitchplayer.phone.navigation.NAV_KIND
import com.twitchplayer.phone.navigation.PhoneNavigator
import com.twitchplayer.phone.navigation.PhoneTab
import com.twitchplayer.phone.navigation.PlaybackTarget
import com.twitchplayer.phone.navigation.PlayerLauncher
import com.twitchplayer.phone.navigation.SearchKey
import com.twitchplayer.phone.navigation.SettingsKey
import com.twitchplayer.phone.navigation.phoneTransition
import com.twitchplayer.phone.navigation.rememberScreenFill
import com.twitchplayer.phone.ui.components.PhoneIcons
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
        var pendingMessage by remember { mutableStateOf<String?>(null) }
        val soon = stringResource(R.string.phone_player_soon)
        val player = remember(soon) {
            // Integration point of the player step: until the player screen exists a tap says so.
            PlayerLauncher { _: PlaybackTarget -> pendingMessage = soon }
        }
        LaunchedEffect(pendingMessage) {
            pendingMessage?.let {
                snackbar.showSnackbar(it)
                pendingMessage = null
            }
        }

        Box(modifier.fillMaxSize()) {
            PhoneScaffold(
                reducedMotion = reducedMotion,
                player = player,
                snackbar = snackbar,
                onSignIn = { authViewModel.presenter.onEvent(AuthEvent.SignIn) },
                onSignOut = { authViewModel.presenter.onEvent(AuthEvent.SignOut) },
            )
        }
        signIn.code?.let { code -> SignInDialog(code, onCancel = { authViewModel.presenter.onEvent(AuthEvent.Cancel) }) }
    }
}

@Composable
private fun PhoneScaffold(
    reducedMotion: Boolean,
    player: PlayerLauncher,
    snackbar: SnackbarHostState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val stacks = PhoneTab.entries.associateWith { rememberNavBackStack(it.root) }
    var selectedName by rememberSaveable { mutableStateOf(PhoneTab.Home.name) }
    val navigator = remember(stacks) {
        PhoneNavigator(stacks, PhoneTab.valueOf(selectedName)) { selectedName = it.name }
    }
    val adaptive = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            PhoneTab.entries.forEachIndexed { index, tab ->
                item(
                    selected = tab == navigator.selected,
                    onClick = { navigator.selectTab(tab) },
                    icon = { Icon(tab.icon(), contentDescription = null) },
                    label = { Text(stringResource(tab.label())) },
                    // In a rail the first item sat right under the status bar, above the page title: it starts level with the title.
                    modifier = if (index == 0 && navigator.showsNavigation && adaptive != NavigationSuiteType.NavigationBar) {
                        Modifier.padding(top = RAIL_TOP_GAP)
                    } else {
                        Modifier
                    },
                )
            }
        },
        layoutType = if (navigator.showsNavigation) adaptive else NavigationSuiteType.None,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        BackHandler(enabled = navigator.canGoBack && navigator.currentStack.size == 1) { navigator.back() }
        val tab = mapOf<String, Any>(NAV_KIND to KIND_TAB)
        Box(Modifier.fillMaxSize()) {
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
                            onChannel = { login -> navigator.push(ChannelKey(login)) },
                            onCategory = { category -> navigator.push(CategoryKey(category.id, category.name)) },
                            onSignIn = onSignIn,
                        )
                    }
                    entry<BrowseKey>(metadata = tab) {
                        BrowseRoute(onCategory = { category -> navigator.push(CategoryKey(category.id, category.name)) }, onSignIn = onSignIn)
                    }
                    entry<SearchKey>(metadata = tab) {
                        SearchRoute(
                            onChannel = { login -> navigator.push(ChannelKey(login)) },
                            onCategory = { category -> navigator.push(CategoryKey(category.id, category.name)) },
                        )
                    }
                    entry<SettingsKey>(metadata = tab) { SettingsRoute(onSignIn = onSignIn, onSignOut = onSignOut) }
                    entry<CategoryKey> { key ->
                        CategoryRoute(key, onBack = navigator::pop, onChannel = { login -> navigator.push(ChannelKey(login)) }, onSignIn = onSignIn)
                    }
                    entry<ChannelKey> { key ->
                        ChannelRoute(
                            login = key.login,
                            onBack = navigator::pop,
                            onPlay = player::open,
                            onChannel = { login -> navigator.push(ChannelKey(login)) },
                            onSignIn = onSignIn,
                        )
                    }
                },
            )
            // Inside the scaffold's content area: above the bar, beside the rail, above the keyboard and the gesture bar, whatever the text size.
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
        }
    }
}

private fun PhoneTab.icon(): ImageVector = when (this) {
    PhoneTab.Home -> Icons.Filled.Home
    PhoneTab.Browse -> PhoneIcons.Browse
    PhoneTab.Search -> Icons.Filled.Search
    PhoneTab.Settings -> Icons.Filled.Settings
}

private fun PhoneTab.label(): Int = when (this) {
    PhoneTab.Home -> R.string.nav_home
    PhoneTab.Browse -> R.string.nav_browse
    PhoneTab.Search -> R.string.nav_search
    PhoneTab.Settings -> R.string.phone_nav_settings
}

private val RAIL_TOP_GAP = 16.dp
