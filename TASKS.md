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

#### Task 1.1: Event Database Migration (v10 -> v11)
- **Status**: [ ]
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

---

#### Task 1.2: Ticket Code Standardization & Core Generation Logic
- **Status**: [ ]
- **Priority**: High
- **Description**: 
  - Create a utility function to format ticket codes into the standard format: `[PREFIX]-[CATEGORY]-[4-DIGIT]-[4-RANDOM]`.
  - Implement a random token generator function that excludes ambiguous characters (`0`, `O`, `1`, `I`) to prevent optical misreading.
- **Affected files**: `utils/TicketFormatters.kt (NEW)`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Create unit tests or self-verification functions where applicable.

---

### Phase 2: Core QR Engine & Visual UI

#### Task 2.1: Core QR Engine Upgrade (ZXing + Canvas)
- **Status**: [ ]
- **Priority**: High
- **Description**: 
  - Update QR Code generation logic to support High Error Correction (Level H), ensuring scannability even with an embedded logo overlay.
  - Add logic (using Android `Canvas`) to overlay the event logo at the center of the QR Code (maximum 20% of total QR area).
  - Add Auto-Scaling HRI Text (human-readable ticket code printed beneath the QR code) that automatically scales down in font size if the text is too long.
- **Affected files**: `utils/TicketExporter.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Ensure a white quiet-zone padding is preserved around the QR code boundary for optimal scanner detection.

---

#### Task 2.2: Visual Ticket Editor UI
- **Status**: [ ]
- **Priority**: High
- **Description**: 
  - Create a new Jetpack Compose screen for the Visual Ticket Editor.
  - Allow users to interactively manipulate the QR code position: drag (X, Y), pinch-to-zoom (Scale), and rotation over an event background template.
  - Persist the final transformation values (`qrX`, `qrY`, `qrScale`, `qrRotation`) to the active Event record in the database.
- **Affected files**: `ui/TicketEditorScreen.kt (NEW)`, `navigation/NavGraph (MainActivity.kt)`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Leverage `pointerInput` and `detectTransformGestures` from Jetpack Compose.

---

#### Task 2.3: Event Profile UI Updates (Logo & Background Picker)
- **Status**: [ ]
- **Priority**: High
- **Description**: 
  - Update `EventSelectionDialog.kt` to allow users to pick an event logo and background template image from the device gallery (using `ActivityResultContracts.GetContent()`).
  - Save the selected image paths to the event record (`logoPath`, `bgPath`).
  - Display the active event logo in the header UI (`MainMenuScreen` and `ScannerScreen`).
- **Affected files**: `ui/EventSelectionDialog.kt`, `ui/MainMenuScreen.kt`, `ui/ScannerScreen.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Properly handle URI permissions or copy images to local app internal storage so paths remain permanently accessible across app restarts.

---

### Phase 3: Generator & Exporter

#### Task 3.1: Advanced Batch Exporter & Memory Management
- **Status**: [ ]
- **Priority**: High
- **Description**: 
  - Integrate the QR generator engine from Task 2.1 with the template position parameters from Task 2.2.
  - Implement a batch export function supporting Auto-Scaling Resolution (min 1000px, max ~2000px).
  - Implement aggressive Bitmap recycling (`bitmap.recycle()`) and memory management during batch generation to prevent `OutOfMemoryError` (OOM).
  - File naming format: `QRTix_[EVENT_CODE]_[CATEGORY]_[SEQUENCE_NO].jpg`.
