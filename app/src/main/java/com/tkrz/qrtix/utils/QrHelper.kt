package com.tkrz.qrtix.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream

object QrHelper {
    fun generateSimpleQrBytes(content: String): ByteArray {
        val qrScanner = QRCodeWriter()
        val size = 512
        val hints = mapOf(
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
            com.google.zxing.EncodeHintType.MARGIN to 2
        )
        val bitMatrix = qrScanner.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val qrWidth = bitMatrix.width
        val qrHeight = bitMatrix.height
        val bitmap = Bitmap.createBitmap(qrWidth, qrHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(qrWidth * qrHeight)
        for (y in 0 until qrHeight) {
            val offset = y * qrWidth
            for (x in 0 until qrWidth) {
                pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, qrWidth, 0, 0, qrWidth, qrHeight)
        
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        bitmap.recycle()
        return byteArray
    }
}
