# PROJECT DOCUMENTATION — QRTix (TicketingScanner)

> **CRITICAL INSTRUCTIONS FOR AI AGENT:**
> 1. **STRICT LANGUAGE RULE**: All documentation, task descriptions, notes, and change log entries MUST ALWAYS be written in **ENGLISH ONLY**. Never write documentation or tasks in Indonesian (even though the Android app's user-facing UI text is in Indonesian).
> 2. Read this file FIRST before doing anything. This file contains the full project context.
> 3. Read `TASKS.md` (same root directory) to know what work needs to be done.
> 4. After completing work, UPDATE the relevant sections of this file (structure, new files, new features, schema changes, etc.) and add an entry to the CHANGE LOG at the bottom in **English**.
> 5. Do NOT rewrite this entire file. Only update sections affected by your changes.
> 6. All paths in this document are relative to the project root unless stated otherwise.

---

## 1. PROJECT IDENTITY

- **Name**: QRTix (TicketingScanner)
- **Package**: `com.tkrz.qrtix`
- **Platform**: Android Native
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3) — NO XML layouts
- **Architecture**: MVVM (Model-View-ViewModel), Single Activity
- **Min SDK**: 24 | **Target SDK**: 35 | **Compile SDK**: 35
- **Java Target**: 17
- **App Version**: 1.0 (versionCode: 1)
- **Build System**: Gradle Kotlin DSL (`.kts`)
- **Root Project Name**: `TicketingScanner`
- **Module**: `:app` (single module)

---

## 2. APP DESCRIPTION

QRTix is an Android app for managing and validating QR Code-based tickets at events.

Core capabilities:
- Import ticket data (from CSV files or manual input) into a local database
- Scan QR Code tickets using the phone camera in real-time
- Validate tickets: check if valid, already scanned, or unregistered
- Manage ticket database: view, search, filter, sort, edit, delete, export to CSV
- Bulk-generate QR Code images and save them as a ZIP file
- Support multiple workspaces (events) — one device can manage many separate events

**UI language**: Indonesian (all labels, messages, and user-facing text are in Bahasa Indonesia)

---

## 3. DEPENDENCIES & LIBRARIES

All dependencies are defined in `app/build.gradle.kts`.

| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose BOM | 2024.10.01 | Declarative UI framework |
| Compose Material 3 | (from BOM) | UI components & theming |
| Material Icons Extended | (from BOM) | Extended icon set |
| Navigation Compose | 2.8.5 | Screen-to-screen navigation |
| CameraX (core, camera2, lifecycle, view) | 1.4.0 | Camera access for preview & frame analysis |
| ML Kit Barcode Scanning | 18.3.1 | QR Code detection from camera frames |
| Room (runtime, ktx, compiler via KSP) | 2.7.0 | ORM for local SQLite database |
| Hilt (android, compiler via KSP, navigation-compose) | 2.59.2 | Dependency Injection |
| KSP | 2.3.2 | Kotlin Symbol Processing (code gen for Room & Hilt) |
| kotlin-csv-jvm | 1.9.3 | CSV file parsing |
| ZXing Core | 3.5.3 | QR Code image generation (Bitmap) |

Gradle plugins (root `build.gradle.kts`):
- `com.android.application` 9.0.1
- `org.jetbrains.kotlin.android` 2.2.10
- `com.google.devtools.ksp` 2.3.2
- `org.jetbrains.kotlin.plugin.compose` 2.2.10
- `com.google.dagger.hilt.android` 2.59.2

---

## 4. FILE STRUCTURE & RESPONSIBILITIES

Root package: `app/src/main/java/com/tkrz/qrtix/`

### 4.1 Root Files

| File | Responsibility |
|------|----------------|
| `MainActivity.kt` | The only Activity. Initializes NavHost with 6 routes (splash, mainmenu, scanner, management, database, generator). Creates SoundManager. Passes TicketViewModel to all screens that need it. |
| `QrtixApplication.kt` | Application class annotated with `@HiltAndroidApp`. Required by Hilt for DI initialization. Contains only an empty class declaration. |

### 4.2 Data Layer (`data/`)

