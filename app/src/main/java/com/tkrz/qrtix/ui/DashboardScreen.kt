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
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: TicketViewModel,
    authState: com.tkrz.qrtix.viewmodel.AuthState,
    onNavigateToScanner: () -> Unit,
    onNavigateToDatabase: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    onNavigateToTicketEditor: () -> Unit,
    onNavigateToDistribution: () -> Unit,
    onNavigateToEventProfile: (Long) -> Unit,
    onNavigateToGuide: () -> Unit,
    onLogout: () -> Unit
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

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (showEventDialog) {
        EventSelectionDialog(
            events = allEvents,
            activeEventId = activeEvent?.id ?: -1L,
            onEventSelected = { id -> viewModel.switchEvent(id) },
            onCreateEvent = { name, code -> viewModel.createAndSwitchEvent(name, code) },
            onEditEventClick = { id ->
                showEventDialog = false
                onNavigateToEventProfile(id)
            },
            onDeleteEvent = { id -> viewModel.deleteEvent(id) },
            onExportEventClick = { id ->
                coroutineScope.launch {
                    val intent = viewModel.exportEventToQrtix(id)
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        android.widget.Toast.makeText(context, "Gagal mengekspor data event", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onImportEventUri = { uri ->
                coroutineScope.launch {
                    val success = viewModel.importEventFromQrtix(uri)
                    if (success) {
                        android.widget.Toast.makeText(context, "Event berhasil diimpor", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Gagal mengimpor event dari file tersebut", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismissRequest = { showEventDialog = false }
        )
    }

    // Cloud initialization retry dialog
    val showRetryInitDialog by viewModel.showRetryInitDialog.collectAsState()
    val isInitializingCloud by viewModel.isInitializingCloud.collectAsState()

    if (showRetryInitDialog) {
        AlertDialog(
            onDismissRequest = { if (!isInitializingCloud) viewModel.dismissRetryDialog() },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Gagal Menyiapkan Database") },
            text = {
                Column {
                    Text("Koneksi ke cloud diperlukan untuk membuat atau mengubah workspace. Periksa koneksi internet Anda.")
                    if (isInitializingCloud) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Menyiapkan database cloud...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.retrySpreadsheetInit() },
                    enabled = !isInitializingCloud
                ) {
                    Text("Coba Lagi")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissRetryDialog() },
                    enabled = !isInitializingCloud
                ) {
                    Text("Batal")
                }
            }
        )
    }

    var showBackupDetailDialog by remember { mutableStateOf(false) }

    if (showBackupDetailDialog) {
        com.tkrz.qrtix.ui.BackupDetailDialog(
            uploadManager = viewModel.backgroundUploadManager,
            onDismissRequest = { showBackupDetailDialog = false }
        )
    }


    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Keluar dari Akun") },
            text = { Text("Data Anda akan tetap tersimpan di cloud dan di perangkat ini.\nSaat login kembali, data akan langsung tersedia.") },
            confirmButton = {
                Button(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarContent(
                authState = authState,
                onNavigateToDashboard = { coroutineScope.launch { drawerState.close() } },
                onNavigateToScanner = { coroutineScope.launch { drawerState.close() }; onNavigateToScanner() },
                onNavigateToGenerator = { coroutineScope.launch { drawerState.close() }; onNavigateToGenerator() },
                onNavigateToDatabase = { coroutineScope.launch { drawerState.close() }; onNavigateToDatabase() },
                onNavigateToTicketEditor = { coroutineScope.launch { drawerState.close() }; onNavigateToTicketEditor() },
                onNavigateToDistribution = { coroutineScope.launch { drawerState.close() }; onNavigateToDistribution() },
                onNavigateToBackup = { coroutineScope.launch { drawerState.close() }; showBackupDetailDialog = true },
                onNavigateToEventProfile = { 
                    coroutineScope.launch { drawerState.close() }
                    activeEvent?.id?.let { onNavigateToEventProfile(it) } ?: run { showEventDialog = true }
                },
                onNavigateToGuide = { coroutineScope.launch { drawerState.close() }; onNavigateToGuide() },
                onLogoutClick = { coroutineScope.launch { drawerState.close() }; showLogoutDialog = true }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dashboard", fontWeight = FontWeight.Bold, color = TextDark) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
                )
            },
            containerColor = BgColor
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

            val uploadState by viewModel.backgroundUploadManager.uploadState.collectAsState()
            com.tkrz.qrtix.ui.components.UploadProgressWidget(
                uploadState = uploadState,
                onDismiss = { viewModel.backgroundUploadManager.resetState() }
            )

            if (allEvents.isEmpty()) {
                // Task 11.5.4: Empty State
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.FolderOff,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = TextMuted.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Belum ada profil event.",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "Buat profil event pertama Anda untuk mulai menggunakan QRTix.",
                    fontSize = 14.sp,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showEventDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buat Event Baru")
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {

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
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Generate Tiket", fontSize = 12.sp)
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
                Pair("Alat Generator", onNavigateToGenerator to Icons.Default.Build),
                Pair("Desain Tiket", onNavigateToTicketEditor to Icons.Default.Brush),
                Pair("Detail Pencadangan", { showBackupDetailDialog = true } to Icons.Default.CloudSync)
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
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        viewModel.syncTicketsFromCloud()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Sekarang",
                        tint = PrimaryColor
                    )
                }
            }
        }
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

@Composable
fun SidebarContent(
    authState: com.tkrz.qrtix.viewmodel.AuthState,
    onNavigateToDashboard: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    onNavigateToDatabase: () -> Unit,
    onNavigateToTicketEditor: () -> Unit,
    onNavigateToDistribution: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToEventProfile: () -> Unit,
    onNavigateToGuide: () -> Unit,
    onLogoutClick: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryColor.copy(alpha = 0.1f))
                    .padding(24.dp)
            ) {
                Column {
                    val email = if (authState is com.tkrz.qrtix.viewmodel.AuthState.Authenticated) authState.email else "User"
                    val name = if (authState is com.tkrz.qrtix.viewmodel.AuthState.Authenticated) authState.displayName else null
                    val photoUrl = if (authState is com.tkrz.qrtix.viewmodel.AuthState.Authenticated) authState.photoUrl else null

                    var profileBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

                    LaunchedEffect(photoUrl) {
                        if (photoUrl != null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val url = java.net.URL(photoUrl)
                                    val bitmap = android.graphics.BitmapFactory.decodeStream(url.openStream())
                                    profileBitmap = bitmap
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileBitmap != null) {
                            Image(
                                bitmap = profileBitmap!!.asImageBitmap(),
                                contentDescription = "Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (name != null) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                    }
                    Text(email, fontSize = 14.sp, color = TextMuted)
                }
            }
            
            Divider()
            
            // Navigation Items
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Dashboard") },
                    selected = true,
                    onClick = onNavigateToDashboard
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    label = { Text("Scanner") },
                    selected = false,
                    onClick = onNavigateToScanner
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text("Generator Tiket") },
                    selected = false,
                    onClick = onNavigateToGenerator
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = { Text("Database Tiket") },
                    selected = false,
                    onClick = onNavigateToDatabase
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Brush, contentDescription = null) },
                    label = { Text("Desain Tiket") },
                    selected = false,
                    onClick = onNavigateToTicketEditor
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Send, contentDescription = null) },
                    label = { Text("Distribusi Tiket") },
                    selected = false,
                    onClick = onNavigateToDistribution
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    label = { Text("Pencadangan") },
                    selected = false,
                    onClick = onNavigateToBackup
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Profil Event") },
                    selected = false,
                    onClick = onNavigateToEventProfile
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Help, contentDescription = null) },
                    label = { Text("Panduan") },
                    selected = false,
                    onClick = onNavigateToGuide
                )
            }
            
            Divider()
            
            // Footer (Logout & Copyright)
            Column(modifier = Modifier.padding(12.dp)) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    label = { Text("Keluar Akun", color = MaterialTheme.colorScheme.error) },
                    selected = false,
                    onClick = onLogoutClick
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "© 2026 QRTix by Takarize",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
