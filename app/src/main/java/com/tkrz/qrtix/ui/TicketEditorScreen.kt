package com.tkrz.qrtix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.unit.dp
import com.tkrz.qrtix.viewmodel.TicketViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketEditorScreen(
    viewModel: TicketViewModel,
    onNavigateBack: () -> Unit
) {
    val activeEvent by viewModel.activeEvent.collectAsState()
    
    // Initial states based on event
    var scale by remember { mutableFloatStateOf(activeEvent?.qrScale ?: 1f) }
    var rotation by remember { mutableFloatStateOf(activeEvent?.qrRotation ?: 0f) }
    var offset by remember { mutableStateOf(Offset(activeEvent?.qrX ?: 0f, activeEvent?.qrY ?: 0f)) }

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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.DarkGray)
        ) {
            // Background Image
            if (activeEvent?.bgPath != null) {
                val bitmap = remember(activeEvent?.bgPath) {
                    val file = File(activeEvent!!.bgPath!!)
                    if (file.exists()) {
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    } else null
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Background",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = "Gagal memuat background.",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                Text(
                    text = "Belum ada background terpilih.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Dummy QR Code Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, rot ->
                            scale *= zoom
                            rotation += rot
                            offset += pan
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer(
                            translationX = offset.x,
                            translationY = offset.y,
                            scaleX = scale,
                            scaleY = scale,
                            rotationZ = rotation
                        )
                        .background(Color.White)
                        .align(Alignment.Center)
                ) {
                    Text("QR Code\nDi Sini", modifier = Modifier.align(Alignment.Center), color = Color.Black)
                }
            }
            
            // Instruction
            Text(
                text = "Geser, putar, atau cubit kotak putih di atas\nuntuk mengatur posisi QR",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}
