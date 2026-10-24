package com.wordbook.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.lifecycleScope
import com.wordbook.app.data.DefaultDecks
import com.wordbook.app.data.ParentAlerts
import com.wordbook.app.data.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.wordbook.app.data.StudySettings
import com.wordbook.app.ui.DeckScreen
import com.wordbook.app.ui.HomeScreen
import com.wordbook.app.ui.OnboardingScreen
import com.wordbook.app.ui.ReviewScreen
import com.wordbook.app.ui.ScanScreen
import com.wordbook.app.ui.SettingsScreen
import com.wordbook.app.ui.StudyScreen
import com.wordbook.app.ui.theme.WordBookTheme

/** Several photo addresses in one navigation argument, one per line. */
private fun encodeUris(uris: List<Uri>) = Uri.encode(uris.joinToString("\n"))

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("home", "홈", Icons.Default.Home),
    Tab("review", "복습·통계", Icons.Default.Insights),
    Tab("settings", "설정", Icons.Default.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = StudySettings(this)
        lifecycleScope.launch(Dispatchers.IO) {
            DefaultDecks.seed(applicationContext)
            ParentAlerts.checkStreak(applicationContext)
        }
        Reminder.schedule(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33 && settings.reminderHour >= 0) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            WordBookTheme {
                var onboarding by remember { mutableStateOf(!settings.onboarded) }
                if (onboarding) {
                    OnboardingScreen(onDone = { settings.onboarded = true; onboarding = false })
                } else {
                    AppContent()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppContent() {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        // The tab bar only shows on the three top-level screens.
                        if (Tabs.any { it.route == route }) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                Tabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = route == tab.route,
                                        onClick = {
                                            nav.navigate(tab.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = { Text(tab.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                    ) {
                        composable("home") {
                            HomeScreen(
                                onPhoto = { uris -> nav.navigate("scan?uri=${encodeUris(uris)}&deckId=-1") },
                                onOpenDeck = { id -> nav.navigate("deck/$id") },
                                onToday = { nav.navigate("study/-1?today=true") },
                                onStudyDeck = { id -> nav.navigate("study/$id") },
                                onWrongNote = { nav.navigate("study/-1?wrong=true") },
                            )
                        }
                        composable("review") {
                            ReviewScreen(
                                onReview = { nav.navigate("study/-1?review=true") },
                                onStudyAll = { nav.navigate("study/-1?all=true") },
                            )
                        }
                        composable("settings") {
                            SettingsScreen()
                        }
                        composable(
                            "scan?uri={uri}&deckId={deckId}",
                            arguments = listOf(
                                navArgument("uri") { type = NavType.StringType },
                                navArgument("deckId") { type = NavType.LongType; defaultValue = -1L },
                            ),
                        ) {
                            ScanScreen(
                                onBack = { nav.popBackStack() },
                                onStudy = { deckId ->
                                    nav.navigate("study/$deckId") { popUpTo("home") }
                                },
                                onHome = { nav.popBackStack("home", inclusive = false) },
                            )
                        }
                        composable(
                            "deck/{deckId}",
                            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
                        ) {
                            DeckScreen(
                                onBack = { nav.popBackStack() },
                                onPhoto = { deckId, uris ->
                                    nav.navigate("scan?uri=${encodeUris(uris)}&deckId=$deckId")
                                },
                                onStudy = { deckId, onlyUnknown, modes, staged ->
                                    nav.navigate("study/$deckId?onlyUnknown=$onlyUnknown&mode=$modes&staged=$staged")
                                },
                            )
                        }
                        composable(
                            "study/{deckId}?onlyUnknown={onlyUnknown}&review={review}&mode={mode}&staged={staged}&all={all}&today={today}&wrong={wrong}",
                            arguments = listOf(
                                navArgument("deckId") { type = NavType.LongType },
                                navArgument("onlyUnknown") { type = NavType.BoolType; defaultValue = false },
                                navArgument("review") { type = NavType.BoolType; defaultValue = false },
                                navArgument("mode") { type = NavType.StringType; defaultValue = "" },
                                navArgument("staged") { type = NavType.StringType; defaultValue = "" },
                                navArgument("all") { type = NavType.BoolType; defaultValue = false },
                                navArgument("today") { type = NavType.BoolType; defaultValue = false },
                                navArgument("wrong") { type = NavType.BoolType; defaultValue = false },
                            ),
                        ) {
                            StudyScreen(onBack = { nav.popBackStack() })
                        }
                    }
                }
}
