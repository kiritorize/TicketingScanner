package com.tkrz.qrtix.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkrz.qrtix.data.Ticket
import com.tkrz.qrtix.utils.TicketExporter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onNavigateBack: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var catInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var showFinishDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Launcher untuk pilih file CSV
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val stream = context.contentResolver.openInputStream(it)
                    val rows = withContext(Dispatchers.IO) {
                        csvReader().readAll(stream!!)
                    }
                    
                    val codes = mutableListOf<String>()
                    val cats = mutableListOf<String>()
                    
                    var isFirstLine = true
                    rows.forEach { row ->
                        if (row.size >= 2) {
                            val qr = row[0].trim()
                            val type = row[1].trim()
                            
                            // Deteksi Header (Opsional)
                            if (isFirstLine && (qr.equals("ID", true) || qr.equals("Kode QR", true))) {
                                isFirstLine = false
                            } else {
                                codes.add(qr)
                                cats.add(type)
                                isFirstLine = false
                            }
                        }
                    }
                    
                    codeInput = codes.joinToString("\n")
                    catInput = cats.joinToString("\n")
                    android.widget.Toast.makeText(context, "Berhasil memuat ${codes.size} baris", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Gagal membaca file!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Dialog Error untuk Validasi
    var errorMessage by remember { mutableStateOf<String?>(null) }
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = {
                Button(onClick = { errorMessage = null }) { Text("Mengerti") }
            },
            title = { Text("⚠️ Kesalahan Data", color = MaterialTheme.colorScheme.error) },
            text = { Text(errorMessage!!) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generator Tiket Baru") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            item {
                Text(
                    "Alat ini digunakan untuk membuat Kode QR dan file CSV Tiket secara massal tanpa memasukkannya ke database aplikasi sekarang.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Tombol Impor CSV
                OutlinedButton(
                    onClick = { csvLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Impor Data dari File CSV / Excel")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Box 1: Kode Unik
                NumberedInputBox(
                    label = "Kode Unik",
                    placeholder = "Ketik / paste kode unik tiap baris....",
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    onClear = { codeInput = "" }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Box 2: Kategori
                NumberedInputBox(
                    label = "Kategori",
                    placeholder = "Ketik / paste kategori tiap baris....",
                    value = catInput,
                    onValueChange = { catInput = it },
                    onClear = { catInput = "" }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val codes = codeInput.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        val cats = catInput.split("\n").map { it.trim() }.filter { it.isNotBlank() }

                        if (codes.isEmpty()) return@Button

                        // 1. Validasi Jumlah Baris
                        if (codes.size != cats.size) {
                            errorMessage = "Jumlah baris tidak sama!\n\nKode: ${codes.size} baris\nKategori: ${cats.size} baris\n\nPastikan data berpasangan dengan benar."
                            return@Button
                        }

                        // 2. Validasi Duplikasi Kode (Persis seperti Setup Database)
                        val codeToRows = mutableMapOf<String, MutableList<Int>>()
                        codes.forEachIndexed { index, code ->
                            codeToRows.getOrPut(code) { mutableListOf() }.add(index + 1)
                        }
                        
                        val duplicates = codeToRows.filter { it.value.size > 1 }
                        if (duplicates.isNotEmpty()) {
                            val msg = duplicates.map { (code, rows) ->
                                "- Kode \"$code\" ada di baris: ${rows.joinToString(", ")}"
                            }.joinToString("\n")
                            errorMessage = "Ditemukan kode duplikat dalam daftar:\n\n$msg\n\nHarap perbaiki sebelum generate."
                            return@Button
                        }

                        // 3. Nama File dengan Timestamp
                        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                        val zipName = "Qrtix_QR_Generated_$timeStamp.zip"

                        isGenerating = true
                        scope.launch {
                            val dummyTickets = codes.mapIndexed { index, code ->
                                Ticket(qrContent = code, ticketType = cats[index])
                            }
                            
                            val exporter = TicketExporter(context)
                            val file = exporter.exportTicketsToZip(dummyTickets, fileName = zipName) { current, total ->
                                progressText = "Menghasilkan QR: $current / $total"
                            }
                            
                            isGenerating = false
                            if (file != null) {
                                showFinishDialog = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = codeInput.isNotBlank() && !isGenerating
                ) {
                    Text("Generate & Simpan ke ZIP")
                }
            }
        }
    }

    if (isGenerating) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Sedang Memproses...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(progressText)
                }
            }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            confirmButton = {
                Button(onClick = { showFinishDialog = false }) { Text("Selesai") }
            },
            title = { Text("Berhasil!") },
            text = { Text("File ZIP berhasil disimpan di folder Documents/QRTix.\n\nSilakan cek melalui File Manager HP Anda.") }
        )
    }
}
