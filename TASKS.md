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

