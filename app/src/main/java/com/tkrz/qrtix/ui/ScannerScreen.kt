package com.tkrz.qrtix.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tkrz.qrtix.utils.QrCodeAnalyzer
import com.tkrz.qrtix.viewmodel.ScanStatus
import com.tkrz.qrtix.viewmodel.TicketViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: TicketViewModel,
    playSuccess: () -> Unit,
    playError: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
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

    DisposableEffect(Unit) {
        viewModel.startPeriodicSync()
        onDispose {
            viewModel.stopPeriodicSync()
        }
    }

    val scanResultStatus by viewModel.scanResultStatus.collectAsState()
    val activeEvent by viewModel.activeEvent.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val ticketCount by viewModel.ticketCount.collectAsState()
    val scannedCount by viewModel.scannedTicketCount.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isScanLoading by viewModel.isScanLoading.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    
    var isProcessing by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    
    var manualSequence by remember { mutableStateOf("") }
    var manualSecret by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && selectedCategory.isBlank()) {
            selectedCategory = categories[0]
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

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

    var lastScannedCode by remember { mutableStateOf("") }
    var lastScannedTime by remember { mutableStateOf(0L) }

    fun vibrate(type: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            when (type) {
                1 -> vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                2 -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 50, 150), -1))
            }
        } else {
            @Suppress("DEPRECATION")
            when (type) {
                1 -> vibrator.vibrate(50)
                2 -> vibrator.vibrate(longArrayOf(0, 150, 50, 150), -1)
            }
        }
    }

    fun processCode(qrContent: String) {
        val currentTime = System.currentTimeMillis()
        if (isProcessing) return
        if (qrContent == lastScannedCode && (currentTime - lastScannedTime) < 2000) return 

        lastScannedCode = qrContent
        lastScannedTime = currentTime
        isProcessing = true
        
        coroutineScope.launch {
            val status = viewModel.processQrCode(qrContent)
            when (status) {
                1 -> {
                    playSuccess()
                    vibrate(1)
                    delay(1500)
                    viewModel.clearScanResult()
                    isProcessing = false
                }
                5 -> {
                    // Network error — show dialog, allow retry
                    playError()
                    vibrate(2)
                }
                else -> {
                    playError()
                    vibrate(2)
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

        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor, QrCodeAnalyzer { qrContent ->
                                processCode(qrContent)
                            })
                        }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProviderFuture.get().unbindAll()
                        val camera = cameraProviderFuture.get().bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageAnalysis
                        )
                        cameraControl = camera.cameraControl
                        cameraControl?.enableTorch(isFlashlightOn)
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Membutuhkan izin kamera untuk melakukan scan.")
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Berikan Izin")
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val frameSize = canvasWidth * 0.7f
            val top = (canvasHeight - frameSize) / 2
            val left = (canvasWidth - frameSize) / 2
            val bottom = top + frameSize
            val right = left + frameSize
            
            val scrimColor = Color.Black.copy(alpha = 0.6f)
            drawRect(color = scrimColor, topLeft = Offset(0f, 0f), size = GeometrySize(canvasWidth, top))
            drawRect(color = scrimColor, topLeft = Offset(0f, bottom), size = GeometrySize(canvasWidth, canvasHeight - bottom))
            drawRect(color = scrimColor, topLeft = Offset(0f, top), size = GeometrySize(left, frameSize))
            drawRect(color = scrimColor, topLeft = Offset(right, top), size = GeometrySize(canvasWidth - right, frameSize))

            drawRect(color = Color.White.copy(alpha = 0.8f), topLeft = Offset(left, top), size = GeometrySize(frameSize, frameSize), style = Stroke(width = 2.dp.toPx()))
            
            if (!isProcessing) {
                val lineY = top + (frameSize * scanLinePosition)
                drawLine(color = Color.Green, start = Offset(left, lineY), end = Offset(right, lineY), strokeWidth = 3.dp.toPx())
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                var localLogoPath by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(activeEvent?.logoPath) {
                    if (activeEvent?.logoPath != null) {
                        localLogoPath = viewModel.resolveMedia(activeEvent!!.logoPath!!)
                    } else {
                        localLogoPath = null
                    }
                }

                if (localLogoPath != null) {
                    val bitmap = remember(localLogoPath) {
                        val f = java.io.File(localLogoPath!!)
                        if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                    }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Event Logo",
                            modifier = Modifier.height(30.dp)
                        )
                    } else {
                        Text(activeEvent?.name ?: "Loading...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                } else {
                    Text(activeEvent?.name ?: "Loading...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text("Scan: $scannedCount / $ticketCount", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                
                // Last Synced string calculation
                var syncText by remember { mutableStateOf("Sinkronisasi...") }
                LaunchedEffect(lastSyncTime, isOnline) {
                    while (true) {
                        if (!isOnline) {
                            syncText = "Offline"
                        } else {
                            val diff = (System.currentTimeMillis() - lastSyncTime) / 1000
                            syncText = if (diff < 10) "Baru saja" else "Sync ${diff}d lalu"
                        }
                        delay(1000L) // Update relative time every second
                    }
                }

                // Online/Offline indicator pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = if (isOnline) Color(0xFF2E7D32).copy(alpha = 0.8f) else Color(0xFFC62828).copy(alpha = 0.8f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isOnline) Color(0xFF76FF03) else Color(0xFFFF5252),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = syncText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Row {
                IconButton(onClick = {
                    viewModel.syncScannerData { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.Sync, contentDescription = "Sinkronisasi", tint = Color.White)
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
        }

        AnimatedVisibility(
            visible = scanResultStatus is ScanStatus.Success,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)
        ) {
            if (scanResultStatus is ScanStatus.Success) {
                val s = scanResultStatus as ScanStatus.Success
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BERHASIL SCANNED", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Text("${s.ticket.qrContent}", color = Color.White)
                        Text("Tipe: ${s.ticket.ticketType}", color = Color.White)
                    }
                }
            }
        }

        // Loading spinner overlay during cloud validation
        if (isScanLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Memvalidasi tiket...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        if (scanResultStatus is ScanStatus.AlreadyScanned || scanResultStatus is ScanStatus.Invalid || scanResultStatus is ScanStatus.CrossEventError) {
            val titleText = when (scanResultStatus) {
                is ScanStatus.AlreadyScanned -> "SUDAH DISCAN!"
                is ScanStatus.Invalid -> "TIDAK TERDAFTAR!"
                is ScanStatus.CrossEventError -> "EVENT BERBEDA!"
                else -> ""
            }
            
            val msgText = when (val s = scanResultStatus) {
                is ScanStatus.AlreadyScanned -> "${s.ticket.qrContent}\nTipe: ${s.ticket.ticketType}\nWaktu: ${s.scanTimeString}"
                is ScanStatus.Invalid -> "Kode tidak ditemukan di database."
                is ScanStatus.CrossEventError -> "Tiket ini (${s.code}) milik event lain!"
                else -> ""
            }
            
            AlertDialog(
                onDismissRequest = { },
                properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                containerColor = Color(0xFFF44336),
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFF44336))
                    ) {
                        Text("Tutup", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Network error dialog
        if (scanResultStatus is ScanStatus.NetworkError) {
            val networkMsg = (scanResultStatus as ScanStatus.NetworkError).message
            AlertDialog(
                onDismissRequest = { },
                properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                containerColor = Color(0xFFFF8F00),
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text("KONEKSI BERMASALAH", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                text = { Text(networkMsg, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearScanResult()
                            isProcessing = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFFF8F00))
                    ) {
                        Text("Tutup", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            viewModel.clearScanResult()
                            isProcessing = false
                            // Retry last scan
                            if (lastScannedCode.isNotBlank()) {
                                lastScannedTime = 0L // Reset debounce to allow immediate retry
                                processCode(lastScannedCode)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFFF8F00))
                    ) {
                        Text("Coba Lagi", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                .background(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Input Manual (Format: PREFIX-KATEGORI-0000-XXXX)", color = Color.White, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1.3f)) {
                        Text(activeEvent?.eventCode ?: "PREF", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        Box {
                            OutlinedButton(onClick = { dropdownExpanded = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                                Text(if (selectedCategory.isNotBlank()) selectedCategory else "Kategori")
                            }
                            DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                                if (categories.isEmpty()) {
                                    DropdownMenuItem(text = { Text("Belum ada kategori") }, onClick = { dropdownExpanded = false })
                                } else {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = { selectedCategory = cat; dropdownExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("-", color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    OutlinedTextField(
                        value = manualSequence,
                        onValueChange = { if (it.length <= 4) manualSequence = it.filter { char -> char.isDigit() } },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("0000", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("-", color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    OutlinedTextField(
                        value = manualSecret,
                        onValueChange = { if (it.length <= 4) manualSecret = it.uppercase() },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true,
                        placeholder = { Text("XXXX", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val prefix = activeEvent?.eventCode ?: "PREF"
                        if (manualSequence.length == 4 && manualSecret.length == 4 && selectedCategory.isNotBlank()) {
                            val code = "$prefix-$selectedCategory-$manualSequence-$manualSecret"
                            processCode(code)
                            manualSequence = ""
                            manualSecret = ""
                        } else {
                            Toast.makeText(context, "Lengkapi 4 digit sequence dan 4 char secret", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cek Manual")
                }
            }
        }
    }
}
