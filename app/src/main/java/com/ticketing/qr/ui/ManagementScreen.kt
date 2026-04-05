package com.ticketing.qr.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
    var codeInput by remember { mutableStateOf("") }
    var catInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
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
                    text = "Input / Paste Manual (Kode & Kategori) tiap baris:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                val codeLines = codeInput.split("\n")
                val catLines = catInput.split("\n")
                val rowCount = maxOf(codeLines.size, catLines.size)
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(bottom = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("No.", modifier = Modifier.width(32.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.Gray.copy(alpha=0.5f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kode Unik", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Divider(modifier = Modifier.height(16.dp).width(1.dp), color = Color.Gray.copy(alpha=0.5f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kategori", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        Box(modifier = Modifier.weight(1f)) {
                            // Vertical and horizontal lines background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        val strokeWidth = 1.dp.toPx()
                                        // A fixed size of 24.sp.toPx() ensures an accurate map to line heights
                                        val yLineHeight = 24.sp.toPx()
                                        var y = yLineHeight
                                        while (y <= size.height + yLineHeight) {
                                            drawLine(
                                                color = Color.LightGray.copy(alpha = 0.5f),
                                                start = Offset(0f, y),
                                                end = Offset(size.width, y),
                                                strokeWidth = strokeWidth
                                            )
                                            y += yLineHeight
                                        }

                                        // Draw vertical lines to match columns
                                        val noWidth = 32.dp.toPx() + 8.dp.toPx() // Based on padding
                                        val divider1X = noWidth
                                        val middleWidth = (size.width - noWidth) / 2f
                                        val divider2X = noWidth + middleWidth

                                        drawLine(
                                            color = Color.LightGray.copy(alpha = 0.5f),
                                            start = Offset(divider1X, 0f),
                                            end = Offset(divider1X, size.height),
                                            strokeWidth = strokeWidth
                                        )
                                        
                                        drawLine(
                                            color = Color.LightGray.copy(alpha = 0.5f),
                                            start = Offset(divider2X, 0f),
                                            end = Offset(divider2X, size.height),
                                            strokeWidth = strokeWidth
                                        )
                                    }
                            )
                            
                            // Input Area
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp)
                            ) {
                                Column(modifier = Modifier.width(32.dp)) {
                                    for (i in 1..rowCount) {
                                        val cLine = codeLines.getOrNull(i - 1)?.isNotBlank() == true
                                        val kLine = catLines.getOrNull(i - 1)?.isNotBlank() == true
                                        if (cLine || kLine) {
                                            Text("$i", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.Gray, lineHeight = 24.sp)
                                        } else {
                                            Text(" ", fontSize = 14.sp, lineHeight = 24.sp)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(10.dp))
                                
                                androidx.compose.foundation.text.BasicTextField(
                                    value = codeInput,
                                    onValueChange = { codeInput = it },
                                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 24.sp),
                                    decorationBox = { innerTextField ->
                                        if (codeInput.isEmpty()) {
                                            Text("Paste Kode...", color = androidx.compose.ui.graphics.Color.LightGray, fontSize = 14.sp, lineHeight = 24.sp)
                                        }
                                        innerTextField()
                                    }
                                )
                                
                                Spacer(modifier = Modifier.width(10.dp))

                                androidx.compose.foundation.text.BasicTextField(
                                    value = catInput,
                                    onValueChange = { catInput = it },
                                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 24.sp),
                                    decorationBox = { innerTextField ->
                                        if (catInput.isEmpty()) {
                                            Text("Paste Kategori...", color = androidx.compose.ui.graphics.Color.LightGray, fontSize = 14.sp, lineHeight = 24.sp)
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        errorMsg = null
                        if (codeInput.isNotBlank() || catInput.isNotBlank()) {
                            val result = viewModel.addTicketsFromTwoBoxes(codeInput, catInput)
                            if (result.first) {
                                playSuccess()
                                codeInput = ""
                                catInput = ""
                                showSuccessAdd = true
                            } else {
                                playError()
                                errorMsg = result.second
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = codeInput.isNotBlank() || catInput.isNotBlank()
                ) {
                    Text("Tambahkan dari Input")
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
}
