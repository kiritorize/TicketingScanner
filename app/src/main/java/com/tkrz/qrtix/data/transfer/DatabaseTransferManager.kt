package com.tkrz.qrtix.data.transfer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tkrz.qrtix.data.Event
import com.tkrz.qrtix.data.HistoryLog
import com.tkrz.qrtix.data.Ticket
import com.tkrz.qrtix.data.TicketCategory
import com.tkrz.qrtix.data.repository.CategoryRepository
import com.tkrz.qrtix.data.repository.EventRepository
import com.tkrz.qrtix.data.repository.HistoryLogRepository
import com.tkrz.qrtix.data.repository.TicketRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class EventExportData(
    val version: Int = 1,
    val event: Event,
    val categories: List<TicketCategory>,
    val tickets: List<Ticket>,
    val historyLogs: List<HistoryLog>
)

@Singleton
class DatabaseTransferManager @Inject constructor(
    private val eventRepository: EventRepository,
    private val ticketRepository: TicketRepository,
    private val categoryRepository: CategoryRepository,
    private val historyLogRepository: HistoryLogRepository,
    @ApplicationContext private val context: Context
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportEvent(eventId: Long): Intent? = withContext(Dispatchers.IO) {
        try {
            val event = eventRepository.getEventById(eventId) ?: return@withContext null
            val categories = categoryRepository.getCategoriesForEvent(eventId)
            val tickets = ticketRepository.getAllTickets(eventId)
            val historyLogs = historyLogRepository.getLogsForEvent(eventId)

            val exportData = EventExportData(
                event = event,
                categories = categories,
                tickets = tickets,
                historyLogs = historyLogs
            )

            val jsonString = gson.toJson(exportData)

            val fileName = "${event.name.replace("[^a-zA-Z0-9.-]".toRegex(), "_")}_Backup.qrtix"
            
            // Create in cache dir first to get a File reference to share
            val cacheDir = File(context.cacheDir, "QRTix_Transfers")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val zipFile = File(cacheDir, fileName)
            val zipOutputStream = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))

            // 1. Write JSON data
            zipOutputStream.putNextEntry(ZipEntry("database.json"))
            zipOutputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            zipOutputStream.closeEntry()

            // 2. Write Logo if exists
            event.logoPath?.let { path ->
                val logoFile = File(path)
                if (logoFile.exists()) {
                    zipOutputStream.putNextEntry(ZipEntry("logo.jpg"))
                    FileInputStream(logoFile).use { it.copyTo(zipOutputStream) }
                    zipOutputStream.closeEntry()
                }
            }

            // 3. Write Background if exists
            event.bgPath?.let { path ->
                val bgFile = File(path)
                if (bgFile.exists()) {
                    zipOutputStream.putNextEntry(ZipEntry("background.jpg"))
                    FileInputStream(bgFile).use { it.copyTo(zipOutputStream) }
                    zipOutputStream.closeEntry()
                }
            }

            zipOutputStream.close()

            // Save to Documents as backup copy
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/QRTix")
                    }
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(zipFile).use { it.copyTo(out) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Return Share Intent
            val uriForShare = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                zipFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uriForShare)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return@withContext Intent.createChooser(shareIntent, "Share Event Backup")
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun importEvent(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(uri) ?: return@withContext false
            val zipInputStream = ZipInputStream(inputStream)
            
            var exportData: EventExportData? = null
            var tempLogoFile: File? = null
            var tempBgFile: File? = null

            val cacheDir = File(context.cacheDir, "QRTix_Transfers_Import")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            var entry = zipInputStream.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "database.json" -> {
                        val jsonString = zipInputStream.readBytes().toString(Charsets.UTF_8)
                        exportData = gson.fromJson(jsonString, EventExportData::class.java)
                    }
                    "logo.jpg" -> {
                        tempLogoFile = File(cacheDir, "temp_logo.jpg")
                        FileOutputStream(tempLogoFile).use { out -> zipInputStream.copyTo(out) }
                    }
                    "background.jpg" -> {
                        tempBgFile = File(cacheDir, "temp_bg.jpg")
                        FileOutputStream(tempBgFile).use { out -> zipInputStream.copyTo(out) }
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            if (exportData == null) return@withContext false

            // Insert new event
            val oldEvent = exportData.event
            val newEventCode = oldEvent.eventCode + "_IMPORTED_" + System.currentTimeMillis().toString().takeLast(4)
            var newEvent = oldEvent.copy(id = 0, eventCode = newEventCode, name = oldEvent.name + " (Imported)")
            val newEventId = eventRepository.insertEvent(newEvent)

            // Handle images
            val mediaDir = File(context.filesDir, "media")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            var newLogoPath: String? = null
            if (tempLogoFile != null && tempLogoFile.exists()) {
                val finalLogo = File(mediaDir, "logo_${newEventId}.jpg")
                tempLogoFile.copyTo(finalLogo, overwrite = true)
                newLogoPath = finalLogo.absolutePath
            }

            var newBgPath: String? = null
            if (tempBgFile != null && tempBgFile.exists()) {
                val finalBg = File(mediaDir, "bg_${newEventId}.jpg")
                tempBgFile.copyTo(finalBg, overwrite = true)
                newBgPath = finalBg.absolutePath
            }

            if (newLogoPath != null || newBgPath != null) {
                newEvent = newEvent.copy(id = newEventId, logoPath = newLogoPath ?: newEvent.logoPath, bgPath = newBgPath ?: newEvent.bgPath)
                eventRepository.updateEvent(newEvent)
            }

            // Insert Categories
            exportData.categories.forEach { cat ->
                categoryRepository.insertCategory(cat.copy(id = 0, eventId = newEventId))
            }

            // Insert Tickets
            val newTickets = exportData.tickets.map { it.copy(id = 0, eventId = newEventId) }
            ticketRepository.insertTickets(newTickets)

            // Insert HistoryLogs
            val newLogs = exportData.historyLogs.map { it.copy(id = 0, eventId = newEventId) }
            newLogs.forEach { log ->
                historyLogRepository.insertLog(log)
            }

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
