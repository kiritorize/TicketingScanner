package com.tkrz.qrtix.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.tkrz.qrtix.R
import com.tkrz.qrtix.viewmodel.SplashViewModel
import kotlinx.coroutines.delay
import kotlin.math.min

private val PrimaryColor   = Color(0xFFA4A2E4)
private val BgColor        = Color(0xFFF8F8FF)  // Very light lavender-white
private val TextDark       = Color(0xFF1A1A2E)
private val TextMuted      = Color(0xFF6B7280)
private val TextLight      = Color(0xFFB0B0C0)

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel(),
    onFinished: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val currentStage by viewModel.currentStage.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val isOfflineBlocking by viewModel.isOfflineBlocking.collectAsState()
    val isAuthMissing by viewModel.isAuthMissing.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startSync()
    }

    LaunchedEffect(isFinished) {
        if (isFinished) {
            onFinished()
        }
    }

    LaunchedEffect(isAuthMissing) {
        if (isAuthMissing) {
            onRequireLogin()
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = currentStage.progress,
        animationSpec = tween(500, easing = FastOutLinearInEasing),
        label = "progress"
    )

    // ── Logo entrance (spring bounce) ─────────────────────────────────────────
    var logoReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(50L); logoReady = true }

    val logoScale by animateFloatAsState(
        targetValue = if (logoReady) 1f else 0.45f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoReady) 1f else 0f,
        animationSpec = tween(800),
        label = "logoAlpha"
    )

    // ── Pulsing ring ─────────────────────────────────────────────────────────
    val pulseInf = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseInf.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val pulseAlpha by pulseInf.animateFloat(
        initialValue = 0.20f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    // ── Ambient glows ─────────────────────────────────────────────────────────
    val glowInf = rememberInfiniteTransition(label = "glow")
    val glow1 by glowInf.animateFloat(
        initialValue = 0.10f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(3000, easing = EaseInOut), RepeatMode.Reverse),
        label = "glow1"
    )
    val glow2 by glowInf.animateFloat(
        initialValue = 0.08f, targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(4000, delayMillis = 500, easing = EaseInOut), RepeatMode.Reverse),
        label = "glow2"
    )

    // ── Delayed text + bottom fade-in ─────────────────────────────────────────
    var textReady   by remember { mutableStateOf(false) }
    var bottomReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(600L);  textReady   = true }
    LaunchedEffect(Unit) { delay(1000L); bottomReady = true }

    val textAlpha by animateFloatAsState(
        targetValue = if (textReady) 1f else 0f,
        animationSpec = tween(800), label = "textAlpha"
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (bottomReady) 1f else 0f,
        animationSpec = tween(1000), label = "bottomAlpha"
    )

    // ═════════════════════════════════════════════════════════════════════════
    // UI
    // ═════════════════════════════════════════════════════════════════════════
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
        contentAlignment = Alignment.Center
    ) {

        // Ambient glow — top-left
        Box(
            modifier = Modifier
                .size(380.dp)
                .offset(x = (-80).dp, y = (-120).dp)
                .align(Alignment.TopStart)
                .blur(90.dp)
                .background(PrimaryColor.copy(alpha = glow1), CircleShape)
        )

        // Ambient glow — bottom-right
        Box(
            modifier = Modifier
                .size(420.dp)
                .offset(x = 80.dp, y = 120.dp)
                .align(Alignment.BottomEnd)
                .blur(100.dp)
                .background(PrimaryColor.copy(alpha = glow2), CircleShape)
        )

        // ── Content column ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // ── Logo + pulse ring ─────────────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha  = logoAlpha
                    }
                    .padding(bottom = 16.dp)
            ) {
                // Pulse ring
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .background(PrimaryColor.copy(alpha = pulseAlpha), CircleShape)
                )

                // Logo circle
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.95f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "QRTix Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Title + subtitle ──────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .graphicsLayer { alpha = textAlpha }
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "QRTix",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDark,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = ".",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Smart Ticketing Solution",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Progress bar + labels ─────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(bottom = 56.dp)
                    .graphicsLayer { alpha = bottomAlpha }
            ) {
                // Progress tracking
                if (!isOfflineBlocking && syncError == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFE5E5F0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(PrimaryColor.copy(alpha = 0.65f), PrimaryColor)))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentStage == com.tkrz.qrtix.viewmodel.SyncStage.COMPLETE) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = currentStage.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                if (isOfflineBlocking) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Koneksi internet diperlukan untuk masuk ke QRTix", fontSize = 12.sp, color = TextDark, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.startSync() }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) {
                            Text("Coba Lagi", fontSize = 14.sp)
                        }
                    }
                } else if (syncError != null) {
                    val errorMsg = syncError
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMsg ?: "", fontSize = 12.sp, color = TextDark, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.startSync() }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) {
                            Text("Coba Lagi", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "v1.0.0",
                    fontSize = 10.sp,
                    color = TextLight,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
