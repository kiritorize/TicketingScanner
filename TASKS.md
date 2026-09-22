# TASKS — QRTix (TicketingScanner)

> **CRITICAL INSTRUCTIONS FOR AI AGENT:**
> 1. **STRICT LANGUAGE RULE**: All documentation, task descriptions, notes, and log entries MUST be written in **ENGLISH ONLY**. Never write task descriptions or notes in Indonesian or any other language (even though the Android app's UI text itself is in Indonesian).
> 2. Read `PROJECT_DOCUMENTATION.md` FIRST to understand the full project context and architecture.
> 3. Read this file to know what tasks need to be done.
> 4. Work on **ONLY ONE** task at a time, strictly following the phase order below. Do NOT attempt multiple tasks in a single prompt or session.
> 5. Verify that the project compiles (`./gradlew assembleDebug` or equivalent build checks) before considering the task complete.
> 6. After completing a task, change its status to `[x]` and write completion notes directly below it in **English**.
> 7. If the task involves changes to structure, files, features, or database schema, also UPDATE `PROJECT_DOCUMENTATION.md` accordingly in **English**.
> 8. If a task is ambiguous or blocked, write a note below it and mark it `[?]` or `[!]`.
> 9. Move completed tasks to the `## COMPLETED TASKS` section at the bottom.
> 10. Base package path for Kotlin code: `app/src/main/java/com/tkrz/qrtix/`.
#### Sub-Phase 11.7: Scanner, Distribution & Misc Fixes
*(Priority: MEDIUM — Polish and feature additions)*

---

#### Task 11.7.1: Move Sync Button from Scanner to Dashboard
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **IMPLEMENTATION**:
    1. In `ScannerScreen`, remove the manual "Sync" button.
    2. Keep the periodic auto-sync (12s interval) running — it still updates scan counters across devices.
    3. In `DashboardScreen`, add a "Sinkronisasi Data" button (or pull-to-refresh gesture) that calls `viewModel.syncTicketsFromCloud()`.
    4. The dashboard sync performs a smart sync: syncs events, categories, and tickets for the active event. If any data changed, update the UI immediately.
    5. Show last sync time on the dashboard: "Terakhir disinkronisasi: X menit yang lalu".
  - **CROSS-SYSTEM WARNING**: Removing the sync button from scanner doesn't affect the auto-sync. The scanner will still refresh data every 12 seconds automatically.
- **Affected files**: `ui/ScannerScreen.kt`, `ui/DashboardScreen.kt`
- **Verification**: Open scanner → verify no sync button. Scan some tickets → verify counts update automatically. Open dashboard → tap sync → verify data refreshes.

---

#### Task 11.7.2: Fix Distribution Back Button — Step Navigation
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **IMPLEMENTATION**:
    1. In `DistributionScreen`, add a `BackHandler` composable inside the content:
       ```kotlin
       BackHandler(enabled = currentStep > 1) {
           currentStep -= 1
       }
       ```
    2. This makes the system back button navigate to the previous wizard step (Step 3 → Step 2 → Step 1).
    3. When `currentStep == 1`, the default `BackHandler` is NOT enabled, so the system back button performs normal navigation (exit DistributionScreen).
    4. Add the required import: `import androidx.activity.compose.BackHandler`.
  - **CROSS-SYSTEM WARNING**: Ensure `currentStep` is the correct state variable name in `DistributionScreen`. Check the existing code for the exact variable name and step numbering.
- **Affected files**: `ui/DistributionScreen.kt`
- **Verification**: Navigate to Distribution Step 3 → press system back → verify it goes to Step 2 (not exit screen). At Step 1 → press back → verify it exits the screen.

---

#### Task 11.7.3: Comprehensive History Logging for All Operations
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **CONTEXT**: History log currently covers: Scan, Import, Manual add, Edit, Delete, Hapus Semua. Missing: Generate, Backup, Distribution, Event changes, Category changes.
  - **IMPLEMENTATION**: Add `historyLogRepository.insertLog()` calls for each missing action:
    1. **Generate tickets** (in `GeneratorScreen` or `TicketViewModel` after batch generation):
       - `action = "Generate"`, `description = "Generate X tiket [CATEGORY] untuk event [EVENT_NAME]"`
    2. **Backup to Drive** (in `BackgroundUploadManager` after completion):
       - `action = "Backup"`, `description = "Pencadangan X gambar QR ke Google Drive"`, `details = "Berhasil: X, Gagal: Y"`
    3. **Distribution email sent** (in `DistributionViewModel` after sending):
       - `action = "Distribusi"`, `description = "Kirim X tiket ke Y penerima via email"`
    4. **Event name changed** (in `TicketViewModel.updateEventName()`):
       - `action = "Ubah Event"`, `description = "Ubah nama event dari [OLD] menjadi [NEW]"`
    5. **Category added** (in `TicketViewModel.addCategory()`):
       - `action = "Tambah Kategori"`, `description = "Tambah kategori [NAME] (ID: [CODE])"`
    6. **Category name updated** (in `TicketViewModel.updateCategory()`):
       - `action = "Ubah Kategori"`, `description = "Ubah nama kategori ID [CODE] menjadi [NEW_NAME]"`
    7. **Category deleted** (in `TicketViewModel.deleteCategory()`):
       - `action = "Hapus Kategori"`, `description = "Hapus kategori [NAME] (ID: [CODE])"`
  - **CROSS-SYSTEM WARNING**: Each `insertLog()` call writes to Google Sheets, which is an async network operation. Ensure it's called inside a `viewModelScope.launch(Dispatchers.IO)` block and does NOT block the UI. If the log write fails, it should fail silently (logs are not critical data).
- **Affected files**: `viewmodel/TicketViewModel.kt`, `ui/GeneratorScreen.kt`, `data/cloud/BackgroundUploadManager.kt`, `viewmodel/DistributionViewModel.kt`
- **Verification**: Perform each operation (generate, backup, distribute, change event name, add/edit/delete category) → check History Log → verify each action appears with correct details.

---

#### Task 11.7.4: Background QR Generation with Foreground Service
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **IMPLEMENTATION**:
    1. Add a toggle switch to `GeneratorScreen` UI: "Layar tetap menyala selama proses" (default: ON).
    2. Create `services/GenerationService.kt` — a `ForegroundService` that:
       - Shows a persistent notification with:
         - Title: "QRTix — Generate Tiket"
         - Text: "Memproses X/Y tiket (Z%)"
         - Progress bar (determinate)
         - Cancel button (stops the service)
       - Runs the `TicketExporter.exportTicketsToZip()` logic inside the service.
       - Sends progress updates to the UI via a `BroadcastReceiver` or `StateFlow` in a shared singleton.
       - On completion, shows a notification: "Generate selesai — X tiket berhasil".
    3. In `GeneratorScreen`:
       - If toggle is ON: use current behavior (keep screen on, run in coroutine).
       - If toggle is OFF: start `GenerationService` and show "Hide" button that minimizes the app.
       - Add a "Cancel" button that stops the service.
    4. Register the service in `AndroidManifest.xml`:
       ```xml
       <service
           android:name=".services.GenerationService"
           android:foregroundServiceType="dataSync"
           android:exported="false" />
       ```
    5. Add the `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions to `AndroidManifest.xml`.
  - **CROSS-SYSTEM WARNING**: Foreground services have strict requirements on Android 14+. The `foregroundServiceType` must be declared in the manifest. Test on API 34+ devices.
- **Affected files**: `services/GenerationService.kt (NEW)`, `ui/GeneratorScreen.kt`, `AndroidManifest.xml`
- **Verification**: Generate 100 tickets with screen off → verify notification shows progress. Verify all 100 tickets are generated successfully. Tap cancel → verify generation stops cleanly.

---

#### Task 11.7.5: Replace Sound Effects with Professional Sounds
- **Status**: [x]
- **Priority**: Low
- **Description**:
  - **IMPLEMENTATION**:
    1. Find royalty-free sound effects suitable for a professional enterprise app:
       - **Success**: Clean, pleasant chime (1-2 seconds). Similar to a checkout success sound.
       - **Error**: Subtle, non-alarming error tone (< 1 second). Similar to a gentle "invalid" sound.
    2. Replace the audio files in `res/raw/`:
       - `res/raw/sound_success.mp3` (or `.ogg`, `.wav`)
       - `res/raw/sound_error.mp3` (or `.ogg`, `.wav`)
    3. Ensure `SoundManager.kt` handles the new file format correctly (check `MediaPlayer` or `SoundPool` compatibility).
    4. Test volume levels on multiple devices to ensure consistency.
  - **Notes**: Free sound resources: freesound.org, mixkit.co, pixabay.com/sound-effects. Ensure the license allows commercial use.
- **Affected files**: `res/raw/sound_success`, `res/raw/sound_error`, `utils/SoundManager.kt`
- **Verification**: Trigger success sound → verify it sounds professional. Trigger error sound → verify it's subtle and non-alarming. Test on device speakers and headphones.

---

### Phase 12: Database Transfer & Advanced Cloud Features (Placeholder)
*(Future phase — groundwork laid in Phase 11.6)*

---

#### Task 12.1: Inter-Account/Inter-Device Database Transfer
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - Build a dedicated "Transfer Database" page that allows users to export an entire event profile (including all tickets, categories, scan history, logos, backgrounds, and QR images) as a `QRTix.data` package file.
  - The package can be shared via Google Drive link or direct file transfer.
  - Another user/device can import the `QRTix.data` package to restore the entire event profile.
  - This builds on the `QRTix.data` JSON file created in Task 11.6.1 and the restructured Drive folder from the same task.
  - Detailed implementation will be defined when Phase 12 begins.
- **Affected files**: TBD
- **Notes**: This is a placeholder task. Full implementation details will be scoped after Phase 11 is complete.

---

---

## STATUS LEGEND

- `[ ]` — Not started
- `[/]` — In progress
- `[x]` — Completed
- `[?]` — Needs clarification from project owner
- `[!]` — Blocked / has issues

---

## ACTIVE TASKS

<!-- 
Format template:
### Task X.Y: Title
- **Status**: [ ]
- **Priority**: High | Medium | Low
- **Description**: Detailed description of what to do (in English).
- **Affected files**: list of files relative to package or project root
- **Notes**: Additional guidelines, cautions, or requirements (in English)
-->

### Phase 1: Database & Data Foundation
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 2: Core QR Engine & Visual UI
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 3: Generator & Exporter
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 4: Scanner & Validation Optimization
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 5: Architecture Refactoring & Google Auth Foundation
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 6: Cloud Data Integration (Google Sheets & Drive)
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 7: Real-Time Multi-Device Scanning
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 8: UI/UX Flow Redesign
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 9: Ticket Distribution via Email
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 9.5: Ticket Category Architecture Refactoring
*(All tasks completed, see COMPLETED TASKS section)*

### Phase 10: Cloud Backup & Synchronization
*(All tasks completed, see COMPLETED TASKS section)*

---

### Phase 11: Bug Fixes, Feature Improvements & Refactoring

> **EXECUTION ORDER**: Sub-phases MUST be executed in this order to prevent system conflicts:
> **11.1 → 11.2 → 11.3 → 11.4 → 11.6 → 11.5 → 11.7**
>
> Data integrity fixes (11.1) MUST be done first because all other phases depend on stable data.
> Network enforcement (11.2) MUST come before UI changes because offline blocking affects every screen.
> Generator workflow (11.3) MUST come before UI overhaul (11.5) because ManagementScreen deletion affects sidebar content.
> Cloud architecture (11.6) should come before UI polish (11.5) because backup detail popup depends on new upload tracking.
> Within each sub-phase, tasks should be done in order (11.1.1 before 11.1.2, etc.).

---

#### Sub-Phase 11.1: Critical Data Integrity & Sync Fixes
*(Priority: CRITICAL — Must be fixed first as other features depend on stable data)*

---

#### Task 11.1.1: Fix Profile/Event Disappearing on App Restart (Sync Safety)
- **Status**: [x]
- **Priority**: Critical
- **Description**:
  - **ROOT CAUSE**: In `EventRepository.syncEventsFromCloud()` (line 53-57), the code calls `eventDao.deleteAllEvents()` THEN inserts events from cloud. If the cloud returns an empty list (network hiccup, API timeout, wrong spreadsheet), ALL local events are permanently deleted with no recovery path.
  - **FIX — Step 1: Safe Upsert Strategy**: Replace the destructive delete-all-then-insert pattern with an **upsert** strategy:
    1. Read all events from cloud.
    2. If cloud returns null or empty list, **DO NOT** delete local data. Log a warning: `"Cloud returned 0 events, skipping destructive sync"` and return early.
    3. If cloud returns valid data, compare with local data:
       - Events in cloud but not in local → Insert locally.
       - Events in both cloud and local → Update local with cloud data (cloud is source of truth).
       - Events in local but not in cloud → Delete locally (they were deleted from another device).
    4. Use `eventDao.insertEvent()` with `OnConflictStrategy.REPLACE` for atomic upsert.
  - **FIX — Step 2: Add `distributionSheetId` to cloud sync**: The `Events` sheet currently has 11 columns (A-K). Add column L for `distributionSheetId`. Update:
    - `SpreadsheetManager.initializeSchema()`: Add `"distributionSheetId"` to the Events headers list.
    - `EventRepository.syncEventsFromCloud()`: Read column 11 (index 11) as `distributionSheetId`.
    - `EventRepository.insertEvent()` and `updateEvent()`: Write `distributionSheetId` as column 12.
    - This requires updating the Events sheet range from `"Events!A2:K"` to `"Events!A2:L"` in ALL read operations, and `"Events!A$rowIndex:K$rowIndex"` to `"Events!A$rowIndex:L$rowIndex"` in ALL write operations.
  - **FIX — Step 3: Validate `activeEventId` after sync**: After syncing, check if `EventPreferences.activeEventId` still references a valid event. If the event was deleted remotely, fall back to the first available event or create a new default event.
  - **CROSS-SYSTEM WARNING**: This change affects `activeEvent` StateFlow in `TicketViewModel`, which is observed by ALL screens (`DashboardScreen`, `ScannerScreen`, `GeneratorScreen`, `DatabaseScreen`, `DistributionScreen`, `TicketEditorScreen`). After this fix, test switching events and restarting the app to confirm no regressions.
- **Affected files**: `data/repository/EventRepository.kt`, `data/cloud/SpreadsheetManager.kt`, `data/EventDao.kt` (may need `@Insert(onConflict = OnConflictStrategy.REPLACE)`)
- **Verification**: Create 3 events → close app → reopen → verify all 3 events are still present. Simulate empty cloud response (temporarily) → verify local events are NOT deleted.
- **Completion Notes**: Implemented safe upsert strategy in `EventRepository`, updated spreadsheet columns to handle `distributionSheetId`, and updated `AppModule` with required dependencies.

---

#### Task 11.1.2: Fix First 3 Tickets Not Entering Database in Quota Mode
- **Status**: [x]
- **Priority**: Critical
- **Description**:
  - **ROOT CAUSE**: In `TicketRepository.insertTickets()` (line 188-227), the `sheetsService.appendRows(spreadsheetId, "Tickets!A1", chunk)` call uses `"Tickets!A1"` as the append range. Google Sheets' Append API with a range starting at row 1 can behave unpredictably — it may overwrite the header row or insert at the wrong position if the sheet has gaps between the header and the first data row.
  - **SECONDARY ISSUE**: The `nextId` calculation (line 194-201) reads `Tickets!A2:A` to find `maxId`, but this races with concurrent writes. If two devices insert at the same time, they may both compute the same `nextId`, causing ID collisions.
  - **FIX — Step 1: Change append range**: Change `"Tickets!A1"` to `"Tickets!A:H"` in the `sheetsService.appendRows()` call. The Sheets API with a full column range (`A:H`) correctly appends data after the last non-empty row, regardless of gaps. This is the documented best practice for appending data.
  - **FIX — Step 2: Atomic ID generation**: After reading `maxId` from `Tickets!A2:A`, add a small delay and re-read to confirm no concurrent writes occurred. Alternatively, use a `synchronized` block or `Mutex` to prevent concurrent `insertTickets()` calls within the same app instance.
  - **FIX — Step 3: Post-insert verification**: After `appendRows()` completes, re-read the last N rows (where N = number of inserted tickets) from the Sheets to verify all rows were written successfully. If the count doesn't match, log an error and retry the missing rows.
  - **ALSO FIX in `EventRepository.insertEvent()`**: Same issue exists — change `"Events!A1"` append range to `"Events!A:L"` (after adding column L from Task 11.1.1).
  - **ALSO FIX in `HistoryLogRepository`**: Check if the same append range issue exists there and fix it.
  - **CROSS-SYSTEM WARNING**: This change affects ALL ticket insertion paths: Quota Mode generation, CSV import (if still exists), and manual input (if still exists). Since Task 11.3.1 will remove CSV/manual import later, this fix should be done first while those code paths still exist, so we can verify the fix works before removing them.
- **Affected files**: `data/repository/TicketRepository.kt`, `data/repository/EventRepository.kt`, `data/repository/HistoryLogRepository.kt`, `data/cloud/GoogleSheetsService.kt`
- **Verification**: Generate 50 tickets in Quota Mode → immediately check Google Sheets → verify all 50 rows exist. Generate 100 tickets → check database list → verify all 100 appear.
- **Completion Notes**: Added `Mutex` to prevent local concurrency, added verification read, changed append range to `Tickets!A:H` and `HistoryLogs!A:G` respectively.

---

#### Task 11.1.3: Fix QR Image Upload (Only 2 Tickets Uploaded to Drive)
- **Status**: [x]
- **Priority**: Critical
- **Description**:
  - **ROOT CAUSE 1**: In `BackgroundUploadManager.processQueue()` (line 72-114), the loop iterates `for (i in 0 until totalFiles)` using index-based access on `uploadQueue`, but `uploadQueue` is a `mutableListOf` that could be concurrently modified by `enqueueUploads()` from another coroutine.
  - **ROOT CAUSE 2**: When a 404 error occurs on any single file (e.g., parent folder was deleted), `DriveFolderManager.clearCache()` clears ALL cached folder IDs. This causes ALL subsequent uploads in the same batch to fail because they need to re-lookup/create the folder structure from scratch, which may also fail in rapid succession.
  - **ROOT CAUSE 3**: No retry logic exists — a single transient network error permanently marks a file as failed with no recovery path.
  - **FIX — Step 1: Snapshot the queue**: At the start of `processQueue()`, take a snapshot: `val tasksToProcess = uploadQueue.toList()` and then `uploadQueue.clear()`. Iterate over the snapshot instead of the live mutable list.
  - **FIX — Step 2: Targeted cache invalidation**: In the catch block (line 107-113), instead of `driveFolderManager.clearCache()`, only clear the specific folder key that failed: create a new method `DriveFolderManager.invalidateKey(cacheKey: String)` that removes a single entry from the `folderCache` ConcurrentHashMap.
  - **FIX — Step 3: Add retry with exponential backoff**: For each failed upload, retry up to 3 times with delays of 1s, 2s, 4s. Only mark as permanently failed after all retries are exhausted.
  - **FIX — Step 4: Track failed uploads with details**: Add `failedTasks: List<Pair<UploadTask, String>>` (task + error message) to `UploadState.Completed`. This enables the backup detail popup (Task 11.6.3) to show exactly which files failed and why.
  - **FIX — Step 5: Add re-queue capability**: Add a new method `retryFailedUploads(failedTasks: List<UploadTask>)` that re-enqueues only the failed tasks for another attempt.
  - **CROSS-SYSTEM WARNING**: `BackgroundUploadManager` is used by `GeneratorScreen` (after QR generation) and will be used by the backup detail popup (Task 11.6.3). Changes here must be backward compatible.
- **Affected files**: `data/cloud/BackgroundUploadManager.kt`, `data/cloud/DriveFolderManager.kt`
- **Verification**: Generate 20 tickets → trigger backup → verify all 20 images appear in Google Drive under the correct folder structure. Temporarily simulate a network error mid-upload → verify retry logic works and remaining files are still uploaded.
- **Completion Notes**: Added queue snapshotting, targeted cache invalidation `invalidateKey`, exponential backoff retry for failed uploads, and extended `UploadState.Completed` to hold failed task details and expose a retry function.

---

#### Task 11.1.4: Fix "Masukkan ke Database" Button Not Working in Quota Mode
- **Status**: [x]
- **Priority**: Critical
- **Description**:
  - **ROOT CAUSE**: In `GeneratorScreen`, the "Masukkan ke Database" button calls `viewModel.insertBatchTickets(tickets)` which writes tickets to Google Sheets and local DB. However, `insertBatchTickets()` (line 418-427 in `TicketViewModel`) does NOT call `refreshTicketCount()` after insertion. The UI state (`ticketList`, `ticketCount`) is not updated until the user manually triggers a sync from `DatabaseScreen`.
  - **FIX — Step 1**: Add `refreshTicketCount()` call at the end of `insertBatchTickets()` in `TicketViewModel`, after the tickets have been successfully inserted into both Sheets and local DB:
    ```kotlin
    suspend fun insertBatchTickets(tickets: List<Ticket>): Int = withContext(Dispatchers.IO) {
        // ... existing code ...
        if (newTickets.isNotEmpty()) {
            ticketRepository.insertTickets(newTickets)
        }
        refreshTicketCount()  // ← ADD THIS LINE
        newTickets.size
    }
    ```
  - **FIX — Step 2**: Ensure the `GeneratorScreen` shows a success toast with the count: "Berhasil menambahkan X tiket ke database [EVENT_NAME]".
  - **FIX — Step 3**: After successful insertion, the `DashboardScreen` ticket count badge should update immediately (it observes `ticketCount` StateFlow, so the `refreshTicketCount()` call should propagate automatically).
  - **CROSS-SYSTEM WARNING**: `insertBatchTickets()` is called from `GeneratorScreen` only. But `refreshTicketCount()` updates `_ticketList`, `_ticketCount`, `_scannedTicketCount`, and `_historyLogs` — all observed by multiple screens. This is actually the desired behavior.
- **Affected files**: `viewmodel/TicketViewModel.kt`, `ui/GeneratorScreen.kt`
- **Verification**: Generate 10 tickets with "Masukkan ke Database" → immediately navigate to Database → verify all 10 tickets appear without manual sync. Check Dashboard ticket count → verify it matches.
- **Completion Notes**: Added `refreshTicketCount()` inside `insertBatchTickets` in `TicketViewModel`, and updated the Toast message format in `GeneratorScreen`.

---

#### Task 11.1.5: Fix Undo Delete Not Restoring Data in Cloud
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: `undoHistoryLog()` in `TicketViewModel` (line 764-775) calls `ticketRepository.insertTickets(ticketsToRestore)` which DOES write to Google Sheets. However, the issue may be that the `insertTickets()` call fails silently (catches exception internally) or that the serialized ticket data in `log.details` is corrupted/incomplete.
  - **FIX — Step 1: Add error handling to `undoHistoryLog()`**: Wrap the `insertTickets()` call in a try-catch and show an error toast if it fails:
    ```kotlin
    try {
        ticketRepository.insertTickets(ticketsToRestore)
        historyLogRepository.markAsUndone(log.id)
        refreshTicketCount()
        showSuccessToast("Berhasil membatalkan penghapusan ${ticketsToRestore.size} tiket")
    } catch (e: Exception) {
        showErrorToast("Gagal membatalkan penghapusan: ${e.message}")
    }
    ```
  - **FIX — Step 2: Validate serialized data**: Before restoring, validate that `deserializeTickets()` returns non-empty list and that each ticket has valid `qrContent` and `ticketType`. Log a warning if any ticket data is malformed.
  - **FIX — Step 3: Add a `showSuccessToast()` helper**: Currently only `showErrorToast()` exists. Add a matching success toast helper for consistent UX.
- **Affected files**: `viewmodel/TicketViewModel.kt`
- **Verification**: Delete 5 tickets → go to History → tap Undo → verify tickets reappear in both local DB and Google Sheets.
- **Completion Notes**: Added error handling with try-catch block, added success/error toasts, and validated deserialized data before restoration.

---

#### Task 11.1.6: Fix Distribution "Gagal Menyimpan Distribusi ke Google Sheets"
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: The distribution feature fails when trying to save the distribution mapping to Google Sheets. The exact root cause needs debugging with proper error logging.
  - **FIX — Step 1: Add detailed error logging**: In `DistributionRepository` and `DistributionViewModel`, wrap all Sheets API calls in try-catch blocks that log the full stack trace: `Log.e("Distribution", "Failed to save distribution", e)`.
  - **FIX — Step 2: User-facing error details**: Instead of generic "Gagal menyimpan", show the actual error reason to the user. Common causes:
    - `"Spreadsheet not found"` → Spreadsheet was deleted or permissions changed.
    - `"Quota exceeded"` → Too many API calls.
    - `"Network timeout"` → Slow connection.
    - `"Sheet already exists"` → Distribution sheet name conflict.
  - **FIX — Step 3: Verify Distribution sheet creation**: Ensure `GoogleSheetsService` properly creates the Distribution tab. Check if the sheet name conflicts with existing tabs.
  - **FIX — Step 4: Add retry button**: Show a "Coba Lagi" button on error that re-attempts the save operation.
  - **CROSS-SYSTEM WARNING**: This task requires reading `DistributionRepository.kt` and `DistributionViewModel.kt` carefully to trace the exact failure point. The Distribution feature depends on `Event.distributionSheetId` which is NOT synced from cloud (fixed in Task 11.1.1).
- **Affected files**: `data/repository/DistributionRepository.kt`, `viewmodel/DistributionViewModel.kt`, `ui/DistributionScreen.kt`
- **Verification**: Create a distribution mapping → confirm save to Sheets succeeds → check Google Sheets to verify data is written correctly.
- **Completion Notes**: Updated `DistributionRepository` to throw exceptions instead of swallowing them, added error message parsing in `DistributionViewModel` for user-friendly errors, and added a "Coba Lagi" button state in `DistributionScreen` on failure.

---

#### Sub-Phase 11.2: Network & Connectivity Enforcement
*(Priority: HIGH — Prevents data corruption from offline operations)*

---

#### Task 11.2.1: Implement Real-Time Network Monitoring
- **Status**: [x]
- **Priority**: Critical
- **Description**:
  - **CONTEXT**: Currently, `isOnline` in `TicketViewModel` (line 77) is only updated when a scan operation succeeds/fails or during periodic sync. There is NO real-time network monitoring — the app cannot detect connectivity changes that happen between operations.
  - **IMPLEMENTATION**:
    1. Create a new file `utils/NetworkMonitor.kt`:
       ```kotlin
       @Singleton
       class NetworkMonitor @Inject constructor(
           @ApplicationContext private val context: Context
       ) {
           private val _isConnected = MutableStateFlow(true)
           val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
           
           private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
           
           private val networkCallback = object : ConnectivityManager.NetworkCallback() {
               override fun onAvailable(network: Network) { _isConnected.value = true }
               override fun onLost(network: Network) { _isConnected.value = false }
               override fun onUnavailable() { _isConnected.value = false }
           }
           
           init {
               val request = NetworkRequest.Builder()
                   .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                   .build()
               connectivityManager.registerNetworkCallback(request, networkCallback)
               // Set initial state
               val activeNetwork = connectivityManager.activeNetworkInfo
               _isConnected.value = activeNetwork?.isConnectedOrConnecting == true
           }
       }
       ```
    2. Register `NetworkMonitor` in `di/AppModule.kt` as a `@Singleton @Provides`.
    3. Inject `NetworkMonitor` into `TicketViewModel` and replace the manual `_isOnline` MutableStateFlow with `networkMonitor.isConnected`.
    4. Remove all manual `_isOnline.value = true/false` assignments scattered throughout `TicketViewModel` (lines 674, 688, 693, 699, 794, 814, 819).
  - **CROSS-SYSTEM WARNING**: Multiple screens observe `isOnline` — `ScannerScreen` (online/offline pill), `DashboardScreen` (sync status). After this change, they will react to REAL network state instead of operation-based guessing. This is the desired behavior but must be tested.
- **Affected files**: `utils/NetworkMonitor.kt (NEW)`, `di/AppModule.kt`, `viewmodel/TicketViewModel.kt`
- **Verification**: Disable WiFi → verify `isConnected` becomes false immediately. Re-enable WiFi → verify it becomes true within 1-2 seconds.
- **Completion Notes**: Created `NetworkMonitor` singleton using `ConnectivityManager.NetworkCallback`. Registered it in `AppModule`. Replaced `_isOnline` manual states in `TicketViewModel` with `networkMonitor.isConnected` flow.

---

#### Task 11.2.2: Block App Operations When Offline
- **Status**: [x]
- **Priority**: Critical
- **Description**:
  - **CONTEXT**: User reported that when going offline while the app is open, all buttons still work and the app shows "Online". This can cause data desynchronization between local DB and Google Sheets.
  - **IMPLEMENTATION**:
    1. Create `ui/OfflineOverlay.kt` — a full-screen composable overlay:
       - Semi-transparent dark background covering entire screen.
       - Centered card with:
         - WiFi-off icon (animated, pulsing).
         - Text: "Koneksi Internet Terputus"
         - Subtext: "QRTix membutuhkan koneksi internet untuk beroperasi. Sambungkan kembali untuk melanjutkan."
         - "Coba Sambungkan Ulang" button that calls `networkMonitor.checkConnectivity()` (a manual re-check method).
         - When connectivity is restored, auto-dismiss the overlay with a brief "Tersambung kembali ✓" toast.
    2. In `MainActivity.kt`, observe `networkMonitor.isConnected` and show `OfflineOverlay` over the `NavHost` when `isConnected == false`:
       ```kotlin
       Box(modifier = Modifier.fillMaxSize()) {
           NavHost(...) { ... }
           val isConnected by networkMonitor.isConnected.collectAsState()
           if (!isConnected) {
               OfflineOverlay(onRetry = { /* re-check */ })
           }
       }
       ```
    3. The overlay MUST block all touch events to screens underneath (use `Modifier.clickable(enabled = true, onClick = {})` on the overlay background to consume touches).
    4. Add a manual `checkConnectivity()` method to `NetworkMonitor` that actively pings Google's servers (e.g., `URL("https://www.google.com").openConnection()`) as a fallback for when `ConnectivityManager` reports connected but internet is actually unreachable.
  - **CROSS-SYSTEM WARNING**: This overlay sits at the `MainActivity` level, meaning it covers ALL screens including SplashScreen and LoginScreen. The SplashScreen already handles offline state (Task 11.2.3), so the overlay should NOT show during splash — add a condition to skip the overlay when on the "splash" route.
- **Affected files**: `ui/OfflineOverlay.kt (NEW)`, `MainActivity.kt`, `utils/NetworkMonitor.kt`
- **Verification**: Open app → disable WiFi → verify blocking overlay appears immediately. Tap "Coba Sambungkan Ulang" → verify it checks connectivity. Re-enable WiFi → verify overlay auto-dismisses.
- **Completion Notes**: Created `OfflineOverlay` composable with pulsing animation. Updated `MainActivity` to inject `NetworkMonitor` and conditionally overlay `OfflineOverlay` over the `NavHost` (except on "splash" route). Added `checkConnectivity()` to `NetworkMonitor` with fallback HTTP ping.

---

#### Task 11.2.3: Rework Splash Screen — Real Sync Progress
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: Current `SplashScreen` has a fake progress bar that increments randomly (`progress + (5..20).random()`) and waits a random total of ~3-5 seconds before navigating. This provides no real information about the app state.
  - **IMPLEMENTATION**:
    1. Replace the fake progress in `SplashScreen` with real sync steps. Define an enum for sync stages:
       ```kotlin
       enum class SyncStage(val label: String, val progress: Float) {
           CHECKING_NETWORK("Memeriksa koneksi internet...", 0.0f),
           CONNECTING_DRIVE("Menghubungkan ke Google Drive...", 0.15f),
           SYNCING_EVENTS("Sinkronisasi profil event...", 0.30f),
           SYNCING_CATEGORIES("Sinkronisasi kategori...", 0.50f),
           SYNCING_TICKETS("Sinkronisasi database tiket...", 0.70f),
           DOWNLOADING_MEDIA("Mengunduh media...", 0.85f),
           COMPLETE("Siap!", 1.0f)
       }
       ```
    2. Move sync logic from `AuthViewModel.checkAuthStatus()` into `SplashScreen`'s `LaunchedEffect` or a new dedicated `SyncManager` class. The splash screen should:
       - Step 1: Check `NetworkMonitor.isConnected`. If offline → show blocking message: "Koneksi internet diperlukan untuk masuk ke QRTix" + "Coba Lagi" button. Do NOT redirect to login.
       - Step 2: Check `AuthPreferences.isSignedIn`. If not signed in → navigate to login.
       - Step 3: Run `SpreadsheetManager.initializeSpreadsheet()`.
       - Step 4: Run `EventRepository.syncEventsFromCloud()`.
       - Step 5: Run `CategoryRepository.syncCategoriesFromCloud()` for all events.
       - Step 6: Run `TicketRepository.syncTicketsFromCloud()` for the active event.
       - Step 7: Download any missing logos/backgrounds via `MediaManager`.
       - Step 8: Navigate to main menu (or onboarding if first time).
    3. Display each step's label text below the progress bar. Show a checkmark (✅) next to completed steps.
    4. If any step fails, show the error with a "Coba Lagi" button that retries from the failed step.
    5. Remove the sync logic from `AuthViewModel.checkAuthStatus()` — it should only check auth state, not perform data sync. The sync is now owned by the splash screen flow.
  - **CROSS-SYSTEM WARNING**: This is a significant change to the app startup flow. `AuthViewModel.checkAuthStatus()` currently calls `spreadsheetManager.initializeSpreadsheet()`, `eventRepository.syncEventsFromCloud()`, and `historyLogRepository.syncLogsFromCloud()` — all of this needs to be moved. After this change, `AuthViewModel` should ONLY handle authentication state (signed in / not signed in). All data synchronization should happen in the splash/sync flow.
  - **DEPENDENCY**: Requires Task 11.2.1 (NetworkMonitor) to be completed first.
- **Affected files**: `ui/SplashScreen.kt`, `viewmodel/AuthViewModel.kt`, `MainActivity.kt`
- **Verification**: Open app (online) → verify real sync progress with step labels. Open app (offline) → verify blocking message appears. Kill app → re-open → verify data is present after sync completes.
- **Completion Notes**: Created `SplashViewModel` to manage the exact 8 sync steps via `SyncStage` enum. Updated `SplashScreen` to observe and display these steps, block if offline, and provide retry mechanics. Stripped data syncing from `AuthViewModel.kt`. Updated `MainActivity` navigation logic.

---

#### Sub-Phase 11.3: Generator & Ticket Pipeline Fixes
*(Priority: HIGH — Core workflow improvements)*

---

#### Task 11.3.1: Remove Manual Input & File Import from Setup Database
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: User wants to remove the ManagementScreen (Setup Database) entirely because the manual input and CSV import features are too complex for non-technical users. The ONLY way to add tickets to the database should be through Generator Mode Kuota (which auto-generates and auto-inserts).
  - **IMPLEMENTATION — Step 1: Remove ManagementScreen route from MainActivity**:
    - In `MainActivity.kt`, delete the `composable("management") { ManagementScreen(...) }` route block (lines 127-136).
    - Remove the `import com.tkrz.qrtix.ui.ManagementScreen` import statement.
  - **IMPLEMENTATION — Step 2: Remove navigation to ManagementScreen from DashboardScreen**:
    - In `DashboardScreen.kt`, remove the `onNavigateToManagement` parameter.
    - Remove the "Setup/Import" button that navigates to `management`.
    - In `MainActivity.kt`, remove `onNavigateToManagement = { navController.navigate("management") }` from the `DashboardScreen` composable call.
  - **IMPLEMENTATION — Step 3: Remove unused ViewModel methods**:
    - In `TicketViewModel.kt`, remove or mark as `@Deprecated`:
      - `addTicketsFromText()` (line 429-497) — CSV text parsing.
      - `addTicketsFromTwoBoxes()` (line 579-648) — Two-box manual input.
      - `importCsvFromUri()` (line 500-514) — URI-based CSV import.
    - Keep `insertBatchTickets()` as it's used by the Generator.
  - **IMPLEMENTATION — Step 4: Clean up ManagementScreen file**:
    - Delete `ui/ManagementScreen.kt` entirely.
    - Check if `NumberedInputBox.kt` is used anywhere else (search for `NumberedInputBox` in all `.kt` files). If only used by `ManagementScreen`, delete it too.
  - **IMPLEMENTATION — Step 5: Clean up references**:
    - Search the entire project for `"management"`, `ManagementScreen`, `onNavigateToManagement`, `addTicketsFromText`, `addTicketsFromTwoBoxes`, `importCsvFromUri`, `NumberedInputBox` — remove ALL remaining references.
  - **CROSS-SYSTEM WARNING**: 
    - The `GeneratorScreen` still has a "Mode CSV" tab that allows users to paste/import a list of existing ticket codes and generate QR images for them (WITHOUT inserting to database). This mode should be KEPT because it serves a different purpose (generating images for pre-existing codes).
    - The `EventBadge` and confirmation dialogs in `ManagementScreen` referenced in Task 8.3 completion notes will be removed with the screen.
    - After removing ManagementScreen, the "Belum punya kode QR? Buat di sini" button (line 183-203 in ManagementScreen) that navigates to Generator is no longer needed.
- **Affected files**: `ui/ManagementScreen.kt (DELETE)`, `ui/NumberedInputBox.kt (POSSIBLY DELETE)`, `ui/DashboardScreen.kt`, `MainActivity.kt`, `viewmodel/TicketViewModel.kt`
- **Verification**: Build project → verify no compilation errors. Navigate all screens → verify no dead links or references to ManagementScreen. Search codebase for "management" → verify zero results.
- **Completion Notes**: Removed `ManagementScreen.kt` and its routes from `MainActivity.kt`. Removed `onNavigateToManagement` and its button in `DashboardScreen.kt`. Deleted unused methods `addTicketsFromText`, `importCsvFromUri`, and `addTicketsFromTwoBoxes` in `TicketViewModel.kt`. Kept `NumberedInputBox.kt` since it is used in `GeneratorScreen.kt`.

---

#### Task 11.3.2: Auto-Insert Tickets to Database from Quota Mode (Remove directInsert Checkbox)
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: Since Quota Mode is now the ONLY way to insert tickets into the database (after Task 11.3.1 removes manual input), the `directInsert` checkbox in `GeneratorScreen` is redundant — tickets should ALWAYS be inserted.
  - **IMPLEMENTATION**:
    1. In `GeneratorScreen`, remove the `directInsert` checkbox state and its related UI.
    2. After ticket generation completes, ALWAYS call `viewModel.insertBatchTickets(generatedTickets)`.
    3. Update the pre-generation confirmation dialog to clearly state: "Akan men-generate X tiket [CATEGORY] untuk event [EVENT_NAME] dan otomatis memasukkannya ke database."
    4. After successful generation AND insertion, show a summary:
       - "✅ Berhasil men-generate X tiket"
       - "✅ X tiket ditambahkan ke database"
       - "Cadangkan gambar QR ke Drive?" button (triggers backup flow).
    5. Also add a history log entry for the generation: `action = "Generate"`, `description = "Generate X tiket [CATEGORY] untuk event [EVENT_NAME]"`.
  - **DEPENDENCY**: Task 11.3.1 must be done first (so manual input is removed and Quota Mode is the only entry point).
  - **CROSS-SYSTEM WARNING**: After this change, every Quota Mode generation creates database entries. This means `DatabaseScreen` ticket list will grow with each generation. Ensure the UI handles large lists efficiently (the existing `LazyColumn` should be fine).
- **Affected files**: `ui/GeneratorScreen.kt`, `viewmodel/TicketViewModel.kt`
- **Verification**: Generate 20 tickets → verify all 20 appear in DatabaseScreen immediately. Check Google Sheets → verify all 20 rows exist. Check History Log → verify "Generate" entry appears.
- **Completion Notes**: Removed `directInsert` from `GeneratorScreen.kt`. Updated `executeGeneration` to unconditionally insert tickets into the database if the user is in the "Mode Kuota" tab (`selectedTabIndex == 1`). Updated the confirmation and finish dialogs to reflect auto-insertion. Added history logging inside `insertBatchTickets` for generated tickets.

---

#### Task 11.3.3: Fix Prefix in Mode Kuota — Use Event Code Automatically
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: In `GeneratorScreen` Mode Kuota, there is a free-text "Prefix" input field. The user reports that the prefix value is incorrectly stored as the event name in the database, instead of using the event's `eventCode` field. The `Event.eventCode` field exists on the entity (default "EVNT1") but there's no input field for it when creating events (fixed in Task 11.3.5).
  - **IMPLEMENTATION**:
    1. In `GeneratorScreen` Mode Kuota, remove the free-text "Prefix" input field.
    2. Replace it with a **read-only display** showing the active event's `eventCode`:
       ```kotlin
       OutlinedTextField(
           value = activeEvent?.eventCode ?: "N/A",
           onValueChange = {},
           label = { Text("Kode Event (Prefix)") },
           readOnly = true,
           enabled = false,
           modifier = Modifier.fillMaxWidth()
       )
       ```
    3. In the `TicketFormatters.formatTicketCode()` call, always pass `activeEvent.eventCode` as the `prefix` parameter instead of the user's free-text input.
    4. Add a small helper text below the field: "Prefix diambil dari Kode Event yang sudah ditetapkan saat membuat profil event."
  - **DEPENDENCY**: Task 11.3.5 (Event Code Input) should ideally be done first or simultaneously, so that newly created events have a meaningful `eventCode` instead of the default "EVNT1".
- **Affected files**: `ui/GeneratorScreen.kt`
- **Verification**: Open Generator Mode Kuota → verify prefix field shows active event's `eventCode` and is NOT editable. Generate a ticket → verify the QR code format is `[EVENT_CODE]-[CATEGORY]-[NNNN]-[RAND]`.
- **Completion Notes**: Replaced the mutable `quotaPrefix` with a read-only `activeEvent?.eventCode` in `GeneratorScreen.kt`. Changed `OutlinedTextField` to be read-only and disabled, preventing any user modification. Added the warning text below it.

---

#### Task 11.3.4: Inline Category Add in Generator Screen
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: User wants the ability to add a new category directly from the Generator screen, similar to YouTube's "Create new playlist" option during video upload. Currently, categories can only be managed from the `CategoryManagementDialog` accessed via DashboardScreen.
  - **IMPLEMENTATION**:
    1. In `GeneratorScreen` Mode Kuota, modify the Category dropdown to include an extra item at the bottom of the list: "+ Tambah Kategori Baru".
    2. When this item is selected, open `CategoryManagementDialog` (or a lightweight inline version that only shows the "Add" form).
    3. After adding a new category, auto-select it in the dropdown.
    4. The category field MUST be required — disable the "Generate" button if no category is selected.
    5. Show the category's full name in the dropdown but use the `categoryCode` internally for ticket generation.
  - **CROSS-SYSTEM WARNING**: `CategoryManagementDialog` currently supports Add, Edit, and Delete. In the generator's inline context, only "Add" is needed. Consider either:
    - Option A: Reuse the full dialog (user can also edit/delete from generator).
    - Option B: Show a simplified "Add only" mini-dialog.
    - Recommended: Option A (reuse full dialog) for consistency.
- **Affected files**: `ui/GeneratorScreen.kt`, `ui/CategoryManagementDialog.kt`
- **Verification**: Open Generator Mode Kuota → tap Category dropdown → verify "+ Tambah Kategori Baru" option exists. Tap it → add a new category → verify it's auto-selected in the dropdown.
- **Completion Notes**: Added a `showCategoryDialog` state to `GeneratorScreen.kt`. Added a `DropdownMenuItem` with "+ Tambah Kategori Baru" which triggers `CategoryManagementDialog`. Upon addition, `quotaCategory` is automatically set to the new category's code.

---

#### Task 11.3.5: Add Event Code Input When Creating New Profile Event
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: Confirmed that `EventSelectionDialog` has NO input field for `eventCode`. The `createAndSwitchEvent()` in `TicketViewModel` (line 277-296) creates events with `Event(name = trimmedName)` which defaults to `eventCode = "EVNT1"` for ALL events. This means all events share the same prefix, making QR codes from different events indistinguishable.
  - **IMPLEMENTATION**:
    1. In `EventSelectionDialog`, in the "Buat Event Baru" dialog (line 94-133), add an `eventCode` input field:
       ```kotlin
       OutlinedTextField(
           value = newEventCode,
           onValueChange = { newEventCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
           label = { Text("Kode Event (ID Singkatan)") },
           placeholder = { Text("Cth: DCD, KONSR, FEST24") },
           supportingText = { Text("2-6 karakter alfanumerik. Tidak dapat diubah setelah disimpan.") },
           isError = newEventCode.length < 2 || newEventCode.length > 6,
           singleLine = true,
           keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
           modifier = Modifier.fillMaxWidth()
       )
       ```
    2. Add a warning text below the input: "⚠️ Kode event akan menjadi prefix pada semua kode QR tiket dan tidak dapat diubah."
    3. Validation rules:
       - 2-6 characters, uppercase, alphanumeric only (no spaces, no special characters).
       - Must be unique across ALL events (check both local DB and cloud).
       - Block the "Simpan" button if validation fails.
    4. Update `TicketViewModel.createAndSwitchEvent()` to accept `eventCode` parameter:
       ```kotlin
       fun createAndSwitchEvent(name: String, eventCode: String) {
           // ... validate uniqueness ...
           val newId = eventRepository.insertEvent(Event(name = trimmedName, eventCode = trimmedCode))
           // ...
       }
       ```
    5. Cross-check against Google Sheets `Events` sheet column F (`eventCode`) for duplicate validation:
       - Read `Events!F2:F` from Sheets.
       - If any row matches the new `eventCode` (case-insensitive), show error: "Kode event sudah digunakan oleh event lain!"
    6. In `EventSelectionDialog`'s edit dialog (line 160-220), show `eventCode` as **read-only** — it cannot be changed after creation:
       ```kotlin
       OutlinedTextField(
           value = eventToEdit!!.eventCode,
           onValueChange = {},
           label = { Text("Kode Event (Permanen)") },
           readOnly = true,
           enabled = false,
           modifier = Modifier.fillMaxWidth()
       )
       ```
  - **CROSS-SYSTEM WARNING**: Changing the event code affects:
    - `TicketFormatters.formatTicketCode()` prefix.
    - `processQrCode()` cross-event prefix validation (line 660).
    - Scanner cross-event detection.
    - All existing events will still have `eventCode = "EVNT1"` — existing data is NOT affected, but users should be encouraged to create new events with proper codes.
- **Affected files**: `ui/EventSelectionDialog.kt`, `viewmodel/TicketViewModel.kt`, `data/repository/EventRepository.kt`
- **Verification**: Create a new event → enter code "DCD" → verify it's saved. Try creating another event with code "DCD" → verify duplicate error. Open edit dialog → verify event code is read-only. Generate tickets → verify QR code prefix uses "DCD".
- **Completion Notes**: Added `newEventCode` to `EventSelectionDialog.kt`'s creation dialog. Updated `TicketViewModel.createAndSwitchEvent()` to accept `eventCode`. Added `checkEventCodeExistsInCloud()` to `EventRepository.kt` to query `Events!F2:F` for existing event codes across the cloud database. Modified `DashboardScreen.kt` to pass `eventCode` appropriately.

---

#### Task 11.3.6: Add Category Code Validation Limits
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **CONTEXT**: The `CategoryManagementDialog` allows free-text input for category codes with no length or character restrictions beyond uppercase conversion. Per PROJECT_DOCUMENTATION.md, category codes should follow strict formatting rules.
  - **IMPLEMENTATION**:
    1. In `CategoryManagementDialog` (line 112-121), add input filtering and validation to the category code field:
       ```kotlin
       OutlinedTextField(
           value = newCatCode,
           onValueChange = { newCatCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
           label = { Text("ID Singkatan (Cth: VIP, RGLR, VVIP)") },
           supportingText = { Text("2-6 karakter alfanumerik. Tidak dapat diubah setelah disimpan.") },
           isError = newCatCode.isNotEmpty() && (newCatCode.length < 2 || newCatCode.length > 6),
           singleLine = true,
           keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
           modifier = Modifier.fillMaxWidth()
       )
       ```
    2. Disable the "Simpan" button if `newCatCode.length < 2 || newCatCode.length > 6`.
    3. Category code must be unique **per event** (already enforced in `TicketViewModel.addCategory()` line 224, but add visual feedback — show inline error on the text field).
    4. In `TicketViewModel.addCategory()`, also add server-side validation: check against cloud data to prevent duplicates across devices.
  - **CROSS-SYSTEM WARNING**: Category codes are embedded in QR ticket codes (format: `[PREFIX]-[CATEGORY]-[NNNN]-[RAND]`). Changing validation rules does NOT affect existing categories — it only applies to newly created ones.
- **Affected files**: `ui/CategoryManagementDialog.kt`, `viewmodel/TicketViewModel.kt`
- **Verification**: Try adding a category with code "A" (too short) → verify error. Try "ABCDEFG" (too long) → verify truncated to 6. Try "VIP!" (special char) → verify exclamation mark is filtered. Try duplicate code → verify error message.
- **Completion Notes**: Added robust UI validation in `CategoryManagementDialog.kt` to enforce 2-6 alphanumeric uppercase characters and blocked the "Simpan" button if invalid. Updated `TicketViewModel.addCategory()` and `CategoryRepository.kt` to include cloud-level uniqueness validation for the category code per event.

---

#### Sub-Phase 11.4: QR Editor & Exporter Fixes
*(Priority: HIGH — Affects QR output quality)*

---

#### Task 11.4.1: Fix QR Size Mismatch — WYSIWYG Editor with HRI Text
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **ROOT CAUSE**: `TicketEditorScreen` uses a fixed 150.dp Box for the QR preview, while `TicketExporter` calculates QR size as `outWidth * 0.4f * qrScale`. The coordinate systems are completely different — the editor uses screen pixels/dp while the exporter uses image pixels with a ratio of `outWidth / 1080f`. Additionally, the editor does NOT show the HRI text (human-readable ticket code printed below the QR), but the exporter renders it, causing further visual mismatch.
  - **IMPLEMENTATION**:
    1. Rewrite `TicketEditorScreen` to use a proportional rendering system:
       - Display the background image (if exists) at its natural aspect ratio within the screen bounds.
       - Calculate the scale factor between the screen display size and the actual image size: `displayRatio = displayWidth / actualImageWidth`.
       - Render the QR preview at size `actualImageWidth * 0.4 * qrScale * displayRatio` — this ensures the QR appears at the same proportional size as in the final export.
       - Map drag/pinch gestures to the actual coordinate space (divide screen coordinates by `displayRatio` before storing to `qrX`, `qrY`).
    2. Add HRI text preview below the QR box in the editor:
       - Show a dummy ticket code (e.g., `"DCD-VIP-0001-A2B4"`) in the same relative font size as the exporter uses (`qrHeight * 0.1f`).
       - Include the text padding area (`qrHeight * 0.25f`) in the QR preview box height.
    3. Add a **"Preview Hasil"** button that:
       - Generates a single sample ticket image using the exact `TicketExporter` logic (same QR size, same text, same coordinates).
       - Displays it in a full-screen dialog so the user can see exactly what the final output looks like.
       - Use a dummy ticket code for the preview.
  - **CROSS-SYSTEM WARNING**: The `qrX`, `qrY`, `qrScale`, `qrRotation` values stored in the `Event` entity are used by BOTH the editor and exporter. After this fix, both will use the same coordinate system, so existing saved values may render differently. Consider adding a migration note or reset button.
- **Affected files**: `ui/TicketEditorScreen.kt`, `utils/TicketExporter.kt`
- **Verification**: Position QR in editor → tap "Preview Hasil" → verify the preview matches the editor layout. Generate actual tickets → verify the output matches both the editor and preview.

---

#### Task 11.4.2: Add Editor Tools (Toolbar + Numeric Inputs + Guide Lines)
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **IMPLEMENTATION**:
    1. Add a bottom toolbar with icon buttons:
       - **Center QR**: Resets `qrX = 0f, qrY = 0f` (centers QR on canvas).
       - **Reset All**: Resets `qrX = 0f, qrY = 0f, qrScale = 1f, qrRotation = 0f`.
       - **Zoom In**: Increases `qrScale` by 0.1f (max 3.0f).
       - **Zoom Out**: Decreases `qrScale` by 0.1f (min 0.3f).
       - **Rotate 90°**: Adds 90f to `qrRotation` (wraps at 360f).
    2. Add a collapsible panel (expand/collapse via chevron icon) showing numeric input fields:
       - X Position (float), Y Position (float), Scale (float), Rotation (degrees).
       - Changing these values directly updates the canvas preview in real-time.
    3. Add guide lines on the canvas:
       - Center crosshair (vertical + horizontal dashed lines through canvas center).
       - QR center dot (small circle at QR's current position).
       - Toggle guide lines on/off via a toolbar button.
  - **DEPENDENCY**: Task 11.4.1 must be completed first (WYSIWYG coordinate system).
- **Affected files**: `ui/TicketEditorScreen.kt`
- **Verification**: Tap "Center QR" → verify QR moves to center. Tap "Reset" → verify all values reset. Enter X=100 in numeric input → verify QR moves. Toggle guide lines → verify crosshair appears/disappears.

---

#### Task 11.4.3: Default Template for Tickets Without Custom Background
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **CONTEXT**: When an event has no custom background (`bgPath == null`), the exporter creates a plain white canvas (1000x1200). When the event has no custom logo (`logoPath == null`), no logo is overlaid on the QR. User wants a built-in default template and default logo.
  - **IMPLEMENTATION**:
    1. Create `utils/DefaultTemplateRenderer.kt` with a method:
       ```kotlin
       fun renderDefaultTemplate(
           eventName: String,
           categoryName: String,
           width: Int = 1080,
           height: Int = 1440
       ): Bitmap
       ```
       The template should include:
       - Gradient background: soft lavender (#F8F8FF) to light purple (#E8E5F4).
       - Event name as header text (centered, bold, large font).
       - Category badge (rounded rectangle with category name).
       - Subtle border (thin line, 2px, primary color).
       - "QRTix" watermark in bottom-right corner (small, semi-transparent).
    2. In `TicketExporter`, when `event.bgPath` is null:
       - Use `DefaultTemplateRenderer.renderDefaultTemplate()` instead of a blank white canvas.
    3. When `event.logoPath` is null:
       - Use the QRTix app icon (`R.mipmap.ic_launcher_foreground`) as the default logo overlay on the QR code.
       - Decode the app icon from resources: `BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)`.
    4. Pass the `Context` to `TicketExporter` (it already has it via constructor) to access resources.
  - **CROSS-SYSTEM WARNING**: The `TicketEditorScreen` should also show the default template when `bgPath == null`, so the editor preview matches the export output.
- **Affected files**: `utils/DefaultTemplateRenderer.kt (NEW)`, `utils/TicketExporter.kt`, `ui/TicketEditorScreen.kt`
- **Verification**: Create an event without logo or background → generate tickets → verify the output uses the default template with gradient background and QRTix logo.

---

#### Sub-Phase 11.6: Cloud Architecture & Backup Improvements
*(Priority: HIGH — Data reliability improvements)*

---

#### Task 11.6.1: Restructure Google Drive Folder Layout
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: Current Drive folder structure is flat: `QRTix/[Event_Name]/QR Images/[Category]/`. User wants a more organized structure with System and Profiles separation.
  - **TARGET STRUCTURE**:
    ```
    QRTix/
    ├── System/
    │   └── QRTix_Data (spreadsheet)
    ├── Profiles/
    │   ├── [Event_Name_1]/
    │   │   ├── Logo/
    │   │   ├── Design/
    │   │   ├── QR_Images/
    │   │   │   ├── [Category_1]/
    │   │   │   └── [Category_2]/
    │   │   └── QRTix.data (JSON metadata file)
    │   └── [Event_Name_2]/
    │       └── ...
    ```
  - **IMPLEMENTATION**:
    1. Update `SpreadsheetManager.initializeSpreadsheet()`:
       - Create `System/` subfolder under `QRTix/`.
       - Move/create `QRTix_Data` spreadsheet inside `System/`.
       - Create `Profiles/` subfolder under `QRTix/`.
       - Store new folder IDs in `CloudPreferences`: `systemFolderId`, `profilesFolderId`.
    2. Update `BackgroundUploadManager.processQueue()`:
       - Change folder creation path from `QRTix/[Event]/QR Images/[Category]` to `QRTix/Profiles/[Event]/QR_Images/[Category]`.
       - Use `profilesFolderId` as the parent instead of `rootId`.
    3. Update `MediaManager`:
       - Upload logos to `QRTix/Profiles/[Event]/Logo/`.
       - Upload backgrounds to `QRTix/Profiles/[Event]/Design/`.
    4. Create `QRTix.data` per event profile — a JSON file containing:
       ```json
       {
           "eventName": "Konser Rock",
           "eventCode": "KR",
           "createdAt": 1234567890,
           "ticketCount": 500,
           "categories": ["VIP", "REGULER"],
           "lastSyncAt": 1234567890
       }
       ```
       Write this file to `QRTix/Profiles/[Event]/QRTix.data` after each event update.
    5. Update `CloudPreferences` to store the new folder IDs.
    6. **Backward compatibility**: If the old folder structure exists (detected by finding `QRTix_Data` directly under `QRTix/` instead of `QRTix/System/`), perform a one-time migration:
       - Create `System/` and `Profiles/` subfolders.
       - Move `QRTix_Data` into `System/`.
       - Move existing event folders into `Profiles/`.
  - **CROSS-SYSTEM WARNING**: This is the most impactful cloud change. ALL cloud operations are affected. Must be tested very carefully. The migration path from old to new structure is critical — if done wrong, existing users lose access to their data.
- **Affected files**: `data/cloud/SpreadsheetManager.kt`, `data/cloud/BackgroundUploadManager.kt`, `data/cloud/MediaManager.kt`, `data/cloud/DriveFolderManager.kt`, `data/cloud/CloudPreferences.kt`
- **Verification**: New install → verify new folder structure created. Existing install → verify migration preserves all data. Upload QR images → verify they go to `Profiles/[Event]/QR_Images/[Category]/`.

---

#### Task 11.6.2: Validate Event Name Uniqueness Against Cloud
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **CONTEXT**: Currently, event name uniqueness is only checked against local DB (`allEvents.value.any { it.name.equals(...) }`). If two devices create events with the same name before syncing, duplicates occur.
  - **IMPLEMENTATION**:
    1. In `TicketViewModel.createAndSwitchEvent()`, before creating a new event:
       - Read `Events!B2:B` (name column) from Google Sheets.
       - Check if any row matches the new name (case-insensitive).
       - If duplicate found in cloud, show error: "Nama event sudah digunakan di perangkat lain!"
    2. In `TicketViewModel.updateEventName()`, same cloud validation before renaming.
    3. Also validate against Google Drive folder names under `Profiles/`:
       - Use `DriveService.findFileByName(eventName, "application/vnd.google-apps.folder", profilesFolderId)`.
       - If folder exists, show error.
  - **DEPENDENCY**: Task 11.6.1 (folder restructure) should be done first so the `profilesFolderId` is available.
- **Affected files**: `viewmodel/TicketViewModel.kt`, `data/repository/EventRepository.kt`
- **Verification**: Create event "Test" on device A. On device B (before sync), try creating event "Test" → verify error message.

---

#### Task 11.6.3: Backup Detail Popup with Failed Upload Retry
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **CONTEXT**: User cannot see backup details — no list of what was uploaded, what failed, or why it failed. The only visible feedback is a progress bar on the dashboard.
  - **IMPLEMENTATION**:
    1. Create `ui/BackupDetailDialog.kt` — a large dialog (or bottom sheet) showing:
       - **Summary header**: "Pencadangan: X/Y berhasil, Z gagal"
       - **Progress bar** (if uploading is in progress)
       - **Scrollable list** of files with status icons:
         - ✅ Successfully uploaded files (file name + upload time)
         - ❌ Failed files (file name + error reason)
         - ⏳ Pending files (file name)
       - For each failed file: individual "Coba Lagi" button.
       - **"Retry Semua Gagal"** button at the bottom (retries all failed uploads).
    2. Update `BackgroundUploadManager` to:
       - Track each upload's result: `data class UploadResult(val task: UploadTask, val status: UploadResultStatus, val errorMessage: String?)`.
       - Store results in a `StateFlow<List<UploadResult>>`.
       - Expose `retryFailed()` method.
    3. Add a "Pencadangan" item in the sidebar (Task 11.5.1) that opens `BackupDetailDialog`.
    4. If no backup has been done yet, show a message: "Belum ada pencadangan. Generate tiket terlebih dahulu."
  - **DEPENDENCY**: Task 11.1.3 (fix upload manager) must be done first so the tracking data is available.
- **Affected files**: `ui/BackupDetailDialog.kt (NEW)`, `data/cloud/BackgroundUploadManager.kt`, `ui/DashboardScreen.kt`
- **Verification**: Generate tickets → trigger backup → open backup detail → verify file list with statuses. Force-fail one upload → verify it appears as ❌ with error message. Tap "Coba Lagi" → verify retry works.

---

#### Task 11.6.4: Full Profile Sync on Login/App Launch
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **CONTEXT**: User reports profiles disappearing after login. The current sync in `AuthViewModel.checkAuthStatus()` only syncs events and history logs — it does NOT download logos, backgrounds, or sync categories for all events.
  - **IMPLEMENTATION**:
    1. Create a comprehensive sync flow (used by Task 11.2.3 Splash Screen):
       ```
       Step 1: syncEventsFromCloud() — get all events
       Step 2: For each event, syncCategoriesFromCloud(eventId) — get all categories
       Step 3: syncTicketsFromCloud(activeEventId) — get tickets for active event only (performance)
       Step 4: For each event with logoPath/bgPath, MediaManager.getOrDownloadMedia(fileId) — download missing media
       Step 5: syncLogsFromCloud() — sync history logs
       ```
    2. Move this logic from `AuthViewModel` to a new `SyncManager` utility class (or keep in splash screen LaunchedEffect if simpler).
    3. Report progress for each step (used by splash screen progress bar).
    4. Handle errors gracefully: if a single step fails, continue with the next step and report the error.
  - **DEPENDENCY**: Task 11.1.1 (fix sync safety) must be done first.
  - **CROSS-SYSTEM WARNING**: Downloading media for ALL events on every login could be slow if there are many events with large images. Consider downloading media lazily (only when the user views that event) instead of eagerly on startup. Or download thumbnails only and full images on demand.
- **Affected files**: `viewmodel/AuthViewModel.kt` (or `utils/SyncManager.kt` NEW), `ui/SplashScreen.kt`, `data/cloud/MediaManager.kt`
- **Verification**: Create 3 events with logos on device A. Login on device B → verify all 3 events sync with their logos. Verify splash screen shows progress for each step.

---

#### Sub-Phase 11.5: UI/UX Overhaul
*(Priority: MEDIUM — Improves user experience)*

---

#### Task 11.5.1: Dashboard Sidebar + Copyright Text
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **IMPLEMENTATION**:
    1. Wrap `DashboardScreen`'s `Scaffold` with `ModalNavigationDrawer`:
       ```kotlin
       val drawerState = rememberDrawerState(DrawerValue.Closed)
       ModalNavigationDrawer(
           drawerState = drawerState,
           drawerContent = { SidebarContent(...) }
       ) {
           Scaffold(...) { ... }
       }
       ```
    2. Create `SidebarContent` composable with sections:
       - **Header**: User Google avatar (circular), display name, email. Use data from `AuthPreferences` or `AuthViewModel`.
       - **Navigation Items** (each with icon + label):
         - 🏠 Dashboard (highlight when active)
         - 📱 Scanner
         - 🎲 Generator Tiket
         - 📊 Database Tiket
         - 🎨 Desain Tiket
         - 📧 Distribusi Tiket
         - 💾 Pencadangan (backup)
         - 👤 Profil Event (new screen from Task 11.5.3)
         - ⚙️ Kelola Kategori
         - 📖 Panduan
       - **Divider**
       - **Logout**: "Keluar Akun" button with red icon at the bottom.
       - **Copyright**: "© 2026 QRTix by Takarize" — centered, small gray text at the very bottom.
    3. Add a hamburger menu icon (`Icons.Default.Menu`) in the Dashboard's `TopAppBar` to open the sidebar.
    4. Each navigation item calls `navController.navigate(route)` and closes the drawer.
  - **DEPENDENCY**: Task 11.5.2 (Logout) and Task 11.5.8 (Guide) should be done before or simultaneously so the sidebar items are functional.
  - **CROSS-SYSTEM WARNING**: Adding a sidebar requires passing additional navigation callbacks or the `NavController` to `DashboardScreen`. Currently, `DashboardScreen` only receives individual `onNavigateToX` lambdas. Consider restructuring to pass all navigation callbacks as a single object, or pass the `NavController` directly (though this breaks the unidirectional data flow pattern — use with caution).
- **Affected files**: `ui/DashboardScreen.kt`, `MainActivity.kt`
- **Verification**: Open dashboard → tap hamburger icon → verify sidebar opens with all items. Tap each item → verify it navigates correctly. Verify copyright text at bottom.

---

#### Task 11.5.2: Add Logout / Switch Account Feature
- **Status**: [x]
- **Priority**: High
- **Description**:
  - **IMPLEMENTATION**:
    1. In `AuthViewModel`, update `signOut()` method to clear ALL cached data:
       ```kotlin
       fun signOut() {
           authPreferences.setSignedIn(false, null)
           authPreferences.setHasSeenOnboarding(false) // Optional: reset onboarding for new account
           cloudPreferences.clear() // Clear spreadsheetId, folderId, mediaFolderId
           _authState.value = AuthState.Idle
       }
       ```
    2. Add a `clear()` method to `CloudPreferences` that resets all stored IDs.
    3. In the sidebar's "Keluar Akun" button, show a confirmation dialog:
       - Title: "Keluar dari Akun"
       - Text: "Apakah Anda yakin ingin keluar? Data lokal akan tetap tersimpan di cloud."
       - Confirm: "Keluar" → call `authViewModel.signOut()` → navigate to `"login"` with `popUpTo(0)` to clear entire back stack.
       - Cancel: "Batal"
    4. Inject `AuthViewModel` into `DashboardScreen` or pass the `signOut` callback through navigation.
  - **CROSS-SYSTEM WARNING**: After logout, the app should navigate to `LoginScreen` and clear the entire navigation back stack. The user should NOT be able to press back to return to the dashboard. Use: `navController.navigate("login") { popUpTo(0) { inclusive = true } }`.
- **Affected files**: `viewmodel/AuthViewModel.kt`, `data/cloud/CloudPreferences.kt`, `ui/DashboardScreen.kt`, `MainActivity.kt`
- **Verification**: Tap "Keluar Akun" → confirm → verify redirect to login screen. Press back → verify cannot go back to dashboard. Login with same/different account → verify data loads correctly.

---

#### Task 11.5.3: Redesign Workspace Edit → Event Profile Page
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **IMPLEMENTATION**:
    1. Create `ui/EventProfileScreen.kt` as a full-screen page with sections:
       - **Header**: Large event logo (circular, 120dp, tap to change), event name (large text, tap to edit inline), event code badge (read-only chip).
       - **Design Section**: Background image thumbnail (tap to change), "Edit Posisi QR" button (navigates to TicketEditorScreen).
       - **Statistics Section**: Cards showing — Total Tickets, Scanned Count, Category Count.
       - **Categories Section**: List of categories with inline Edit (name only) and Add button. Same functionality as `CategoryManagementDialog` but embedded in the page.
       - **Danger Zone Section**: Red-bordered card with "Hapus Event" button. Double confirmation: first tap shows warning dialog, confirm deletes.
    2. Add navigation route `"event_profile"` in `MainActivity.kt`.
    3. `EventSelectionDialog` remains for **selecting/switching** events. Add an "Edit" button on each event row that navigates to `EventProfileScreen` (instead of opening the inline edit dialog).
    4. Remove the inline edit dialog from `EventSelectionDialog` (lines 160-220) — editing is now done in `EventProfileScreen`.
  - **CROSS-SYSTEM WARNING**: `EventSelectionDialog` is opened from `DashboardScreen`. The new `EventProfileScreen` should receive the event ID as a navigation argument: `composable("event_profile/{eventId}") { ... }`. Parse the argument and load the event from the ViewModel.
- **Affected files**: `ui/EventProfileScreen.kt (NEW)`, `ui/EventSelectionDialog.kt`, `ui/DashboardScreen.kt`, `MainActivity.kt`
- **Verification**: Tap event → open profile page → verify all sections display correctly. Change logo → verify it updates. Change name → verify it updates in dashboard.

---

#### Task 11.5.4: Allow Default Event Deletion + Empty State
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - **CONTEXT**: Currently, Event with `id = 1` cannot be deleted (hardcoded guard in `deleteEvent()` line 400: `if (id != 1L && id != activeEventId.value)`). User wants ALL events to be deletable. When no events exist, show an empty state.
  - **IMPLEMENTATION**:
    1. In `TicketViewModel.deleteEvent()`, remove the `id != 1L` guard. Change to:
       ```kotlin
       fun deleteEvent(id: Long) {
           if (id == activeEventId.value) {
               showErrorToast("Tidak bisa menghapus event yang sedang aktif. Pindah ke event lain terlebih dahulu.")
               return
           }
           viewModelScope.launch(Dispatchers.IO) { ... }
       }
       ```
    2. In `EventSelectionDialog`, remove the `if (event.id != 1L)` condition on line 75 that hides the delete button. Show delete button for ALL events except the currently active one.
    3. In `DashboardScreen`, observe `allEvents` StateFlow. When `allEvents.value.isEmpty()`:
       - Show an empty state UI:
         - Large illustration icon (e.g., folder + plus icon).
         - Text: "Belum ada profil event. Buat profil event pertama Anda untuk mulai menggunakan QRTix."
         - "Buat Event Baru" button (opens the create event dialog).
       - Hide/disable all other navigation buttons (Scanner, Generator, Database, etc.) because they require an active event.
    4. In `TicketViewModel.init {}`, handle the case where `eventRepository.getEventById(id)` returns null AND there are zero events in the DB (don't auto-create a default event anymore — let the user create one explicitly).
  - **CROSS-SYSTEM WARNING**: Removing the hardcoded default event changes the app's initial state for new users. New users will see the empty state and MUST create an event before doing anything. This is a significant UX change from the current behavior where a "Event Default" is auto-created.
- **Affected files**: `viewmodel/TicketViewModel.kt`, `ui/EventSelectionDialog.kt`, `ui/DashboardScreen.kt`
- **Verification**: Delete all events → verify empty state appears. Create a new event → verify it becomes active. Delete the last remaining event → verify empty state appears again.

---

#### Task 11.5.5: Navigation Animations — Slide Transitions
- **Status**: [x]
- **Priority**: Low
- **Description**:
  - **IMPLEMENTATION**:
    1. In `MainActivity.kt`, add transition animations to the `NavHost`:
       ```kotlin
       NavHost(
           navController = navController,
           startDestination = "splash",
           enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) },
           exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) },
           popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) },
           popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) }
       ) { ... }
       ```
    2. This gives a professional slide-left for forward navigation and slide-right for backward navigation, with a 250ms duration.
    3. Exception: Splash → Login/MainMenu should use a fade transition instead of slide (it's not a "navigation" in the traditional sense). Override per-route:
       ```kotlin
       composable("splash",
           exitTransition = { fadeOut(tween(300)) }
       ) { ... }
       ```
    4. Add the required import: `import androidx.compose.animation.*`
  - **CROSS-SYSTEM WARNING**: The navigation animation library may need to be added to `build.gradle.kts` if not already present: `implementation("androidx.navigation:navigation-compose:2.7.x")` (check current version in build file). The animations require `AnimatedNavHost` or the animations parameter on `NavHost` — verify which API is available in the project's navigation-compose version.
- **Affected files**: `MainActivity.kt`, potentially `app/build.gradle.kts`
- **Verification**: Navigate forward (Dashboard → Scanner) → verify slide-left animation. Navigate back → verify slide-right animation. Splash → Main Menu → verify fade transition.

---

#### Task 11.5.6: Fix Button Color Consistency Across All Screens
- **Status**: [x]
- **Priority**: Low
- **Description**:
  - **IMPLEMENTATION**:
    1. Audit all screens and create a consistent button styling guide:
       - Primary `Button`: use `MaterialTheme.colorScheme.primary` (enabled), `primary.copy(alpha = 0.38f)` (disabled).
       - Secondary `OutlinedButton`: consistent border color and text color.
       - Destructive `TextButton`: use `MaterialTheme.colorScheme.error` for dangerous actions.
    2. Create a `ui/theme/QrtixTheme.kt` (or update existing theme file) with custom `ButtonDefaults` if needed.
    3. Review and fix button states in: `DashboardScreen`, `GeneratorScreen`, `DatabaseScreen`, `DistributionScreen`, `ScannerScreen`.
  - This is a minor polish task — no functional changes, only visual consistency.
- **Affected files**: All UI screen files, `ui/theme/` directory
- **Verification**: Visual inspection of all screens — buttons should look consistent in enabled/disabled/pressed states.

---

#### Task 11.5.7: Fix DatabaseScreen Header Text Truncation
- **Status**: [x]
- **Priority**: Low
- **Description**:
  - **CONTEXT**: The "List Database Lengkap" header text in `DatabaseScreen` is cut off on smaller screens.
  - **FIX**: In the `TopAppBar` title of `DatabaseScreen`, add `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to the `Text` composable. Or reduce the font size. Or simplify the title to "Database Tiket".