| File | Contents | Responsibility |
|------|----------|----------------|
| `AppDatabase.kt` | Class `AppDatabase` | Room Database (version 10). Declares entities: Ticket, Event, HistoryLog. Provides DAOs: ticketDao(), eventDao(), historyLogDao(). Contains migrations v5→v6 through v9→v10. Uses Singleton pattern. |
| `TicketDao.kt` | Entity `Ticket`, Interface `TicketDao` | **Ticket entity** — fields: id(PK auto), qrContent, ticketType, isScanned, createdAt, scannedAt(nullable), isModified, eventId. Unique index on (qrContent, eventId). **TicketDao** — functions: getAllTickets(eventId), getTicketByQr(qrContent, eventId), getTicketCount(eventId), insertTickets(list), markAsScanned(qrContent, eventId, scannedAt), deleteAllTickets(eventId), resetSequence(), deleteTickets(ids), updateTicket(id, newQr, newType, updatedAt), getExistingCodes(codes, eventId). |
| `Event.kt` | Entity `Event` | Fields: id(PK auto), name, createdAt, lastAccessedAt. Represents one workspace/event. |
| `EventDao.kt` | Interface `EventDao` | Functions: getAllEvents() → Flow<List<Event>>, getEventById(id), insertEvent(event) → Long, updateEvent(event), deleteEvent(id). |
| `EventPreferences.kt` | Class `EventPreferences` | Stores the active event ID in SharedPreferences ("EventPrefs", key "ACTIVE_EVENT_ID"). Exposes StateFlow<Long> for reactive observation. Default value: 1L. |
| `HistoryLog.kt` | Entity `HistoryLog`, Interface `HistoryLogDao` | **HistoryLog entity** — fields: id(PK auto), eventId, action, description, details(default ""), timestamp, isUndone(default false). **HistoryLogDao** — functions: getLogsForEvent(eventId), insertLog(log) → Long, markAsUndone(logId), deleteLogsForEvent(eventId). |

### 4.3 Dependency Injection (`di/`)

| File | Contents | Responsibility |
|------|----------|----------------|
| `AppModule.kt` | Object `AppModule` | Hilt Module (@InstallIn SingletonComponent). Provides: AppDatabase, TicketDao, EventDao, HistoryLogDao, EventPreferences, TicketRepository, EventRepository, HistoryLogRepository. All @Singleton. |

### 4.4 Repository Layer (`data/repository/`)

| File | Contents | Responsibility |
|------|----------|----------------|
| `TicketRepository.kt` | Class `TicketRepository`, Sealed class `OnlineScanResult` | Acts as a single source of truth for Ticket data operations. Wraps `TicketDao` and `GoogleSheetsService`. Provides `validateAndScanOnline()` for real-time cloud ticket validation. `OnlineScanResult` variants: Success, AlreadyScanned, NotFound, NetworkError. |
| `EventRepository.kt` | Class `EventRepository` | Acts as a single source of truth for Event data operations. Wraps `EventDao`. |
| `HistoryLogRepository.kt` | Class `HistoryLogRepository` | Acts as a single source of truth for HistoryLog data operations. Wraps `HistoryLogDao`. |
| `DistributionRepository.kt` | Class `DistributionRepository` | Handles complex distribution validation (data, quantity, matching rules) and deterministic ticket assignment. Manages saving mapping to Google Sheets. |

### 4.5 ViewModel Layer (`viewmodel/`)

| File | Contents | Responsibility |
|------|----------|----------------|
| `AuthViewModel.kt` | Sealed class `AuthState`, Class `AuthViewModel` | Manages Google Sign-In state using Credential Manager. |
| `TicketViewModel.kt` | Sealed class `ScanStatus`, Class `TicketViewModel` | All business logic for the app. Details below. |
| `DistributionViewModel.kt` | Class `DistributionViewModel` | Exposes distribution validation results, loading state, and error handling for the distribution UI flow. |

**ScanStatus** (sealed class — QR scan result states):
- `ScanStatus.Success(ticket, scanTimeString)` — ticket valid, successfully scanned
- `ScanStatus.AlreadyScanned(ticket, scanTimeString)` — ticket was already scanned before
- `ScanStatus.Invalid(code)` — QR code not found in database
- `ScanStatus.CrossEventError(code)` — ticket belongs to a different event
- `ScanStatus.NetworkError(message)` — network/cloud error during online validation
- `ScanStatus.Idle` — no scan result (initial/reset state)

**TicketViewModel** — injected dependencies: TicketRepository, EventRepository, EventPreferences, HistoryLogRepository, Context.

Exposed state (StateFlow):
- `activeEventId: StateFlow<Long>` — currently active event ID
- `activeEvent: StateFlow<Event?>` — active Event object
- `allEvents: StateFlow<List<Event>>` — all events from DB (via Flow from EventDao)
- `ticketCount: StateFlow<Int>` — total tickets in active event
- `scannedTicketCount: StateFlow<Int>` — total scanned tickets in active event
- `scanResultStatus: StateFlow<ScanStatus>` — latest scan result status
- `ticketList: StateFlow<List<Ticket>>` — all tickets in active event
- `historyLogs: StateFlow<List<HistoryLog>>` — activity history for active event
- `filteredList: StateFlow<List<Ticket>>` — filtered & sorted result from ticketList
- `isOnline: StateFlow<Boolean>` — whether the last cloud operation succeeded (true=Online, false=Offline)
- `isScanLoading: StateFlow<Boolean>` — true while a cloud scan validation round-trip is in progress
- `lastSyncTime: StateFlow<Long>` — timestamp of the last successful periodic sync

