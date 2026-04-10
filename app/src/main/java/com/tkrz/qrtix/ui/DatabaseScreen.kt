package com.tkrz.qrtix.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkrz.qrtix.viewmodel.TicketViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DatabaseScreen(
    viewModel: TicketViewModel,
    onNavigateBack: () -> Unit
) {
    val ticketList by viewModel.ticketList.collectAsState()
    val filteredList by viewModel.filteredList.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val filterCategory by viewModel.filterCategory.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()

    var expandedSort by remember { mutableStateOf(false) }
    val sortOptions = listOf("ID Tiket", "Waktu Dimodifikasi", "Terakhir Discan")
    val statusFilterOptions = listOf("Semua", "Sudah Scan", "Belum Scan")
    var expandedCatFilter by remember { mutableStateOf(false) }

    val uniqueCategories = remember(ticketList) {
        listOf("Semua Kategori") + ticketList.map { it.ticketType }.distinct().sorted()
    }

    val isFiltering = searchQuery.isNotBlank() || filterStatus != "Semua" || filterCategory != "Semua Kategori"

    var showFilters by remember { mutableStateOf(false) }
    var selectedTickets = remember { mutableStateListOf<Int>() }
    val inSelectionMode = selectedTickets.isNotEmpty()
    var showClearDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Intercept system back button: cancel selection mode instead of navigating back
    BackHandler(enabled = inSelectionMode) {
        selectedTickets.clear()
    }

    var editingTicket by remember { mutableStateOf<com.tkrz.qrtix.data.Ticket?>(null) }
    var editCodeInput by remember { mutableStateOf("") }
    var editCatInput by remember { mutableStateOf("") }

    var exportOnlyScanned by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportDataToCsvUri(it, exportOnlyScanned) { success ->
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    val activeEvent by viewModel.activeEvent.collectAsState()
                    Column {
                        if (inSelectionMode) {
                            Text("${selectedTickets.size} Terpilih")
                        } else {
                            Text("List Lengkap Database") 
                        }
                        Text(activeEvent?.name ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
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
                            if (selectedTickets.size == filteredList.size) {
                                selectedTickets.clear()
                            } else {
                                selectedTickets.clear()
                                selectedTickets.addAll(filteredList.map { it.id })
                            }
                        }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Pilih Semua")
                        }
                        IconButton(onClick = {
                            val count = selectedTickets.size
                            viewModel.deleteTickets(selectedTickets.toList())
                            selectedTickets.clear()
                            
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "$count tiket dihapus",
                                    actionLabel = "UNDO",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoDelete()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { showHistoryDialog = true }) {
                            Icon(Icons.Default.History, contentDescription = "Riwayat Aktivitas")
                        }
                        var showExportMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Default.IosShare, contentDescription = "Export Excel (CSV)")
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export Semua Data") },
                                    onClick = {
                                        exportOnlyScanned = false
                                        exportLauncher.launch("List_Semua_Tiket.csv")
                                        showExportMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Kehadiran Saja") },
                                    onClick = {
                                        exportOnlyScanned = true
                                        exportLauncher.launch("List_Kehadiran.csv")
                                        showExportMenu = false
                                    }
                                )
                            }
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
                        TextButton(
                            onClick = { showFilters = !showFilters },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(if (showFilters) "Tutup Filter" else "Filter & Urutkan", fontSize = 12.sp)
                        }
                    }

                    if (isFiltering) {
                        Text(
                            text = "Menampilkan hasil filter: ${filteredList.size}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        placeholder = { Text("Cari tiket....") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    if (showFilters) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            statusFilterOptions.forEach { status ->
                                FilterChip(
                                    selected = filterStatus == status,
                                    onClick = { viewModel.filterStatus.value = status },
                                    label = { Text(status, fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Kategori:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box {
                                    OutlinedButton(
                                        onClick = { expandedCatFilter = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.fillMaxWidth().height(32.dp)
                                    ) {
                                        Text(filterCategory, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = expandedCatFilter,
                                        onDismissRequest = { expandedCatFilter = false }
                                    ) {
                                        uniqueCategories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text(cat) },
                                                onClick = {
                                                    viewModel.filterCategory.value = cat
                                                    expandedCatFilter = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(Color.Gray.copy(alpha = 0.5f))
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Urutkan:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { expandedSort = true },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.fillMaxWidth().height(32.dp)
                                        ) {
                                            Text(sortMode, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        DropdownMenu(
                                            expanded = expandedSort,
                                            onDismissRequest = { expandedSort = false }
                                        ) {
                                            sortOptions.forEach { option ->
                                                DropdownMenuItem(
                                                    text = { Text(option) },
                                                    onClick = {
                                                        viewModel.sortMode.value = option
                                                        expandedSort = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.sortAscending.value = !sortAscending },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = if (sortAscending) "Ascending" else "Descending",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()) }

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
                    val statusText = if (isScanned) "Sudah Scan" else "Belum Scan"
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
                                Spacer(modifier = Modifier.height(6.dp))
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
                                    if (ticket.isModified) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(Dimodifikasi)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = statusText,
                                    color = color,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                if (isScanned && ticket.scannedAt != null) {
                                    Text(
                                        text = "Waktu: ${dateFormat.format(java.util.Date(ticket.scannedAt))}",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    if (!inSelectionMode) {
                                        IconButton(
                                            onClick = {
                                                editCodeInput = ticket.qrContent
                                                editCatInput = ticket.ticketType
                                                editingTicket = ticket
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "#${index + 1}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (ticketList.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Hapus Semua / Reset Database")
                        }
                    }
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

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Konfirmasi Reset") },
            text = { Text("Apakah Anda yakin ingin menghapus semua tiket dan progres scan yang ada? Data tidak dapat dikembalikan.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllTickets()
                        showClearDialog = false
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Semua database direset",
                                actionLabel = "UNDO",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoDelete()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showHistoryDialog) {
        HistoryLogDialog(
            viewModel = viewModel,
            onDismissRequest = { showHistoryDialog = false }
        )
    }
}