- **Affected files**: `ui/DatabaseScreen.kt`
- **Verification**: Open DatabaseScreen on a small screen → verify header text is fully visible or properly truncated with ellipsis.

---

#### Task 11.5.8: Add Guide Page Button (Re-trigger Onboarding)
- **Status**: [x]
- **Priority**: Low
- **Description**:
  - **IMPLEMENTATION**:
    1. In the sidebar (Task 11.5.1), the "Panduan" item navigates to the `"onboarding"` route.
    2. Modify the `onboarding` route in `MainActivity.kt` to accept an optional query parameter or use a flag to distinguish between "first-time onboarding" and "re-viewing guide":
       - First-time: Shows "Lanjut" on last page, navigates to main menu.
       - Re-viewing: Shows "Tutup" on last page, navigates BACK (not to main menu).
    3. Do NOT reset `hasSeenOnboarding` flag when re-viewing — it's just for reference.
  - **ALTERNATIVE (simpler)**: Add a separate route `"guide"` that shows the same `OnboardingOverlay` content but with a "Tutup" button instead of "Lanjut". This avoids modifying the existing onboarding flow.
- **Affected files**: `ui/DashboardScreen.kt`, `MainActivity.kt`, possibly `ui/OnboardingOverlay.kt`
- **Verification**: Tap "Panduan" in sidebar → verify onboarding screens appear. Tap "Tutup" → verify return to dashboard (not re-trigger flow).