- **Affected files**: `utils/TicketExporter.kt`, `ui/GeneratorScreen.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: The entire export process must run inside a background coroutine dispatcher (`Dispatchers.IO` or `Dispatchers.Default`).

---

#### Task 3.2: Generator UI Form Revamp (Quota Mode)
- **Status**: [ ]
- **Priority**: High
- **Description**: 
  - Build a "Quota Auto-Generate" input mode in `GeneratorScreen`.
  - Input fields: Category, Quota Count, Prefix (Event Code), and Starting Sequence Number.
  - Retain the existing manual text/CSV input mode for advanced/flexible workflows.
- **Affected files**: `ui/GeneratorScreen.kt`

---

#### Task 3.3: Direct Database Insertion Logic (via ViewModel)
- **Status**: [ ]
- **Priority**: High
- **Description**: 
  - Add a checkbox: "Directly insert tickets into active Event Database" in `GeneratorScreen`.
  - Implement batch ticket insertion logic via `TicketViewModel` (following MVVM pattern, avoiding direct DAO calls from Composable UI).
- **Affected files**: `ui/GeneratorScreen.kt`, `viewmodel/TicketViewModel.kt`, `PROJECT_DOCUMENTATION.md`

---

#### Task 3.4: Prevent Screen Timeout (KeepScreenOn) During Generation
- **Status**: [ ]
- **Priority**: Medium
- **Description**: 
  - Keep the device display on (`FLAG_KEEP_SCREEN_ON`) while batch generation is in progress to prevent CPU throttling or OS killing background tasks.
  - Clear the `KeepScreenOn` flag once generation completes or fails.
- **Affected files**: `ui/GeneratorScreen.kt`

---

### Phase 4: Scanner & Validation Optimization

#### Task 4.1: Scanner Optimization — Auto-Resume / Continuous Scanning
- **Status**: [ ]
- **Priority**: High
- **Description**: 
  - Eliminate scanning bottlenecks with camera auto-resume.
  - Valid (Success): Display green banner for 1.0–1.5s + success audio, then automatically resume camera scanning without requiring user to tap "Continue".
  - Error (Invalid / Already Scanned): Display a red blocking alert modal requiring explicit dismissal by staff.
  - Haptic feedback: Distinct vibration patterns for success vs error/warning.
- **Affected files**: `ui/ScannerScreen.kt`, `utils/SoundManager.kt`
- **Notes**: Implement debounce/throttle logic to avoid immediate duplicate triggers on the same physical ticket.

---

#### Task 4.2: System-wide Full Capitalization & Emergency Manual Input
- **Status**: [ ]
- **Priority**: Medium
- **Description**: 
  - Auto-uppercase across input fields: Event Code, Category, Scanner Manual Input (using `KeyboardCapitalization.Characters`).
  - Strict 4-digit numpad for sequence numbers and strict 4-character keyboard for secret tokens on the Emergency Manual Input scanner dialog.
  - Add `COLLATE NOCASE` to `TicketDao` queries and `.uppercase().trim()` in ViewModel to ensure case-insensitive validation.
- **Affected files**: `ui/ScannerScreen.kt`, `ui/GeneratorScreen.kt`, `ui/EventSelectionDialog.kt`, `data/TicketDao.kt`, `viewmodel/TicketViewModel.kt`

---

#### Task 4.3: Smart Detection & Alert for Cross-Event Ticket Scans
- **Status**: [ ]
- **Priority**: Low
- **Description**: 
  - Display a distinct, specific error message if a scanned ticket belongs to a different event.
  - Validate the scanned QR prefix against the active event's `eventCode`.
- **Affected files**: `viewmodel/TicketViewModel.kt`, `ui/ScannerScreen.kt`

---

### Phase 5: Architecture Refactoring & Google Auth Foundation

#### Task 5.1: Repository Pattern Refactoring
- **Status**: [ ]
- **Priority**: High
- **Description**:
  - Introduce a Repository layer between `TicketViewModel` and the DAOs (`TicketDao`, `EventDao`, `HistoryLogDao`).
  - Create `TicketRepository`, `EventRepository`, and `HistoryLogRepository` classes.
  - Refactor `TicketViewModel` to call Repository methods instead of calling DAOs directly.
  - The Repository will initially just delegate to the local DAOs (no behavior change), but this abstraction is required so that Phase 6 can swap/extend the data source to Google Sheets without modifying ViewModel logic.
- **Affected files**: `data/repository/TicketRepository.kt (NEW)`, `data/repository/EventRepository.kt (NEW)`, `data/repository/HistoryLogRepository.kt (NEW)`, `viewmodel/TicketViewModel.kt`, `di/AppModule.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: This is a pure refactoring task. All existing app behavior must remain identical after this change. Run full manual regression test (import, scan, delete, undo, export, event switching) to verify.

---

#### Task 5.2: Google Sign-In Integration
- **Status**: [ ]
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

---

#### Task 5.3: Google API Service Layer Setup
- **Status**: [ ]
- **Priority**: High
- **Description**:
  - Add Google API Client library dependencies (`google-api-services-sheets`, `google-api-services-drive`, `google-api-client-android`, `google-http-client-gson`).
  - Create a `GoogleSheetsService` class that wraps the Sheets API v4 client. Provide methods for: creating a spreadsheet, reading a range, writing/appending rows, updating cells, and batch operations.
  - Create a `GoogleDriveService` class that wraps the Drive API v3 client. Provide methods for: uploading a file, downloading a file, and listing files in the app's folder.
  - Both services should accept the authenticated user's credential from `AuthViewModel` and handle common API errors (401 token expired → trigger re-auth, 429 rate limit → retry with backoff, network errors → throw descriptive exceptions).
  - Provide these services via Hilt DI as `@Singleton`.
- **Affected files**: `data/cloud/GoogleSheetsService.kt (NEW)`, `data/cloud/GoogleDriveService.kt (NEW)`, `di/AppModule.kt`, `app/build.gradle.kts`, `PROJECT_DOCUMENTATION.md`
- **Notes**: All API calls must run on `Dispatchers.IO`. Implement exponential backoff for rate-limited (429) responses.

---

### Phase 6: Cloud Data Integration (Google Sheets & Drive)

#### Task 6.1: Spreadsheet Schema & Initialization
- **Status**: [ ]
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

---

