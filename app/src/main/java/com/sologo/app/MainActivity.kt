package com.sologo.app

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sologo.app.data.AppSettings
import com.sologo.app.ui.about.AboutScreen
import com.sologo.app.ui.about.PrivacyPolicyScreen
import com.sologo.app.ui.components.PrivacyDialog
import com.sologo.app.ui.game.GameScreen
import com.sologo.app.ui.home.HomeScreen
import com.sologo.app.ui.review.ReviewScreen
import com.sologo.app.ui.review.ReviewDetailScreen
import com.sologo.app.ui.rules.RulesScreen
import com.sologo.app.ui.settings.SettingsScreen
import com.sologo.app.ui.theme.SoloGoAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SoloGoApplication
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            SoloGoAppTheme(darkTheme = darkTheme) {
                val settingsState by app.container.settings.settings
                    .collectAsStateWithLifecycle(initialValue = null as AppSettings?)
                val settings = settingsState
                if (settings == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    SoloGoApp(container = app.container)
                    if (!settings.privacyDecisionMade) {
                        PrivacyDialog(
                            onAccept = {
                                lifecycleScope.launch { app.container.settings.setPrivacyChoice(true) }
                            },
                            onDecline = {
                                lifecycleScope.launch { app.container.settings.setPrivacyChoice(false) }
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: Int)

private val tabs = listOf(
    TabItem("home", "对弈", R.drawable.ic_play),
    TabItem("rules", "规则", R.drawable.ic_rules),
    TabItem("review", "复盘", R.drawable.ic_review),
    TabItem("settings", "设置", R.drawable.ic_settings)
)

@Composable
private fun SoloGoApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route.orEmpty()
    val routeBase = route.substringBefore("?")
    val showBottomBar = routeBase in setOf("rules", "home", "review", "settings")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                ZenBottomNavigation(
                    selectedRoute = routeBase,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("rules") {
                RulesScreen()
            }
            composable("home") {
                HomeScreen(
                    container = container,
                    onStartGame = { navController.navigate("game") }
                )
            }
            composable("game") {
                GameScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("review") {
                ReviewScreen(
                    container = container,
                    onOpenReview = { file ->
                        navController.navigate("review/detail?path=${Uri.encode(file.absolutePath)}")
                    }
                )
            }
            composable(
                route = "review/detail?path={path}",
                arguments = listOf(
                    navArgument("path") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                ReviewDetailScreen(
                    container = container,
                    filePath = entry.arguments?.getString("path")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(Uri::decode),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    container = container,
                    onOpenPrivacy = { navController.navigate("privacy") },
                    onOpenAbout = { navController.navigate("about") }
                )
            }
            composable("privacy") {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable("about") {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun ZenBottomNavigation(
    selectedRoute: String,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(21.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            ),
            shadowElevation = 7.dp
        ) {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(21.dp)),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedRoute == tab.route,
                        onClick = { onSelect(tab.route) },
                        icon = {
                            Icon(
                                painterResource(tab.icon),
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor =
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