Mutable filter/sort state (MutableStateFlow, can be set directly from UI):
- `searchQuery: MutableStateFlow<String>` — search text
- `filterStatus: MutableStateFlow<String>` — "Semua" | "Sudah Scan" | "Belum Scan"
- `filterCategory: MutableStateFlow<String>` — "Semua Kategori" | category name
- `sortMode: MutableStateFlow<String>` — "ID Tiket" | "Waktu Dimodifikasi" | "Terakhir Discan"
- `sortAscending: MutableStateFlow<Boolean>` — true = ascending

Public functions:
- `createAndSwitchEvent(name)` — create new event and switch to it
- `switchEvent(id)` — switch to another event
- `updateEventName(id, newName)` — rename an event
- `deleteEvent(id)` — delete event (cannot delete id=1 or the active event)
- `processQrCode(qrContent): Int` — process QR scan: returns 1=success, 2=alreadyScanned, 3=invalid
- `addTicketsFromText(text): String` — import tickets from CSV text, returns result message
- `addTicketsFromTwoBoxes(codesText, categoriesText): Pair<Boolean, String>` — add tickets from 2 input boxes
- `importCsvFromUri(uri, onResult)` — import CSV from file URI
- `clearAllTickets()` — delete all tickets in active event
- `deleteTickets(ids)` — delete tickets by ID list
- `undoDelete()` — undo last deletion (via Snackbar)
- `updateTicket(id, newQr, newType)` — edit a ticket
- `exportDataToCsvUri(uri, onlyScanned, onResult)` — export data to CSV file
- `clearScanResult()` — reset scanResultStatus to Idle
- `undoHistoryLog(log)` — undo a delete action from history
- `clearHistoryLogs()` — clear all activity history

Private functions:
- `refreshTicketCount()` — refresh all ticket-related state from DB
- `serializeTickets(tickets): String` — serialize tickets to string (format: field1::field2||field1::field2)
- `deserializeTickets(data): List<Ticket>` — deserialize string back to ticket list

### 4.6 UI Layer (`ui/`)

| File | Composable Signature | Responsibility |
|------|---------------------|----------------|
| `DashboardScreen.kt` | `DashboardScreen(viewModel, onNavigateToScanner, onNavigateToManagement, onNavigateToDatabase, onNavigateToGenerator, onNavigateToTicketEditor)` | Replaces MainMenuScreen. Event-centric dashboard that guides users contextually (Generate vs Scan vs Continue Scan) and displays current event metrics. |
| `DatabaseScreen.kt` | `DatabaseScreen(viewModel, onNavigateBack)` | Displays list of tickets. Features: search by QR code/ID, filter by status (Scanned/Unscanned), sort options. Includes multi-select for batch delete, and manual sync button. |
| `DistributionScreen.kt` | `DistributionScreen(viewModel, distViewModel, onNavigateBack)` | Multi-step wizard UI for extracting data from Google Forms, mapping columns, matching categories, validating assignments, and distributing tickets. |
| `EventSelectionDialog.kt` | `EventSelectionDialog(events, activeEventId, onEventSelected, onCreateEvent, onEditEvent, onDeleteEvent, onDismissRequest)` | Workspace selection dialog. Lists events (active on top, others sorted by lastAccessedAt). Each event can be: selected, renamed, deleted (except id=1 and active event). "Buat Event Baru" button. Validates duplicate names. Also allows picking Logo and Background images from gallery. |
| `GeneratorScreen.kt` | `GeneratorScreen(viewModel, onNavigateBack)` | Bulk generation tool. Two modes: Manual CSV (input/paste codes) and Mode Kuota (auto-generate sequences). Outputs a ZIP file containing the generated CSV and standard-sized QR image files. Supports direct DB insertion. |
| `HistoryLogDialog.kt` | `HistoryLogDialog(viewModel, onDismissRequest)` | Activity history dialog. Log list color-coded by action (green=Scan, blue=Import/Tambah, orange=Edit, red=Hapus). Expandable details (tap to view involved tickets). Undo button for delete actions that haven't been undone. "Hapus Semua" button to clear history. |
| `LoginScreen.kt` | `LoginScreen(viewModel, onLoginSuccess)` | Landing page showing app logo and tagline. Handles Google Sign-In button click to start authentication flow. |
| `MainMenuScreen.kt` | `MainMenuScreen(viewModel, ...)` | **DEPRECATED**. Replaced by `DashboardScreen.kt`. Legacy main menu with 4 primary action buttons. |
| `ManagementScreen.kt` | `ManagementScreen(viewModel, ...)` | Database setup screen. Provides options to import CSV/Excel and manual multi-line text input for Ticket ID and Categories. |
| `NumberedInputBox.kt` | `NumberedInputBox(label, placeholder, value, onValueChange, onClear, modifier)` | Reusable component. Multi-line text input with line numbers, header label, custom scrollbar, line separators between rows, clear button. Fixed height 160.dp. Used in ManagementScreen and GeneratorScreen. |
| `OnboardingOverlay.kt` | `OnboardingOverlay(onComplete)` | 4-step wizard/stepper explaining the app flow to first-time users (Buat Event, Generate, Distribusi, Scan). Displayed after first successful Google Sign-In. |
| `ScannerScreen.kt` | `ScannerScreen(viewModel, playSuccess, playError, onNavigateBack)` | QR Scanner interface using CameraX and ML Kit. Features: animated scanning line, overlay guides, manual input fallback, flashlight toggle. Validates tickets and triggers audio/haptic feedback. |
| `SplashScreen.kt` | `SplashScreen(onFinished)` | App entry point. Displays QRTix branding and runs initialization/animation before routing to Login or Onboarding/Dashboard. |
| `TicketEditorScreen.kt` | `TicketEditorScreen(viewModel, onNavigateBack)` | Visual ticket editor. Displays event background image and dummy QR code. User can drag, scale, and rotate the dummy QR. Saves transformation values to event database. |

