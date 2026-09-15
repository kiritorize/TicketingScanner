# Agent Instructions & Guidelines for QRTix

## 1. Mandatory Language Rule (STRICT)
- **Documentation & Tasks**: All content in `PROJECT_DOCUMENTATION.md`, `TASKS.md`, code comments, pull requests, and commit messages MUST be written in **ENGLISH ONLY**.
- **No Indonesian in Documentation**: Never write task descriptions, documentation sections, technical notes, or change log entries in Indonesian or any other non-English language.
- **App UI Strings Exception**: The Android application's user interface is in Indonesian (Bahasa Indonesia). When writing or modifying user-facing UI labels, toasts, or dialog messages in Kotlin code, keep them in Bahasa Indonesia as specified. All developer documentation and tasks, however, remain strictly in English.

## 2. Task Execution Workflow
- Work on **ONLY ONE task at a time** from `TASKS.md`. Do not batch multiple tasks into a single session or response.
- Follow the sequence outlined in `TASKS.md` from top to bottom.
- Always verify that the project compiles (`./gradlew assembleDebug` or equivalent build checks) before marking a task as completed (`[x]`).
- Update `PROJECT_DOCUMENTATION.md` in English immediately after completing any task that introduces new files, changes architecture, or alters the database schema.
