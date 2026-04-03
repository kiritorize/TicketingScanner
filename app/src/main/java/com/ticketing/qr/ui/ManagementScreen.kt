package com.ticketing.qr.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ticketing.qr.viewmodel.TicketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(
    viewModel: TicketViewModel,
    onNavigateToScanner: () -> Unit,
    onNavigateToDatabase: () -> Unit,
    playSuccess: () -> Unit,
    playError: () -> Unit
) {
    var qrInput by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showSuccessAdd by remember { mutableStateOf(false) }
    val ticketCount by viewModel.ticketCount.collectAsState()
    val ticketList by viewModel.ticketList.collectAsState()

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importCsvFromUri(it) { success ->
                if (success) {
                    playSuccess()
                    showSuccessAdd = true
                } else {
                    playError()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup Database Tiket") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToScanner,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Mulai Scan")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Total Tiket Terdaftar",
                            fontSize = 16.sp
                        )
                        Text(
                            text = "$ticketCount",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
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
                    Text("Pilih File CSV / Excel (Save As CSV)")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Input / Paste Manual",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = qrInput,
                    onValueChange = { qrInput = it },
                    label = { Text("Ketik kode dan tipe dipisah koma (Contoh: TIKET1,VIP)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp),
                    singleLine = false,
                    maxLines = 10,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Default
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (qrInput.isNotBlank()) {
                            val success = viewModel.addTicketsFromText(qrInput)
                            if (success) {
                                playSuccess()
                                qrInput = ""
                                showSuccessAdd = true
                            } else {
                                playError()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = qrInput.isNotBlank()
                ) {
                    Text("Tambahkan dari Teks")
                }

                if (showSuccessAdd) {
                    Text(
                        text = "Berhasil ditambahkan ke Database!",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                    LaunchedEffect(showSuccessAdd) {
                        kotlinx.coroutines.delay(2000)
                        showSuccessAdd = false
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Hapus Semua / Reset Database")
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
                Spacer(modifier = Modifier.height(80.dp)) // Extra space for FAB
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Konfirmasi Reset") },
            text = { Text("Apakah Anda yakin ingin menghapus semua tiket dan progres scan yang ada? Data tidak dapat dikembalikan.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllTickets()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
