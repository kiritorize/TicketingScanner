package com.tkrz.qrtix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkrz.qrtix.utils.CategoryMatcher
import com.tkrz.qrtix.utils.ColumnAutoDetector
import com.tkrz.qrtix.utils.ColumnMapping
import com.tkrz.qrtix.viewmodel.DistributionViewModel
import com.tkrz.qrtix.viewmodel.TicketViewModel
import kotlinx.coroutines.launch

private val PrimaryColor = Color(0xFFA4A2E4)
private val TextDark = Color(0xFF1A1A2E)
private val TextMuted = Color(0xFF6B7280)
private val CardBg = Color.White
private val BgColor = Color(0xFFF8F8FF)
private val SuccessColor = Color(0xFF4CAF50)
private val WarningColor = Color(0xFFFF9800)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistributionScreen(
    viewModel: TicketViewModel,
    distViewModel: DistributionViewModel,
    onNavigateBack: () -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    
    // Step 1 State
    var sheetUrl by remember { mutableStateOf("") }
    var sheetId by remember { mutableStateOf("") }
    var isLoadingStep1 by remember { mutableStateOf(false) }
    var step1Error by remember { mutableStateOf<String?>(null) }
    var headers by remember { mutableStateOf<List<String>>(emptyList()) }
    var dataSample by remember { mutableStateOf<List<List<String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (viewModel.isNavigatingToStatus) {
            val savedSheetId = viewModel.activeEvent.value?.distributionSheetId
            if (savedSheetId != null) {
                sheetId = savedSheetId
                currentStep = 5
                distViewModel.loadStatus(savedSheetId)
                // reset flag
                viewModel.isNavigatingToStatus = false
            }
        }
    }

    // Step 2 State
    var columnMapping by remember { mutableStateOf(ColumnMapping()) }
    var isManualMappingMode by remember { mutableStateOf(false) }

    // Step 3 State (Category Mapping)
    var categoryMappings by remember { mutableStateOf<List<com.tkrz.qrtix.utils.CategoryMappingResult>>(emptyList()) }
    var manualCategorySelections by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Distribusi Tiket", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgColor,
                    titleContentColor = TextDark
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Stepper UI
            StepperUI(currentStep = currentStep, totalSteps = 5)
            Spacer(modifier = Modifier.height(24.dp))

            when (currentStep) {
                1 -> Step1LinkSheet(
                    sheetUrl = sheetUrl,
                    onUrlChange = { sheetUrl = it },
                    isLoading = isLoadingStep1,
                    errorMessage = step1Error,
                    onNext = {
                        val extractedId = extractSpreadsheetId(sheetUrl)
                        if (extractedId != null) {
                            sheetId = extractedId
                            isLoadingStep1 = true
                            step1Error = null
                            coroutineScope.launch {
                                val data = viewModel.readExternalSheetData(sheetId, "A1:Z6")
                                isLoadingStep1 = false
                                if (data != null && data.isNotEmpty()) {
                                    val stringData = data.map { row -> row.map { it.toString() } }
                                    headers = stringData.first()
                                    dataSample = if (stringData.size > 1) stringData.drop(1) else emptyList()
                                    
                                    // Auto-detect columns immediately
                                    columnMapping = ColumnAutoDetector.detectMapping(headers, dataSample)
                                    currentStep = 2
                                } else {
                                    step1Error = "Gagal membaca data. Pastikan Sheet tidak kosong dan izin akses terbuka."
                                }
                            }
                        } else {
                            step1Error = "URL Google Sheets tidak valid."
                        }
                    }
                )
                2 -> Step2ColumnMapping(
                    headers = headers,
                    dataSample = dataSample,
                    mapping = columnMapping,
                    isManualMode = isManualMappingMode,
                    onMappingChange = { columnMapping = it },
                    onToggleManualMode = { isManualMappingMode = it },
                    onNext = {
                        if (columnMapping.isAllMatched) {
                            // Prepare for step 3: Extract unique categories from the sample 
                            // (In a real app, you might want to fetch all categories from the sheet, 
                            // but for this task, reading A1:Z1000 might be needed. We'll simulate with sample for now)
                            coroutineScope.launch {
                                // Re-fetch a larger chunk to get all unique categories if needed
                                val fullData = viewModel.readExternalSheetData(sheetId, "A2:Z1000")
                                val stringFullData = fullData?.map { row -> row.map { it.toString() } } ?: dataSample
                                
                                val catIndex = columnMapping.categoryColIndex ?: -1
                                if (catIndex >= 0) {
                                    val uniqueFormCategories = stringFullData.mapNotNull { it.getOrNull(catIndex) }
                                        .filter { it.isNotBlank() }
                                        .toSet()
                                        .toList()
                                        
                                    val dbCategories = viewModel.ticketCategories.value
                                    categoryMappings = CategoryMatcher.mapCategories(uniqueFormCategories, dbCategories.map { it.categoryName })
                                    
                                    // Initialize manual selections with auto-matched results
                                    manualCategorySelections = categoryMappings.associate { 
                                        it.formCategory to it.mappedDbCategory 
                                    }
                                    
                                    currentStep = 3
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Semua kolom harus dipetakan!")
                            }
                        }
                    },
                    onBack = { currentStep = 1 }
                )
                3 -> Step3CategoryMapping(
                    mappings = categoryMappings,
                    dbCategories = viewModel.ticketCategories.value.map { it.categoryName },
                    manualSelections = manualCategorySelections,
                    onSelectionChange = { formCat, dbCat -> 
                        val newSelections = manualCategorySelections.toMutableMap()
                        newSelections[formCat] = dbCat
                        manualCategorySelections = newSelections
                    },
                    onNext = {
                        val allMapped = manualCategorySelections.values.all { it != null }
                        if (allMapped) {
                            coroutineScope.launch {
                                val fullData = viewModel.readExternalSheetData(sheetId, "A2:Z1000")
                                val stringFullData = fullData?.map { row -> row.map { it.toString() } } ?: emptyList()
                                
                                val buyers = stringFullData.mapNotNull { row ->
                                    val name = columnMapping.nameColIndex?.let { row.getOrNull(it) } ?: ""
                                    val email = columnMapping.emailColIndex?.let { row.getOrNull(it) } ?: ""
                                    val formCat = columnMapping.categoryColIndex?.let { row.getOrNull(it) } ?: ""
                                    val qtyStr = columnMapping.quantityColIndex?.let { row.getOrNull(it) } ?: "0"
                                    
                                    if (name.isBlank() && email.isBlank()) return@mapNotNull null
                                    
                                    val dbCatName = manualCategorySelections[formCat] ?: formCat
                                    val dbCatCode = viewModel.ticketCategories.value.find { it.categoryName == dbCatName }?.categoryCode ?: dbCatName
                                    val qty = qtyStr.filter { it.isDigit() }.toIntOrNull() ?: 0
                                    
                                    com.tkrz.qrtix.data.BuyerData(name, email, dbCatCode, qty)
                                }
                                
                                distViewModel.processDistributionData(
                                    buyers, 
                                    viewModel.activeEvent.value?.id ?: 1L, 
                                    sheetId
                                )
                                currentStep = 4
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Semua kategori form harus dipetakan!")
                            }
                        }
                    },
                    onBack = { currentStep = 2 }
                )
                4 -> {
                    val validationResult by distViewModel.validationResult.collectAsState()
                    val isLoading by distViewModel.isLoading.collectAsState()
                    val errorMessage by distViewModel.errorMessage.collectAsState()
                    val saveSuccess by distViewModel.saveSuccess.collectAsState()
                    
                    LaunchedEffect(saveSuccess) {
                        if (saveSuccess) {
                            currentStep = 5 // Go to email sending step (Task 9.3)
                        }
                    }

                    LaunchedEffect(errorMessage) {
                        if (errorMessage != null) {
                            snackbarHostState.showSnackbar(errorMessage!!)
                        }
                    }

                    Step4ReviewAndValidation(
                        result = validationResult,
                        isLoading = isLoading,
                        onBack = { currentStep = 3 },
                        onConfirm = {
                            distViewModel.saveDistributionMapping(
                                spreadsheetId = sheetId,
                                eventId = viewModel.activeEvent.value?.id ?: 1L,
                                eventName = viewModel.activeEvent.value?.name ?: "Event"
                            )
                        }
                    )
                }
                5 -> {
                    val progress by distViewModel.sendingProgress.collectAsState()
                    val isSending by distViewModel.isSending.collectAsState()
                    val isPaused by distViewModel.isPaused.collectAsState()
                    val sendLogs by distViewModel.sendLogs.collectAsState()

                    Step5SendAndStatus(
                        progress = progress,
                        isSending = isSending,
                        isPaused = isPaused,
                        logs = sendLogs,
                        onStart = {
                            distViewModel.startSending(
                                spreadsheetId = sheetId,
                                eventName = viewModel.activeEvent.value?.name ?: "Event"
                            )
                        },
                        onPause = { distViewModel.pauseSending() },
                        onResume = {
                            distViewModel.resumeSending(
                                spreadsheetId = sheetId,
                                eventName = viewModel.activeEvent.value?.name ?: "Event"
                            )
                        },
                        onRetryFailed = {
                            distViewModel.retryFailed(
                                spreadsheetId = sheetId,
                                eventName = viewModel.activeEvent.value?.name ?: "Event"
                            )
                        },
                        onExport = {
                            distViewModel.exportReport(
                                context = androidx.compose.ui.platform.LocalContext.current,
                                spreadsheetId = sheetId,
                                eventName = viewModel.activeEvent.value?.name ?: "Event"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StepperUI(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..totalSteps) {
            val isActive = i <= currentStep
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isActive) PrimaryColor else Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = i.toString(),
                    color = if (isActive) Color.White else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (i < totalSteps) {
                Divider(
                    modifier = Modifier
                        .width(24.dp)
                        .padding(horizontal = 4.dp),
                    color = if (i < currentStep) PrimaryColor else Color(0xFFE0E0E0)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step1LinkSheet(
    sheetUrl: String,
    onUrlChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onNext: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tautkan Google Form Responses",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Paste link Google Sheets dari jawaban Google Form Anda. Pastikan sheet dapat diakses (Anyone with the link).",
                fontSize = 14.sp,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = sheetUrl,
                onValueChange = onUrlChange,
                label = { Text("URL Google Sheets") },
                placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMessage != null,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = PrimaryColor,
                    focusedLabelColor = PrimaryColor
                )
            )
            
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = sheetUrl.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Selanjutnya", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Step2ColumnMapping(
    headers: List<String>,
    dataSample: List<List<String>>,
    mapping: ColumnMapping,
    isManualMode: Boolean,
    onMappingChange: (ColumnMapping) -> Unit,
    onToggleManualMode: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pemetaan Kolom",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isManualMode) "Pilih kolom yang sesuai dari spreadsheet Anda." else "Kolom berhasil dideteksi otomatis. Periksa apakah sudah benar.",
                fontSize = 14.sp,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!isManualMode) {
                // Auto Mapping View
                MappingRowItem("👤 Nama", mapping.nameHeaderName, mapping.isNameAutoMatched)
                MappingRowItem("📧 Email", mapping.emailHeaderName, mapping.isEmailAutoMatched)
                MappingRowItem("🏷️ Kategori", mapping.categoryHeaderName, mapping.isCategoryAutoMatched)
                MappingRowItem("🔢 Jumlah", mapping.quantityHeaderName, mapping.isQuantityAutoMatched)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (dataSample.isNotEmpty()) {
                    Text("Preview Data Pertama:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth()) {
                        Column {
                            val row = dataSample.first()
                            Text("Nama: ${mapping.nameColIndex?.let { row.getOrNull(it) } ?: "-"}", fontSize = 12.sp)
                            Text("Email: ${mapping.emailColIndex?.let { row.getOrNull(it) } ?: "-"}", fontSize = 12.sp)
                            Text("Kategori: ${mapping.categoryColIndex?.let { row.getOrNull(it) } ?: "-"}", fontSize = 12.sp)
                            Text("Jumlah: ${mapping.quantityColIndex?.let { row.getOrNull(it) } ?: "-"}", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                // Manual Mapping View
                ManualMappingDropdown("👤 Nama", headers, mapping.nameColIndex) { 
                    onMappingChange(mapping.copy(nameColIndex = it, nameHeaderName = it?.let { headers[it] } ?: "", isNameAutoMatched = false))
                }
                ManualMappingDropdown("📧 Email", headers, mapping.emailColIndex) { 
                    onMappingChange(mapping.copy(emailColIndex = it, emailHeaderName = it?.let { headers[it] } ?: "", isEmailAutoMatched = false))
                }
                ManualMappingDropdown("🏷️ Kategori", headers, mapping.categoryColIndex) { 
                    onMappingChange(mapping.copy(categoryColIndex = it, categoryHeaderName = it?.let { headers[it] } ?: "", isCategoryAutoMatched = false))
                }
                ManualMappingDropdown("🔢 Jumlah", headers, mapping.quantityColIndex) { 
                    onMappingChange(mapping.copy(quantityColIndex = it, quantityHeaderName = it?.let { headers[it] } ?: "", isQuantityAutoMatched = false))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (!isManualMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onToggleManualMode(true) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tidak, Perbaiki", color = PrimaryColor)
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Ya, Sudah Benar")
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Kembali", color = PrimaryColor)
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        shape = RoundedCornerShape(12.dp),
                        enabled = mapping.isAllMatched
                    ) {
                        Text("Lanjutkan")
                    }
                }
            }
        }
    }
}

@Composable
fun Step3CategoryMapping(
    mappings: List<com.tkrz.qrtix.utils.CategoryMappingResult>,
    dbCategories: List<String>,
    manualSelections: Map<String, String?>,
    onSelectionChange: (String, String?) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val allMapped = manualSelections.values.all { it != null }

    Card(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pemetaan Kategori",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cocokkan nama kategori di Form dengan tipe tiket di Database Anda.",
                fontSize = 14.sp,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(mappings) { mapping ->
                    val selectedDbCat = manualSelections[mapping.formCategory]
                    val isMatched = selectedDbCat != null
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Form: ${mapping.formCategory}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                            Spacer(modifier = Modifier.height(8.dp))
                            // Dropdown for DB Category
                            ManualCategoryDropdown(
                                selected = selectedDbCat,
                                options = dbCategories,
                                onSelection = { onSelectionChange(mapping.formCategory, it) }
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = if (isMatched) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isMatched) SuccessColor else WarningColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Kembali", color = PrimaryColor)
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                    shape = RoundedCornerShape(12.dp),
                    enabled = allMapped
                ) {
                    Text("Konfirmasi & Lanjut")
                }
            }
        }
    }
}

@Composable
fun MappingRowItem(label: String, headerName: String, isAutoMatched: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        val displayText = headerName.ifEmpty { "Tidak Ditemukan" }
        Text(text = displayText, modifier = Modifier.weight(1.5f), fontSize = 14.sp, color = if (headerName.isEmpty()) WarningColor else TextDark)
        
        Icon(
            imageVector = if (isAutoMatched) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isAutoMatched) SuccessColor else WarningColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualMappingDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int?,
    onSelection: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        
        Box(modifier = Modifier.weight(2f)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                val displayText = selectedIndex?.let { options.getOrNull(it) } ?: "Pilih Kolom"
                OutlinedTextField(
                    value = displayText,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option, fontSize = 13.sp) },
                            onClick = {
                                onSelection(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualCategoryDropdown(
    selected: String?,
    options: List<String>,
    onSelection: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        val displayText = selected ?: "Pilih Kategori DB"
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                unfocusedBorderColor = if (selected == null) WarningColor else Color.Gray
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        onSelection(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun extractSpreadsheetId(url: String): String? {
    val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
    val matchResult = regex.find(url)
    return matchResult?.groups?.get(1)?.value
}

@Composable
fun Step4ReviewAndValidation(
    result: com.tkrz.qrtix.data.ValidationResult?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    if (result == null) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryColor)
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Review & Validasi", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Sistem telah melakukan pengecekan data form terhadap ketersediaan tiket lokal.", fontSize = 14.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Summary Info
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryBox("Pembeli Valid", result.totalValidBuyers.toString(), Modifier.weight(1f))
                SummaryBox("Tiket Disiapkan", result.totalTicketsToDistribute.toString(), Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Errors Box
            if (!result.isValid || result.errors.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFFFFF4E5), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ditemukan Masalah Validasi", fontWeight = FontWeight.Bold, color = WarningColor, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    result.errors.forEach { error ->
                        Text("• $error", fontSize = 12.sp, color = TextDark, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Assignment List Preview
            Text("Preview Distribusi:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false)
                    .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(result.assignments.take(50)) { assignment ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(4.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(assignment.buyerName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(assignment.buyerEmail, fontSize = 11.sp, color = TextMuted)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(assignment.ticketCategory, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryColor)
                            Text("${assignment.ticketCodes.size} Tiket", fontSize = 11.sp)
                        }
                    }
                }
                if (result.assignments.size > 50) {
                    item {
                        Text("... dan ${result.assignments.size - 50} data lainnya", fontSize = 12.sp, color = TextMuted, modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Text("Kembali", color = PrimaryColor)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.5f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                    shape = RoundedCornerShape(12.dp),
                    enabled = result.isValid && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Simpan & Lanjutkan")
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryBox(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFFF0F0FA), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PrimaryColor)
        Text(title, fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
fun Step5SendAndStatus(
    progress: com.tkrz.qrtix.data.SendingProgress,
    isSending: Boolean,
    isPaused: Boolean,
    logs: List<com.tkrz.qrtix.data.SendLogEntry>,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetryFailed: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text("Progres Pengiriman", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Pengiriman email sedang berlangsung menggunakan Gmail API. Proses ini dibatasi ~400 email per jam. Jangan tutup aplikasi.", fontSize = 13.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(24.dp))

            // Progress Summary
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryBox("Terkirim", progress.sent.toString(), Modifier.weight(1f))
                SummaryBox("Gagal", progress.failed.toString(), Modifier.weight(1f))
                SummaryBox("Menunggu", progress.pending.toString(), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar
            val progressPercent = if (progress.total > 0) ((progress.sent + progress.failed).toFloat() / progress.total) else 0f
            LinearProgressIndicator(
                progress = { progressPercent },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = PrimaryColor,
                trackColor = Color(0xFFE0E0E0),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("${(progressPercent * 100).toInt()}% Selesai dari ${progress.total} tiket", fontSize = 12.sp, color = TextMuted)

            Spacer(modifier = Modifier.height(24.dp))

            // Live Log
            Text("Log Aktivitas:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp)).padding(8.dp)) {
                if (logs.isEmpty()) {
                    Text("Menunggu untuk memulai...", fontSize = 12.sp, color = TextMuted, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(logs) { log ->
                            val color = when (log.status) {
                                "SENT" -> SuccessColor
                                "FAILED" -> WarningColor
                                else -> TextMuted
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(log.timestamp.takeLast(8), fontSize = 11.sp, color = TextMuted, modifier = Modifier.width(60.dp))
                                Text(log.email, fontSize = 12.sp, color = TextDark, modifier = Modifier.weight(1f))
                                Text(log.status, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
                            }
                            if (log.message.isNotBlank()) {
                                Text("   Error: ${log.message}", fontSize = 10.sp, color = WarningColor)
                            }
                            Divider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (progress.pending == 0 && progress.total > 0) {
                    // Completed state
                    if (progress.failed > 0) {
                        Button(
                            onClick = onRetryFailed,
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarningColor),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSending
                        ) {
                            Text("Ulangi yang Gagal")
                        }
                    }
                    Button(
                        onClick = onExport,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Export CSV")
                    }
                } else {
                    if (!isSending) {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (progress.sent > 0 || progress.failed > 0) "Lanjutkan Pengiriman" else "Mulai Pengiriman")
                        }
                    } else if (!isPaused) {
                        Button(
                            onClick = onPause,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarningColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Jeda Pengiriman")
                        }
                    } else {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Lanjutkan")
                        }
                    }
                }
            }
        }
    }
}
