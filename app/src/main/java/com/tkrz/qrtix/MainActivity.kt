package com.tkrz.qrtix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: TicketViewModel by viewModels()
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        soundManager = SoundManager(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    val safeNavigateBack: () -> Unit = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        }
                    }

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
                                onNavigateToManagement = { navController.navigate("management") },
                                onNavigateToDatabase = { navController.navigate("database") }
                            )
                        }
                        composable("scanner") {
                            ScannerScreen(
                                viewModel = viewModel,
                                playSuccess = { soundManager.playSuccess() },
                                playError = { soundManager.playError() },
                                onNavigateToManagement = { navController.navigate("management") },
                                onNavigateBack = safeNavigateBack
                            )
                        }
                        composable("management") {
                            ManagementScreen(
                                viewModel = viewModel,
                                onNavigateToDatabase = { navController.navigate("database") },
                                playSuccess = { soundManager.playSuccess() },
                                playError = { soundManager.playError() },
                                onNavigateBack = safeNavigateBack
                            )
                        }
                        composable("database") {
                            DatabaseScreen(
                                viewModel = viewModel,
                                onNavigateBack = safeNavigateBack
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
