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
    onTriggerManualSync: () -> Unit
) {
    val context = LocalContext.current
    var expandedTip by remember { mutableStateOf<SecurityTip?>(null) }

    val todayTip = remember { SecurityTipsProvider.getTodayTip() }

    // Real-time Permission / Protection status check
    val hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    // Deterministic Security Scoring:
    // Base 100, deduct -15 per unresolved threat, -15 if SMS disabled, -10 if Notifications disabled
    val unresolvedThreats = uiState.threatHistory.filter { it.actionTaken != "RESOLVED" && it.actionTaken != "FALSE_POSITIVE" }
    val calculatedScore = remember(unresolvedThreats.size, hasSmsPermission, hasNotificationPermission) {
        var score = 100
        score -= (unresolvedThreats.size * 15)
        if (!hasSmsPermission) score -= 15
        if (!hasNotificationPermission) score -= 10
        score.coerceIn(15, 100)
    }

    val (scoreLabel, scoreColor) = when {
        calculatedScore >= 80 -> "Protected" to Color(0xFF34D399)
        calculatedScore >= 50 -> "Attention Needed" to Color(0xFFFBBF24)
        else -> "High Risk" to Color(0xFFF87171)
    }

    val greetingName = when {
        currentUser?.isGuest == true -> "Guest User"
        !currentUser?.displayName.isNullOrBlank() -> currentUser?.displayName!!
        !currentUser?.email.isNullOrBlank() -> currentUser?.email!!.substringBefore("@")
        else -> "User"
    }

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
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Header User Greeting & Status
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

                // Cloud Sync Indicator
                Surface(
                    color = if (uiState.pendingSyncCount == 0) Color(0xFF0EA5E9).copy(alpha = 0.18f) else Color(0xFFF59E0B).copy(alpha = 0.22f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTriggerManualSync() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uiState.pendingSyncCount == 0) Icons.Default.CloudDone else Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = if (uiState.pendingSyncCount == 0) Color(0xFF38BDF8) else Color(0xFFFBBF24),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (uiState.pendingSyncCount == 0) "Synced" else "${uiState.pendingSyncCount} pending",
                            fontSize = 11.sp,
                            color = if (uiState.pendingSyncCount == 0) Color(0xFF38BDF8) else Color(0xFFFBBF24),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Section A: Prominent Security Score Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(scoreColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = scoreLabel.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreColor
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Device Security Score",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        // Big Score Ring Display
                        Surface(
                            shape = CircleShape,
                            color = scoreColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$calculatedScore",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    HorizontalDivider(color = Color(0xFF334155))

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Unresolved Threats", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("${unresolvedThreats.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (unresolvedThreats.isEmpty()) Color(0xFF34D399) else Color(0xFFF87171))
                        }
                        Column {
                            Text("Total Scans", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("${uiState.smsScannedCount + uiState.qrScannedCount}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column {
                            Text("Protection Engine", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(if (hasSmsPermission) "Real-Time Active" else "Partial", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }
                    }
                }
            }
        }

        // Section B: Real-Time Protection Indicators
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Protection Service Health",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ProtectionHealthItem(
                            label = "SMS Shield",
                            isActive = hasSmsPermission,
                            icon = Icons.Default.Sms,
                            onClick = { onNavigateToTab(4) }
                        )
                        ProtectionHealthItem(
                            label = "QR / Camera",
                            isActive = hasCameraPermission,
                            icon = Icons.Default.QrCodeScanner,
                            onClick = { onNavigateToTab(2) }
                        )
                        ProtectionHealthItem(
                            label = "Notifications",
                            isActive = hasNotificationPermission,
                            icon = Icons.Default.NotificationsActive,
                            onClick = { onNavigateToTab(4) }
                        )
                        ProtectionHealthItem(
                            label = "On-Device AI",
                            isActive = true,
                            icon = Icons.Default.Memory,
                            onClick = { onNavigateToTab(4) }
                        )
                    }
                }
            }
        }

        // Section C: Today's Threat Statistics
        item {
            Text(
                text = "Threat Overview",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThreatStatCard(
                    title = "Smishing Blocked",
                    count = uiState.threatHistory.count { it.source.contains("SMS", ignoreCase = true) },
                    icon = Icons.Default.SmsFailed,
                    accentColor = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f)
                )
                ThreatStatCard(
                    title = "Quishing Scanned",
                    count = uiState.qrScannedCount,
                    icon = Icons.Default.QrCode,
                    accentColor = Color(0xFF0EA5E9),
                    modifier = Modifier.weight(1f)
                )
                ThreatStatCard(
                    title = "Suspicious Links",
                    count = uiState.threatHistory.count { it.details.contains("URL", ignoreCase = true) || it.details.contains("http", ignoreCase = true) },
                    icon = Icons.Default.LinkOff,
                    accentColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section E: Quick Actions
        item {
            Text(
                text = "Quick Actions",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Message,
                    label = "Scan Message",
                    color = Color(0xFF0EA5E9),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(2) }
                )
                QuickActionButton(
                    icon = Icons.Default.QrCodeScanner,
                    label = "Scan QR",
                    color = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(2) }
                )
                QuickActionButton(
                    icon = Icons.Default.Warning,
                    label = "Alerts",
                    color = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(3) }
                )
                QuickActionButton(
                    icon = Icons.Default.School,
                    label = "Academy",
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenEducation
                )
            }
        }

        // Section F: Daily Security Tip Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedTip = todayTip },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Daily Security Tip", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                        }
                        Text("Tap to read", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(todayTip.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(todayTip.summary, fontSize = 12.sp, color = Color(0xFF94A3B8), maxLines = 2)
                }
            }
        }

        // Section D: Recent Security Activity
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Security Activity",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                TextButton(onClick = { onNavigateToTab(1) }) {
                    Text("View All", fontSize = 12.sp, color = Color(0xFF38BDF8))
                }
            }
        }

        if (uiState.threatHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No threats detected yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Your device is monitoring incoming SMS and scanned QR codes.", fontSize = 12.sp, color = Color(0xFF94A3B8), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        } else {
            items(uiState.threatHistory.take(5)) { threat ->
                RecentThreatItem(
                    threat = threat,
                    onClick = { onThreatClicked(threat) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProtectionHealthItem(
    label: String,
    isActive: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isActive) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFF34D399) else Color(0xFFF87171),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = Color(0xFF94A3B8))
        Text(
            if (isActive) "Active" else "Action",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color(0xFF34D399) else Color(0xFFF87171)
        )
    }
}

@Composable
fun ThreatStatCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("$count", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(title, fontSize = 10.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun RecentThreatItem(
    threat: ThreatRecord,
    onClick: () -> Unit
) {
    val isCritical = threat.riskScore >= 70
    val isSuspicious = threat.riskScore in 40..69

    val badgeColor = when {
        isCritical -> Color(0xFFEF4444)
        isSuspicious -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(badgeColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (threat.source.contains("QR", ignoreCase = true)) Icons.Default.QrCode else Icons.Default.Sms,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(threat.category, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(threat.timestamp)),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    threat.details.ifBlank { "Flagged suspicious activity from ${threat.sender}" },
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = badgeColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "${threat.riskScore}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
