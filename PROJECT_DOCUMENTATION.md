# PROJECT DOCUMENTATION — QRTix (TicketingScanner)

> **INSTRUCTIONS FOR AI AGENT:**
> 1. Read this file FIRST before doing anything. This file contains the full project context.
> 2. Read `TASKS.md` (same root directory) to know what work needs to be done.
> 3. After completing work, UPDATE the relevant sections of this file (structure, new files, new features, schema changes, etc.) and add an entry to the CHANGE LOG at the bottom.
> 4. Do NOT rewrite this entire file. Only update sections affected by your changes.
> 5. All paths in this document are relative to the project root unless stated otherwise.

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
| `AppModule.kt` | Object `AppModule` | Hilt Module (@InstallIn SingletonComponent). Provides: AppDatabase, TicketDao, EventDao, HistoryLogDao, EventPreferences. All @Singleton. |

### 4.4 ViewModel Layer (`viewmodel/`)

| File | Contents | Responsibility |
|------|----------|----------------|
| `TicketViewModel.kt` | Sealed class `ScanStatus`, Class `TicketViewModel` | All business logic for the app. Details below. |

**ScanStatus** (sealed class — QR scan result states):
- `ScanStatus.Success(ticket, scanTimeString)` — ticket valid, successfully scanned
- `ScanStatus.AlreadyScanned(ticket, scanTimeString)` — ticket was already scanned before
- `ScanStatus.Invalid(code)` — QR code not found in database
- `ScanStatus.Idle` — no scan result (initial/reset state)

**TicketViewModel** — injected dependencies: TicketDao, EventDao, EventPreferences, HistoryLogDao, Context.

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

### 4.5 UI Layer (`ui/`)

