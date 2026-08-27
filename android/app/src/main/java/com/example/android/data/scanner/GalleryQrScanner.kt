package com.example.android.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.android.data.decoder.ZxingQrDecoder
import java.io.InputStream

/**
 * Gallery Image QR Scanner with comprehensive bitmap sampling, image validation,
 * and memory safety checks.
 */
object GalleryQrScanner {

    private const val MAX_IMAGE_DIMENSION = 2048

    /**
     * Decodes a QR code payload from a gallery image [Uri].
     * Validates image accessibility, format, and dimensions offline without external network calls.
     */
    fun scanGalleryUri(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver

            // Step 1: Validate MIME type and accessibility
            val mimeType = contentResolver.getType(uri)
            if (mimeType != null && !mimeType.startsWith("image/")) {
                return null
            }

            // Step 2: Decode bounds to validate dimensions and compute sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null
            }

            // Step 3: Compute safe sample size to prevent OutOfMemory errors
            options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            // Step 4: Decode bitmap safely
            val sampledBitmap: Bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            // Step 5: Decode QR code via ZXing
            val qrText = ZxingQrDecoder.decodeBitmap(sampledBitmap)
            sampledBitmap.recycle()
            qrText
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
