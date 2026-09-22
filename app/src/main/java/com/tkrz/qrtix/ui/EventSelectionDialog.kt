package com.tkrz.qrtix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tkrz.qrtix.data.Event

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSelectionDialog(
    events: List<Event>,
    activeEventId: Long,
    onEventSelected: (Long) -> Unit,
    onCreateEvent: (String, String) -> Unit,
    onEditEventClick: (Long) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onExportEventClick: (Long) -> Unit,
    onImportEventUri: (android.net.Uri) -> Unit,
    onDismissRequest: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<Event?>(null) }
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImportEventUri(uri)
            onDismissRequest()
        }
    }

    val activeEvent = events.find { it.id == activeEventId }
    val otherEvents = events.filter { it.id != activeEventId }.sortedByDescending { it.lastAccessedAt }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pilih Workspace Event", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    if (activeEvent != null) {
                        item {
                            EventRow(
                                event = activeEvent,
                                isActive = true,
                                onSelect = { onEventSelected(activeEvent.id); onDismissRequest() },
                                onEdit = { 
                                    onDismissRequest()
                                    onEditEventClick(activeEvent.id)
                                },
                                onExport = {
                                    onDismissRequest()
                                    onExportEventClick(activeEvent.id)
                                },
                                onDelete = null
                            )
                            Divider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                thickness = 2.dp
                            )
                        }
                    }
                    items(otherEvents) { event ->
                        EventRow(
                            event = event,
                            isActive = false,
                            onSelect = { onEventSelected(event.id); onDismissRequest() },
                            onEdit = { 
                                onDismissRequest()
                                onEditEventClick(event.id) 
                            },
                            onExport = {
                                onDismissRequest()
                                onExportEventClick(event.id)
                            },
                            onDelete = { eventToDelete = event }
                        )
                        Divider()
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Buat Event")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buat Event Baru")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { importLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Import Event")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Event dari File")
                }
            }
        }
    }

    if (showCreateDialog) {
        var newEventName by remember { mutableStateOf("") }
        var newEventCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Buat Event Baru") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newEventName,
                        onValueChange = { newEventName = it },
                        label = { Text("Nama Event") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newEventCode,
                        onValueChange = { newEventCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                        label = { Text("Kode Event (ID Singkatan)") },
                        placeholder = { Text("Cth: DCD, KONSR, FEST24") },
                        supportingText = { Text("2-6 karakter alfanumerik. Tidak dapat diubah setelah disimpan.") },
                        isError = newEventCode.length in 1..1 || newEventCode.length > 6,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "⚠️ Kode event akan menjadi prefix pada semua kode QR tiket dan tidak dapat diubah.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            },
            confirmButton = {
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(
                    onClick = {
                        val trimmedName = newEventName.trim()
                        val trimmedCode = newEventCode.trim()
                        if (trimmedName.isNotBlank() && trimmedCode.length in 2..6) {
                            if (events.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                                android.widget.Toast.makeText(context, "Nama workspace sudah digunakan!", android.widget.Toast.LENGTH_SHORT).show()
                            } else if (events.any { it.eventCode.equals(trimmedCode, ignoreCase = true) }) {
                                android.widget.Toast.makeText(context, "Kode event sudah digunakan oleh event lain!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                onCreateEvent(trimmedName, trimmedCode)
                                showCreateDialog = false
                                onDismissRequest()
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Mohon lengkapi form dengan benar!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }


    if (eventToDelete != null) {
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text("Hapus Event") },
            text = { Text("Apakah Anda yakin ingin menghapus workspace '${eventToDelete!!.name}'? Semua data tiket di dalamnya akan ikut terhapus permanen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEvent(eventToDelete!!.id)
                        eventToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { eventToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun EventRow(
    event: Event,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
            if (isActive) {
                Text("Aktif saat ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Event", modifier = Modifier.size(20.dp))
        }
        
        IconButton(onClick = onExport) {
            Icon(Icons.Default.Share, contentDescription = "Export Event", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        }
        
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus Event", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        } else if (isActive) {
            Icon(Icons.Default.Check, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(20.dp))
        } else {
            Spacer(modifier = Modifier.width(44.dp))
        }
    }
}
