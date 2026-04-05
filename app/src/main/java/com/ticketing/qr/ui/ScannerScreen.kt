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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var manualInput by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Animasi Garis Scanner Naik Turun
    val infiniteTransition = rememberInfiniteTransition(label = "scan_transition")
    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line_anim"
    )

    fun processCode(qrContent: String) {
        if (!isProcessing) {
            isProcessing = true
            coroutineScope.launch {
                val status = viewModel.processQrCode(qrContent)
                when (status) {
                    1 -> playSuccess() // Success
                    else -> playError() // Already Scanned or Invalid
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Lapisan Bawah: Kamera
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
                                        processCode(qrContent)
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

        // Lapisan Tengah: UI Scanner Frame + Area Gelap
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val frameSize = canvasWidth * 0.7f
            val top = (canvasHeight - frameSize) / 2
            val left = (canvasWidth - frameSize) / 2
            val bottom = top + frameSize
            val right = left + frameSize
            
            // Background redup (scrim) menggunakan 4 kotak
            val scrimColor = Color.Black.copy(alpha = 0.6f)
            drawRect(color = scrimColor, topLeft = Offset(0f, 0f), size = GeometrySize(canvasWidth, top)) // Atas
            drawRect(color = scrimColor, topLeft = Offset(0f, bottom), size = GeometrySize(canvasWidth, canvasHeight - bottom)) // Bawah
            drawRect(color = scrimColor, topLeft = Offset(0f, top), size = GeometrySize(left, frameSize)) // Kiri
            drawRect(color = scrimColor, topLeft = Offset(right, top), size = GeometrySize(canvasWidth - right, frameSize)) // Kanan

            // Bingkai Scanner (Border Kotak Putih)
            drawRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(left, top),
                size = GeometrySize(frameSize, frameSize),
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Garis Scanner bergerak (merah/hijau)
            if (!isProcessing) {
                val lineY = top + (frameSize * scanLinePosition)
                drawLine(
                    color = Color.Green,
                    start = Offset(left, lineY),
                    end = Offset(right, lineY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Lapisan Atas: Top Bar Menu
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
                text = "Data Terdaftar: $ticketCount",
                color = Color.White,
                fontSize = 16.sp
            )
            Button(onClick = onNavigateToManagement) {
                Text("Setup Database")
            }
        }

        // Lapisan Atas: Notifikasi Floating Modern
        if (scanResultMessage != null) {
            val msg = scanResultMessage ?: ""
            val isSuccess = msg.startsWith("BERHASIL")
            val isAlreadyScanned = msg.startsWith("SUDAH DISCAN")
            
            val title = when {
                isSuccess -> "BERHASIL SCANNED"
                isAlreadyScanned -> "PERINGATAN!"
                else -> "TIDAK TERDAFTAR!"
            }
            val bgColor = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336)
            
            AlertDialog(
                onDismissRequest = { /* Must click button */ },
                containerColor = bgColor,
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                text = { Text(msg, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearScanResult()
                            isProcessing = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = bgColor)
                    ) {
                        Text("Lanjut Scan", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        
        // Lapisan Atas: Input Manual Form di Bawah Layar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Jika kamera bermasalah, ketikkan manual:", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Ketik kode tiket....") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualInput.isNotBlank()) {
                                processCode(manualInput)
                                manualInput = ""
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text("Kirim")
                    }
                }
            }
        }
    }
}
