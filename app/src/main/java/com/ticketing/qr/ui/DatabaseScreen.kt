package com.ticketing.qr.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ticketing.qr.viewmodel.TicketViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DatabaseScreen(
    viewModel: TicketViewModel,
    onNavigateBack: () -> Unit
) {
    val ticketList by viewModel.ticketList.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    var sortMode by remember { mutableStateOf("ID Tiket") }
    var expandedSort by remember { mutableStateOf(false) }
    val sortOptions = listOf("ID Tiket", "Kategori Tiket", "Status Scan", "Waktu Dimodifikasi")

    val filteredList = if (searchQuery.isBlank()) {
        ticketList
    } else {
        ticketList.filter { 
            it.qrContent.contains(searchQuery, ignoreCase = true) || 
            it.ticketType.contains(searchQuery, ignoreCase = true) 
        }
    }.sortedWith { t1, t2 ->
        when (sortMode) {
            "ID Tiket" -> t1.id.compareTo(t2.id)
            "Kategori Tiket" -> t1.ticketType.compareTo(t2.ticketType)
            "Status Scan" -> t1.isScanned.compareTo(t2.isScanned)
            "Waktu Dimodifikasi" -> t2.createdAt.compareTo(t1.createdAt) // Using createdAt which is updated on modify
            else -> t1.id.compareTo(t2.id)
        }
    }

    var selectedTickets = remember { mutableStateListOf<Int>() }
    val inSelectionMode = selectedTickets.isNotEmpty()

    var editingTicket by remember { mutableStateOf<com.ticketing.qr.data.Ticket?>(null) }
    var editCodeInput by remember { mutableStateOf("") }
    var editCatInput by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportDataToCsvUri(it) { success ->
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (inSelectionMode) {
                        Text("${selectedTickets.size} Terpilih")
                    } else {
                        Text("List Lengkap Database") 
                    }
                },
                navigationIcon = {
                    if (inSelectionMode) {
                        IconButton(onClick = { selectedTickets.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Batal Select")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                },
                actions = {
                    if (inSelectionMode) {
                        IconButton(onClick = {
                            viewModel.deleteTickets(selectedTickets.toList())
                            selectedTickets.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { exportLauncher.launch("List_Tiket.csv") }) {
                            Icon(Icons.Default.Share, contentDescription = "Export Excel (CSV)")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search and Status Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Data: ${ticketList.size} Tiket",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            OutlinedButton(
                                onClick = { expandedSort = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(sortMode, fontSize = 12.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                            DropdownMenu(
                                expanded = expandedSort,
                                onDismissRequest = { expandedSort = false }
                            ) {
                                sortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            sortMode = option
                                            expandedSort = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (searchQuery.isNotBlank()) {
                        Text(
                            text = "Menampilkan hasil filter: ${filteredList.size}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari berdasar kode atau tipe...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            // List Content
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                if (filteredList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Tidak ada data ditemukan", color = Color.Gray)
                        }
                    }
                }
                
                itemsIndexed(filteredList) { index, ticket ->
                    val isScanned = ticket.isScanned
                    val color = if (isScanned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    val statusText = if (isScanned) "Scanned" else "Pending"
                    val typeText = ticket.ticketType

                    val isSelected = selectedTickets.contains(ticket.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .combinedClickable(
                                onClick = {
                                    if (inSelectionMode) {
                                        if (isSelected) selectedTickets.remove(ticket.id)
                                        else selectedTickets.add(ticket.id)
                                    }
                                },
                                onLongClick = {
                                    if (!inSelectionMode) {
                                        selectedTickets.add(ticket.id)
                                    }
                                }
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ticket.qrContent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = typeText,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!inSelectionMode) {
                                        IconButton(
                                            onClick = {
                                                editCodeInput = ticket.qrContent
                                                editCatInput = ticket.ticketType
                                                editingTicket = ticket
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = statusText,
                                            color = color,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "#${ticket.id}",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (editingTicket != null) {
        AlertDialog(
            onDismissRequest = { editingTicket = null },
            title = { Text("Edit Tiket") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editCodeInput,
                        onValueChange = { editCodeInput = it },
                        label = { Text("Kode Unik") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = editCatInput,
                        onValueChange = { editCatInput = it },
                        label = { Text("Kategori") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editCodeInput.isNotBlank() && editCatInput.isNotBlank()) {
                        viewModel.updateTicket(editingTicket!!.id, editCodeInput, editCatInput)
                        editingTicket = null
                    }
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTicket = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