---

#### Task 11.5.9: Fix Keyboard Pushing Manual Input Box Up
- **Status**: N/A — Resolved by Task 11.3.1
- **Priority**: N/A
- **Description**:
  - This bug existed in `ManagementScreen` where the input box was pushed up when the keyboard opened. Since `ManagementScreen` is being deleted in Task 11.3.1, this bug is automatically resolved.
- **Notes**: No action needed. Marking as N/A.

---

## COMPLETED TASKS

#### Task 10.1: Full Event Cloud Backup
- **Status**: [x]
- **Priority**: Low
- **Description**:
  - Implement background uploading of QR images to Google Drive.
  - Create dynamic nested folder structures based on Event Name and Ticket Category.
  - Add real-time folder renaming on Drive when event name changes.
  - Export entire event data (tickets, logs, and optionally all generated QR codes) to Google Drive.
  - Provide an option to upload all generated QR code images to a specific user Drive folder.
  - Generate a backup metadata file containing total tickets, total size, etc.
- **Affected files**: `PROJECT_DOCUMENTATION.md`, `utils/TicketExporter.kt`, `data/cloud/MediaManager.kt`
- **Completion Notes**: Completed 16-Sep-2026.

---

#### Task 9.5.3: UI Implementation & Migration (Step 3)
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Implement `CategoryManagementDialog` for CRUD operations on categories (accessible via `DashboardScreen`).
  - Update `GeneratorScreen` (Mode Kuota) to use a Dropdown menu driven by registered categories.
  - Add validation to `GeneratorScreen` (Mode CSV) and `ManagementScreen` to ensure manually pasted categories map strictly to valid Category Codes.
  - Update `DistributionScreen` to leverage the new category names and properly map back to the immutable Category Codes for assignment.
