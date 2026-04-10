package com.tkrz.qrtix.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tkrz.qrtix.utils.QrCodeAnalyzer
import com.tkrz.qrtix.viewmodel.ScanStatus
import com.tkrz.qrtix.viewmodel.TicketViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import androidx.compose.ui.geometry.Size as GeometrySize

@Composable
fun ScannerScreen(
    viewModel: TicketViewModel,
    playSuccess: () -> Unit,
    playError: () -> Unit,
    onNavigateToManagement: () -> Unit,
    onNavigateBack: () -> Unit
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

    val scanResultStatus by viewModel.scanResultStatus.collectAsState()
    var isProcessing by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFlashlightOn by remember { mutableStateOf(false) }
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
        val executor = remember { Executors.newSingleThreadExecutor() }
        DisposableEffect(Unit) {
            onDispose {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                executor.shutdown()
            }
        }

        // Lapisan Bawah: Kamera
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
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
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            cameraControl = camera.cameraControl
                            cameraControl?.enableTorch(isFlashlightOn)
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Kembali",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            val ticketCount by viewModel.ticketCount.collectAsState()
            val scannedCount by viewModel.scannedTicketCount.collectAsState()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scan: $scannedCount / $ticketCount",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = {
                isFlashlightOn = !isFlashlightOn
                cameraControl?.enableTorch(isFlashlightOn)
            }) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = "Senter",
                    tint = if (isFlashlightOn) Color.Yellow else Color.White
                )
            }
        }

        // Lapisan Atas: Notifikasi Floating Modern
        if (scanResultStatus !is ScanStatus.Idle) {
            val titleText = when (scanResultStatus) {
                is ScanStatus.Success -> "BERHASIL SCANNED"
                is ScanStatus.AlreadyScanned -> "PERINGATAN!"
                is ScanStatus.Invalid -> "TIDAK TERDAFTAR!"
                else -> ""
            }
            
            val msgText = when (val s = scanResultStatus) {
                is ScanStatus.Success -> "${s.ticket.qrContent}\nTipe: ${s.ticket.ticketType}\nWaktu: ${s.scanTimeString}"
                is ScanStatus.AlreadyScanned -> "SUDAH DISCAN:\n${s.ticket.qrContent}\nTipe: ${s.ticket.ticketType}\nWaktu: ${s.scanTimeString}"
                is ScanStatus.Invalid -> "Kode: ${s.code}"
                else -> ""
            }
            
            val bgColor = if (scanResultStatus is ScanStatus.Success) Color(0xFF4CAF50) else Color(0xFFF44336)
            
            AlertDialog(
                onDismissRequest = { /* Must click button */ },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                containerColor = bgColor,
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text(titleText, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                text = { Text(msgText, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
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
                .imePadding()
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
                            } else {
                                Toast.makeText(context, "Input karakter kode terlebih dahulu!", Toast.LENGTH_SHORT).show()
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
