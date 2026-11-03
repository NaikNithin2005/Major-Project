package com.example.android.presentation.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.android.data.tips.SecurityTip
import com.example.android.data.tips.SecurityTipsProvider
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.User
import com.example.android.util.PermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeTab(
    currentUser: User?,
    uiState: DashboardUiState,
    onNavigateToTab: (Int) -> Unit,
    onOpenEducation: () -> Unit,
    onThreatClicked: (ThreatRecord) -> Unit,
    onTriggerManualSync: () -> Unit,
    onOpenProtectionSetup: (() -> Unit)? = null,
    onOpenLiveCameraQr: (() -> Unit)? = null,
    onNavigateToHistoryWithFilter: ((type: String, severity: String) -> Unit)? = null,
    onNavigateToSafetyAlerts: (() -> Unit)? = null,
    onNavigateToSecurity: (() -> Unit)? = null,
    onNavigateToFeedbackHistory: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var expandedTip by remember { mutableStateOf<SecurityTip?>(null) }
    val todayTip = remember { SecurityTipsProvider.getTodayTip() }

    val hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val greetingName = when {
        currentUser?.isGuest == true -> "Guest User"
        !currentUser?.displayName.isNullOrBlank() -> currentUser?.displayName!!
        !currentUser?.email.isNullOrBlank() -> currentUser?.email!!.substringBefore("@")
        else -> "User"
    }

    val timeFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    if (expandedTip != null) {
        AlertDialog(
            onDismissRequest = { expandedTip = null },
            title = { Text(expandedTip!!.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(expandedTip!!.details, fontSize = 13.sp, color = Color(0xFFCBD5E1), lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Recommended Action:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    Text(expandedTip!!.recommendedAction, fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            },
            confirmButton = {
                TextButton(onClick = { expandedTip = null }) {
                    Text("Got It", color = Color(0xFF38BDF8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: User Greeting & Cloud Sync Status
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, $greetingName 👋",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (currentUser?.isGuest == true) "On-Device Protection Active" else currentUser?.email ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Surface(
                    color = if (uiState.isSyncing) Color(0xFF6366F1).copy(alpha = 0.22f)
                            else if (uiState.pendingSyncCount == 0) Color(0xFF0EA5E9).copy(alpha = 0.18f) 
                            else Color(0xFFF59E0B).copy(alpha = 0.22f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(enabled = !uiState.isSyncing) { onTriggerManualSync() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF818CF8)
                            )
                        } else {
                            Icon(
                                imageVector = if (uiState.pendingSyncCount == 0) Icons.Default.CloudDone else Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = if (uiState.pendingSyncCount == 0) Color(0xFF38BDF8) else Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (uiState.isSyncing) "Syncing..." 
                                   else if (uiState.pendingSyncCount == 0) "Synced" 
                                   else "${uiState.pendingSyncCount} pending",
                            fontSize = 11.sp,
                            color = if (uiState.isSyncing) Color(0xFF818CF8) 
                                   else if (uiState.pendingSyncCount == 0) Color(0xFF38BDF8) 
                                   else Color(0xFFFBBF24),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Section 1: Primary SMS Message Statistics (4 Real Stats)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MailOutline, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Message Safety Activity",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = if (hasSmsPermission) "Auto Checking Active" else "Manual Checking",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasSmsPermission) Color(0xFF34D399) else Color(0xFFFBBF24)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Total Scanned",
                        count = uiState.smsScannedCount,
                        countColor = Color.White,
                        icon = Icons.Default.Inbox,
                        iconTint = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToHistoryWithFilter?.invoke("Messages", "All") ?: onNavigateToTab(1) }
                    )
                    StatCard(
                        title = "Looks Safe",
                        count = uiState.smsSafeCount,
                        countColor = Color(0xFF34D399),
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToHistoryWithFilter?.invoke("Messages", "Looks Safe") ?: onNavigateToTab(1) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Needs Attention",
                        count = uiState.smsAttentionCount,
                        countColor = Color(0xFFFBBF24),
                        icon = Icons.Default.WarningAmber,
                        iconTint = Color(0xFFFBBF24),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToHistoryWithFilter?.invoke("Messages", "Needs Attention") ?: onNavigateToTab(1) }
                    )
                    StatCard(
                        title = "Dangerous",
                        count = uiState.smsDangerousCount,
                        countColor = Color(0xFFF87171),
                        icon = Icons.Default.Dangerous,
                        iconTint = Color(0xFFF87171),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToHistoryWithFilter?.invoke("Messages", "Dangerous") ?: onNavigateToTab(1) }
                    )
                }
            }
        }

        // Section 2: Separate QR Code Activity (4 Real Stats)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "QR Code Safety Activity",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = if (hasCameraPermission) "Camera Ready" else "Permission Needed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasCameraPermission) Color(0xFF34D399) else Color(0xFFFBBF24)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Total Scanned",
                        count = uiState.qrScannedCount,
                        countColor = Color.White,
                        icon = Icons.Default.QrCode,
                        iconTint = Color(0xFFA78BFA),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToHistoryWithFilter?.invoke("QR Codes", "All") ?: onNavigateToTab(1) }
                    )
                    StatCard(
                        title = "Looks Safe",
                        count = uiState.qrSafeCount,
                        countColor = Color(0xFF34D399),
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToHistoryWithFilter?.invoke("QR Codes", "Looks Safe") ?: onNavigateToTab(1) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Needs Attention",
                        count = uiState.qrAttentionCount,
                        countColor = Color(0xFFFBBF24),
                        icon = Icons.Default.WarningAmber,
                        iconTint = Color(0xFFFBBF24),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToHistoryWithFilter?.invoke("QR Codes", "Needs Attention") ?: onNavigateToTab(1) }
                    )
                    StatCard(
                        title = "Dangerous",
                        count = uiState.qrDangerousCount,
                        countColor = Color(0xFFF87171),
                        icon = Icons.Default.Dangerous,
                        iconTint = Color(0xFFF87171),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToHistoryWithFilter?.invoke("QR Codes", "Dangerous") ?: onNavigateToTab(1) }
                    )
                }
            }
        }

        // Section 3: Quick Action Buttons (Phase 9 Sequence)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Quick Actions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onNavigateToTab(2) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check SMS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { if (onOpenLiveCameraQr != null) onOpenLiveCameraQr() else onNavigateToTab(2) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan QR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigateToSafetyAlerts?.invoke() ?: onNavigateToTab(3) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Alerts (${uiState.safetyAlerts.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { onNavigateToSecurity?.invoke() ?: onNavigateToTab(4) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trusted Users", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Section 4: Important Safety Alerts (Phase 9 Sequence)
        if (uiState.safetyAlerts.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1517)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFFF87171), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Important Safety Alerts (${uiState.safetyAlerts.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5)
                                )
                            }
                            Text(
                                text = "View All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.clickable { onNavigateToSafetyAlerts?.invoke() ?: onNavigateToTab(1) }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val topAlert = uiState.safetyAlerts.first()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF3F191B))
                                .clickable { onThreatClicked(topAlert) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (topAlert.source.contains("QR", ignoreCase = true)) Icons.Default.QrCode else Icons.Default.Sms,
                                contentDescription = null,
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topAlert.sender.ifBlank { "Suspicious Alert" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = topAlert.details.ifBlank { "Dangerous indicators detected" },
                                    fontSize = 11.sp,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFFCA5A5))
                        }
                    }
                }
            }
        }

                // Section 5: Safety Tip Recommendation (Phase 9 Sequence)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2937)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Safety Tip: Verify Bank SMS Headers", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBAE6FD))
                        Text("Official Indian bank transaction alerts always feature registered 6-character TRAI headers (e.g. VK-HDFCBK or AX-SBIBNK). Legitimate banks never ask you to click links to cancel transfers.", fontSize = 11.sp, color = Color(0xFF94A3B8), lineHeight = 15.sp)
                    }
                }
            }
        }

