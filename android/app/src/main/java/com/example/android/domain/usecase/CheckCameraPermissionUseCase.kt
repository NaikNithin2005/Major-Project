package com.example.android.domain.usecase

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Use Case checking runtime Camera permission status.
 */
open class CheckCameraPermissionUseCase(
    private val context: Context
) {
    open operator fun invoke(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}