- **Affected files**: `ui/CategoryManagementDialog.kt`, `ui/DashboardScreen.kt`, `ui/GeneratorScreen.kt`, `ui/ManagementScreen.kt`, `ui/DistributionScreen.kt`
- **Completion Notes**: Added full UI layer for managing Category Name vs Code. Removed free-text category input from Generator's Mode Kuota. Enforced strict validation for pasted codes in Management and CSV Mode.

---

#### Task 9.5.2: Repository & State Management (Step 2)
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Implement `CategoryRepository` for SQLite operations and Google Sheets synchronization.
  - Update `TicketViewModel` to expose a `StateFlow` of `TicketCategory` for the active event.
  - Integrate category sync into `syncTicketsFromCloud()`.
- **Affected files**: `data/repository/CategoryRepository.kt`, `di/AppModule.kt`, `viewmodel/TicketViewModel.kt`
- **Completion Notes**: Created `CategoryRepository` with bi-directional sync logic to the `Categories` sheet. Injected via `AppModule`. Added CRUD methods and `ticketCategories` StateFlow to `TicketViewModel`.

---

#### Task 9.5.1: Database & Cloud Schema (Step 1)
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Separate Category Name and Category ID to support mutable names with immutable IDs.
  - Create `TicketCategory` entity and `CategoryDao`.
  - Update `AppDatabase` migration to v13.
  - Update `SpreadsheetManager` to create a `Categories` sheet in Google Sheets.
