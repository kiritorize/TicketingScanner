package com.tkrz.qrtix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tkrz.qrtix.viewmodel.TicketViewModel

@Composable
fun HistoryLogDialog(
    viewModel: TicketViewModel,
    onDismissRequest: () -> Unit
) {
    val historyLogs by viewModel.historyLogs.collectAsState()
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Riwayat Aktivitas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (historyLogs.isNotEmpty()) {
                        var showClearHistoryDialog by remember { mutableStateOf(false) }
                        
                        TextButton(onClick = { showClearHistoryDialog = true }) {
                            Text("Hapus Semua", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        
                        if (showClearHistoryDialog) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showClearHistoryDialog = false },
                                title = { Text("Hapus Riwayat") },
                                text = { Text("Apakah Anda yakin ingin menghapus semua riwayat?") },
                                confirmButton = {
                                    androidx.compose.material3.Button(
                                        onClick = { 
                                            viewModel.clearHistoryLogs()
                                            showClearHistoryDialog = false 
                                        },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Hapus") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearHistoryDialog = false }) { Text("Batal") }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (historyLogs.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Belum ada riwayat", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(historyLogs) { log ->
                            var expanded by remember { mutableStateOf(false) }
                            val hasDetails = log.details.isNotBlank() && (log.details.contains("::") || log.action == "Edit")
                            val canUndo = (log.action == "Hapus" || log.action == "Hapus Semua") && log.details.isNotBlank()

                            val color = when (log.action) {
                                "Scan" -> Color(0xFF4CAF50)
                                "Import" -> Color(0xFF2196F3)
                                "Tambah" -> Color(0xFF2196F3)
                                "Edit" -> Color(0xFFFF9800)
                                "Hapus", "Hapus Semua" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { if (hasDetails) expanded = !expanded }) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(40.dp)
                                        .background(color, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = log.action,
                                            fontWeight = FontWeight.Bold,
                                            color = color,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = dateFormat.format(java.util.Date(log.timestamp)),
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = log.description + if (hasDetails) " (Ketuk untuk detail)" else "",
                                        fontSize = 14.sp,
                                        color = if (log.isUndone) Color.Gray else Color.Unspecified
                                    )
                                    
                                    if (expanded && hasDetails) {
                                        val tickets = if (log.action == "Edit") {
                                            log.details.split("||")
                                        } else {
                                            log.details.split("||").mapNotNull { 
                                                val parts = it.split("::")
                                                if (parts.size >= 8) {
                                                    "${parts[1]} (${parts[2]})"
                                                } else if (parts.size >= 2) {
                                                    "${parts[0]} (${parts[1]})"
                                                } else null
                                            }
                                        }
                                        Column(
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .fillMaxWidth()
                                                .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                .padding(8.dp)
                                        ) {
                                            tickets.forEach { ticketStr ->
                                                Text(text = "• $ticketStr", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    if (canUndo) {
                                        if (log.isUndone) {
                                            Text(
                                                text = "Telah diundo",
                                                fontSize = 12.sp,
                                                color = Color.Gray,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        } else {
                                            TextButton(
                                                onClick = { viewModel.undoHistoryLog(log) },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.height(24.dp).padding(top = 4.dp)
                                            ) {
                                                Text("Undo (Batal)", fontSize = 12.sp, color = color)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Tutup")
                    }
                }
            }
        }
    }
}
