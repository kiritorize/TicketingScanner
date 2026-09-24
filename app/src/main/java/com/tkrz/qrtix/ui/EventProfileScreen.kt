package com.tkrz.qrtix.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.tkrz.qrtix.data.Event
import com.tkrz.qrtix.viewmodel.TicketViewModel
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventProfileScreen(
    eventId: Long,
    viewModel: TicketViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTicketEditor: () -> Unit
) {
    val allEvents by viewModel.allEvents.collectAsState()
    val event = allEvents.find { it.id == eventId }

    if (event == null) {
        // Event might have been deleted, auto-back
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    val ticketList by viewModel.ticketList.collectAsState()
    val categories by viewModel.ticketCategories.collectAsState()
    val isMediaUploading by viewModel.isMediaUploading.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var editNameDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = java.io.File(context.filesDir, "logo_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(it)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.updateEventMedia(eventId, file.absolutePath, event.bgPath)
        }
    }

    val bgPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val file = java.io.File(context.filesDir, "bg_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(it)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.updateEventMedia(eventId, event.logoPath, file.absolutePath)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil Event") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            var localLogoPath by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(event.logoPath) {
                localLogoPath = event.logoPath?.let { viewModel.resolveMedia(it) }
            }

            val logoBitmap = remember(localLogoPath) {
                localLogoPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
            }

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .clickable(enabled = !isMediaUploading) { logoPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap.asImageBitmap(),
                        contentDescription = "Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("Pilih Logo")
                }

                if (isMediaUploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name and Code
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { editNameDialog = true }
                )
                IconButton(onClick = { editNameDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Nama", modifier = Modifier.size(20.dp))
                }
            }
            
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = event.eventCode,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Total Tiket", ticketList.size.toString())
                StatItem("Discan", ticketList.count { it.isScanned }.toString())
                StatItem("Kategori", categories.size.toString())
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Design Section
            Text("Desain Tiket", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(
                    onClick = { bgPickerLauncher.launch("image/*") }, 
                    modifier = Modifier.weight(1f),
                    enabled = !isMediaUploading
                ) {
                    if (isMediaUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (event.bgPath != null) "Ganti Background" else "Pilih Background")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.switchEvent(eventId) // Ensure it's active before editing
                        onNavigateToTicketEditor()
                    }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edit Posisi QR")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Danger Zone
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hapus Event")
            }
        }
    }

    if (editNameDialog) {
        var newName by remember { mutableStateOf(event.name) }
        AlertDialog(
            onDismissRequest = { editNameDialog = false },
            title = { Text("Edit Nama Event") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.updateEventName(eventId, newName.trim())
                        editNameDialog = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { editNameDialog = false }) { Text("Batal") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Event") },
            text = { Text("Apakah Anda yakin ingin menghapus '${event.name}'? Data tidak dapat dipulihkan.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEvent(eventId)
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}
