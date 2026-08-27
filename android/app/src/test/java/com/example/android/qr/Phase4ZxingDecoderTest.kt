package com.example.android.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test

class Phase4ZxingDecoderTest {

    private fun generateQrMatrix(content: String, size: Int = 250): IntArray {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (bitMatrix.get(x, y)) -0x1000000 else -0x1
            }
        }
        return pixels
    }

    @Test
    fun testZxingQrDecodingUrlPayloadOnJvm() {
        val originalPayload = "https://squishshield.org/verify?id=99"
        val size = 250
        val pixels = generateQrMatrix(originalPayload, size)

        val source = RGBLuminanceSource(size, size, pixels)
        val binarizer = HybridBinarizer(source)
        val binaryBitmap = BinaryBitmap(binarizer)

        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }

        val result = reader.decode(binaryBitmap)
        assertNotNull(result)
        assertEquals(originalPayload, result.text)
    }

    @Test
    fun testZxingQrDecodingPlainTextPayloadOnJvm() {
        val originalPayload = "WIFI:S:HomeWifi;P:SecretKey123;;"
        val size = 250
        val pixels = generateQrMatrix(originalPayload, size)

        val source = RGBLuminanceSource(size, size, pixels)
        val binarizer = HybridBinarizer(source)
        val binaryBitmap = BinaryBitmap(binarizer)

        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }

        val result = reader.decode(binaryBitmap)
        assertNotNull(result)
        assertEquals(originalPayload, result.text)
    }
}