- **Affected files**: `data/TicketCategory.kt`, `data/CategoryDao.kt`, `data/AppDatabase.kt`, `data/cloud/SpreadsheetManager.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Do not touch UI logic yet. Ensure backward compatibility with existing databases via proper Room migration.
- **Completion Notes**: Created `TicketCategory` entity and `CategoryDao`. Added `MIGRATION_12_13` to `AppDatabase` creating the `categories` table. Updated `SpreadsheetManager` to create the `Categories` sheet upon spreadsheet initialization.

---

#### Task 9.4: Distribution Status Dashboard & Retry
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - Enhance Step 4 of `DistributionScreen` with a full status dashboard:
    - Progress bar with percentage and count: "Mengirim 182/248 (73%)"
    - Estimated time remaining.
    - Real-time log feed showing the last 10 send results (timestamp + email + status icon).
    - Summary counters: ✅ Terkirim: X | ❌ Gagal: Y | ⏳ Menunggu: Z
  - **Retry mechanism**:
    - "Retry Gagal" button that re-attempts all rows with `emailStatus = "FAILED"`.
    - Individual retry: tap a failed row to retry just that one email.
  - **Export report**: "Export Laporan" button that generates a CSV summary of the distribution (buyer name, email, tickets sent, status) and saves it to the device or Google Drive.
  - After all emails are sent (or user explicitly marks distribution as complete), update the event's state so the `DashboardScreen` reflects that distribution is done.
- **Affected files**: `ui/DistributionScreen.kt`, `viewmodel/DistributionViewModel.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: The status dashboard should remain accessible even after all emails are sent, so the panitia can review the final distribution report at any time.
- **Completion Notes**: Added `distributionSheetId` to `Event` and migrated DB to v12. Added Retry and Export Report (CSV) functionality to ViewModel and UI. Export saves to `Documents/QRTix/[EventName]` and updated `TicketExporter` to follow the same directory structure. DashboardScreen now shows "Status Distribusi" when a sheet ID is present.

