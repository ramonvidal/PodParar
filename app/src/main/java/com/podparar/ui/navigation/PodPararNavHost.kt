package com.podparar.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.podparar.R
import com.podparar.core.ui.R as CoreUiR
import com.podparar.feature.discover.DISCOVER_ROUTE
import com.podparar.feature.discover.discoverScreen
import com.podparar.feature.home.HOME_ROUTE
import com.podparar.feature.home.homeScreen
import com.podparar.feature.library.LIBRARY_ROUTE
import com.podparar.feature.library.libraryScreen
import com.podparar.feature.player.MiniPlayer
import com.podparar.feature.player.playerScreen
import com.podparar.feature.settings.settingsScreen
import com.podparar.feature.update.UpdateBanner

enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Home(HOME_ROUTE, R.string.nav_home, CoreUiR.drawable.ic_home),
    Library(LIBRARY_ROUTE, R.string.nav_library, CoreUiR.drawable.ic_library),
    Discover(DISCOVER_ROUTE, R.string.nav_discover, CoreUiR.drawable.ic_search),
}

/** The three tabs, with the mini player sitting just above them on every screen. */
@Composable
fun PodPararNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        topBar = { UpdateBanner() },
        bottomBar = {
            Column {
                MiniPlayer()
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination.route) },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                // No pill: the selected tab is just green.
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            homeScreen()
            libraryScreen()
            discoverScreen()
            playerScreen()
            settingsScreen()
        }
    }
}

/** Switches tabs the way the bottom bar does, keeping each tab's state. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