### 4.7 Component Layer (`ui/components/`)

| File | Main Function/Component | Description |
|------|-------------------------|-------------|
| `EventBadge.kt` | `EventBadge(event)` | Persistent visual indicator for active event name using a dynamically generated color based on event ID. Ensures user awareness of the workspace context. |

### 4.8 Utility Layer (`utils/`)

| File | Class | Responsibility |
|------|-------|----------------|
| `QrCodeAnalyzer.kt` | `QrCodeAnalyzer(onQrCodeScanned)` | CameraX ImageAnalysis.Analyzer implementation. Takes camera frame → sends to ML Kit BarcodeScanning → if QR detected AND its bounding box intersects the center 60% area of the frame → calls onQrCodeScanned(value). isAnalyzing flag prevents parallel processing. |
| `SoundManager.kt` | `SoundManager(context)` | Manages sound effects via SoundPool and vibration via Vibrator. Sound files: res/raw/sound_success and res/raw/sound_error. playSuccess() = sound + 50ms vibration. playError() = sound + vibration pattern [0,100,100,100]. Must call release() in Activity.onDestroy(). |
| `TicketExporter.kt` | `TicketExporter(context)` | Function: exportTicketsToZip(tickets, fileName, onProgress). Creates ZIP file in Documents/QRTix/ via MediaStore API. ZIP contents: (1) data_impor_qrtix.csv, (2) QR PNG image per ticket named QRTix_{code}_{category}.png at 512x512px. Progress callback fires every 10 tickets. |

### 4.8 Cloud Data Layer (`data/cloud/`)

| File | Class | Responsibility |
|------|-------|----------------|
| `GoogleCredentialManager.kt` | `GoogleCredentialManager` | Configures and provides `GoogleAccountCredential` with necessary OAuth scopes for API requests. |
| `GoogleSheetsService.kt` | `GoogleSheetsService` | Wraps the Google Sheets API v4. Provides methods to create spreadsheets, read ranges, append rows, and batch update. Includes exponential backoff for rate limits. |
| `GoogleDriveService.kt` | `GoogleDriveService` | Wraps the Google Drive API v3. Provides methods to upload, download, and search files. |
| `CloudPreferences.kt` | `CloudPreferences` | Stores cloud-related IDs (Google Drive folder ID, Spreadsheet ID) in SharedPreferences. |
| `SpreadsheetManager.kt` | `SpreadsheetManager` | Handles the initialization of the Google Spreadsheet database (`QRTix_Data`) in Google Drive and creates the necessary schemas and headers. |

---

## 5. NAVIGATION

Navigation uses Jetpack Navigation Compose, defined in `MainActivity.kt`.

Start destination: `"splash"`

```
Route           → Screen               → Can navigate to
─────────────────────────────────────────────────────────────
"splash"        → SplashScreen          → "mainmenu" or "login" (auto, popUpTo splash)
"login"         → LoginScreen           → "mainmenu" (on successful auth)
"mainmenu"      → MainMenuScreen        → "scanner", "management", "database", "generator", "ticket_editor"
"scanner"       → ScannerScreen         → "management" (via button), back
"management"    → ManagementScreen      → "database", "generator", back
"database"      → DatabaseScreen        → back
"generator"     → GeneratorScreen       → back
"ticket_editor" → TicketEditorScreen    → back
```

All screens receive an `onNavigateBack` callback that calls `navController.popBackStack()` with a safety check for null previousBackStackEntry.

---

## 6. DATABASE SCHEMA

Database name: `ticketing_database`
Current version: 13

### Table: categories
```
id              INTEGER  PRIMARY KEY AUTOINCREMENT
eventId         INTEGER  NOT NULL
categoryName    TEXT     NOT NULL
categoryCode    TEXT     NOT NULL

UNIQUE INDEX: (categoryCode, eventId)
```

### Table: tickets
```
id          INTEGER  PRIMARY KEY AUTOINCREMENT
qrContent   TEXT     NOT NULL
ticketType  TEXT     NOT NULL
isScanned   INTEGER  NOT NULL (boolean: 0/1)
createdAt   INTEGER  NOT NULL (timestamp millis)
scannedAt   INTEGER  NULLABLE (timestamp millis)
isModified  INTEGER  NOT NULL (boolean: 0/1)
eventId     INTEGER  NOT NULL DEFAULT 1

UNIQUE INDEX: (qrContent, eventId)
```

