package com.vknxworks.truectrl.ui.nav

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.ui.settings.ServersScreen
import com.vknxworks.truectrl.ui.setup.SetupScreen
import com.vknxworks.truectrl.ui.components.CtrlBottomBar
import com.vknxworks.truectrl.ui.components.LocalBottomClearance
import com.vknxworks.truectrl.ui.components.TabItem
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.components.rememberNotificationAccess
import com.vknxworks.truectrl.ui.lock.lockCovering
import com.vknxworks.truectrl.ui.overview.AlertsScreen
import com.vknxworks.truectrl.ui.overview.OverviewScreen
import com.vknxworks.truectrl.ui.protection.ProtectionScreen
import com.vknxworks.truectrl.ui.settings.SettingsScreen
import com.vknxworks.truectrl.ui.settings.YouScreen
import com.vknxworks.truectrl.ui.sharing.SharingScreen
import com.vknxworks.truectrl.ui.storage.DiskScreen
import com.vknxworks.truectrl.ui.storage.PoolScreen
import com.vknxworks.truectrl.ui.storage.StorageScreen
import com.vknxworks.truectrl.ui.system.SystemScreen

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val servers by c.sessions.servers.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (servers.isEmpty()) {
            // First start (or every server removed): setup fills the screen, no tabs.
            SetupScreen(onDone = {}, onCancel = null)
        } else {
            MainScaffold()
        }
    }
}

private data class TopDest(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/** The five tabs. Overview is the only summary screen; account and settings open from the profile picture. */
private val topDestinations = listOf(
    TopDest(OverviewRoute, R.string.tab_overview, Icons.Outlined.Dashboard, Icons.Outlined.Dashboard),
    TopDest(StorageRoute, R.string.tab_storage, Icons.Outlined.Storage, Icons.Outlined.Storage),
    TopDest(ProtectionRoute, R.string.tab_protection, Icons.Outlined.Shield, Icons.Outlined.Shield),
    TopDest(SharingRoute, R.string.tab_sharing, Icons.Outlined.FolderShared, Icons.Outlined.FolderShared),
    TopDest(SystemRoute, R.string.tab_system, Icons.Outlined.Dns, Icons.Outlined.Dns),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }
    val tabs = topDestinations.map { TabItem(stringResource(it.label), it.icon, it.selectedIcon) }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Android 13+ drops notifications until they're allowed: with background checks or push on, ask
    // once, as soon as TrueCtrl is unlocked. After that, the Notifications screen shows it and offers the fix.
    val c = LocalContext.current.container
    val notifySettings by c.notifySettings.settings.collectAsStateWithLifecycle()
    val stored by c.settings.stored.collectAsState(initial = null)
    val covered = lockCovering(stored?.appLock ?: c.lockAtStart, c.vault)
    val notificationAccess = rememberNotificationAccess()
    val notificationsInUse = notifySettings.polling || notifySettings.push
    LaunchedEffect(covered, notificationsInUse, notificationAccess.granted) {
        if (!covered && notificationsInUse) notificationAccess.askOnce()
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                // Tablets, unfolded foldables and landscape: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = CtrlColors.Background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        tabs.forEachIndexed { i, tab ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp)) },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.Text,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(
                    navController = navController,
                    tabs = tabs,
                    showTabs = selectedIndex >= 0,
                    selectedIndex = selectedIndex,
                    onSelect = ::selectTab,
                )
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under a floating glass island.
 * The island shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    tabs: List<TabItem>,
    showTabs: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AppNavHost(navController)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = showTabs,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    CtrlBottomBar(items = tabs, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = OverviewRoute) {
        composable<OverviewRoute> { OverviewScreen() }
        composable<StorageRoute> { StorageScreen() }
        composable<ProtectionRoute> { ProtectionScreen() }
        composable<SharingRoute> { SharingScreen() }
        composable<SystemRoute> { SystemScreen() }

        composable<AlertsRoute> { AlertsScreen() }
        composable<PoolRoute> { PoolScreen(it.toRoute<PoolRoute>().id) }
        composable<DiskRoute> { DiskScreen(it.toRoute<DiskRoute>().name) }
        composable<DatasetRoute> { com.vknxworks.truectrl.ui.storage.DatasetScreen(it.toRoute<DatasetRoute>().id) }
        composable<SnapshotsRoute> { com.vknxworks.truectrl.ui.protection.SnapshotsScreen(it.toRoute<SnapshotsRoute>().dataset) }

        composable<ShareEditRoute> {
            val r = it.toRoute<ShareEditRoute>()
            com.vknxworks.truectrl.ui.sharing.ShareEditScreen(
                if (r.kind == "NFS") com.vknxworks.truectrl.data.sharing.ShareKind.NFS else com.vknxworks.truectrl.data.sharing.ShareKind.SMB,
                r.id.takeIf { id -> id >= 0 },
            )
        }

        composable<AppsRoute> { com.vknxworks.truectrl.ui.system.AppsScreen() }
        composable<AppLogsRoute> {
            val r = it.toRoute<AppLogsRoute>()
            com.vknxworks.truectrl.ui.system.AppLogsScreen(r.app, r.container, r.service)
        }
        composable<JobsRoute> { com.vknxworks.truectrl.ui.system.JobsScreen() }
        composable<UpdatesRoute> { com.vknxworks.truectrl.ui.system.UpdatesScreen() }
        composable<NetworkRoute> { com.vknxworks.truectrl.ui.system.NetworkScreen() }
        composable<ShellRoute> { com.vknxworks.truectrl.ui.system.ShellScreen() }

        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<NotificationsRoute> { com.vknxworks.truectrl.ui.settings.NotificationsScreen() }
        composable<ServersRoute> { ServersScreen() }
        composable<AddServerRoute> {
            val navigator = LocalNavigator.current
            SetupScreen(onDone = { navController.popBackStack(OverviewRoute, false) }, onCancel = navigator::back)
        }
        composable<EditServerRoute> {
            val navigator = LocalNavigator.current
            SetupScreen(editId = it.toRoute<EditServerRoute>().id, onDone = navigator::back, onCancel = navigator::back)
        }
    }
}
