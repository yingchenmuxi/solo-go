package com.sologo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sologo.app.data.AppSettings
import com.sologo.app.ui.about.AboutScreen
import com.sologo.app.ui.about.PrivacyPolicyScreen
import com.sologo.app.ui.components.PrivacyDialog
import com.sologo.app.ui.game.GameScreen
import com.sologo.app.ui.home.HomeScreen
import com.sologo.app.ui.review.ReviewScreen
import com.sologo.app.ui.settings.SettingsScreen
import com.sologo.app.ui.theme.SoloGoAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SoloGoApplication
        setContent {
            SoloGoAppTheme {
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
    TabItem("home", "对弈", R.drawable.ic_home),
    TabItem("review", "复盘", R.drawable.ic_account_box),
    TabItem("settings", "设置", R.drawable.ic_account_box)
)

@Composable
private fun SoloGoApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route.orEmpty()
    val routeBase = route.substringBefore("?")
    val showBottomBar = routeBase in setOf("home", "review", "settings")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = routeBase == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painterResource(tab.icon),
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    container = container,
                    onStartGame = { navController.navigate("game") },
                    onOpenReview = { navController.navigate("review") },
                    onOpenSettings = { navController.navigate("settings") }
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