| File | Composable Signature | Responsibility |
|------|---------------------|----------------|
| `SplashScreen.kt` | `SplashScreen(onFinished)` | Splash/loading screen. Logo bounce animation, pulsing glow effect, progress bar 0→100%. Auto-calls onFinished() when done. Theme color: lavender (#A4A2E4). Does NOT use ViewModel. |
| `MainMenuScreen.kt` | `MainMenuScreen(viewModel, onNavigateToScanner, onNavigateToManagement, onNavigateToDatabase, onNavigateToGenerator)` | Main menu hub. Header shows active workspace name (tap to open EventSelectionDialog). 4 navigation buttons: Mulai Scan, Setup Database, List Database, Alat Generator Tiket. Copyright footer. |
| `ScannerScreen.kt` | `ScannerScreen(viewModel, playSuccess, playError, onNavigateToManagement, onNavigateBack)` | QR scanning screen. Full-screen CameraX preview. Scan frame (70% screen width) with animated green scan line moving up/down. Dark scrim outside scan area. Top bar: event name, scan counter ("Scan: X / Y"), flashlight toggle. Popup dialog for scan results (green=success, red=error/warning). Manual text input at bottom of screen. Requests camera permission on first open. |
| `ManagementScreen.kt` | `ManagementScreen(viewModel, onNavigateToDatabase, onNavigateToGenerator, playSuccess, playError, onNavigateBack)` | Database setup screen. Info card showing total registered tickets. Link to Generator. CSV import button. Two NumberedInputBox inputs (Kode Unik + Kategori). "Tambahkan dari Input" button. Validates duplicates both internally and against DB. Error dialog for duplicate issues. |
| `DatabaseScreen.kt` | `DatabaseScreen(viewModel, onNavigateBack)` | Full database list screen. Search bar. Status filter (Semua/Sudah Scan/Belum Scan) & category filter. Sorting (3 modes, asc/desc). LazyColumn of ticket Cards. Multi-select via long-press (select all, batch delete). Individual ticket edit (AlertDialog). CSV export (all data or scanned-only). "Hapus Semua / Reset Database" button with confirmation. HistoryLogDialog access. Snackbar undo after deletion. BackHandler intercepts back press during selection mode. |
| `GeneratorScreen.kt` | `GeneratorScreen(onNavigateBack)` | Bulk ticket generator. CSV import. Two NumberedInputBox inputs. Validates row count match & duplicate codes. Generates ZIP (via TicketExporter) containing: data_impor_qrtix.csv + QR PNG images per ticket. Progress dialog during generation. Files saved to Documents/QRTix/. Does NOT use TicketViewModel (standalone screen). |
| `EventSelectionDialog.kt` | `EventSelectionDialog(events, activeEventId, onEventSelected, onCreateEvent, onEditEvent, onDeleteEvent, onDismissRequest)` | Workspace selection dialog. Lists events (active on top, others sorted by lastAccessedAt). Each event can be: selected, renamed, deleted (except id=1 and active event). "Buat Event Baru" button. Validates duplicate names. |
| `HistoryLogDialog.kt` | `HistoryLogDialog(viewModel, onDismissRequest)` | Activity history dialog. Log list color-coded by action (green=Scan, blue=Import/Tambah, orange=Edit, red=Hapus). Expandable details (tap to view involved tickets). Undo button for delete actions that haven't been undone. "Hapus Semua" button to clear history. |
| `NumberedInputBox.kt` | `NumberedInputBox(label, placeholder, value, onValueChange, onClear, modifier)` | Reusable component. Multi-line text input with line numbers, header label, custom scrollbar, line separators between rows, clear button. Fixed height 160.dp. Used in ManagementScreen and GeneratorScreen. |

### 4.6 Utility Layer (`utils/`)

| File | Class | Responsibility |
|------|-------|----------------|
| `QrCodeAnalyzer.kt` | `QrCodeAnalyzer(onQrCodeScanned)` | CameraX ImageAnalysis.Analyzer implementation. Takes camera frame → sends to ML Kit BarcodeScanning → if QR detected AND its bounding box intersects the center 60% area of the frame → calls onQrCodeScanned(value). isAnalyzing flag prevents parallel processing. |
| `SoundManager.kt` | `SoundManager(context)` | Manages sound effects via SoundPool and vibration via Vibrator. Sound files: res/raw/sound_success and res/raw/sound_error. playSuccess() = sound + 50ms vibration. playError() = sound + vibration pattern [0,100,100,100]. Must call release() in Activity.onDestroy(). |
| `TicketExporter.kt` | `TicketExporter(context)` | Function: exportTicketsToZip(tickets, fileName, onProgress). Creates ZIP file in Documents/QRTix/ via MediaStore API. ZIP contents: (1) data_impor_qrtix.csv, (2) QR PNG image per ticket named QRTix_{code}_{category}.png at 512x512px. Progress callback fires every 10 tickets. |

---

## 5. NAVIGATION

Navigation uses Jetpack Navigation Compose, defined in `MainActivity.kt`.

Start destination: `"splash"`

```
Route           → Screen               → Can navigate to
─────────────────────────────────────────────────────────────
"splash"        → SplashScreen          → "mainmenu" (auto, popUpTo splash inclusive)
"mainmenu"      → MainMenuScreen        → "scanner", "management", "database", "generator"
"scanner"       → ScannerScreen         → "management" (via button), back
"management"    → ManagementScreen      → "database", "generator", back
"database"      → DatabaseScreen        → back
"generator"     → GeneratorScreen       → back
```

All screens receive an `onNavigateBack` callback that calls `navController.popBackStack()` with a safety check for null previousBackStackEntry.

---

## 6. DATABASE SCHEMA

Database name: `ticketing_database`
Current version: 10

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

**IMPORTANT**: If adding/modifying schema, you MUST create a new migration object and increment the version number in the @Database annotation in AppDatabase.kt.

---

## 7. BUSINESS RULES & IMPORTANT BEHAVIORS

1. **Default Event (id=1)**: Cannot be deleted. If the active event is not found in DB, falls back to event id=1. If event id=1 also doesn't exist, it is auto-created with name "Event Default".

2. **Ticket uniqueness**: qrContent is unique PER EVENT. The same code can exist in different events.

3. **QR scan process** (in processQrCode):
   - Looks up ticket by qrContent + active eventId in DB
   - Not found → ScanStatus.Invalid (returns 3)
   - Found & isScanned=true → ScanStatus.AlreadyScanned (returns 2)
   - Found & isScanned=false → marks as scanned, logs to history, ScanStatus.Success (returns 1)

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
- [x] Delete all / reset database

---

## CHANGE LOG

Format: `[YYYY-MM-DD] — Description of changes — (files changed/added/deleted)`

```
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