---

#### Task 9.3: Gmail API Integration & Batch Email Sending
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Add the Gmail API dependency and request the `gmail.send` OAuth scope during Google Sign-In (update Task 5.2's scope list).
  - Create a `GmailService` class that wraps the Gmail API. Provide a method: `sendEmailWithAttachment(to, subject, body, attachmentBytes, attachmentFileName)`.
  - Implement **Step 4 (Send & Status)** of the `DistributionScreen` wizard — the sending logic:
    - Read the `Distribution` sheet to get all rows with `emailStatus = "PENDING"` or `"FAILED"`.
    - For each row:
      1. Check `emailStatus` → if `"SENT"`, **skip** (idempotent).
      2. Download the corresponding QR code image(s) from Google Drive.
      3. Compose email: subject = "[Event Name] — Tiket Anda", body = greeting + event details + ticket info, attachment = QR code image(s).
      4. Send via Gmail API.
      5. On success → update `emailStatus` to `"SENT"` and `sentAt` to current timestamp in the `Distribution` sheet.
      6. On failure → update `emailStatus` to `"FAILED"` and `errorMessage` to the error description.
    - **Rate limiting**: Insert a delay between each email send (~7–8 seconds) to stay safely within Gmail API quota (≈480 emails/hour, under the 500/day limit for free Gmail accounts).
    - Support **Pause** and **Resume**: user can pause sending mid-batch, and resume later (the process picks up from the first non-"SENT" row).
    - If the app is closed and reopened, the process can be resumed from where it left off by reading the `Distribution` sheet status.
  - The "Kirim" button must be **disabled** while sending is in progress (prevents double-trigger).
  - **Supervisor Monitoring via Google Sheets**:
    - After the `Distribution` sheet is created (Task 9.2), insert a **summary header block** in rows 1–2 above the data:
      - Row 1: "DISTRIBUSI TIKET — [Event Name]"
      - Row 2: "Total: X | Terkirim: Y | Gagal: Z | Menunggu: W | Progres: XX.X%"
    - After **each email is sent** (or batch of emails), update the summary row counters and percentage in real-time via Sheets API.
    - Apply **conditional formatting** to the `emailStatus` column via Sheets API (`batchUpdate` with `AddConditionalFormatRuleRequest`):
      - "SENT" → green background
      - "FAILED" → red background
      - "PENDING" → yellow background
    - This allows supervisors/managers to open the Google Sheets link from any device (browser, phone) and monitor distribution progress in real-time without needing the QRTix app installed.
- **Affected files**: `data/cloud/GmailService.kt (NEW)`, `ui/DistributionScreen.kt`, `viewmodel/DistributionViewModel.kt`, `di/AppModule.kt`, `app/build.gradle.kts`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Gmail API daily sending limits: 500/day (free Gmail), 2000/day (Google Workspace). For large events (>500 buyers), the app should notify the user that sending will be spread across multiple days and offer an estimated completion date. Keep the screen on (`FLAG_KEEP_SCREEN_ON`) during active sending. The summary row update frequency should be batched (e.g., update after every 5 emails sent) to minimize API calls.

---

#### Task 9.2: Distribution Matching, Validation & Persistence
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Implement **Step 3 (Review & Validation)** of the `DistributionScreen` wizard.
  - After column mapping is confirmed, process the Form response data:
    - For each buyer row: read name, email, category, and quantity.
    - Auto-assign the appropriate number of generated tickets (from the active event's ticket pool) to each buyer.
    - Assignment is deterministic: tickets are assigned sequentially by category (first available unassigned ticket of the matching category).
  - **Validation (3 layers)**:
    1. **Data validation**: Flag rows with empty email, invalid email format, empty name, quantity = 0, or unrecognized category.
    2. **Quantity validation**: Verify that the total requested tickets per category does not exceed the total available tickets of that category in the event database. If it does, show a clear error: "Kategori VIP: diminta 120, tersedia 100".
    3. **Match validation**: After assignment, verify that every buyer's assigned ticket count matches their requested quantity exactly.
  - Display a summary screen:
    - Total valid buyers, total tickets to distribute, per-category breakdown.
    - List of any flagged errors (must be resolved before proceeding).
    - Scrollable detail table: Buyer Name | Email | Category | Qty Requested | Tickets Assigned | Status (✅ or ❌).
  - Create a `Distribution` sheet tab in the `QRTix_Data` spreadsheet (via `GoogleSheetsService`) with columns: buyerName, buyerEmail, ticketCategory, ticketCodes (comma-separated), emailStatus ("PENDING"/"SENT"/"FAILED"), sentAt, errorMessage.
  - Write the confirmed distribution mapping to this sheet. Once written, the mapping is **frozen** — subsequent runs read from this sheet rather than re-generating the mapping.
  - "Konfirmasi & Lanjut" button is **disabled** if any validation errors exist.
- **Affected files**: `ui/DistributionScreen.kt`, `data/repository/DistributionRepository.kt (NEW)`, `data/cloud/GoogleSheetsService.kt`, `viewmodel/DistributionViewModel.kt (NEW)`, `PROJECT_DOCUMENTATION.md`
- **Notes**: If the user returns to Step 3 after distribution has already been confirmed (mapping frozen in Sheets), load the existing mapping from the `Distribution` sheet instead of re-generating. Show a notice: "Distribusi sudah dikonfirmasi sebelumnya."
- **Completion Notes**: Created `BuyerData`, `DistributionAssignment`, `ValidationResult` in `Distribution.kt`. Implemented `DistributionRepository.kt` to handle frozen mapping detection, 3-layer validation, deterministic assignment, and saving to Sheets via `GoogleSheetsService`. Created `DistributionViewModel.kt` injected via Hilt in `AppModule.kt`. Refactored `DistributionScreen.kt` to transition to Step 4 (Review & Validation) UI with summary cards and validation errors preview.

---

#### Task 9.1: Google Form Response Sheet Linking, Auto-Detect Column Mapping & Category Mapping
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Create a new `DistributionScreen` composable with a wizard/stepper layout (5 steps). This task implements **Step 1 (Link Sheet)**, **Step 2 (Column Mapping)**, and **Step 2.5 (Category Mapping)**.
  - **Step 1 — Link Sheet**:
    - User pastes a Google Sheets URL (from their Google Form responses).
    - App extracts the spreadsheet ID from the URL, reads the header row and first 5 data rows via `GoogleSheetsService`.
    - Display a preview table of the detected data.
  - **Step 2 — Auto-Detect Column Mapping**:
    - Implement a `ColumnAutoDetector` utility that identifies which column corresponds to: **Name**, **Email**, **Ticket Category**, and **Quantity**.
    - Detection strategy (2 methods combined):
      1. **Header keyword matching** — match column header text against known keywords in Indonesian and English:
         - Email: "email", "e-mail", "alamat email", "email address"
         - Name: "nama", "name", "nama lengkap", "full name", "nama pembeli"
         - Category: "kategori", "category", "jenis", "tipe", "type", "tiket", "ticket"
         - Quantity: "jumlah", "qty", "quantity", "banyak", "berapa"
      2. **Data pattern analysis** (fallback when headers don't match):
         - Column values containing "@" and "." → Email
         - Column values that are small integers (1–10) → Quantity
         - Column values from a small set of distinct values → Category
         - Remaining text column → Name
    - Show a simple visual confirmation screen with icons and the detected mapping:
      - 👤 Nama ← "Nama Lengkap" ✅
      - 📧 Email ← "Email Anda" ✅
      - 🏷️ Kategori ← "Pilih Jenis Tiket" ✅
      - 🔢 Jumlah ← "Berapa Tiket?" ✅
    - Show a preview of the first data row with the detected mapping applied so the user can visually verify correctness.
    - Two buttons: "Ya, Sudah Benar" (proceed) and "Tidak, Perbaiki" (opens manual swap mode with dropdowns).
  - **Step 2.5 — Category Mapping (Auto-Suggest)**:
    - After column mapping is confirmed, read all **unique category values** from the Google Form response sheet (e.g., "VIP Mantap (Rp 500.000)", "Regular Biasa (Rp 200.000)").
    - Read all **existing ticket categories** from the QRTix database for the active event (e.g., "VIP", "REGULER", "VVIP").
    - Auto-suggest the mapping using a 4-priority matching strategy (all case-insensitive):
      1. **Exact match**: Form value equals DB category exactly (e.g., "VIP" = "VIP").
      2. **DB category is substring of Form value**: DB category found inside Form value (e.g., "VIP" found in "VIP Mantap (Rp 500.000)"). Check longest DB categories first to avoid "VIP" matching before "VVIP".
      3. **Form word is substring of DB category**: A word from the Form value is contained in or closely matches the DB category (e.g., "Regular" ≈ "REGULER").
      4. **Fuzzy match**: Levenshtein distance ≤ 2 between a Form word and a DB category (e.g., "Reguler" ≈ "REGULER").
    - If no match is found → mark with ⚠️ and require manual selection from a dropdown of available DB categories.
    - Display a simple confirmation screen:
      - Left column: Form category values
      - Right column: Matched DB category (dropdown, pre-selected with auto-suggest result)
      - Status icon: ✅ (auto-matched) or ⚠️ (needs manual selection)
    - Two buttons: "Ya, Sudah Benar" (proceed) and dropdown to manually change any incorrect match.
    - **Block proceeding** if any category is unmatched (still showing ⚠️).
  - Add a navigation route for `DistributionScreen` in `MainActivity.kt`.
  - Add a "Distribusi Tiket" button in `DashboardScreen` (Task 8.2), visible only when the active event has generated tickets.
- **Affected files**: `ui/DistributionScreen.kt (NEW)`, `utils/ColumnAutoDetector.kt (NEW)`, `utils/CategoryMatcher.kt (NEW)`, `MainActivity.kt`, `ui/DashboardScreen.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Both auto-detectors (column and category) should be case-insensitive and trim whitespace. If a column or category cannot be confidently detected, mark it with a ⚠️ warning icon instead of ✅ and require manual selection for that item only. The category matcher must check longer DB category names before shorter ones (e.g., "VVIP" before "VIP") to prevent incorrect substring matches.
- **Completion Notes**: Implemented Steps 1-3 of `DistributionScreen` UI with wizard pattern. Created `ColumnAutoDetector` utility for smart header and data pattern matching. Created `CategoryMatcher` using Levenshtein distance for fuzzy matching of ticket categories. Updated `TicketViewModel` with helper methods for external Google Sheets read and category retrieval. Added navigation and UI entry point from `DashboardScreen`.

---

#### Task 8.3: Event Context Lock & Cross-Event Safety Guards
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - Add persistent visual indicators on EVERY screen showing the active event name and color-coded badge (each event gets a distinct accent color) so the user always knows which event they're operating on.
  - Before critical operations (import tickets, delete all, scan), show a **confirmation dialog** that explicitly states the active event name: e.g., "Anda akan mengimpor tiket ke event **Konser Rock**. Lanjutkan?".
  - In `GeneratorScreen`: if the "Direct insert to DB" checkbox (Task 3.3) is enabled, show the target event name prominently and require explicit confirmation.
  - Prevent event switching while a scan session is actively in progress (require the user to explicitly "end" the scan session first).
- **Affected files**: `ui/ScannerScreen.kt`, `ui/ManagementScreen.kt`, `ui/DatabaseScreen.kt`, `ui/GeneratorScreen.kt`, `ui/components/EventBadge.kt (NEW)`, `PROJECT_DOCUMENTATION.md`
- **Notes**: The event badge component should be reusable across all screens. Use a consistent position (e.g., top-left or integrated into the top app bar) for muscle memory.
- **Completion Notes**: Created `EventBadge.kt` using consistent dynamic colors. Added `EventBadge` and explicit event name confirmation dialogs to `DatabaseScreen` (Delete All), `ManagementScreen` (CSV/Manual Import), `GeneratorScreen` (Direct Insert), and `DashboardScreen` (Start Scan). Implemented event switch warning in `DashboardScreen` when `scannedTicketCount > 0`.

---

#### Task 8.2: Event-Centric Dashboard Navigation Redesign
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Replace the current `MainMenuScreen` (4 equal buttons) with a new `DashboardScreen` that is **event-centric**:
    - Top section: Active event card (name, logo, ticket count, scanned count, event code). Tap to switch/manage events.
    - Primary action area: Context-aware buttons that guide the user based on event state:
      - If event has 0 tickets → prominently show "Generate Tiket" / "Import Tiket" buttons.
      - If event has tickets but 0 scanned → show "Mulai Scan" as primary button.
      - If event is mid-scan → show scan progress and "Lanjutkan Scan" button.
    - Secondary actions: "Lihat Database", "Generator", "Pengaturan Event" as smaller/secondary buttons.
    - Bottom: Sync status indicator (last synced, online/offline badge).
  - This ensures the user always knows **what to do next** without guessing.
- **Affected files**: `ui/DashboardScreen.kt (NEW)`, `ui/MainMenuScreen.kt (DEPRECATED/REPLACED)`, `MainActivity.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: The current `MainMenuScreen.kt` can be kept as a fallback or deleted after the new dashboard is verified to be fully functional. The dashboard should feel like a "control center" for the active event.
- **Completion Notes**: Created `DashboardScreen.kt` implementing the dynamic UI logic. Replaced `MainMenuScreen` in `MainActivity.kt` routes. 

---

#### Task 8.1: Login & Onboarding Screen
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Redesign the app entry flow:
    1. `SplashScreen` → check if user is already signed in.
    2. If NOT signed in → navigate to `LoginScreen` (Google Sign-In button, app logo, tagline).
    3. If signed in → navigate to the new `DashboardScreen` (replaces current `MainMenuScreen`).
  - After first-time sign-in, show a brief onboarding overlay or tooltip sequence explaining the app flow:
    1. "Buat Event baru" → 2. "Generate tiket" → 3. "Distribusikan tiket" → 4. "Scan tiket di hari-H".
  - Store a flag (`hasSeenOnboarding`) in local preferences so onboarding only shows once.
- **Affected files**: `ui/LoginScreen.kt`, `ui/SplashScreen.kt`, `ui/OnboardingOverlay.kt (NEW)`, `MainActivity.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Onboarding text should be in Indonesian (Bahasa Indonesia). Keep it concise — maximum 4 steps/screens.
- **Completion Notes**: Implemented `hasSeenOnboarding` flag in `AuthPreferences` and `AuthViewModel`. Created `OnboardingOverlay.kt` showing a 4-step wizard. Updated `MainActivity.kt` to route to `"onboarding"` before `"mainmenu"` for first-time sign-in. Redesigned `LoginScreen.kt` with logo, tagline, and better UI layout.

---

#### Task 7.2: Multi-Gate Conflict Prevention & Sync Indicator
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Handle the race condition scenario where two gates scan the same ticket within milliseconds:
    - After writing `isScanned=TRUE` to Sheets, **immediately re-read** the row to confirm the write succeeded and no conflicting write occurred.
    - If the `scannedAt` timestamp in Sheets doesn't match what this device just wrote, it means another device scanned it first → treat as `AlreadyScanned`.
  - Add a "Last Synced: X seconds ago" indicator on the Scanner screen.
  - Add a periodic background sync (every 10–15 seconds) that refreshes the local ticket cache from Sheets, so the scan counter ("Scan: X / Y") stays up-to-date across devices.
  - Implement a "Sync Now" button on the Scanner screen for manual refresh.
- **Affected files**: `ui/ScannerScreen.kt`, `data/repository/TicketRepository.kt`, `viewmodel/TicketViewModel.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: The periodic sync should NOT block the UI or interfere with active scanning. Run it on a background coroutine. If sync fails silently (network blip), just skip and retry on the next interval.
- **Completion Notes**: Added immediate re-read to `validateAndScanOnline` with a mismatch returning `AlreadyScanned`. Increased scan timeout to 5s. Added `lastSyncTime`, 12s periodic `syncJob`, and `syncScannerData()` to `TicketViewModel`. Updated `ScannerScreen` with a dynamic relative time pill and a manual sync button. Added `DisposableEffect` for starting and stopping the periodic sync loop.

---

#### Task 7.1: Online Ticket Validation via Sheets API
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Modify the scan validation flow in `TicketRepository` (called by `TicketViewModel.processQrCode()`) to validate tickets **directly against Google Sheets in real-time** instead of local SQLite.
  - Scan flow:
    1. Read the ticket row from the `Tickets` sheet by matching `qrContent` + `eventId` (use Sheets API `values.get` with a filtered range or search through cached data + confirm with a targeted read).
    2. If not found → `ScanStatus.Invalid`.
    3. If found and `isScanned` = TRUE → `ScanStatus.AlreadyScanned`.
    4. If found and `isScanned` = FALSE → **immediately write** `isScanned=TRUE` and `scannedAt=<timestamp>` to the Sheets row → `ScanStatus.Success`.
  - Update local cache after successful scan.
  - Display a subtle "Online" / "Connected" indicator on the Scanner screen so the operator knows real-time validation is active.
- **Affected files**: `data/repository/TicketRepository.kt`, `viewmodel/TicketViewModel.kt`, `ui/ScannerScreen.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: The entire scan round-trip (read + write to Sheets) should target under 1 second. If network latency exceeds 3 seconds, show a timeout error and allow retry. Implement a loading/spinner state on the scan UI between scan detection and result display.
- **Completion Notes**: Added `OnlineScanResult` sealed class and `validateAndScanOnline()` method to `TicketRepository` with a 3-second timeout via `withTimeout`. Refactored `TicketViewModel.processQrCode()` to use cloud-first validation. Added `ScanStatus.NetworkError`, `isOnline` and `isScanLoading` StateFlows. Updated `ScannerScreen` with an Online/Offline pill indicator, a loading spinner overlay during cloud round-trip, and an amber NetworkError dialog with "Coba Lagi" retry button. Also fixed two pre-existing bugs: missing `LaunchedEffect` import in `MainMenuScreen.kt` and incomplete `HistoryLogRepository` constructor in `AppModule.kt`.

---

#### Task 6.5: History Log Sync via Google Sheets
- **Status**: [x]
- **Priority**: Low
- **Description**:
  - Update `HistoryLogRepository` to write all history log entries to the `HistoryLogs` sheet in Google Sheets.
  - Logs are append-only in normal operation (new entries are appended to the sheet).
  - Undo operations update the `isUndone` column in the corresponding row.
  - "Clear all history" deletes all rows for the active event from the sheet.
  - On app launch, sync history logs from Sheets to local cache.
- **Affected files**: `data/repository/HistoryLogRepository.kt`, `data/cloud/GoogleSheetsService.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: History logs are less critical than ticket data. If a Sheets write fails for a log entry, it is acceptable to log it locally only and retry on next sync.
- **Completion Notes**: Added `syncLogsFromCloud` to `HistoryLogRepository.kt` to do a full read of the `HistoryLogs` sheet, replacing local data. Implemented a logic to compute dynamic auto-increment IDs for new logs based on cloud data during `insertLog`. Implemented cloud updates for `markAsUndone` and dimension range deletions for `deleteLogsForEvent`. Added `syncLogsFromCloud` hook directly to `AuthViewModel`.

---

#### Task 6.4: Google Drive Media Storage (Logo & Background Images)
- **Status**: [x]
- **Priority**: Medium
- **Description**:
  - When a user picks a logo or background image for an event (Task 2.3), upload the image to Google Drive in a dedicated app folder (e.g., `QRTix_Media/`).
  - Store the Drive file ID (not local path) in the `Events` sheet columns (`logoFileId`, `bgFileId`).
  - When displaying the logo/background in the app, download from Drive and cache locally (using a simple file cache in `context.cacheDir`).
  - This ensures images are accessible from any device signed into the same Google account.
- **Affected files**: `data/cloud/GoogleDriveService.kt`, `data/repository/EventRepository.kt`, `ui/EventSelectionDialog.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Implement a simple cache-first strategy: check local cache → if missing or stale, download from Drive. Compress images before upload (max 1MB recommended) to save Drive quota.
- **Completion Notes**: Implemented `MediaManager.kt` to handle async uploads to `QRTix_Media` and cached downloads using the Drive API. Kept Room DB schema unchanged by storing the Google Drive File ID inside the existing `logoPath` and `bgPath` properties. Updated `TicketViewModel` and relevant UI components (`MainMenuScreen`, `ScannerScreen`, `GeneratorScreen`) to resolve the File ID asynchronously via `LaunchedEffect`.

---

#### Task 6.3: Ticket CRUD via Google Sheets
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Update `TicketRepository` to perform all Ticket operations against the `Tickets` sheet in Google Sheets.
  - **Import/Insert**: When tickets are added (from CSV, manual input, or generator), write them to Sheets first, then cache locally.
  - **Read (for UI list/filter/search)**: Read from local cache (populated on sync). Provide a manual "Refresh/Sync" button to pull latest data from Sheets.
  - **Update/Delete**: Write to Sheets first, then update local cache.
  - **Scan (mark as scanned)**: This is handled in Phase 7 (real-time scanning). In this task, only implement the non-scan CRUD operations.
  - Implement batch operations for efficiency: use Sheets API `batchUpdate` or `values.append` for bulk inserts instead of one-row-at-a-time.
- **Affected files**: `data/repository/TicketRepository.kt`, `data/cloud/GoogleSheetsService.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: For large imports (1000+ tickets), consider chunking the batch into groups of 500 rows per API call to avoid request size limits.
- **Completion Notes**: Added `appendRows` to `GoogleSheetsService` for chunked bulk inserts. Refactored `TicketRepository` to read `Tickets!A:H` for syncing, and fetch row indices by ID in `A:A` for updates/deletes. Modified `TicketViewModel` and `DatabaseScreen` to include a manual `Sync` button that repopulates the local cache from Sheets.

---

#### Task 6.2: Event CRUD via Google Sheets
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Update `EventRepository` to perform all Event CRUD operations against the `Events` sheet in Google Sheets (create, read, update, delete).
  - On app launch (after login), fetch all events from Sheets and cache them in local Room DB for fast UI rendering.
  - On any write operation (create/update/delete event), write to Sheets first (source of truth), then update local cache.
  - Handle the "Default Event" (id=1) rule: if no events exist in Sheets, auto-create the default event.
- **Affected files**: `data/repository/EventRepository.kt`, `data/cloud/GoogleSheetsService.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Implement proper error handling: if Sheets write fails (network error), show a user-facing error toast in Indonesian and do NOT update local cache (to maintain consistency).
- **Completion Notes**: Refactored `EventRepository` to fetch data from `Events` sheet using `GoogleSheetsService` on sync, rewrite the local DB to match, and push every insert/update/delete directly to the Google Sheets via `appendRow`, `updateRow`, and `deleteRow` first before updating the local SQLite cache. Integrated `syncEventsFromCloud` directly into `AuthViewModel` after successful login initialization.

---

#### Task 6.1: Spreadsheet Schema & Initialization
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Define the standard spreadsheet structure that will be auto-created in the user's Google Drive on first login:
    - Spreadsheet name: `QRTix_Data`
    - Sheet/tab 1: `Events` — columns: id, name, createdAt, lastAccessedAt, logoFileId, eventCode, bgFileId, qrX, qrY, qrScale, qrRotation
    - Sheet/tab 2: `Tickets` — columns: id, qrContent, ticketType, isScanned, createdAt, scannedAt, isModified, eventId
    - Sheet/tab 3: `HistoryLogs` — columns: id, eventId, action, description, details, timestamp, isUndone
  - On first login (no existing spreadsheet found in Drive), auto-create the spreadsheet with the above schema and frozen header rows.
  - Store the spreadsheet ID in local SharedPreferences (or DataStore) so it doesn't need to be looked up every time.
  - If the spreadsheet is found to be deleted or inaccessible, prompt the user to re-create or re-link.
- **Affected files**: `data/cloud/SpreadsheetManager.kt (NEW)`, `data/cloud/GoogleSheetsService.kt`, `di/AppModule.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Use a custom app property or a known file name in a specific Drive folder to locate the spreadsheet reliably. Consider using `appDataFolder` scope if you want the spreadsheet hidden from the user's regular Drive view, OR use a regular folder if you want the user to be able to see/access their data directly in Google Sheets (recommended for transparency).
- **Completion Notes**: Added `findFileByName` and `createSpreadsheetFile` to `GoogleDriveService`. Created `CloudPreferences` to store folder and spreadsheet IDs locally. Implemented `SpreadsheetManager` which ensures the "QRTix" directory exists, then creates or verifies "QRTix_Data" inside it. It uses batch operations to add the three sheets (`Events`, `Tickets`, `HistoryLogs`), configure their headers, and freeze the first row. Integrated it into `AuthViewModel` to run directly after successful Google Sign-In.

---

#### Task 5.4: Google OAuth Remote Consent Handling (Bug Fix)
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Handle `UserRecoverableAuthIOException` during the initial Google Cloud API calls (e.g., Google Drive/Sheets initialization).
  - Modify `AuthViewModel` to catch this exception and emit a new `AuthState.NeedsConsent(intent)`.
  - Update `LoginScreen` to observe `NeedsConsent` and launch the provided Intent via `ActivityResultContracts.StartActivityForResult()`, prompting the user for remote consent.
  - Add a `retryInitialization()` function to attempt cloud initialization again after the user grants consent.
- **Affected files**: `viewmodel/AuthViewModel.kt`, `ui/LoginScreen.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Resolves the "NeedRemoteConsent" crash that occurs when the app first tries to access Drive/Sheets before explicit permission is granted via OAuth consent screen.
- **Completion Notes**: Completed 17-Sep-2026. Handled the `UserRecoverableAuthIOException` and correctly triggered the consent dialog from Compose UI.

---

#### Task 5.3: Google API Service Layer Setup
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Add Google API Client library dependencies (`google-api-services-sheets`, `google-api-services-drive`, `google-api-client-android`, `google-http-client-gson`).
  - Create a `GoogleSheetsService` class that wraps the Sheets API v4 client. Provide methods for: creating a spreadsheet, reading a range, writing/appending rows, updating cells, and batch operations.
  - Create a `GoogleDriveService` class that wraps the Drive API v3 client. Provide methods for: uploading a file, downloading a file, and listing files in the app's folder.
  - Both services should accept the authenticated user's credential from `AuthViewModel` and handle common API errors (401 token expired → trigger re-auth, 429 rate limit → retry with backoff, network errors → throw descriptive exceptions).
  - Provide these services via Hilt DI as `@Singleton`.
- **Affected files**: `data/cloud/GoogleSheetsService.kt (NEW)`, `data/cloud/GoogleDriveService.kt (NEW)`, `di/AppModule.kt`, `app/build.gradle.kts`, `PROJECT_DOCUMENTATION.md`
- **Notes**: All API calls must run on `Dispatchers.IO`. Implement exponential backoff for rate-limited (429) responses.
- **Completion Notes**: Added Google API Client dependencies and META-INF packaging exclude. Created GoogleCredentialManager, GoogleSheetsService, and GoogleDriveService with Hilt injection and exponential backoff retry logic.

---

#### Task 5.2: Google Sign-In Integration
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Add Google Sign-In using the Credential Manager API (modern replacement for legacy GoogleSignInClient).
  - Request OAuth scopes: `https://www.googleapis.com/auth/spreadsheets` (read/write Sheets) and `https://www.googleapis.com/auth/drive.file` (read/write only files created by the app in Drive).
  - Create a `LoginScreen` composable: Google Sign-In button, app branding, loading state.
  - Create an `AuthViewModel` (or `AuthManager`) to manage sign-in state, access tokens, and token refresh.
  - Store sign-in session state so the user doesn't need to re-login on every app launch.
  - If not signed in → force redirect to `LoginScreen`. All other screens require auth.
- **Affected files**: `ui/LoginScreen.kt (NEW)`, `viewmodel/AuthViewModel.kt (NEW)`, `MainActivity.kt`, `di/AppModule.kt`, `app/build.gradle.kts`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Register the app in Google Cloud Console, enable Sheets API v4 and Drive API v3, and configure OAuth consent screen. Add the SHA-1 fingerprint of the debug/release keystore to the Cloud Console credentials. UI text for the login screen should be in Indonesian (Bahasa Indonesia) as per app convention.
- **Completion Notes**: Implemented Google Sign-In using Credential Manager API and GoogleIdTokenCredential. Created `AuthPreferences` for session token storage and `AuthViewModel` for auth state management. Added `LoginScreen` composable and updated `MainActivity.kt` with authentication guard routing.

---

#### Task 5.1: Repository Pattern Refactoring
- **Status**: [x]
- **Priority**: High
- **Description**:
  - Introduce a Repository layer between `TicketViewModel` and the DAOs (`TicketDao`, `EventDao`, `HistoryLogDao`).
  - Create `TicketRepository`, `EventRepository`, and `HistoryLogRepository` classes.
  - Refactor `TicketViewModel` to call Repository methods instead of calling DAOs directly.
  - The Repository will initially just delegate to the local DAOs (no behavior change), but this abstraction is required so that Phase 6 can swap/extend the data source to Google Sheets without modifying ViewModel logic.
- **Affected files**: `data/repository/TicketRepository.kt (NEW)`, `data/repository/EventRepository.kt (NEW)`, `data/repository/HistoryLogRepository.kt (NEW)`, `viewmodel/TicketViewModel.kt`, `di/AppModule.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: This is a pure refactoring task. All existing app behavior must remain identical after this change. Run full manual regression test (import, scan, delete, undo, export, event switching) to verify.
- **Completion Notes**: Created Repository classes and refactored TicketViewModel to inject them instead of DAOs directly. Also updated AppModule.kt to provide these repositories.

---

#### Task 4.3: Smart Detection & Alert for Cross-Event Ticket Scans
- **Status**: [x]
- **Priority**: Low
- **Description**: 
  - Display a distinct, specific error message if a scanned ticket belongs to a different event.
  - Validate the scanned QR prefix against the active event's `eventCode`.
- **Affected files**: `viewmodel/TicketViewModel.kt`, `ui/ScannerScreen.kt`
- **Completion Notes**: Added `CrossEventError` to `ScanStatus` and validated `eventCode` in `TicketViewModel`.

---

#### Task 4.2: System-wide Full Capitalization & Emergency Manual Input
- **Status**: [x]
- **Priority**: Medium
- **Description**: 
  - Auto-uppercase across input fields: Event Code, Category, Scanner Manual Input (using `KeyboardCapitalization.Characters`).
  - Strict 4-digit numpad for sequence numbers and strict 4-character keyboard for secret tokens on the Emergency Manual Input scanner dialog.
  - Add `COLLATE NOCASE` to `TicketDao` queries and `.uppercase().trim()` in ViewModel to ensure case-insensitive validation.
- **Affected files**: `ui/ScannerScreen.kt`, `ui/GeneratorScreen.kt`, `ui/EventSelectionDialog.kt`, `data/TicketDao.kt`, `viewmodel/TicketViewModel.kt`
- **Completion Notes**: Rewrote manual input in Scanner to use sequence and token boxes plus category dropdown. Updated `TicketDao` with `COLLATE NOCASE`.

---

#### Task 4.1: Scanner Optimization — Auto-Resume / Continuous Scanning
- **Status**: [x]
- **Priority**: High
- **Description**: 
  - Eliminate scanning bottlenecks with camera auto-resume.
  - Valid (Success): Display green banner for 1.0–1.5s + success audio, then automatically resume camera scanning without requiring user to tap "Continue".
  - Error (Invalid / Already Scanned): Display a red blocking alert modal requiring explicit dismissal by staff.
  - Haptic feedback: Distinct vibration patterns for success vs error/warning.
- **Affected files**: `ui/ScannerScreen.kt`, `utils/SoundManager.kt`
- **Notes**: Implement debounce/throttle logic to avoid immediate duplicate triggers on the same physical ticket.
- **Completion Notes**: Added debounce logic to `ScannerScreen.kt` and used `Vibrator` for haptics. Success triggers a 1.5s green banner while errors trigger blocking modals.

---

#### Task 3.4: Prevent Screen Timeout (KeepScreenOn) During Generation
- **Status**: [x]
- **Priority**: Medium
- **Description**: 
  - Keep the device display on (`FLAG_KEEP_SCREEN_ON`) while batch generation is in progress to prevent CPU throttling or OS killing background tasks.
  - Clear the `KeepScreenOn` flag once generation completes or fails.
- **Affected files**: `ui/GeneratorScreen.kt`
- **Completion Notes**: Used `DisposableEffect` with `window.addFlags(FLAG_KEEP_SCREEN_ON)` during the `isGenerating` state.

---

#### Task 3.3: Direct Database Insertion Logic (via ViewModel)
- **Status**: [x]
- **Priority**: High
- **Description**: 
  - Add a checkbox: "Directly insert tickets into active Event Database" in `GeneratorScreen`.
  - Implement batch ticket insertion logic via `TicketViewModel` (following MVVM pattern, avoiding direct DAO calls from Composable UI).
- **Affected files**: `ui/GeneratorScreen.kt`, `viewmodel/TicketViewModel.kt`, `PROJECT_DOCUMENTATION.md`
- **Completion Notes**: Added `insertBatchTickets` in `TicketViewModel` and wired it to a checkbox in `GeneratorScreen`.

---

#### Task 3.2: Generator UI Form Revamp (Quota Mode)
- **Status**: [x]
- **Priority**: High
- **Description**: 
  - Build a "Quota Auto-Generate" input mode in `GeneratorScreen`.
  - Input fields: Category, Quota Count, Prefix (Event Code), and Starting Sequence Number.
  - Retain the existing manual text/CSV input mode.
- **Affected files**: `ui/GeneratorScreen.kt`
- **Completion Notes**: Implemented a TabRow to toggle between Manual CSV and Quota Mode.

---

#### Task 3.1: Batch QR Code Exporter Engine
- **Status**: [x]
- **Priority**: High
- **Description**: 
  - Integrate the QR generator engine from Task 2.1 with the template position parameters from Task 2.2.
  - Implement a batch export function supporting Auto-Scaling Resolution (min 1000px, max ~2000px).
  - Implement aggressive Bitmap recycling (`bitmap.recycle()`) and memory management during batch generation to prevent `OutOfMemoryError` (OOM).
  - File naming format: `QRTix_[EVENT_CODE]_[CATEGORY]_[SEQUENCE_NO].jpg`.
- **Affected files**: `utils/TicketExporter.kt`, `ui/GeneratorScreen.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: The entire export process must run inside a background coroutine dispatcher (`Dispatchers.IO` or `Dispatchers.Default`).
- **Completion Notes**: Added memory management with bitmap recycling, QR scale/rotation overlay on background templates, and updated the filename matching the standard ticket code format.

---

#### Task 2.3: Event Profile UI Updates (Logo & Background Picker)
- **Status**: [x]
- **Priority**: High
- **Description**: 
  - Update `EventSelectionDialog.kt` to allow users to pick an event logo and background template image from the device gallery (using `ActivityResultContracts.GetContent()`).
  - Save the selected image paths to the event record (`logoPath`, `bgPath`).
  - Display the active event logo in the header UI (`MainMenuScreen` and `ScannerScreen`).
- **Affected files**: `ui/EventSelectionDialog.kt`, `ui/MainMenuScreen.kt`, `ui/ScannerScreen.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Properly handle URI permissions or copy images to local app internal storage so paths remain permanently accessible across app restarts.
- **Completion Notes**: Implemented image pickers in EventSelectionDialog, copying images to internal storage and updating ViewModel.

---

#### Task 2.2: Visual Ticket Editor UI
- **Status**: [x]
- **Priority**: High
- **Description**: 
  - Create a new Jetpack Compose screen for the Visual Ticket Editor.
  - Allow users to interactively manipulate the QR code position: drag (X, Y), pinch-to-zoom (Scale), and rotation over an event background template.
  - Persist the final transformation values (`qrX`, `qrY`, `qrScale`, `qrRotation`) to the active Event record in the database.
- **Affected files**: `ui/TicketEditorScreen.kt (NEW)`, `navigation/NavGraph (MainActivity.kt)`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Leverage `pointerInput` and `detectTransformGestures` from Jetpack Compose.
- **Completion Notes**: Created TicketEditorScreen using pointerInput and detectTransformGestures, saving values to TicketViewModel.

---

#### Task 2.1: Core QR Engine Upgrade (ZXing + Canvas)
- **Status**: [x]
- **Priority**: High
- **Description**: 
  - Update QR Code generation logic to support High Error Correction (Level H), ensuring scannability even with an embedded logo overlay.
  - Add logic (using Android `Canvas`) to overlay the event logo at the center of the QR Code (maximum 20% of total QR area).
  - Add Auto-Scaling HRI Text (human-readable ticket code printed beneath the QR code) that automatically scales down in font size if the text is too long.
- **Affected files**: `utils/TicketExporter.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Ensure a white quiet-zone padding is preserved around the QR code boundary for optimal scanner detection.
- **Completion Notes**: Integrated ZXing EncodeHintType.ERROR_CORRECTION H and used Canvas to overlay the logo bitmap in TicketExporter. Also implemented scaling for HRI text.

---

#### Task 1.2: Ticket Code Standardization & Core Generation Logic
- **Status**: [x]
- **Priority**: High
- **Description**: 
  - Create a utility function to format ticket codes into the standard format: `[PREFIX]-[CATEGORY]-[4-DIGIT]-[4-RANDOM]`.
  - Implement a random token generator function that excludes ambiguous characters (`0`, `O`, `1`, `I`) to prevent optical misreading.
- **Affected files**: `utils/TicketFormatters.kt (NEW)`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Create unit tests or self-verification functions where applicable.
- **Completion Notes**: Implemented TicketFormatters object with generateRandomToken and formatTicketCode functions.

---

#### Task 1.1: Event Database Migration (v10 -> v11)
- **Status**: [x]
- **Priority**: High
- **Description**: 
  - Add new columns to the `events` table in `Event.kt`:
    - `logoPath` (String?, nullable)
    - `eventCode` (String, default 'EVNT1')
    - `bgPath` (String?, nullable)
    - `qrX` (Float, default 0f)
    - `qrY` (Float, default 0f)
    - `qrScale` (Float, default 1f)
    - `qrRotation` (Float, default 0f)
  - Create Room Migration `MIGRATION_10_11` in `data/AppDatabase.kt`.
  - Increment DB version to 11 in `@Database(version = 11, ...)`.
- **Affected files**: `data/Event.kt`, `data/AppDatabase.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Ensure migration script executes safely with valid default values for existing event records.
- **Completion Notes**: Added columns to Event data class and created MIGRATION_10_11 in AppDatabase.

