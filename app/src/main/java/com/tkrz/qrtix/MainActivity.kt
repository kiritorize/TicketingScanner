package com.tkrz.qrtix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tkrz.qrtix.ui.DatabaseScreen
import com.tkrz.qrtix.ui.ManagementScreen
import com.tkrz.qrtix.ui.ScannerScreen
import com.tkrz.qrtix.ui.SplashScreen
import com.tkrz.qrtix.utils.SoundManager
import com.tkrz.qrtix.viewmodel.TicketViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TicketViewModel by viewModels()
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        soundManager = SoundManager(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(
                                onFinished = {
                                    navController.navigate("mainmenu") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("mainmenu") {
                            com.tkrz.qrtix.ui.MainMenuScreen(
                                onNavigateToScanner = { navController.navigate("scanner") },
                                onNavigateToManagement = { navController.navigate("management") }
                            )
                        }
                        composable("scanner") {
                            ScannerScreen(
                                viewModel = viewModel,
                                playSuccess = { soundManager.playSuccess() },
                                playError = { soundManager.playError() },
                                onNavigateToManagement = { navController.navigate("mainmenu") { popUpTo(0) } } // Back to home
                            )
                        }
                        composable("management") {
                            ManagementScreen(
                                viewModel = viewModel,
                                onNavigateToScanner = { navController.navigate("scanner") },
                                onNavigateToDatabase = { navController.navigate("database") },
                                playSuccess = { soundManager.playSuccess() },
                                playError = { soundManager.playError() }
                            )
                        }
                        composable("database") {
                            DatabaseScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}
