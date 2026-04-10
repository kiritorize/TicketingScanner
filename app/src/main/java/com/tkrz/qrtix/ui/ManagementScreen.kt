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
@Composable
private fun NumberedInputBox(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = value.split("\n")
    val lineCount = lines.size
    val scrollState = rememberScrollState()

    // Build the number text: one number per line, matching TextField lines
    val numberText = (1..lineCount).joinToString("\n") { "$it" }

    // Shared base style ensures identical vertical positioning for numbers & input
    val baseLineStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 24.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    // Capture the actual text layout result from BasicTextField
    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    val topPadDp = 8.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            // Header row — taller than input rows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "No",
                    modifier = Modifier.width(32.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Divider(
                    modifier = Modifier.height(20.dp).width(1.dp),
                    color = Color.Gray.copy(alpha = 0.5f)
                )
                Text(
                    label,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                // Clear button — only show when box has content
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Kosongkan",
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp)
                            .clickable { onClear() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Content area: grid lines + scrollable content + scrollbar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .drawWithContent {
                        val strokeWidth = 1.dp.toPx()
                        val scrollY = scrollState.value.toFloat()
                        val numColW = 32.dp.toPx()
                        val topPad = topPadDp.toPx()

                        // --- Draw grid lines BEHIND content ---

                        // Vertical line after number column
                        drawLine(
                            Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(numColW, 0f),
                            end = Offset(numColW, size.height),
                            strokeWidth = strokeWidth
                        )

                        // Horizontal lines — based on ACTUAL text layout positions
                        val layout = textLayoutResult
                        if (layout != null && layout.lineCount > 1) {
                            for (i in 0 until layout.lineCount - 1) {
                                // getLineBottom gives the exact bottom of line i
                                // relative to the TextField content area.
                                // Add topPad because TextField has padding(top = 8.dp)
                                val viewY = topPad + layout.getLineBottom(i) - scrollY
                                if (viewY > size.height) break
                                if (viewY > 0f) {
                                    drawLine(
                                        Color.LightGray.copy(alpha = 0.5f),
                                        start = Offset(0f, viewY),
                                        end = Offset(size.width, viewY),
                                        strokeWidth = strokeWidth
                                    )
                                }
                            }
                        }

                        // --- Draw actual composable content ---
                        drawContent()

                        // --- Draw scrollbar ON TOP ---
                        val maxScroll = scrollState.maxValue.toFloat()
                        if (maxScroll > 0f) {
                            val viewH = size.height
                            val contentH = viewH + maxScroll
                            val thumbH = (viewH / contentH * viewH).coerceAtLeast(20.dp.toPx())
                            val thumbY = (scrollY / maxScroll) * (viewH - thumbH)
                            val barW = 4.dp.toPx()
                            val barX = size.width - barW - 2.dp.toPx()
                            drawRoundRect(
                                color = Color.Gray.copy(alpha = 0.4f),
                                topLeft = Offset(barX, thumbY),
                                size = Size(barW, thumbH),
                                cornerRadius = CornerRadius(barW / 2f)
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    // Row numbers — uses identical base style for perfect vertical alignment
                    Text(
                        text = numberText,
                        modifier = Modifier
                            .width(32.dp)
                            .padding(top = topPadDp, bottom = 8.dp),
                        style = baseLineStyle.copy(
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    )

                    // Text input field — uses identical base style
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 8.dp, top = topPadDp, bottom = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        textStyle = baseLineStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        onTextLayout = { result ->
                            textLayoutResult = result
                        },
                        decorationBox = { innerTextField ->
                            if (value.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = baseLineStyle.copy(
                                        color = Color.LightGray
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(
    viewModel: TicketViewModel,
    onNavigateToDatabase: () -> Unit,
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
                title = { Text("Setup Database Tiket") },
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
