package com.tkrz.qrtix.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkrz.qrtix.viewmodel.TicketViewModel
import kotlinx.coroutines.launch

/**
 * Reusable input box with row numbers, scrollable content, scrollbar, and clear button.
 * Uses onTextLayout to get exact line positions from the rendering engine.
 */
// NumberedInputBox has been moved to a shared component in ui package

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(
    viewModel: TicketViewModel,
    onNavigateToDatabase: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    playSuccess: () -> Unit,
    playError: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var catInput by remember { mutableStateOf("") }
    var importResultMessage by remember { mutableStateOf<String?>(null) }
    val ticketCount by viewModel.ticketCount.collectAsState()
    val ticketList by viewModel.ticketList.collectAsState()

    // For duplicate error popup
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var duplicateErrorMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importCsvFromUri(it) { resultMsg ->
                if (resultMsg.startsWith("Gagal", ignoreCase = true) || resultMsg.startsWith("File CSV", ignoreCase = true) || resultMsg.startsWith("Tidak ada", ignoreCase = true)) {
                    playError()
                } else {
                    playSuccess()
                }
                importResultMessage = resultMsg
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val activeEvent by viewModel.activeEvent.collectAsState()
                    Column {
                        Text("Setup Database Tiket")
                        Text(activeEvent?.name ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Tiket Terdaftar",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "$ticketCount Tiket",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tombol Bantuan ke Generator
                OutlinedButton(
                    onClick = onNavigateToGenerator,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Belum punya kode QR? Buat di sini", 
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Impor dari File",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        csvLauncher.launch("*/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pilih File CSV / Excel")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Input / Paste Manual tiap baris:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                // Box 1: Kode Unik
                NumberedInputBox(
                    label = "Kode Unik",
                    placeholder = "Ketik / paste kode unik tiap baris...",
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    onClear = { codeInput = "" }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Box 2: Kategori
                NumberedInputBox(
                    label = "Kategori",
                    placeholder = "Ketik / paste kategori tiap baris...",
                    value = catInput,
                    onValueChange = { catInput = it },
                    onClear = { catInput = "" }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            if (codeInput.isNotBlank() || catInput.isNotBlank()) {
                                val result = viewModel.addTicketsFromTwoBoxes(codeInput, catInput)
                                if (result.first) {
                                    playSuccess()
                                    codeInput = ""
                                    catInput = ""
                                    importResultMessage = "Berhasil ditambahkan ke Database!"
                                } else {
                                    playError()
                                    duplicateErrorMessage = result.second
                                    showDuplicateDialog = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = codeInput.isNotBlank() || catInput.isNotBlank()
                ) {
                    Text("Tambahkan dari Input")
                }

                if (importResultMessage != null) {
                    Text(
                        text = importResultMessage ?: "",
                        color = if ((importResultMessage ?: "").startsWith("Gagal") || (importResultMessage ?: "").startsWith("File CSV") || (importResultMessage ?: "").startsWith("Tidak ada")) 
                                MaterialTheme.colorScheme.error 
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    LaunchedEffect(importResultMessage) {
                        kotlinx.coroutines.delay(4000)
                        importResultMessage = null
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onNavigateToDatabase,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lihat List Database Lengkap")
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp)) 
            }
        }
    }

    // Duplicate error popup dialog
    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = {
                Text(
                    text = "⚠️ Gagal Menambahkan",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = duplicateErrorMessage,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tidak ada kode yang ditambahkan. Perbaiki data duplikat terlebih dahulu.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showDuplicateDialog = false }) {
                    Text("Mengerti")
                }
            }
        )
    }
}
