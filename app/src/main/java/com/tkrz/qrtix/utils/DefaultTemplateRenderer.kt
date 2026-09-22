package com.tkrz.qrtix.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.tkrz.qrtix.data.Event

object DefaultTemplateRenderer {
    
    fun renderDefaultTemplate(event: Event?): Bitmap {
        val outWidth = 1000
        val outHeight = 1200
        val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw solid dark background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0f172a") // Slate 900
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, outWidth.toFloat(), outHeight.toFloat(), bgPaint)

        // 2. Draw outer ticket border with rounded corners
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155") // Slate 700
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val padding = 40f
        val ticketRect = RectF(padding, padding, outWidth - padding, outHeight - padding)
        canvas.drawRoundRect(ticketRect, 40f, 40f, borderPaint)

        // 3. Draw a box for the QR code (Size matched to default exporter size: 1000 * 0.4 = 400)
        // Default scale is 1f. The QR is placed exactly in the center.
        val qrBoxSize = 400f
        val cx = outWidth / 2f
        val cy = outHeight / 2f
        val qrRect = RectF(cx - qrBoxSize / 2f, cy - qrBoxSize / 2f, cx + qrBoxSize / 2f, cy + qrBoxSize / 2f)
        
        val qrBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1e293b") // Slate 800
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(qrRect, 20f, 20f, qrBoxPaint)

        // Draw a subtle border for the QR box
        val qrBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569") // Slate 600
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(qrRect, 20f, 20f, qrBorderPaint)

        // 4. Draw Header Text (Event Name)
        val eventName = event?.name ?: "QRTix Event"
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        
        // Auto-scale event name if too long
        var textWidth = headerPaint.measureText(eventName)
        while (textWidth > outWidth - (padding * 4) && headerPaint.textSize > 24f) {
            headerPaint.textSize -= 2f
            textWidth = headerPaint.measureText(eventName)
        }
        canvas.drawText(eventName, cx, padding + 150f, headerPaint)
        
        // 5. Draw Subheader (Event Code)
        val subheaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94a3b8") // Slate 400
            textSize = 32f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("EVENT CODE: ${event?.eventCode ?: "N/A"}", cx, padding + 220f, subheaderPaint)

        // 6. Draw "ADMIT ONE" label at the bottom
        val admitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38bdf8") // Light Blue
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
        }
        canvas.drawText("ADMIT ONE", cx, outHeight - padding - 80f, admitPaint)

        return bitmap
    }
}
