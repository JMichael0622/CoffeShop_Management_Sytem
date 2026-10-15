package com.project2.coffeeshoppos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.project2.coffeeshoppos.ui.auth.AuthViewModel
import com.project2.coffeeshoppos.ui.auth.LocalCurrentUser
import com.project2.coffeeshoppos.ui.auth.SetupAdminScreen
import com.project2.coffeeshoppos.ui.auth.SignInScreen
import com.project2.coffeeshoppos.ui.navigation.AppNavHost
import com.project2.coffeeshoppos.ui.navigation.TopLevelDestination
import com.project2.coffeeshoppos.ui.navigation.navigateToTab

/**
 * The app's front door: create the admin account on first launch, then sign in, then the shop.
 * Signing in as someone else starts a fresh set of screens.
 */
@Composable
fun CoffeeShopApp(authViewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    val user = state.currentUser
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        !state.isSetUp -> SetupAdminScreen(state, authViewModel)
        user == null -> SignInScreen(state, authViewModel)
        else -> CompositionLocalProvider(LocalCurrentUser provides user) {
            key(user.id) { ShopShell(onSignOut = authViewModel::signOut) }
        }
    }
}

/**
 * The bottom navigation bar plus the area where screens are shown. Tabs this person may not use
 * are left out. The bar is hidden on secondary screens (like Manage), which have their own Back button.
 */
@Composable
private fun ShopShell(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val user = LocalCurrentUser.current
    val tabs = TopLevelDestination.entries.filter { tab -> tab.permission?.let(user::can) ?: true }
    val showBottomBar = tabs.any { currentDestination.isOn(it) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen handles the status bar itself, so the shell adds no extra top padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    tabs.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination.isOn(destination),
                            onClick = { navController.navigateToTab(destination) },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = {
                                Text(
                                    text = destination.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(navController = navController, onSignOut = onSignOut, modifier = Modifier.padding(innerPadding))
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
