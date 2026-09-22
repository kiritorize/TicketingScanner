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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.tkrz.qrtix.ui.DatabaseScreen
import com.tkrz.qrtix.ui.LoginScreen
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

    @Inject
    lateinit var networkMonitor: com.tkrz.qrtix.utils.NetworkMonitor

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

                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route
                    val isConnected by networkMonitor.isConnected.collectAsState()
                    val coroutineScope = rememberCoroutineScope()

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController, 
                            startDestination = "splash",
                            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) },
                            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) },
                            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) },
                            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) }
                        ) {
                            composable(
                                "splash",
                                exitTransition = { fadeOut(tween(300)) }
                            ) {
                            SplashScreen(
                                onFinished = {
                                    if (authViewModel.hasSeenOnboarding.value) {
                                        navController.navigate("mainmenu") {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("onboarding") {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    }
                                },
                                onRequireLogin = {
                                    navController.navigate("login") {
                                        popUpTo("splash") { inclusive = true }
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
                                isGuideMode = false,
                                onComplete = {
                                    authViewModel.completeOnboarding()
                                    navController.navigate("mainmenu") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("onboarding_guide") {
                            OnboardingOverlay(
                                isGuideMode = true,
                                onComplete = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("mainmenu") {
                            val authState by authViewModel.authState.collectAsState()
                            
                            com.tkrz.qrtix.ui.DashboardScreen(
                                viewModel = viewModel,
                                authState = authState,
                                onNavigateToScanner = { navController.navigate("scanner") },
                                onNavigateToDatabase = { navController.navigate("database") },
                                onNavigateToGenerator = { navController.navigate("generator") },
                                onNavigateToTicketEditor = { navController.navigate("ticket_editor") },
                                onNavigateToDistribution = { navController.navigate("distribution") },
                                onNavigateToEventProfile = { eventId -> navController.navigate("event_profile/$eventId") },
                                onNavigateToGuide = {
                                    navController.navigate("onboarding_guide")
                                },
                                onLogout = {
                                    authViewModel.signOut()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
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
                        composable(
                            "event_profile/{eventId}",
                            arguments = listOf(androidx.navigation.navArgument("eventId") { type = androidx.navigation.NavType.LongType })
                        ) { backStackEntry ->
                            val eventId = backStackEntry.arguments?.getLong("eventId") ?: return@composable
                            com.tkrz.qrtix.ui.EventProfileScreen(
                                eventId = eventId,
                                viewModel = viewModel,
                                onNavigateBack = safeNavigateBack,
                                onNavigateToTicketEditor = { navController.navigate("ticket_editor") }
                            )
                        }
                    }
                    
                    if (!isConnected && currentRoute != "splash") {
                        com.tkrz.qrtix.ui.OfflineOverlay(
                            onRetry = {
                                coroutineScope.launch {
                                    networkMonitor.checkConnectivity()
                                }
                            }
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
