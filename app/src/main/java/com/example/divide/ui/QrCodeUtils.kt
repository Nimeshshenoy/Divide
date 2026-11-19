package com.example.divide.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import java.util.Locale

/**
 * Utility for generating QR Codes and UPI Payment URIs.
 */
object QrCodeUtils {

    /**
     * Generates a standard Indian UPI payment deep link string.
     * e.g., "upi://pay?pa=nimesh@okaxis&pn=Nimesh&cu=INR&am=1250.00"
     */
    fun createUpiUri(upiId: String, name: String, amountRupees: Double? = null): String {
        val encodedName = URLEncoder.encode(name, "UTF-8")
        var uri = "upi://pay?pa=${upiId.trim()}&pn=$encodedName&cu=INR"
        if (amountRupees != null && amountRupees > 0) {
            uri += "&am=${"%.2f".format(Locale.US, amountRupees)}"
        }
        return uri
    }

    /**
     * Generates an [ImageBitmap] representing a QR code for the given content string.
     */
    fun generateQrCodeBitmap(content: String, sizePx: Int = 512): ImageBitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
