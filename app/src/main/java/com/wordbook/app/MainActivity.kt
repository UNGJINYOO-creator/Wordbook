package com.wordbook.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wordbook.app.ui.DeckScreen
import com.wordbook.app.ui.HomeScreen
import com.wordbook.app.ui.ScanScreen
import com.wordbook.app.ui.SettingsScreen
import com.wordbook.app.ui.StudyScreen
import com.wordbook.app.ui.theme.WordBookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WordBookTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onPhoto = { uri -> nav.navigate("scan?uri=${Uri.encode(uri.toString())}&deckId=-1") },
                            onOpenDeck = { id -> nav.navigate("deck/$id") },
                            onReview = { nav.navigate("study/-1?onlyUnknown=false&review=true") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
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
                            onPhoto = { deckId, uri ->
                                nav.navigate("scan?uri=${Uri.encode(uri.toString())}&deckId=$deckId")
                            },
                            onStudy = { deckId, onlyUnknown ->
                                nav.navigate("study/$deckId?onlyUnknown=$onlyUnknown")
                            },
                        )
                    }
                    composable(
                        "study/{deckId}?onlyUnknown={onlyUnknown}&review={review}",
                        arguments = listOf(
                            navArgument("deckId") { type = NavType.LongType },
                            navArgument("onlyUnknown") { type = NavType.BoolType; defaultValue = false },
                            navArgument("review") { type = NavType.BoolType; defaultValue = false },
                        ),
                    ) {
                        StudyScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
