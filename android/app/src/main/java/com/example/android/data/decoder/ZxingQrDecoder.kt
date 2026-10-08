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

            val hints = mapOf<DecodeHintType, Any>(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )

            // Step 2: Try Center Region-of-Interest (ROI) first (center 70% where viewfinder is aligned)
            val cropWidth = (rotatedWidth * 0.70f).toInt()
            val cropHeight = (rotatedHeight * 0.70f).toInt()
            val cropLeft = (rotatedWidth - cropWidth) / 2
            val cropTop = (rotatedHeight - cropHeight) / 2

            val centerSource = PlanarYUVLuminanceSource(
                rotatedData, rotatedWidth, rotatedHeight, cropLeft, cropTop, cropWidth, cropHeight, false
            )
            val centerResult = decodeLuminanceSource(centerSource, hints)
            if (!centerResult.isNullOrBlank()) {
                return centerResult
            }

            // Step 3: Fall back to full frame if center ROI did not decode
            val fullSource = PlanarYUVLuminanceSource(
                rotatedData, rotatedWidth, rotatedHeight, 0, 0, rotatedWidth, rotatedHeight, false
            )
            decodeLuminanceSource(fullSource, hints)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeLuminanceSource(source: LuminanceSource, hints: Map<DecodeHintType, Any>): String? {
        val reader = MultiFormatReader().apply { setHints(hints) }

        // Strategy 1: Standard HybridBinarizer
        try {
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            if (result?.text?.isNotBlank() == true) return result.text
        } catch (_: NotFoundException) {}
        catch (_: Exception) {}

        // Strategy 2: GlobalHistogramBinarizer (superior for low contrast and moderate blur)
        try {
            reader.reset()
            val result = reader.decodeWithState(BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source)))
            if (result?.text?.isNotBlank() == true) return result.text
        } catch (_: NotFoundException) {}
        catch (_: Exception) {}

        // Strategy 3: Inverted HybridBinarizer (for dark mode / inverted QR codes)
        try {
            reader.reset()
            val invertedSource = source.invert()
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(invertedSource)))
            if (result?.text?.isNotBlank() == true) return result.text
        } catch (_: NotFoundException) {}
        catch (_: Exception) {}

        // Strategy 4: Inverted GlobalHistogramBinarizer
        try {
            reader.reset()
            val invertedSource = source.invert()
            val result = reader.decodeWithState(BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(invertedSource)))
            if (result?.text?.isNotBlank() == true) return result.text
        } catch (_: NotFoundException) {}
        catch (_: Exception) {}

        return null
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
            val hints = mapOf<DecodeHintType, Any>(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )

            val result = decodeLuminanceSource(source, hints)
            if (!result.isNullOrBlank()) {
                return result
            }

            try {
                val multiReader = QRCodeMultiReader()
                val multiResults = multiReader.decodeMultiple(BinaryBitmap(HybridBinarizer(source)))
                if (multiResults != null && multiResults.isNotEmpty()) {
                    return multiResults[0].text
                }
            } catch (_: Exception) {}

            null
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
