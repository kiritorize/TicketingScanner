package com.tkrz.qrtix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkrz.qrtix.R
import com.tkrz.qrtix.viewmodel.AuthState
import com.tkrz.qrtix.viewmodel.AuthViewModel

private val PrimaryColor = Color(0xFFA4A2E4)
private val TextDark = Color(0xFF1A1A2E)
private val TextMuted = Color(0xFF6B7280)

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToMainMenu: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsState()
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.retryInitialization()
        } else {
            viewModel.setAuthError("Izin akses ditolak. Aplikasi membutuhkan akses Google Drive & Sheets.")
        }
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Authenticated -> {
                if (hasSeenOnboarding) {
                    onNavigateToMainMenu()
                } else {
                    onNavigateToOnboarding()
                }
            }
            is AuthState.NeedsConsent -> {
                consentLauncher.launch(state.intent)
            }
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "QRTix Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "QRTix",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = ".",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryColor
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Smart Ticketing Solution",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            when (authState) {
                is AuthState.Loading -> {
                    CircularProgressIndicator(color = PrimaryColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sedang memproses...", color = TextMuted)
                }
                is AuthState.Error -> {
                    val error = (authState as AuthState.Error).message
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.signIn(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                    ) {
                        Text("Coba Lagi", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Button(
                        onClick = { viewModel.signIn(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Google Icon",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Masuk dengan Google", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
