package com.tkrz.qrtix.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkrz.qrtix.data.Ticket
import com.tkrz.qrtix.utils.TicketExporter
import com.tkrz.qrtix.utils.TicketFormatters
import com.tkrz.qrtix.viewmodel.TicketViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.tkrz.qrtix.ui.components.EventBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    viewModel: TicketViewModel,
    onNavigateBack: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var catInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var showFinishDialog by remember { mutableStateOf(false) }
    
    val activeEvent by viewModel.activeEvent.collectAsState()
    val ticketCategories by viewModel.ticketCategories.collectAsState()
    
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Manual CSV", "Mode Kuota")
    
    var quotaCategory by remember { mutableStateOf("") }
    var quotaCategoryExpanded by remember { mutableStateOf(false) }
    var quotaCount by remember { mutableStateOf("50") }
    var quotaPrefix by remember { mutableStateOf(activeEvent?.eventCode ?: "EVNT") }
    var directInsert by remember { mutableStateOf(false) }

    var showGenerateConfirmation by remember { mutableStateOf(false) }
    var generateCodes by remember { mutableStateOf(emptyList<String>()) }
    var generateCats by remember { mutableStateOf(emptyList<String>()) }

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    fun executeGeneration() {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
        val zipName = "Qrtix_QR_Generated_$timeStamp.zip"

        isGenerating = true
        scope.launch {
            val dummyTickets = generateCodes.mapIndexed { index, code ->
                Ticket(
                    qrContent = code,
                    ticketType = generateCats[index],
                    eventId = activeEvent?.id ?: 1L,
                    createdAt = System.currentTimeMillis()
                )
            }
            
            val exporter = TicketExporter(context)
            val resolvedLogoPath = viewModel.resolveMedia(activeEvent?.logoPath)
            val eventForExport = activeEvent?.copy(logoPath = resolvedLogoPath)

            val file = exporter.exportTicketsToZip(dummyTickets, eventForExport, fileName = zipName) { current, total ->
                progressText = "Menghasilkan QR: $current / $total"
            }
            
            if (directInsert && activeEvent != null) {
                progressText = "Menyimpan ke Database..."
                val addedCount = viewModel.insertBatchTickets(dummyTickets)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "$addedCount tiket ditambahkan ke database.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            
            isGenerating = false
            if (file != null) {
                showFinishDialog = true
            }
        }
    }

    DisposableEffect(isGenerating) {
        if (isGenerating) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Generator Tiket Baru")
                        }
                        EventBadge(event = activeEvent)
                    }
                },
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
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTabIndex == 0) {
                    Text(
                        "Gunakan mode ini untuk membuat QR dari daftar kode yang sudah ada.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { csvLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Impor Data dari File CSV / Excel")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    NumberedInputBox(
                        label = "Kode Unik",
                        placeholder = "Ketik / paste kode unik...",
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        onClear = { codeInput = "" }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    NumberedInputBox(
                        label = "Kategori",
                        placeholder = "Ketik / paste kategori...",
                        value = catInput,
                        onValueChange = { catInput = it },
                        onClear = { catInput = "" }
                    )
                } else {
                    Text(
                        "Gunakan mode ini untuk membuat tiket secara massal dengan format standar QRTix.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = quotaPrefix,
                        onValueChange = { quotaPrefix = it.uppercase() },
                        label = { Text("Prefix Event (Cth: EVNT)") },
                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = quotaCategoryExpanded,
                        onExpandedChange = { quotaCategoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = quotaCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori Tiket") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = quotaCategoryExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = quotaCategoryExpanded,
                            onDismissRequest = { quotaCategoryExpanded = false }
                        ) {
                            if (ticketCategories.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Belum ada kategori", color = MaterialTheme.colorScheme.error) },
                                    onClick = { quotaCategoryExpanded = false }
                                )
                            } else {
                                ticketCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text("${category.categoryName} (${category.categoryCode})") },
                                        onClick = {
                                            quotaCategory = category.categoryCode
                                            quotaCategoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = quotaCount,
                        onValueChange = { quotaCount = it.filter { char -> char.isDigit() } },
                        label = { Text("Jumlah Kuota") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = directInsert, onCheckedChange = { directInsert = it })
                    Text("Langsung masukkan ke Database: ${activeEvent?.name}")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val codes = mutableListOf<String>()
                        val cats = mutableListOf<String>()

                        if (selectedTabIndex == 0) {
                            codes.addAll(codeInput.split("\n").map { it.trim() }.filter { it.isNotBlank() })
                            cats.addAll(catInput.split("\n").map { it.trim() }.filter { it.isNotBlank() })
                            if (codes.isEmpty()) return@Button
                            if (codes.size != cats.size) {
                                errorMessage = "Jumlah baris tidak sama!\n\nKode: ${codes.size} baris\nKategori: ${cats.size} baris"
                                return@Button
                            }
                            
                            val validCategoryCodes = ticketCategories.map { it.categoryCode }
                            val invalidCats = cats.filter { !validCategoryCodes.contains(it) }.distinct()
                            if (invalidCats.isNotEmpty()) {
                                errorMessage = "Kategori berikut tidak terdaftar atau tidak sesuai ID (Code):\n\n" + invalidCats.joinToString(", ") + "\n\nHarap pastikan semua baris menggunakan ID Singkatan kategori yang valid."
                                return@Button
                            }
                        } else {
                            val count = quotaCount.toIntOrNull() ?: 0
                            if (count <= 0 || quotaCategory.isBlank() || quotaPrefix.isBlank()) {
                                errorMessage = "Harap isi Prefix, Kategori, dan Jumlah Kuota (min 1)."
                                return@Button
                            }
                            for (i in 1..count) {
                                val randomToken = com.tkrz.qrtix.utils.TicketFormatters.generateRandomToken(4)
                                val code = com.tkrz.qrtix.utils.TicketFormatters.formatTicketCode(quotaPrefix, quotaCategory, i, randomToken)
                                codes.add(code)
                                cats.add(quotaCategory)
                            }
                        }

                        val codeToRows = mutableMapOf<String, MutableList<Int>>()
                        codes.forEachIndexed { index, code ->
                            codeToRows.getOrPut(code) { mutableListOf() }.add(index + 1)
                        }
                        
                        val duplicates = codeToRows.filter { it.value.size > 1 }
                        if (duplicates.isNotEmpty()) {
                            errorMessage = "Ditemukan kode duplikat dalam daftar. Harap perbaiki sebelum generate."
    var showGenerateConfirmation by remember { mutableStateOf(false) }
    var generateCodes by remember { mutableStateOf(emptyList<String>()) }
    var generateCats by remember { mutableStateOf(emptyList<String>()) }

    var pendingUploadTasks by remember { mutableStateOf<List<com.tkrz.qrtix.data.cloud.UploadTask>>(emptyList()) }

    fun executeGeneration() {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
        val zipName = "Qrtix_QR_Generated_$timeStamp.zip"

        isGenerating = true
        scope.launch {
            val dummyTickets = generateCodes.mapIndexed { index, code ->
                Ticket(
                    qrContent = code,
                    ticketType = generateCats[index],
                    eventId = activeEvent?.id ?: 1L,
                    createdAt = System.currentTimeMillis()
                )
            }
            
            val exporter = com.tkrz.qrtix.utils.TicketExporter(context)
            val resolvedLogoPath = viewModel.resolveMedia(activeEvent?.logoPath)
            val eventForExport = activeEvent?.copy(logoPath = resolvedLogoPath)

            val (file, uploadTasks) = exporter.exportTicketsToZip(dummyTickets, eventForExport, fileName = zipName) { current, total ->
                progressText = "Menghasilkan QR: $current / $total"
            }
            
            if (directInsert && activeEvent != null) {
                progressText = "Menyimpan ke Database..."
                val addedCount = viewModel.insertBatchTickets(dummyTickets)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "$addedCount tiket ditambahkan ke database.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            
            isGenerating = false
            if (file != null) {
                pendingUploadTasks = uploadTasks
                showFinishDialog = true
            }
        }
    }

                        if (directInsert) {
                            generateCodes = codes
                            generateCats = cats
                            showGenerateConfirmation = true
                        } else {
                            generateCodes = codes
                            generateCats = cats
                            executeGeneration()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isGenerating
                ) {
                    Text("Generate & Simpan ke ZIP")
                }
            }
        }
    }

    if (showGenerateConfirmation) {
        AlertDialog(
            onDismissRequest = { showGenerateConfirmation = false },
            title = { Text("Konfirmasi Simpan ke Database") },
            text = { Text("Anda telah memilih untuk langsung menyimpan ${generateCodes.size} tiket ke event **${activeEvent?.name}**. Lanjutkan?") },
            confirmButton = {
                Button(onClick = {
                    showGenerateConfirmation = false
                    executeGeneration()
                }) {
                    Text("Ya, Lanjutkan")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showGenerateConfirmation = false }) {
                    Text("Batal")
                }
            }
        )
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
                    Spacer(Modifier.height(8.dp))
                    Text("Jangan tutup aplikasi atau layar.", fontSize = 12.sp, color = Color.Gray)
                }
            }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            confirmButton = {
                Button(onClick = { 
                    viewModel.backgroundUploadManager.enqueueUploads(pendingUploadTasks)
                    showFinishDialog = false 
                }) { 
                    Text("Cadangkan ke Drive (Disarankan)") 
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showFinishDialog = false }) {
                    Text("Nanti Saja")
                }
            },
            title = { Text("Berhasil!") },
            text = { Text("File ZIP berhasil disimpan di folder Documents/QRTix.\n\nApakah Anda ingin mencadangkan gambar QR secara satuan ke Google Drive agar sinkron dengan Cloud?") }
        )
    }
}