#### Task 6.2: Event CRUD via Google Sheets
- **Status**: [ ]
- **Priority**: High
- **Description**:
  - Update `EventRepository` to perform all Event CRUD operations against the `Events` sheet in Google Sheets (create, read, update, delete).
  - On app launch (after login), fetch all events from Sheets and cache them in local Room DB for fast UI rendering.
  - On any write operation (create/update/delete event), write to Sheets first (source of truth), then update local cache.
  - Handle the "Default Event" (id=1) rule: if no events exist in Sheets, auto-create the default event.
- **Affected files**: `data/repository/EventRepository.kt`, `data/cloud/GoogleSheetsService.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Implement proper error handling: if Sheets write fails (network error), show a user-facing error toast in Indonesian and do NOT update local cache (to maintain consistency).

---

#### Task 6.3: Ticket CRUD via Google Sheets
- **Status**: [ ]
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

---

#### Task 6.4: Google Drive Media Storage (Logo & Background Images)
- **Status**: [ ]
- **Priority**: Medium
- **Description**:
  - When a user picks a logo or background image for an event (Task 2.3), upload the image to Google Drive in a dedicated app folder (e.g., `QRTix_Media/`).
  - Store the Drive file ID (not local path) in the `Events` sheet columns (`logoFileId`, `bgFileId`).
  - When displaying the logo/background in the app, download from Drive and cache locally (using a simple file cache in `context.cacheDir`).
  - This ensures images are accessible from any device signed into the same Google account.
- **Affected files**: `data/cloud/GoogleDriveService.kt`, `data/repository/EventRepository.kt`, `ui/EventSelectionDialog.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: Implement a simple cache-first strategy: check local cache → if missing or stale, download from Drive. Compress images before upload (max 1MB recommended) to save Drive quota.

---

#### Task 6.5: History Log Sync via Google Sheets
- **Status**: [ ]
- **Priority**: Low
- **Description**:
  - Update `HistoryLogRepository` to write all history log entries to the `HistoryLogs` sheet in Google Sheets.
  - Logs are append-only in normal operation (new entries are appended to the sheet).
  - Undo operations update the `isUndone` column in the corresponding row.
  - "Clear all history" deletes all rows for the active event from the sheet.
  - On app launch, sync history logs from Sheets to local cache.
- **Affected files**: `data/repository/HistoryLogRepository.kt`, `data/cloud/GoogleSheetsService.kt`, `PROJECT_DOCUMENTATION.md`
- **Notes**: History logs are less critical than ticket data. If a Sheets write fails for a log entry, it is acceptable to log it locally only and retry on next sync.

---

### Phase 7: Real-Time Multi-Device Scanning

#### Task 7.1: Online Ticket Validation via Sheets API
- **Status**: [ ]
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

---

#### Task 7.2: Multi-Gate Conflict Prevention & Sync Indicator
- **Status**: [ ]
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

---

### Phase 8: UI/UX Flow Redesign

#### Task 8.1: Login & Onboarding Screen
- **Status**: [ ]
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

---

#### Task 8.2: Event-Centric Dashboard Navigation Redesign
- **Status**: [ ]
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

---

#### Task 8.3: Event Context Lock & Cross-Event Safety Guards
- **Status**: [ ]
- **Priority**: Medium
- **Description**:
  - Add persistent visual indicators on EVERY screen showing the active event name and color-coded badge (each event gets a distinct accent color) so the user always knows which event they're operating on.
  - Before critical operations (import tickets, delete all, scan), show a **confirmation dialog** that explicitly states the active event name: e.g., "Anda akan mengimpor tiket ke event **Konser Rock**. Lanjutkan?".
  - In `GeneratorScreen`: if the "Direct insert to DB" checkbox (Task 3.3) is enabled, show the target event name prominently and require explicit confirmation.
  - Prevent event switching while a scan session is actively in progress (require the user to explicitly "end" the scan session first).
- **Affected files**: `ui/ScannerScreen.kt`, `ui/ManagementScreen.kt`, `ui/DatabaseScreen.kt`, `ui/GeneratorScreen.kt`, `ui/components/EventBadge.kt (NEW)`, `PROJECT_DOCUMENTATION.md`
- **Notes**: The event badge component should be reusable across all screens. Use a consistent position (e.g., top-left or integrated into the top app bar) for muscle memory.

---

### Phase 9: Ticket Distribution via Email

#### Task 9.1: Google Form Response Sheet Linking, Auto-Detect Column Mapping & Category Mapping
- **Status**: [ ]
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

---

#### Task 9.2: Distribution Matching, Validation & Persistence
- **Status**: [ ]
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

---

#### Task 9.3: Gmail API Integration & Batch Email Sending
- **Status**: [ ]
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

#### Task 9.4: Distribution Status Dashboard & Retry
- **Status**: [ ]
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

---

## COMPLETED TASKS

<!-- Completed tasks are moved here by the AI agent after finishing -->

(No completed tasks yet.)

