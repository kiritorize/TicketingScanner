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
import com.tkrz.qrtix.ui.LoginScreen
import com.tkrz.qrtix.ui.ManagementScreen
import com.tkrz.qrtix.ui.ScannerScreen
import com.tkrz.qrtix.ui.SplashScreen
import com.tkrz.qrtix.ui.GeneratorScreen
import com.tkrz.qrtix.ui.OnboardingOverlay
import com.tkrz.qrtix.ui.TicketEditorScreen
import com.tkrz.qrtix.ui.DistributionScreen
import com.tkrz.qrtix.utils.SoundManager
import com.tkrz.qrtix.viewmodel.AuthViewModel
import com.tkrz.qrtix.viewmodel.DistributionViewModel
import com.tkrz.qrtix.viewmodel.TicketViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: TicketViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val distViewModel: DistributionViewModel by viewModels()
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        soundManager = SoundManager(this)
        
        // Initialize auth state check
        authViewModel.checkAuthStatus()

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
                                    if (authViewModel.isUserAuthenticated.value) {
                                        if (authViewModel.hasSeenOnboarding.value) {
                                            navController.navigate("mainmenu") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate("onboarding") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        }
                                    } else {
                                        navController.navigate("login") {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                viewModel = authViewModel,
                                onNavigateToMainMenu = {
                                    navController.navigate("mainmenu") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToOnboarding = {
                                    navController.navigate("onboarding") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("onboarding") {
                            OnboardingOverlay(
                                onComplete = {
                                    authViewModel.completeOnboarding()
                                    navController.navigate("mainmenu") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("mainmenu") {
                            com.tkrz.qrtix.ui.DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToScanner = { navController.navigate("scanner") },
                                onNavigateToManagement = { navController.navigate("management") },
                                onNavigateToDatabase = { navController.navigate("database") },
                                onNavigateToGenerator = { navController.navigate("generator") },
                                onNavigateToTicketEditor = { navController.navigate("ticket_editor") },
                                onNavigateToDistribution = { navController.navigate("distribution") }
                            )
                        }
                        composable("scanner") {
                            ScannerScreen(
                                viewModel = viewModel,
                                playSuccess = { soundManager.playSuccess() },
                                playError = { soundManager.playError() },
                                onNavigateBack = safeNavigateBack
                            )
                        }
                        composable("management") {
                            ManagementScreen(
                                viewModel = viewModel,
                                onNavigateToDatabase = { navController.navigate("database") },
                                onNavigateToGenerator = { navController.navigate("generator") },
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
                        composable("generator") {
                            GeneratorScreen(
                                viewModel = viewModel,
                                onNavigateBack = safeNavigateBack
                            )
                        }
                        composable("ticket_editor") {
                            TicketEditorScreen(
                                viewModel = viewModel,
                                onNavigateBack = safeNavigateBack
                            )
                        }
                        composable("distribution") {
                            DistributionScreen(
                                viewModel = viewModel,
                                distViewModel = distViewModel,
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