### Table: events
```
id              INTEGER  PRIMARY KEY AUTOINCREMENT
name            TEXT     NOT NULL
createdAt       INTEGER  NOT NULL (timestamp millis)
lastAccessedAt  INTEGER  NOT NULL (timestamp millis)
logoPath        TEXT     NULLABLE
eventCode       TEXT     NOT NULL DEFAULT 'EVNT1'
bgPath          TEXT     NULLABLE
qrX             REAL     NOT NULL DEFAULT 0.0
qrY             REAL     NOT NULL DEFAULT 0.0
qrScale         REAL     NOT NULL DEFAULT 1.0
qrRotation      REAL     NOT NULL DEFAULT 0.0
distributionSheetId TEXT NULLABLE
```

### Table: history_logs
```
id          INTEGER  PRIMARY KEY AUTOINCREMENT
eventId     INTEGER  NOT NULL
action      TEXT     NOT NULL  ("Scan"|"Import"|"Tambah"|"Edit"|"Hapus"|"Hapus Semua")
description TEXT     NOT NULL
details     TEXT     NOT NULL DEFAULT ''
timestamp   INTEGER  NOT NULL (timestamp millis)
isUndone    INTEGER  NOT NULL DEFAULT 0 (boolean)
```

### Existing migrations:
- v5→v6: Added `isModified` column to tickets
- v6→v7: Created events table, migrated tickets to support eventId, created unique index
- v7→v8: Added `lastAccessedAt` column to events
- v8→v9: Created history_logs table
- v9→v10: Added `details` and `isUndone` columns to history_logs
- v10→v11: Added `logoPath`, `eventCode`, `bgPath`, `qrX`, `qrY`, `qrScale`, `qrRotation` to events

**IMPORTANT**: If adding/modifying schema, you MUST create a new migration object and increment the version number in the @Database annotation in AppDatabase.kt.

---

## 7. BUSINESS RULES & IMPORTANT BEHAVIORS

1. **Default Event (id=1)**: Cannot be deleted. If the active event is not found in DB, falls back to event id=1. If event id=1 also doesn't exist, it is auto-created with name "Event Default".

2. **Ticket uniqueness**: qrContent is unique PER EVENT. The same code can exist in different events.

3. **QR scan process** (in processQrCode):
   - Local pre-check: validates QR prefix against active event's eventCode. If mismatch → ScanStatus.CrossEventError (returns 4)
   - Cloud validation: calls `ticketRepository.validateAndScanOnline()` which reads and writes directly to Google Sheets in real-time (5-second timeout)
   - Not found in Sheets → ScanStatus.Invalid (returns 3)
   - Found & isScanned=true → ScanStatus.AlreadyScanned (returns 2)
   - Found & isScanned=false → writes isScanned=TRUE + scannedAt to Sheets row.
   - **Conflict Detection**: Immediately re-reads the row to confirm write success. If timestamp matches → updates local cache, logs to history → ScanStatus.Success (returns 1). If mismatch → ScanStatus.AlreadyScanned (returns 2).
   - Network error or timeout → ScanStatus.NetworkError (returns 5)

4. **Ticket import** (addTicketsFromText): Reads CSV, skips header row if detected, skips blank rows, detects internal duplicates, detects duplicates against existing DB data, inserts only new unique tickets, logs action to history.

5. **Undo mechanism**: When tickets are deleted, their data is serialized to the `details` field in HistoryLog. Serialization format: `id::qrContent::ticketType::isScanned::createdAt::scannedAt::isModified::eventId` separated by `||` between tickets. Undo = deserialize + re-insert into DB.

6. **QR analyzer scan area**: Only detects QR Codes whose bounding box intersects with the center 60% area of the camera frame. This prevents accidental scans from QR codes at the edges.

7. **Thread dispatchers**: Database operations → Dispatchers.IO. Heavy computation → Dispatchers.Default. UI updates → Dispatchers.Main.

8. **Generator screen isolation**: GeneratorScreen does NOT use TicketViewModel. It is standalone and only uses TicketExporter directly. Generated data does NOT enter the app's database — it only produces files.

9. **Sound files**: Must exist at `res/raw/sound_success` and `res/raw/sound_error`. If missing, the app still runs but without sound effects.

10. **Workspace = Event**: The terms "workspace" and "event" are used interchangeably in the UI and code. The technical entity name is `Event`.

---

## 8. ANDROID MANIFEST HIGHLIGHTS

File: `app/src/main/AndroidManifest.xml`

- Permissions: `CAMERA` (required feature), `VIBRATE`
- Application class: `.QrtixApplication`
- Single Activity: `.MainActivity`
- ML Kit auto-download config: barcode model is downloaded at app install time

---

## 9. CODE CONVENTIONS & PATTERNS

