package com.uvindu.linuxsyncandroid.service

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.uvindu.linuxsyncandroid.domain.model.QRCodePayload
import kotlinx.serialization.json.Json

class QRCodeAnalyzer(
    private val onQrCodeScanned: (QRCodePayload) -> Unit,
    private val onFailure: (Exception) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        if (barcode.valueType == Barcode.TYPE_TEXT || barcode.valueType == Barcode.TYPE_UNKNOWN) {
                            val rawValue = barcode.rawValue ?: continue
                            try {
                                // Parse the shortened JSON payload from the QR code
                                val payload = jsonParser.decodeFromString<QRCodePayload>(rawValue)
                                onQrCodeScanned(payload)
                                break // Stop after matching the first valid profile
                            } catch (e: Exception) {
                                // Not a valid LinuxSync QR structure, keep scanning silently
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    onFailure(exception)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}