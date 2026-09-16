package com.tkrz.qrtix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkrz.qrtix.viewmodel.TicketViewModel
import java.io.File
import android.graphics.BitmapFactory

private val PrimaryColor = Color(0xFFA4A2E4)
private val TextDark = Color(0xFF1A1A2E)
private val TextMuted = Color(0xFF6B7280)
private val CardBg = Color.White
private val BgColor = Color(0xFFF8F8FF)

@Composable
fun DashboardScreen(
    viewModel: TicketViewModel,
    onNavigateToScanner: () -> Unit,
    onNavigateToManagement: () -> Unit,
    onNavigateToDatabase: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    onNavigateToTicketEditor: () -> Unit,
    onNavigateToDistribution: () -> Unit
) {
    val activeEvent by viewModel.activeEvent.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    val ticketCount by viewModel.ticketCount.collectAsState()
    val scannedTicketCount by viewModel.scannedTicketCount.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var showEventDialog by remember { mutableStateOf(false) }

    var showScanConfirmation by remember { mutableStateOf(false) }
    var showEventSwitchWarning by remember { mutableStateOf(false) }

    if (showScanConfirmation) {
        AlertDialog(
            onDismissRequest = { showScanConfirmation = false },
            title = { Text("Konfirmasi Scan") },
            text = { Text("Anda akan memulai sesi scan untuk event **${activeEvent?.name}**. Lanjutkan?") },
            confirmButton = {
                Button(onClick = {
                    showScanConfirmation = false
                    onNavigateToScanner()
                }) {
                    Text("Mulai Scan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScanConfirmation = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showEventSwitchWarning) {
        AlertDialog(
            onDismissRequest = { showEventSwitchWarning = false },
            title = { Text("Sesi Scan Aktif") },
            text = { Text("Event **${activeEvent?.name}** sedang dalam proses scan. Apakah Anda ingin mengakhiri sesi scan di event ini untuk mengganti workspace?") },
            confirmButton = {
                Button(onClick = {
                    showEventSwitchWarning = false
                    showEventDialog = true
                }) {
                    Text("Ya, Ganti Event")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEventSwitchWarning = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showEventDialog) {
        EventSelectionDialog(
            events = allEvents,
            activeEventId = activeEvent?.id ?: 1L,
            onEventSelected = { id -> viewModel.switchEvent(id) },
            onCreateEvent = { name -> viewModel.createAndSwitchEvent(name) },
            onEditEvent = { id, newName, logoPath, bgPath ->
                viewModel.updateEventName(id, newName)
                viewModel.updateEventMedia(id, logoPath, bgPath)
            },
            onDeleteEvent = { id -> viewModel.deleteEvent(id) },
            onDismissRequest = { showEventDialog = false }
        )
    }

    val ticketCategories by viewModel.ticketCategories.collectAsState()
    var showCategoryDialog by remember { mutableStateOf(false) }

    if (showCategoryDialog) {
        CategoryManagementDialog(
            categories = ticketCategories,
            onDismissRequest = { showCategoryDialog = false },
            onAddCategory = { name, code -> viewModel.addCategory(name, code) },
            onUpdateCategory = { id, newName -> viewModel.updateCategory(id, newName) },
            onDeleteCategory = { id -> viewModel.deleteCategory(id) }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Spacer for status bar
            Spacer(modifier = Modifier.height(24.dp))

            val uploadState by viewModel.backgroundUploadManager.uploadState.collectAsState()
            com.tkrz.qrtix.ui.components.UploadProgressWidget(
                uploadState = uploadState,
                onDismiss = { viewModel.backgroundUploadManager.resetState() }
            )

            // 1. Active Event Card (Top Section)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        if (scannedTicketCount > 0 && scannedTicketCount < ticketCount) {
                            showEventSwitchWarning = true
                        } else {
                            showEventDialog = true 
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Logo
                        var localLogoPath by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(activeEvent?.logoPath) {
                            localLogoPath = activeEvent?.logoPath?.let { viewModel.resolveMedia(it) }
                        }
                        
                        val bitmap = remember(localLogoPath) {
                            localLogoPath?.let { path ->
                                val file = File(path)
                                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                            }
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Event Logo",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = PrimaryColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Event Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WORKSPACE AKTIF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Text(
                                text = activeEvent?.name ?: "Memuat...",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "Kode: ${activeEvent?.eventCode ?: "-"}",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Ganti Event",
                            tint = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFFEEEEEE))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "$ticketCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text(text = "Total Tiket", fontSize = 12.sp, color = TextMuted)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "$scannedTicketCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PrimaryColor)
                            Text(text = "Sudah Scan", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Primary Action Area
            Text(
                text = "Tindakan Utama",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (ticketCount == 0) {
                // Scenario 1: 0 tickets
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onNavigateToGenerator,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Generate Tiket", fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onNavigateToManagement,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Text("Import Tiket", fontSize = 12.sp)
                        }
                    }
                }
            } else if (scannedTicketCount == 0) {
                // Scenario 2: tickets exist but 0 scanned
                Button(
                    onClick = { showScanConfirmation = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Mulai Scan", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Scenario 3: mid-scan
                Button(
                    onClick = { showScanConfirmation = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Lanjutkan Scan", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                // Progress Bar
                val progress = if (ticketCount > 0) scannedTicketCount.toFloat() / ticketCount.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PrimaryColor,
                    trackColor = Color(0xFFE5E5F0)
                )
                Text(
                    text = "${(progress * 100).toInt()}% Selesai",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Secondary Actions
            Text(
                text = "Menu Lainnya",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            val secondaryButtons = mutableListOf(
                Pair("Lihat Database", onNavigateToDatabase to Icons.Default.Storage),
                Pair("Setup/Import", onNavigateToManagement to Icons.Default.Settings),
                Pair("Alat Generator", onNavigateToGenerator to Icons.Default.Build),
                Pair("Desain Tiket", onNavigateToTicketEditor to Icons.Default.Brush),
                Pair("Kelola Kategori", { showCategoryDialog = true } to Icons.Default.Category)
            )

            if (activeEvent?.distributionSheetId != null) {
                secondaryButtons.add(Pair("Status Distribusi", {
                    viewModel.isNavigatingToStatus = true
                    onNavigateToDistribution()
                } to Icons.Default.Assessment))
            } else if (ticketCount > 0) {
                secondaryButtons.add(Pair("Distribusi Tiket", {
                    viewModel.isNavigatingToStatus = false
                    onNavigateToDistribution()
                } to Icons.Default.Send))
            }
            
            secondaryButtons.add(Pair("Pengaturan Event", { showEventDialog = true } to Icons.Default.Edit))

            // Grid of secondary actions (2 columns)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in secondaryButtons.indices step 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val item1 = secondaryButtons[i]
                        SecondaryActionButton(
                            text = item1.first,
                            icon = item1.second.second,
                            onClick = item1.second.first,
                            modifier = Modifier.weight(1f)
                        )

                        if (i + 1 < secondaryButtons.size) {
                            val item2 = secondaryButtons[i + 1]
                            SecondaryActionButton(
                                text = item2.first,
                                icon = item2.second.second,
                                onClick = item2.second.first,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. Sync Status (Bottom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentTime = System.currentTimeMillis()
                val syncText = if (lastSyncTime > 0) {
                    val diffSec = (currentTime - lastSyncTime) / 1000
                    if (diffSec < 60) "Sinkronisasi: $diffSec dtk lalu"
                    else "Sinkronisasi: ${diffSec / 60} mnt lalu"
                } else {
                    "Belum pernah sinkronisasi"
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOnline) "Online • $syncText" else "Offline",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = PrimaryColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDark
            )
        }
    }
}