- UI is built entirely with Jetpack Compose (no XML layouts)
- State management: StateFlow in ViewModel, consumed via collectAsState() in Composable functions
- Dependency injection: Hilt (@HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel, @Inject, @Module)
- Database: Room with KSP for code generation
- Navigation: NavHost + composable() routes in MainActivity
- UI language: Indonesian (all user-facing strings are in Bahasa Indonesia)
- Date format: "dd/MM/yyyy HH:mm:ss" using SimpleDateFormat
- Expected CSV format: column 1 = QR Code, column 2 = Ticket Type (header row is optional and auto-detected)

---

## 10. FEATURE CHECKLIST

- [x] **Phase 1: Multi-Event Support & Room Schema Update** (Completed)
- [x] **Phase 2: Ticket Editing, UI/UX, & Print Adjustments** (Completed)
- [x] **Phase 3: Generator Tab, Bulk Delete, QR Logic, Background & Template** (Completed)
- [x] **Phase 4: Scanner & Validation Optimization** (Completed)
- [x] **Phase 5: Architecture Refactoring & Google Auth Foundation**
- [x] **Phase 6: Cloud Data Integration**
- [ ] **Phase 7: Real-Time Multi-Device Scanning**
- [x] **Phase 8: UI/UX Flow Redesign**
- [ ] **Phase 9: Ticket Distribution via Email**
- [x] Splash screen with animations
- [x] Multi-workspace (event) management — CRUD events, switch active event
- [x] Import tickets from CSV files
- [x] Manual ticket input (2 boxes: code + category)
- [x] Duplicate ticket validation (internal & against DB)
- [x] QR Code scanning via camera (CameraX + ML Kit)
- [x] Manual scan input (keyboard fallback)
- [x] Flashlight toggle during scanning
- [x] Sound & vibration feedback on scan
- [x] Scan result dialog (success / already scanned / unregistered)
- [x] Database list with search, status filter, category filter, sorting
- [x] Multi-select & batch delete tickets
- [x] Individual ticket editing
- [x] Export data to CSV (all data / scanned-only)
- [x] Activity history (history log) per event
- [x] Undo deletion (via Snackbar & via History Log)
- [x] Bulk ticket generator (output: ZIP containing CSV + QR images)
- [x] Quota Auto-Generate Mode for Bulk Ticket Generation
- [x] Direct Database Insertion from Generator
- [x] Delete all / reset database
- [x] Event Media (Logo and Background)
- [x] Visual Ticket Editor (QR manipulation)
- [x] Real-time online ticket validation via Google Sheets (cloud-first scanning)
- [x] Onboarding overlay wizard for first-time sign-in
- [x] Event-centric dynamic Dashboard screen
- [x] Event Context Lock & Cross-Event safety guards
- [x] Google Form response sheet linking & column auto-detection
- [x] Ticket category fuzzy matching & mapping UI

---

## CHANGE LOG

Format: `[YYYY-MM-DD] — Description of changes — (files changed/added/deleted)`

