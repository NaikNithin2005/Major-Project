package com.example.android.data.decoder

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

/**
 * Robust ZXing QR Decoder supporting both CameraX real-time frame buffers and Gallery bitmaps.
 * Handles CameraX YUV rowStride padding, sensor rotation, and inverted QR codes completely offline.
 */
object ZxingQrDecoder {

    /**
     * Decodes a QR code payload from a CameraX [ImageProxy] frame.
     * Note: Does NOT close the [ImageProxy]; caller must manage lifecycle.
     */
    fun decodeImageProxy(imageProxy: ImageProxy): String? {
        return try {
            val plane = imageProxy.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = plane.rowStride
            val rotation = imageProxy.imageInfo.rotationDegrees

            // Step 1: Rotate and compact Y plane byte buffer according to sensor rotation & rowStride
            val (rotatedData, rotatedWidth, rotatedHeight) = rotateAndCompactYuv(data, width, height, rowStride, rotation)

            val source = PlanarYUVLuminanceSource(
                rotatedData, rotatedWidth, rotatedHeight, 0, 0, rotatedWidth, rotatedHeight, false
            )
            val binarizer = HybridBinarizer(source)
            val binaryBitmap = BinaryBitmap(binarizer)

            val hints = mapOf<DecodeHintType, Any>(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )

            // Step 2: Decode using fresh reader instance per frame
            val reader = MultiFormatReader().apply { setHints(hints) }
            try {
                val result = reader.decodeWithState(binaryBitmap)
                result?.text
            } catch (e: NotFoundException) {
                // Inverted binarizer fallback for dark mode / inverted QR codes
                val invertedSource = source.invert()
                val invertedBinaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
                val invertedResult = MultiFormatReader().apply { setHints(hints) }.decodeWithState(invertedBinaryBitmap)
                invertedResult?.text
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes a QR code payload from an Android [Bitmap].
     * Handles normal single QR codes as well as detecting multiple QR codes deterministically.
     */
    fun decodeBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val intArray = IntArray(width * height)
            bitmap.getPixels(intArray, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, intArray)
            val binarizer = HybridBinarizer(source)
            val binaryBitmap = BinaryBitmap(binarizer)

            // Try standard QR reader first
            try {
                val result = QRCodeReader().decode(binaryBitmap)
                return result.text
            } catch (e: NotFoundException) {
                // Try multi-reader if single reader returns NotFoundException
                val multiReader = QRCodeMultiReader()
                val multiResults = multiReader.decodeMultiple(binaryBitmap)
                if (multiResults != null && multiResults.isNotEmpty()) {
                    return multiResults[0].text
                }
                // Try inverted binarizer fallback
                val invertedSource = source.invert()
                val invertedBinaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
                val invertedResult = QRCodeReader().decode(invertedBinaryBitmap)
                invertedResult?.text
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateAndCompactYuv(
        data: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        rotationDegrees: Int
    ): Triple<ByteArray, Int, Int> {
        return when (rotationDegrees) {
            90 -> {
                val rotated = ByteArray(width * height)
                var i = 0
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        rotated[i++] = data[y * rowStride + x]
                    }
                }
                Triple(rotated, height, width)
            }
            180 -> {
                val rotated = ByteArray(width * height)
                var i = 0
                for (y in height - 1 downTo 0) {
                    for (x in width - 1 downTo 0) {
                        rotated[i++] = data[y * rowStride + x]
                    }
                }
                Triple(rotated, width, height)
            }
            270 -> {
                val rotated = ByteArray(width * height)
                var i = 0
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        rotated[i++] = data[y * rowStride + x]
                    }
                }
                Triple(rotated, height, width)
            }
            else -> {
                if (rowStride == width) {
                    Triple(data, width, height)
                } else {
                    val compacted = ByteArray(width * height)
                    for (y in 0 until height) {
                        System.arraycopy(data, y * rowStride, compacted, y * width, width)
                    }
                    Triple(compacted, width, height)
                }
            }
        }
    }
}