// Section 6: Daily Tip (Phase 9 Sequence)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedTip = todayTip },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Safety Tip: " + todayTip.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(todayTip.details, fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF64748B))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        // Section 7: Academy & Educational Resources (Phase 9 Sequence)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenEducation() },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Squish Safety Academy", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Explore interactive defense guides against Smishing, Quishing & Fake KYC scams", fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF64748B))
                }
            }
        }

        // Section 8: Remaining Sections - Recent Activity (Phase 9 Sequence)
// Section 5: Recently Checked Messages
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recently Checked Messages",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "View All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.clickable { onNavigateToHistoryWithFilter?.invoke("Messages", "All") ?: onNavigateToTab(1) }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.recentMessages.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No messages checked yet", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.recentMessages.take(4).forEach { item ->
                            ActivityRecordRow(item = item, timeFormatter = timeFormatter, onClick = { onThreatClicked(item) })
                        }
                    }
                }
            }
        }

        // Section 6: Recently Scanned QR Codes
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recently Scanned QR Codes",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "View All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.clickable { onNavigateToHistoryWithFilter?.invoke("QR Codes", "All") ?: onNavigateToTab(1) }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.recentQrScans.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No QR codes scanned yet", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.recentQrScans.take(4).forEach { item ->
                            ActivityRecordRow(item = item, timeFormatter = timeFormatter, onClick = { onThreatClicked(item) })
                        }
                    }
                }
            }
        }

                item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun StatCard(
    title: String,
    count: Int,
    countColor: Color,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$count",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = countColor
            )
        }
    }
}

@Composable
fun ActivityRecordRow(
    item: ThreatRecord,
    timeFormatter: SimpleDateFormat,
    onClick: () -> Unit
) {
    val isDangerous = item.riskScore >= 70 || item.category.contains("HIGH", ignoreCase = true) || item.category.contains("SMISHING", ignoreCase = true) || item.category.contains("QUISHING", ignoreCase = true)
    val isAttention = !isDangerous && (item.riskScore >= 40 || item.category.contains("MEDIUM", ignoreCase = true) || item.category.contains("SUSPICIOUS", ignoreCase = true) || item.category.contains("SPAM", ignoreCase = true))

    val (badgeText, badgeColor, badgeBg) = when {
        isDangerous -> Triple("Dangerous", Color(0xFFF87171), Color(0xFFF87171).copy(alpha = 0.15f))
        isAttention -> Triple("Needs Attention", Color(0xFFFBBF24), Color(0xFFFBBF24).copy(alpha = 0.15f))
        else -> Triple("Looks Safe", Color(0xFF34D399), Color(0xFF34D399).copy(alpha = 0.15f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.source.contains("QR", ignoreCase = true)) Icons.Default.QrCode else Icons.Default.MailOutline,
                contentDescription = null,
                tint = if (item.source.contains("QR", ignoreCase = true)) Color(0xFFA78BFA) else Color(0xFF38BDF8),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.sender.ifBlank { if (item.source.contains("QR", ignoreCase = true)) "QR Code" else "Unknown Sender" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.rawContent?.take(60) ?: item.details.take(60),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeFormatter.format(Date(item.timestamp)),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}
