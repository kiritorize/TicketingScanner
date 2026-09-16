package com.tkrz.qrtix.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tkrz.qrtix.data.Event
import com.tkrz.qrtix.data.Ticket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TicketExporter(private val context: Context) {

    /**
     * Mengerjakan ekspor tiket ke ZIP secara background.
     * @param tickets Daftar ribuan tiket yang ingin di-generate.
     * @param fileName Nama file output zip.
     * @param onProgress Callback untuk mengupdate UI (misal: "Memproses 100/1000")
     */
    suspend fun exportTicketsToZip(
        tickets: List<Ticket>,
        event: Event?,
        fileName: String = "Export_Tiket_Qrtix.zip",
        onProgress: (Int, Int) -> Unit
    ): Pair<File?, List<com.tkrz.qrtix.data.cloud.UploadTask>> = withContext(Dispatchers.IO) {
        val uploadTasks = mutableListOf<com.tkrz.qrtix.data.cloud.UploadTask>()
        try {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                val sanitizedEventName = event?.name?.replace("[^a-zA-Z0-9.-]".toRegex(), "_") ?: "Default"
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Documents/QRTix/\$sanitizedEventName")
                } else {
                    val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "QRTix/\$sanitizedEventName")
                    if (!dir.exists()) dir.mkdirs()
                }
            }

            val uri = resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), contentValues)
                ?: return@withContext Pair(null, emptyList())

            resolver.openOutputStream(uri)?.use { outputStream ->
                val zipOutputStream = ZipOutputStream(BufferedOutputStream(outputStream))
                val qrScanner = QRCodeWriter()
                val total = tickets.size

                // 1. Tambahkan file CSV
                zipOutputStream.putNextEntry(ZipEntry("data_impor_qrtix.csv"))
                val csvHeader = "Kode QR,Tipe Tiket\n"
                zipOutputStream.write(csvHeader.toByteArray())
                tickets.forEach { ticket ->
                    val csvLine = "\"${ticket.qrContent.replace("\"", "\"\"")}\",\"${ticket.ticketType.replace("\"", "\"\"")}\"\n"
                    zipOutputStream.write(csvLine.toByteArray())
                }
                zipOutputStream.closeEntry()

                // Determine base background if exists
                var baseBgBitmap: Bitmap? = null
                event?.bgPath?.let { bgPath ->
                    val file = File(bgPath)
                    if (file.exists()) {
                        val options = android.graphics.BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                        var sampleSize = 1
                        while (options.outWidth / sampleSize > 2000 || options.outHeight / sampleSize > 2000) {
                            sampleSize *= 2
                        }
                        options.inJustDecodeBounds = false
                        options.inSampleSize = sampleSize
                        baseBgBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    }
                }
                
                val outWidth = baseBgBitmap?.width ?: 1000
                val outHeight = baseBgBitmap?.height ?: 1200

                val hints = mapOf(
                    com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
                    com.google.zxing.EncodeHintType.MARGIN to 2
                )

                tickets.forEachIndexed { index, ticket ->
                    val finalBitmap = if (baseBgBitmap != null) {
                        baseBgBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
                    } else {
                        Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(Color.WHITE)
                        }
                    }
                    val canvas = android.graphics.Canvas(finalBitmap)
                    
                    val qrScaleUi = event?.qrScale ?: 1f
                    val qrRotation = event?.qrRotation ?: 0f
                    val qrXUi = event?.qrX ?: 0f
                    val qrYUi = event?.qrY ?: 0f
                    
                    val qrSize = (outWidth * 0.4f * qrScaleUi).toInt().coerceAtLeast(100)
                    
                    val bitMatrix = qrScanner.encode(ticket.qrContent, BarcodeFormat.QR_CODE, qrSize, qrSize, hints)
                    val qrWidth = bitMatrix.width
                    val qrHeight = bitMatrix.height
                    
                    val textPadding = (qrHeight * 0.25f).toInt()
                    
                    val qrBitmap = Bitmap.createBitmap(qrWidth, qrHeight + textPadding, Bitmap.Config.ARGB_8888)
                    val qrCanvas = android.graphics.Canvas(qrBitmap)
                    qrCanvas.drawColor(Color.WHITE)
                    
                    val pixels = IntArray(qrWidth * qrHeight)
                    for (y in 0 until qrHeight) {
                        val offset = y * qrWidth
                        for (x in 0 until qrWidth) {
                            pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                        }
                    }
                    qrBitmap.setPixels(pixels, 0, qrWidth, 0, 0, qrWidth, qrHeight)

                    // Logo Embedding
                    event?.logoPath?.let { logoPath ->
                        val logoFile = File(logoPath)
                        if (logoFile.exists()) {
                            val logoBitmap = android.graphics.BitmapFactory.decodeFile(logoPath)
                            if (logoBitmap != null) {
                                val maxLogoSide = (qrWidth * 0.44f).toInt()
                                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, maxLogoSide, maxLogoSide, true)
                                val logoX = (qrWidth - maxLogoSide) / 2f
                                val logoY = (qrHeight - maxLogoSide) / 2f
                                qrCanvas.drawBitmap(scaledLogo, logoX, logoY, null)
                                scaledLogo.recycle()
                                logoBitmap.recycle()
                            }
                        }
                    }

                    // Auto-Scaling HRI Text
                    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = qrHeight * 0.1f
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    
                    var textWidth = textPaint.measureText(ticket.qrContent)
                    while (textWidth > qrWidth - 40f && textPaint.textSize > 10f) {
                        textPaint.textSize -= 2f
                        textWidth = textPaint.measureText(ticket.qrContent)
                    }
                    
                    qrCanvas.drawText(ticket.qrContent, qrWidth / 2f, qrHeight + (textPadding / 2f) + (textPaint.textSize / 3f), textPaint)

                    // Draw QR to final Canvas
                    canvas.save()
                    val cx = outWidth / 2f
                    val cy = outHeight / 2f
                    val ratio = outWidth / 1080f
                    val transX = cx + (qrXUi * ratio)
                    val transY = cy + (qrYUi * ratio)
                    
                    canvas.translate(transX, transY)
                    canvas.rotate(qrRotation)
                    canvas.drawBitmap(qrBitmap, -qrBitmap.width / 2f, -qrBitmap.height / 2f, null)
                    canvas.restore()
                    
                    qrBitmap.recycle()

                    val fileNameCode = ticket.qrContent.replace("-", "_")
                    val entryName = "QRTix_${fileNameCode}.jpg"
                    zipOutputStream.putNextEntry(ZipEntry(entryName))
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, zipOutputStream)
                    zipOutputStream.closeEntry()
                    
                    // Save to cache for Background Upload
                    val tempDir = File(context.cacheDir, "QRTix_Temp_Upload")
                    if (!tempDir.exists()) tempDir.mkdirs()
                    val tempFile = File(tempDir, entryName)
                    FileOutputStream(tempFile).use { out ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    
                    val evtName = event?.name ?: "Unknown Event"
                    uploadTasks.add(com.tkrz.qrtix.data.cloud.UploadTask(tempFile, evtName, ticket.ticketType))
                    
                    finalBitmap.recycle()

                    if (index % 10 == 0 || index == total - 1) {
                        withContext(Dispatchers.Main) {
                            onProgress(index + 1, total)
                        }
                    }
                }
                zipOutputStream.close()
                baseBgBitmap?.recycle()
            }

            val sanitizedEventName = event?.name?.replace("[^a-zA-Z0-9.-]".toRegex(), "_") ?: "Default"
            val file = File("/storage/emulated/0/Documents/QRTix/$sanitizedEventName/$fileName")
            Pair(file, uploadTasks)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, emptyList())
        }
    }
}
