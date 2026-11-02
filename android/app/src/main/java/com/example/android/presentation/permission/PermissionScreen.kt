package com.example.android.presentation.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.android.util.ProtectionStatusLabel
import com.example.android.util.PermissionUtils
import com.example.android.util.ProtectionReadinessSummary

/**
 * Protection-Readiness Flow at app launch.
 * Evaluates device capabilities, guides users through permissions and roles,
 * and allows continuing with limited protection without trapping the user.
 */
@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live state tracking computed from real system status
    var readiness by remember { mutableStateOf(PermissionUtils.getProtectionReadiness(context)) }

    fun refreshReadiness() {
        readiness = PermissionUtils.getProtectionReadiness(context)
    }

    // Recheck status whenever app returns to foreground (onResume)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshReadiness()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshReadiness()
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshReadiness()
    }

    val isAllReady = readiness.overallStatus == ProtectionStatusLabel.ENABLED

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFF020617)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Shield Icon with Status Aura
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = if (isAllReady) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF0EA5E9).copy(alpha = 0.15f),
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            if (isAllReady) listOf(Color(0xFF34D399), Color(0xFF10B981))
                            else listOf(Color(0xFF38BDF8), Color(0xFF6366F1))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAllReady) Icons.Default.VerifiedUser else Icons.Default.Security,
                    contentDescription = "Protection Shield",
                    tint = if (isAllReady) Color(0xFF34D399) else Color(0xFF38BDF8),
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Squish Protection Readiness",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Verify security capabilities to safeguard your device against smishing, malicious links, and QR-based threats.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Overall Readiness Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (readiness.overallStatus) {
                        ProtectionStatusLabel.ENABLED -> Color(0xFF10B981).copy(alpha = 0.15f)
                        ProtectionStatusLabel.LIMITED_PROTECTION -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                        else -> Color(0xFFEF4444).copy(alpha = 0.15f)
                    }
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        listOf(
                            when (readiness.overallStatus) {
                                ProtectionStatusLabel.ENABLED -> Color(0xFF10B981)
                                ProtectionStatusLabel.LIMITED_PROTECTION -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            },
                            Color.Transparent
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (readiness.overallStatus) {
                            ProtectionStatusLabel.ENABLED -> Icons.Default.CheckCircle
                            ProtectionStatusLabel.LIMITED_PROTECTION -> Icons.Default.Info
                            else -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = when (readiness.overallStatus) {
                            ProtectionStatusLabel.ENABLED -> Color(0xFF34D399)
                            ProtectionStatusLabel.LIMITED_PROTECTION -> Color(0xFFFBBF24)
                            else -> Color(0xFFF87171)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Current Status",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            StatusBadge(status = readiness.overallStatus)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = readiness.statusDescription,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Text Message Checking Card
            CapabilityCard(
                icon = Icons.Default.Sms,
                title = "Checking Incoming Messages",
                status = readiness.smsStatus,
                description = "Allows Squish to check incoming text messages automatically for dangerous scam links and fake messages. Your text messages stay 100% private on your phone.",
                actionLabel = if (readiness.smsStatus == ProtectionStatusLabel.ENABLED) null else "Allow Message Checking",
                onAction = {
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.RECEIVE_SMS,
                            android.Manifest.permission.READ_SMS
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Safety Alerts Card
            CapabilityCard(
                icon = Icons.Default.NotificationsActive,
                title = "Helpful Safety Alerts",
                status = readiness.notificationStatus,
                description = "Sends a clear, instant alert if a dangerous link or scam message is detected so you can stay safe.",
                actionLabel = when (readiness.notificationStatus) {
                    ProtectionStatusLabel.ENABLED -> null
                    ProtectionStatusLabel.NOTIFICATIONS_DISABLED -> "Open Notification Settings"
                    else -> "Turn On Alerts"
                },
                onAction = {
                    if (readiness.notificationStatus == ProtectionStatusLabel.NOTIFICATIONS_DISABLED) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    } else {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Camera & QR Code Card
            CapabilityCard(
                icon = Icons.Default.QrCodeScanner,
                title = "QR Code Scanner (Camera)",
                status = readiness.cameraStatus,
                description = "Allows you to scan QR codes with your camera to check whether website addresses and payment links are safe before opening them.",
                actionLabel = if (readiness.cameraStatus == ProtectionStatusLabel.ENABLED) null else "Allow Camera",
                onAction = {
                    permissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. On-Device Scam Checking Engine Card
            CapabilityCard(
                icon = Icons.Default.Memory,
                title = "On-Device Scam Detection Engine",
                status = readiness.modelStatus,
                description = if (readiness.modelStatus == ProtectionStatusLabel.ENABLED) {
                    "Installed and ready on your device. Checks messages completely offline without sending your private texts to the cloud."
                } else {
                    "Operating with on-device safety rules to protect against known scam patterns."
                },
                actionLabel = null,
                onAction = null
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Action Button
            Button(
                onClick = {
                    if (isAllReady) {
                        onPermissionsGranted()
                    } else {
                        val required = PermissionUtils.getRequiredPermissions()
                        permissionLauncher.launch(required)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAllReady) Color(0xFF10B981) else Color(0xFF0EA5E9)
                )
            ) {
                Icon(
                    imageVector = if (isAllReady) Icons.Default.CheckCircle else Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAllReady) "All Defenses Active — Continue" else "Enable All Supported Defenses",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Actions: Settings shortcut and Proceed with Limited Protection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "App Settings",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Important navigation rule: Never create an endless loop. Allow proceeding to manual scan/QR!
                TextButton(
                    onClick = {
                        onPermissionsGranted()
                    }
                ) {
                    Text(
                        text = if (isAllReady) "Continue" else "Continue with Limited Protection",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAllReady) Color(0xFF38BDF8) else Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (isAllReady) Color(0xFF38BDF8) else Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CapabilityCard(
    icon: ImageVector,
    title: String,
    status: ProtectionStatusLabel,
    description: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    if (status == ProtectionStatusLabel.ENABLED) Color(0xFF10B981).copy(alpha = 0.4f)
                    else Color(0xFF334155),
                    Color(0xFF1E293B)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = if (status == ProtectionStatusLabel.ENABLED) Color(0xFF10B981).copy(alpha = 0.15f)
                            else Color(0xFF0EA5E9).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (status == ProtectionStatusLabel.ENABLED) Color(0xFF34D399) else Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        StatusBadge(status = status)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 15.sp
            )

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF38BDF8)
                    )
                ) {
                    Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ProtectionStatusLabel) {
    val (badgeBg, badgeText) = when (status) {
        ProtectionStatusLabel.ENABLED -> Color(0xFF10B981).copy(alpha = 0.2f) to Color(0xFF34D399)
        ProtectionStatusLabel.LIMITED_PROTECTION -> Color(0xFFF59E0B).copy(alpha = 0.2f) to Color(0xFFFBBF24)
        ProtectionStatusLabel.DEFAULT_SMS_REQUIRED -> Color(0xFF8B5CF6).copy(alpha = 0.2f) to Color(0xFFA78BFA)
        ProtectionStatusLabel.ACTION_REQUIRED -> Color(0xFFF59E0B).copy(alpha = 0.2f) to Color(0xFFFBBF24)
        ProtectionStatusLabel.NOTIFICATIONS_DISABLED -> Color(0xFFEF4444).copy(alpha = 0.2f) to Color(0xFFF87171)
        ProtectionStatusLabel.MODEL_UNAVAILABLE -> Color(0xFF64748B).copy(alpha = 0.2f) to Color(0xFF94A3B8)
        ProtectionStatusLabel.NOT_GRANTED -> Color(0xFFEF4444).copy(alpha = 0.2f) to Color(0xFFF87171)
    }

    Surface(
        color = badgeBg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = badgeText,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
