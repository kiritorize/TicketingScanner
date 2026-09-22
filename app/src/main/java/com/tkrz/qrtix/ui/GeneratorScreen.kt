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
import com.tkrz.qrtix.ui.CategoryManagementDialog

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
    val quotaPrefix = activeEvent?.eventCode ?: "EVNT"

    var showGenerateConfirmation by remember { mutableStateOf(false) }
    var generateCodes by remember { mutableStateOf(emptyList<String>()) }
    var generateCats by remember { mutableStateOf(emptyList<String>()) }

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var pendingUploadTasks by remember { mutableStateOf<List<com.tkrz.qrtix.data.cloud.UploadTask>>(emptyList()) }
    
    var showCategoryDialog by remember { mutableStateOf(false) }
    var keepScreenOn by remember { mutableStateOf(true) }

    val generationProgress by com.tkrz.qrtix.services.GenerationTaskHolder.progress.collectAsState()
    
    LaunchedEffect(generationProgress) {
        if (generationProgress.total > 0 && !generationProgress.isFinished) {
            isGenerating = true
            progressText = "Menghasilkan QR: ${generationProgress.current} / ${generationProgress.total}"
        } else if (generationProgress.isFinished && generationProgress.total > 0) {
            isGenerating = false
            if (generationProgress.successFile != null) {
                pendingUploadTasks = generationProgress.uploadTasks
                showFinishDialog = true
            }
            com.tkrz.qrtix.services.GenerationTaskHolder.reset()
        }
    }

    if (showCategoryDialog) {
        CategoryManagementDialog(
            categories = ticketCategories,
            onDismissRequest = { showCategoryDialog = false },
            onAddCategory = { name, code -> 
                viewModel.addCategory(name, code)
                quotaCategory = code
                showCategoryDialog = false
            },
            onUpdateCategory = { id, newName -> viewModel.updateCategory(id, newName) },
            onDeleteCategory = { id -> viewModel.deleteCategory(id) }
        )
    }

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
            
            var insertedCount = 0
            if (selectedTabIndex == 1 && activeEvent != null) {
                progressText = "Menyimpan ke Database..."
                val catForLog = generateCats.firstOrNull() ?: ""
                val evtName = activeEvent?.name ?: ""
                insertedCount = viewModel.insertBatchTickets(dummyTickets, isGenerated = true, categoryForLog = catForLog, eventNameForLog = evtName)
            }

            val resolvedLogoPath = viewModel.resolveMedia(activeEvent?.logoPath)
            val eventForExport = activeEvent?.copy(logoPath = resolvedLogoPath)

            if (keepScreenOn) {
                val exporter = com.tkrz.qrtix.utils.TicketExporter(context)
                val (file, uploadTasks) = exporter.exportTicketsToZip(dummyTickets, eventForExport, fileName = zipName) { current, total ->
                    progressText = "Menghasilkan QR: $current / $total"
                }
                isGenerating = false
                if (file != null) {
                    pendingUploadTasks = uploadTasks
                    showFinishDialog = true
                }
            } else {
                com.tkrz.qrtix.services.GenerationTaskHolder.reset()
                com.tkrz.qrtix.services.GenerationTaskHolder.dummyTickets = dummyTickets
                com.tkrz.qrtix.services.GenerationTaskHolder.eventForExport = eventForExport
                com.tkrz.qrtix.services.GenerationTaskHolder.zipName = zipName
                
                val serviceIntent = android.content.Intent(context, com.tkrz.qrtix.services.GenerationService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                
                // Allow user to hide dialog
                isGenerating = false
                android.widget.Toast.makeText(context, "Generate berjalan di latar belakang", android.widget.Toast.LENGTH_LONG).show()
                
                // If they minimize app, they see notification. If they stay here, LaunchedEffect catches it.
                // But wait, if they stay, LaunchedEffect will set isGenerating=true again.
                // We don't want to block the screen if they opted for background.
                // So if keepScreenOn is false, we let them know it's running via Toast.
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
                        onValueChange = {},
                        label = { Text("Kode Event (Prefix)") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "Prefix diambil dari Kode Event yang sudah ditetapkan saat membuat profil event.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
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
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("+ Tambah Kategori Baru", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        quotaCategoryExpanded = false
                                        showCategoryDialog = true
                                    }
                                )
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
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { keepScreenOn = it }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Layar tetap menyala selama proses", fontSize = 14.sp)
                }

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
                            return@Button
                        }

                        generateCodes = codes
                        generateCats = cats
                        if (selectedTabIndex == 1) {
                            showGenerateConfirmation = true
                        } else {
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
            title = { Text("Konfirmasi Generate & Simpan") },
            text = { Text("Akan men-generate ${generateCodes.size} tiket [${generateCats.firstOrNull() ?: "-"}] untuk event **${activeEvent?.name}** dan otomatis memasukkannya ke database. Lanjutkan?") },
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
            text = { 
                Column {
                    Text("✅ Berhasil men-generate ${generateCodes.size} tiket")
                    if (selectedTabIndex == 1) {
                        Text("✅ ${generateCodes.size} tiket ditambahkan ke database")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("File ZIP berhasil disimpan di folder Documents/QRTix.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Apakah Anda ingin mencadangkan gambar QR secara satuan ke Google Drive agar sinkron dengan Cloud?")
                }
            }
        )
    }
}
