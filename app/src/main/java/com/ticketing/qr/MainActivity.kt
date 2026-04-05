package com.ticketing.qr

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
import com.ticketing.qr.ui.DatabaseScreen
import com.ticketing.qr.ui.ManagementScreen
import com.ticketing.qr.ui.ScannerScreen
import com.ticketing.qr.utils.SoundManager
import com.ticketing.qr.viewmodel.TicketViewModel

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

                    NavHost(navController = navController, startDestination = "mainmenu") {
                        composable("mainmenu") {
                            com.ticketing.qr.ui.MainMenuScreen(
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
