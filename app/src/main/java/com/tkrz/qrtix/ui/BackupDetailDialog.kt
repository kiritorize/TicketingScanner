package com.tkrz.qrtix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tkrz.qrtix.data.cloud.BackgroundUploadManager
import com.tkrz.qrtix.data.cloud.UploadResultStatus
import com.tkrz.qrtix.data.cloud.UploadState

@Composable
fun BackupDetailDialog(
    uploadManager: BackgroundUploadManager,
    onDismissRequest: () -> Unit
) {
    val results by uploadManager.uploadResults.collectAsState()
    val uploadState by uploadManager.uploadState.collectAsState()
    
    val successCount = results.count { it.status == UploadResultStatus.SUCCESS }
    val failedCount = results.count { it.status == UploadResultStatus.FAILED }
    val pendingCount = results.count { it.status == UploadResultStatus.PENDING }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Detail Pencadangan",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Summary
                Text("Berhasil: $successCount | Gagal: $failedCount | Menunggu: $pendingCount", fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress Bar if uploading
                if (uploadState is UploadState.Uploading) {
                    val state = uploadState as UploadState.Uploading
                    LinearProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Mengunggah ${state.currentCount}/${state.totalFiles}...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (results.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Belum ada file yang dicadangkan sesi ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { result ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (icon, color) = when (result.status) {
                                        UploadResultStatus.SUCCESS -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
                                        UploadResultStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                                        UploadResultStatus.PENDING -> Icons.Default.HourglassEmpty to MaterialTheme.colorScheme.primary
                                    }
                                    
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            result.task.file.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                        if (result.errorMessage != null) {
                                            Text(
                                                result.errorMessage,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                    
                                    if (result.status == UploadResultStatus.FAILED) {
                                        IconButton(
                                            onClick = { uploadManager.retryFailedUploads(listOf(result.task)) }
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Coba Lagi",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (failedCount > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { uploadManager.retryAllFailed() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Coba Lagi Semua yang Gagal")
                    }
                }
            }
        }
    }
}
