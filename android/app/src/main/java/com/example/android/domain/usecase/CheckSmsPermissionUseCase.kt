package com.example.android.domain.usecase

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Use case to check if SMS permissions (RECEIVE_SMS and READ_SMS) are granted.
 */
class CheckSmsPermissionUseCase(
    private val context: Context
) {

    fun isReceiveSmsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isReadSmsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAllSmsPermissionsGranted(): Boolean {
        return isReceiveSmsGranted() && isReadSmsGranted()
    }
}