```
[2026-09-22] — Phase 12.1: Inter-Device Database Transfer: Built export and import workflow using a custom .qrtix package file containing JSON data and media assets. Added UI actions in EventSelectionDialog. — (data/transfer/DatabaseTransferManager.kt [NEW], ui/EventSelectionDialog.kt, ui/DashboardScreen.kt, viewmodel/TicketViewModel.kt)
[2026-09-22] — Phase 11.7: Post-Refactor Cleanup & Polish: Moved Sync button to Dashboard, Fixed Distribution BackButton navigation, Implemented comprehensive History Logging (Generator/Distribution/Events/Categories), Background QR Generation with Foreground Service and Synthesized Professional Sounds for Scanner — (ui/DashboardScreen.kt, ui/ScannerScreen.kt, ui/DistributionScreen.kt, viewmodel/TicketViewModel.kt, viewmodel/DistributionViewModel.kt, data/cloud/BackgroundUploadManager.kt, ui/GeneratorScreen.kt, services/GenerationTaskHolder.kt [NEW], services/GenerationService.kt [NEW], AndroidManifest.xml, res/raw/sound_success.wav [NEW], res/raw/sound_error.wav [NEW])
[2026-09-21] — Phase 11.5: UI/UX Overhaul: Added Dashboard Sidebar with Navigation, Logout feature, full Event Profile Screen, Empty State Dashboard, and global slide transitions — (ui/DashboardScreen.kt, MainActivity.kt, ui/EventProfileScreen.kt, ui/EventSelectionDialog.kt, viewmodel/TicketViewModel.kt, viewmodel/AuthViewModel.kt)
[2026-09-21] — Phase 11.6 (Part 2): Added Event Name uniqueness validation against Cloud, created BackupDetailDialog for monitoring and retrying uploads, and implemented full Profile Sync (Media & Categories) on Splash Screen — (viewmodel/TicketViewModel.kt, data/repository/EventRepository.kt, data/cloud/BackgroundUploadManager.kt, ui/BackupDetailDialog.kt, ui/DashboardScreen.kt, viewmodel/SplashViewModel.kt)
[2026-09-21] — Phase 11.6.1: Restructured Google Drive Layout into System/Profiles architecture, implemented backward compatibility migration, and added QRTix.data JSON backups per event — (data/cloud/*, viewmodel/TicketViewModel.kt, data/repository/EventRepository.kt)
[2026-09-21] — Phase 11.4: Refactored TicketEditorScreen for proportional WYSIWYG rendering, added numeric editing tools and preview dialog, and implemented native 2D Canvas DefaultTemplateRenderer for tickets without background — (ui/TicketEditorScreen.kt, utils/TicketExporter.kt, utils/DefaultTemplateRenderer.kt)
[2026-09-21] — Phase 11.3.6: Added Category Code Validation limits and cloud checking — (ui/CategoryManagementDialog.kt, viewmodel/TicketViewModel.kt, data/repository/CategoryRepository.kt)
[2026-09-21] — Phase 11.3.5: Added Event Code input when creating events, enforced uniqueness across local and cloud DB — (ui/EventSelectionDialog.kt, viewmodel/TicketViewModel.kt, data/repository/EventRepository.kt)
[2026-09-21] — Phase 11.3.4: Added Inline Category creation in Generator Screen — (ui/GeneratorScreen.kt)
[2026-09-21] — Phase 11.3.3: Fixed Prefix in Mode Kuota to use Event Code Automatically — (ui/GeneratorScreen.kt)
[2026-09-21] — Phase 11.3.2: Auto-Insert Tickets to Database from Quota Mode and removed directInsert checkbox — (ui/GeneratorScreen.kt, viewmodel/TicketViewModel.kt)
[2026-09-21] — Phase 11.3.1: Removed Manual Input & File Import Setup Database screen — (ui/ManagementScreen.kt, ui/DashboardScreen.kt, MainActivity.kt, viewmodel/TicketViewModel.kt)
[2026-09-21] — Phase 11.2.3: Reworked Splash Screen to show real sync progress via SplashViewModel and stripped sync logic from AuthViewModel — (ui/SplashScreen.kt, viewmodel/SplashViewModel.kt, viewmodel/AuthViewModel.kt, MainActivity.kt)
[2026-09-21] — Phase 11.2.2: Blocked App Operations When Offline by adding full-screen OfflineOverlay to MainActivity — (ui/OfflineOverlay.kt, MainActivity.kt)
[2026-09-21] — Phase 11.2.1: Implemented Real-Time Network Monitoring using ConnectivityManager.NetworkCallback — (utils/NetworkMonitor.kt, di/AppModule.kt, viewmodel/TicketViewModel.kt)
[2026-09-21] — Phase 11.1.6: Fixed Distribution "Gagal Menyimpan..." by adding error handling, detailed messaging and retry button — (data/repository/DistributionRepository.kt, viewmodel/DistributionViewModel.kt, ui/DistributionScreen.kt)
[2026-09-21] — Phase 11.1.5: Fixed Undo Delete Not Restoring Data in Cloud by adding error handling and data validation — (viewmodel/TicketViewModel.kt)
[2026-09-21] — Phase 11.1.4: Fixed "Masukkan ke Database" Button Not Working in Quota Mode by refreshing state on insert — (viewmodel/TicketViewModel.kt, ui/GeneratorScreen.kt)
[2026-09-21] — Phase 11.1.3: Fixed QR Image Upload (Only 2 Tickets Uploaded to Drive) by taking queue snapshot, targeted invalidation, and adding retry mechanism — (data/cloud/BackgroundUploadManager.kt, data/cloud/DriveFolderManager.kt)
[2026-09-21] — Phase 11.1.2: Fixed First 3 Tickets Not Entering Database in Quota Mode by fixing append range and adding Mutex + atomic generation — (data/repository/TicketRepository.kt, data/repository/HistoryLogRepository.kt)
[2026-09-21] — Phase 11.1.1: Fixed Profile/Event Disappearing on App Restart (Sync Safety) via Safe Upsert and added distributionSheetId to Google Sheets sync — (data/repository/EventRepository.kt, data/cloud/SpreadsheetManager.kt, data/EventDao.kt, di/AppModule.kt)
[2026-09-17] — Bug Fix: Handled Google OAuth UserRecoverableAuthIOException for remote consent — (viewmodel/AuthViewModel.kt, ui/LoginScreen.kt)
[2026-09-16] — Phase 9.1: Distribution Sheet Linking & Mapping — (ui/DistributionScreen.kt [NEW], utils/ColumnAutoDetector.kt [NEW], utils/CategoryMatcher.kt [NEW], MainActivity.kt, ui/DashboardScreen.kt, viewmodel/TicketViewModel.kt)
[2026-09-16] — Phase 8.2 & 8.3: Dashboard Redesign & Event Safety Guards — (ui/DashboardScreen.kt [NEW], ui/components/EventBadge.kt [NEW], ui/ManagementScreen.kt, ui/DatabaseScreen.kt, ui/GeneratorScreen.kt, MainActivity.kt)
[2026-09-16] — Phase 8.1: Login & Onboarding Screen — (ui/LoginScreen.kt, ui/OnboardingOverlay.kt [NEW], MainActivity.kt, data/AuthPreferences.kt, viewmodel/AuthViewModel.kt)
[2026-09-16] — Phase 7.2: Multi-Gate Conflict Prevention & Sync Indicator — (data/repository/TicketRepository.kt, viewmodel/TicketViewModel.kt, ui/ScannerScreen.kt)
[2026-09-16] — Phase 7.1: Online Ticket Validation via Google Sheets — (data/repository/TicketRepository.kt, viewmodel/TicketViewModel.kt, ui/ScannerScreen.kt)
[2026-09-16] — Phase 6.5: History Log Sync via Google Sheets — (data/repository/HistoryLogRepository.kt, data/HistoryLog.kt, viewmodel/AuthViewModel.kt)
[2026-09-16] — Phase 6.4: Google Drive Media Storage — (data/cloud/MediaManager.kt [NEW], data/cloud/SpreadsheetManager.kt, data/cloud/CloudPreferences.kt, viewmodel/TicketViewModel.kt, ui/MainMenuScreen.kt, ui/ScannerScreen.kt, ui/GeneratorScreen.kt)
[2026-09-16] — Phase 6.3: Ticket CRUD via Google Sheets — (data/repository/TicketRepository.kt, data/cloud/GoogleSheetsService.kt, di/AppModule.kt, viewmodel/TicketViewModel.kt, ui/DatabaseScreen.kt)
[2026-09-16] — Phase 6.2: Event CRUD via Google Sheets — (data/repository/EventRepository.kt, data/cloud/GoogleSheetsService.kt, data/EventDao.kt, di/AppModule.kt, viewmodel/AuthViewModel.kt)
[2026-09-16] — Phase 6.1: Spreadsheet Schema & Initialization — (data/cloud/SpreadsheetManager.kt [NEW], data/cloud/CloudPreferences.kt [NEW], data/cloud/GoogleDriveService.kt, data/cloud/GoogleSheetsService.kt, di/AppModule.kt, viewmodel/AuthViewModel.kt)
[2026-09-16] — Phase 5.3: Google API Service Layer Setup — (build.gradle.kts, data/cloud/GoogleCredentialManager.kt [NEW], data/cloud/GoogleSheetsService.kt [NEW], data/cloud/GoogleDriveService.kt [NEW], di/AppModule.kt)
[2026-09-16] — Phase 5.2: Google Sign-In Integration — (build.gradle.kts, strings.xml, data/AuthPreferences.kt [NEW], viewmodel/AuthViewModel.kt [NEW], ui/LoginScreen.kt [NEW], MainActivity.kt)
[2026-09-16] — Phase 5.1: Repository Pattern Refactoring — (data/repository/TicketRepository.kt [NEW], data/repository/EventRepository.kt [NEW], data/repository/HistoryLogRepository.kt [NEW], viewmodel/TicketViewModel.kt, di/AppModule.kt)
[2026-09-15] — Phase 4: Scanner Optimization, Auto-Resume, Haptics, Capitalization & Cross-Event Validation — (ui/ScannerScreen.kt, data/TicketDao.kt, viewmodel/TicketViewModel.kt, ui/GeneratorScreen.kt, ui/EventSelectionDialog.kt)
[2026-09-15] — Phase 3: Generator UI Revamp, Direct DB Insert, memory optimization, KeepScreenOn — (ui/GeneratorScreen.kt, utils/TicketExporter.kt, viewmodel/TicketViewModel.kt)
[2026-09-15] — Phase 2: Core QR Engine Upgrade & Visual Ticket Editor — (utils/TicketExporter.kt, ui/TicketEditorScreen.kt [NEW], ui/EventSelectionDialog.kt, ui/MainMenuScreen.kt, MainActivity.kt)
[2026-09-15] — Phase 1: Event Database Migration to v11 and Ticket Formatters — (data/Event.kt, data/AppDatabase.kt, utils/TicketFormatters.kt [NEW])
[2026-09-15] — Initial project documentation created — (PROJECT_DOCUMENTATION.md, TASKS.md)
```

<!-- 
INSTRUCTIONS FOR UPDATING CHANGE LOG:
After completing a task, add a new entry ABOVE the last entry using the same format. Example:

[2026-09-20] — Added dark mode support — (ui/theme/Theme.kt [NEW], ui/SplashScreen.kt, ui/MainMenuScreen.kt)
[2026-09-18] — Fixed CSV import duplicate bug — (viewmodel/TicketViewModel.kt)

If your changes affect the project architecture, add new files, or modify the database schema,
UPDATE THE RELEVANT SECTIONS ABOVE as well (file structure, schema, feature checklist, etc.)
-->
