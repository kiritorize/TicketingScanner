package com.tkrz.qrtix.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.tkrz.qrtix.viewmodel.TicketViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DatabaseScreen(
    viewModel: TicketViewModel,
    onNavigateBack: () -> Unit
) {
    val ticketList by viewModel.ticketList.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    var sortMode by remember { mutableStateOf("ID Tiket") }
    var sortAscending by remember { mutableStateOf(true) }
    var expandedSort by remember { mutableStateOf(false) }
    val sortOptions = listOf("ID Tiket", "Waktu Dimodifikasi", "Terakhir Discan")

    // --- Filter state ---
    var filterStatus by remember { mutableStateOf("Semua") }
    val statusFilterOptions = listOf("Semua", "Sudah Scan", "Belum Scan")

    var filterCategory by remember { mutableStateOf("Semua Kategori") }
    var expandedCatFilter by remember { mutableStateOf(false) }

    // Unique categories from all ticket data
    val uniqueCategories = remember(ticketList) {
        listOf("Semua Kategori") + ticketList.map { it.ticketType }.distinct().sorted()
    }

    val filteredList = ticketList
        // Search filter
        .filter { ticket ->
            searchQuery.isBlank() ||
            ticket.qrContent.contains(searchQuery, ignoreCase = true) ||
            ticket.ticketType.contains(searchQuery, ignoreCase = true)
        }
        // Status filter
        .filter { ticket ->
            when (filterStatus) {
                "Sudah Scan" -> ticket.isScanned
                "Belum Scan" -> !ticket.isScanned
                else -> true
            }
        }
        // Category filter
        .filter { ticket ->
            filterCategory == "Semua Kategori" || ticket.ticketType == filterCategory
        }
        // Sort
        .sortedWith { t1, t2 ->
            val baseCompare = when (sortMode) {
                "ID Tiket" -> t1.id.compareTo(t2.id)
                "Waktu Dimodifikasi" -> t1.createdAt.compareTo(t2.createdAt)
                "Terakhir Discan" -> {
                    // Null scannedAt (belum discan) goes last when ascending, first when descending
                    val s1 = t1.scannedAt
                    val s2 = t2.scannedAt
                    when {
                        s1 == null && s2 == null -> 0
                        s1 == null -> 1  // null always goes to end (before direction flip)
                        s2 == null -> -1
                        else -> s1.compareTo(s2)
                    }
                }
                else -> t1.id.compareTo(t2.id)
            }
            if (sortAscending) baseCompare else -baseCompare
        }

    val isFiltering = searchQuery.isNotBlank() || filterStatus != "Semua" || filterCategory != "Semua Kategori"

    var showFilters by remember { mutableStateOf(false) }
    var selectedTickets = remember { mutableStateListOf<Int>() }
    val inSelectionMode = selectedTickets.isNotEmpty()
    var showClearDialog by remember { mutableStateOf(false) }

    // Intercept system back button: cancel selection mode instead of navigating back
    BackHandler(enabled = inSelectionMode) {
        selectedTickets.clear()
    }

    var editingTicket by remember { mutableStateOf<com.tkrz.qrtix.data.Ticket?>(null) }
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
            // Search, Filter, and Sort Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Total + Filter Toggle row
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

                    // Filter info
                    if (isFiltering) {
                        Text(
                            text = "Menampilkan hasil filter: ${filteredList.size}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari berdasarkan kode atau kategori....") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    if (showFilters) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Status Filter Chips
                        Text("Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            statusFilterOptions.forEach { status ->
                                FilterChip(
                                    selected = filterStatus == status,
                                    onClick = { filterStatus = status },
                                    label = { Text(status, fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category Filter & Sort row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Kategori
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
                                                    filterCategory = cat
                                                    expandedCatFilter = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Vertical Separator
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(Color.Gray.copy(alpha = 0.5f))
                            )
                            
                            // Sort
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
                                                        sortMode = option
                                                        expandedSort = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { sortAscending = !sortAscending },
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
                                    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
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
                                        text = "#${ticket.id}",
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
}
