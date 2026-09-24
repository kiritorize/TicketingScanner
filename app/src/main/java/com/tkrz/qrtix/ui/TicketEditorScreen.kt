package com.tkrz.qrtix.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tkrz.qrtix.utils.DefaultTemplateRenderer
import com.tkrz.qrtix.utils.TicketExporter
import com.tkrz.qrtix.viewmodel.TicketViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketEditorScreen(
    viewModel: TicketViewModel,
    onNavigateBack: () -> Unit
) {
    val activeEvent by viewModel.activeEvent.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    var scale by remember { mutableFloatStateOf(activeEvent?.qrScale ?: 1f) }
    var rotation by remember { mutableFloatStateOf(activeEvent?.qrRotation ?: 0f) }
    var offset by remember { mutableStateOf(Offset(activeEvent?.qrX ?: 0f, activeEvent?.qrY ?: 0f)) }

    var displayWidthPx by remember { mutableFloatStateOf(1f) }
    var showPreview by remember { mutableStateOf(false) }
    var showGuides by remember { mutableStateOf(true) }
    var showNumericPanel by remember { mutableStateOf(false) }

    // Resolve bgPath from Drive File ID to local cached path
    var localBgPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeEvent?.bgPath) {
        localBgPath = activeEvent?.bgPath?.let { viewModel.resolveMedia(it) }
    }

    val baseBitmap = remember(localBgPath, activeEvent) {
        if (localBgPath != null) {
            val file = File(localBgPath!!)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                DefaultTemplateRenderer.renderDefaultTemplate(activeEvent)
            }
        } else {
            DefaultTemplateRenderer.renderDefaultTemplate(activeEvent)
        }
    }

    val outWidth = baseBitmap?.width ?: 1000
    val outHeight = baseBitmap?.height ?: 1200
    val aspectRatio = outWidth.toFloat() / outHeight.toFloat()
    
    val displayRatio = if (outWidth > 0) displayWidthPx / outWidth else 1f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Desain Tiket") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(onClick = { showPreview = true }) {
                        Text("Preview Hasil", color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        activeEvent?.let {
                            viewModel.updateEventQrTransform(it.id, offset.x, offset.y, scale, rotation)
                        }
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Simpan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Column {
                if (showNumericPanel) {
                    NumericInputPanel(
                        offset = offset,
                        scale = scale,
                        rotation = rotation,
                        onOffsetChange = { offset = it },
                        onScaleChange = { scale = it },
                        onRotationChange = { rotation = it }
                    )
                }
                BottomAppBar(
                    actions = {
                        IconButton(onClick = { offset = Offset(0f, 0f) }) {
                            Icon(Icons.Default.FilterCenterFocus, contentDescription = "Center QR")
                        }
                        IconButton(onClick = {
                            offset = Offset(0f, 0f)
                            scale = 1f
                            rotation = 0f
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset All")
                        }
                        IconButton(onClick = { scale = (scale + 0.1f).coerceAtMost(3.0f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                        }
                        IconButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(0.3f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                        }
                        IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90")
                        }
                        IconButton(onClick = { showGuides = !showGuides }) {
                            Icon(if (showGuides) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle Guides")
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showNumericPanel = !showNumericPanel }) {
                            Icon(if (showNumericPanel) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = "Toggle Numeric Panel")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.DarkGray)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rot ->
                        scale = (scale * zoom).coerceIn(0.3f, 3.0f)
                        rotation = (rotation + rot) % 360f
                        val ratioMultiplier = outWidth / 1080f
                        offset = Offset(
                            x = offset.x + (pan.x / (displayRatio * ratioMultiplier)),
                            y = offset.y + (pan.y / (displayRatio * ratioMultiplier))
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(aspectRatio)
                    .onGloballyPositioned { layoutCoordinates ->
                        displayWidthPx = layoutCoordinates.size.width.toFloat()
                    }
                    .background(Color.Black)
            ) {
                // Base Image
                if (baseBitmap != null) {
                    Image(
                        bitmap = baseBitmap.asImageBitmap(),
                        contentDescription = "Background",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }

                // Guides
                if (showGuides) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).align(Alignment.Center).background(Color.Green.copy(alpha = 0.5f)))
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).align(Alignment.Center).background(Color.Green.copy(alpha = 0.5f)))
                }

                // QR Container
                val qrSizePx = outWidth * 0.4f * scale * displayRatio
                val qrSizeDp = with(density) { qrSizePx.toDp() }
                
                val textPaddingPx = qrSizePx * 0.25f
                val textPaddingDp = with(density) { textPaddingPx.toDp() }
                val fontSizeSp = with(density) { (qrSizePx * 0.1f).toSp() }

                val ratioMultiplier = outWidth / 1080f
                val screenTransX = offset.x * ratioMultiplier * displayRatio
                val screenTransY = offset.y * ratioMultiplier * displayRatio

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer(
                            translationX = screenTransX,
                            translationY = screenTransY,
                            rotationZ = rotation
                        )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(qrSizeDp)
                                .background(Color.White)
                                .border(1.dp, Color.Black)
                        ) {
                            Text(
                                "QR Code",
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.Black,
                                fontSize = fontSizeSp
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(qrSizeDp)
                                .height(textPaddingDp)
                                .background(Color.White)
                        ) {
                            Text(
                                text = "DCD-VIP-0001-A2B4",
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.Black,
                                fontSize = fontSizeSp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (showGuides) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color.Red, shape = RoundedCornerShape(3.dp))
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }

    if (showPreview) {
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Generate exact preview using TicketExporter
                var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
                
                LaunchedEffect(Unit) {
                    val exporter = TicketExporter(context)
                    // Temporarily update event with current slider values for preview
                    val tempEvent = activeEvent?.copy(
                        qrX = offset.x,
                        qrY = offset.y,
                        qrScale = scale,
                        qrRotation = rotation
                    )
                    previewBitmap = exporter.generateSingleTicketBitmap(
                        qrContent = "DCD-VIP-0001-A2B4",
                        event = tempEvent,
                        baseBgBitmap = baseBitmap,
                        outWidth = outWidth,
                        outHeight = outHeight
                    )
                }

                if (previewBitmap != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showPreview = false }) {
                            Text("Tutup Preview")
                        }
                    }
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun NumericInputPanel(
    offset: Offset,
    scale: Float,
    rotation: Float,
    onOffsetChange: (Offset) -> Unit,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NumericField("X", offset.x) { onOffsetChange(Offset(it, offset.y)) }
            NumericField("Y", offset.y) { onOffsetChange(Offset(offset.x, it)) }
            NumericField("Scale", scale) { onScaleChange(it.coerceIn(0.1f, 10f)) }
            NumericField("Rot", rotation) { onRotationChange(it % 360f) }
        }
    }
}

@Composable
private fun NumericField(label: String, value: Float, onValueChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(String.format("%.1f", value)) }
    
    OutlinedTextField(
        value = text,
        onValueChange = { 
            text = it
            it.toFloatOrNull()?.let { num -> onValueChange(num) }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.width(70.dp).padding(4.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodySmall
    )
}
