package com.ticketing.qr.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ticketing.qr.utils.QrCodeAnalyzer
import com.ticketing.qr.viewmodel.TicketViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    viewModel: TicketViewModel,
    playSuccess: () -> Unit,
    playError: () -> Unit,
    onNavigateToManagement: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val scanResultMessage by viewModel.scanResultMsg.collectAsState()
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val executor = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(
                                    executor,
                                    QrCodeAnalyzer { qrContent ->
                                        if (!isProcessing) {
                                            isProcessing = true
                                            coroutineScope.launch {
                                                val status = viewModel.processQrCode(qrContent)
                                                when (status) {
                                                    1 -> playSuccess() // Success
                                                    else -> playError() // Already Scanned or Invalid
                                                }
                                                // Delay 2 seconds before allowing next scan
                                                delay(2000)
                                                viewModel.clearScanResult()
                                                isProcessing = false
                                            }
                                        }
                                    }
                                )
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            exc.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Membutuhkan izin kamera untuk melakukan scan.")
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Berikan Izin")
                }
            }
        }

        // Overlay status message
        scanResultMessage?.let { msg ->
            val bgColor = if (msg.startsWith("BERHASIL")) Color(0xFF4CAF50) else Color(0xFFF44336)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor.copy(alpha = 0.9f))
                    .padding(32.dp)
                    .align(Alignment.Center)
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ticketCount by viewModel.ticketCount.collectAsState()
            Text(
                text = "Data Terdaftar: \$ticketCount",
                color = Color.White,
                fontSize = 16.sp
            )
            Button(onClick = onNavigateToManagement) {
                Text("Database")
            }
        }
        
        // Scan Target Box Indicator
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.Center)
        ) {
            // A simple visual indicator for where to scan (can be improved with canvas lines)
        }
    }
}
