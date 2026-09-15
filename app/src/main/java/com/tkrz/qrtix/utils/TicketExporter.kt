package com.tkrz.qrtix.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
        fileName: String = "Export_Tiket_Qrtix.zip",
        onProgress: (Int, Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                // Simpan di Documents/QRTix
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Documents/QRTix")
                } else {
                    // Untuk Android lama (jika perlu)
                    val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "QRTix")
                    if (!dir.exists()) dir.mkdirs()
                }
            }

            val uri = resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), contentValues)
                ?: return@withContext null

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

                // 2. Generate Gambar-gambar QR
                tickets.forEachIndexed { index, ticket ->
                    val bitMatrix = qrScanner.encode(ticket.qrContent, BarcodeFormat.QR_CODE, 512, 512)
                    val width = bitMatrix.width
                    val height = bitMatrix.height
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

                    for (x in 0 until width) {
                        for (y in 0 until height) {
                            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                        }
                    }

                    // Format baru: QRTix_(kode)_(kategori).png
                    val entryName = "QRTix_${ticket.qrContent}_${ticket.ticketType}.png"
                    zipOutputStream.putNextEntry(ZipEntry(entryName))
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, zipOutputStream)
                    zipOutputStream.closeEntry()
                    bitmap.recycle()

                    if (index % 10 == 0 || index == total - 1) {
                        withContext(Dispatchers.Main) {
                            onProgress(index + 1, total)
                        }
                    }
                }
                zipOutputStream.close()
            }

            // Kembalikan file object fiktif atau gunakan URI untuk akses selanjutnya jika perlu.
            // Di Android modern, kita sebaiknya mengembalikan path string atau null.
            File("/storage/emulated/0/Documents/QRTix/$fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
