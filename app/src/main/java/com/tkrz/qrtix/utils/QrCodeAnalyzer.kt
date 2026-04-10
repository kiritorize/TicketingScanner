package com.tkrz.qrtix.utils

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    private val scanner = BarcodeScanning.getClient(options)
    private var isAnalyzing = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (isAnalyzing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            isAnalyzing = true
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            // Define 60% center box
            val imgWidth = image.width
            val imgHeight = image.height
            val boxWidth = imgWidth * 0.6f
            val boxHeight = imgHeight * 0.6f
            val left = (imgWidth - boxWidth) / 2
            val top = (imgHeight - boxHeight) / 2
            val centerBox = android.graphics.RectF(left, top, left + boxWidth, top + boxHeight)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { value ->
                            val boundingBox = barcode.boundingBox
                            if (boundingBox != null) {
                                val barcodeRect = android.graphics.RectF(boundingBox)
                                if (android.graphics.RectF.intersects(centerBox, barcodeRect)) {
                                    onQrCodeScanned(value)
                                    return@addOnSuccessListener // Break early safely
                                }
                            } else {
                                onQrCodeScanned(value)
                                return@addOnSuccessListener
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    isAnalyzing = false
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
