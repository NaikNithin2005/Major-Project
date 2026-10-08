package com.example.android.presentation.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    currentUser: User?,
    uiState: DashboardUiState = DashboardUiState(),
    onDeleteThreat: (String) -> Unit = {},
    onTriggerManualSync: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToSmsMonitoring: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {},
    onLogoutClicked: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var selectedThreat by remember { mutableStateOf<ThreatRecord?>(null) }
    val context = LocalContext.current

    val greetingName = when {
        currentUser?.isGuest == true -> "User"
        !currentUser?.displayName.isNullOrBlank() -> currentUser?.displayName!!
        else -> "User"
    }

    if (selectedThreat != null) {
        ThreatDetailDialog(
            threat = selectedThreat!!,
            onDismiss = { selectedThreat = null },
            onDelete = {
                onDeleteThreat(selectedThreat!!.id)
                selectedThreat = null
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color(0xFF94A3B8)
            ) {
                val items = listOf(
                    Triple("Home", Icons.Default.Home, 0),
                    Triple("History", Icons.Default.History, 1),
                    Triple("Scanner", Icons.Default.QrCodeScanner, 2),
                    Triple("Alerts", Icons.Default.Notifications, 3),
                    Triple("Settings", Icons.Default.Settings, 4)
                )

                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index == 2) {
                                onNavigateToQrScanner()
                            } else if (index == 4) {
                                onNavigateToSettings()
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF38BDF8),
                            selectedTextColor = Color(0xFF38BDF8),
                            indicatorColor = Color(0xFF0EA5E9).copy(alpha = 0.2f),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                    )
                )
                .padding(innerPadding)
        ) {
            if (selectedTab == 1) {
                // HISTORY TAB CONTENT
                ThreatHistoryView(
                    threats = uiState.threatHistory,
                    onDeleteThreat = onDeleteThreat,
                    onThreatClicked = { selectedThreat = it },
                    onNavigateToSmsMonitoring = onNavigateToSmsMonitoring
                )
            } else {
                // HOME DASHBOARD CONTENT
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Header User Greeting
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Hello, $greetingName",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (currentUser?.isGuest == true) "Guest Session" else currentUser?.email ?: "",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            IconButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color(0xFF1E293B), CircleShape)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color(0xFF38BDF8))
                            }
                        }
                    }

                    // Security Overview Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(Color(0xFF10B981), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Protection Active", fontSize = 12.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Medium)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Interactive Cloud Synchronization Status Indicator
                                        Surface(
                                            color = if (uiState.pendingSyncCount == 0) {
                                                Color(0xFF0EA5E9).copy(alpha = 0.18f)
                                            } else {
                                                Color(0xFFF59E0B).copy(alpha = 0.22f)
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable {
                                                    onTriggerManualSync()
                                                    Toast.makeText(context, "Initiating cloud sync...", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (uiState.pendingSyncCount == 0) Icons.Default.CloudDone else Icons.Default.Sync,
                                                    contentDescription = "Sync",
                                                    tint = if (uiState.pendingSyncCount == 0) Color(0xFF38BDF8) else Color(0xFFFBBF24),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = when {
                                                        uiState.pendingSyncCount == 0 -> "Synced"
                                                        else -> "⏳ ${uiState.pendingSyncCount} pending"
                                                    },
                                                    fontSize = 11.sp,
                                                    color = when {
                                                        uiState.pendingSyncCount == 0 -> Color(0xFF38BDF8)
                                                        else -> Color(0xFFFBBF24)
                                                    },
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text("Security Score", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                    Text("${uiState.securityScore} / 100", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(Color(0xFF0EA5E9).copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(40.dp))
                                }
                            }
                        }
                    }

                    // Dynamic Activity Counters Grid
                    item {
                        Text("Today's Activity", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard("Threats", "${uiState.threatCount}", Icons.Default.Warning, Color(0xFFEF4444), Modifier.weight(1f)) {
                                selectedTab = 1
                            }
                            StatCard("SMS Scanned", "${uiState.smsScannedCount}", Icons.Default.Sms, Color(0xFF3B82F6), Modifier.weight(1f), onClick = onNavigateToSmsMonitoring)
                            StatCard("QR Scanned", "${uiState.qrScannedCount}", Icons.Default.QrCode, Color(0xFF10B981), Modifier.weight(1f), onClick = onNavigateToQrScanner)
                        }
                    }

                    // Quick Actions
                    item {
                        Text("Quick Actions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onNavigateToSmsMonitoring,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SMS Monitor", fontSize = 13.sp)
                            }

                            Button(
                                onClick = onNavigateToQrScanner,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("QR Scanner", fontSize = 13.sp)
                            }
                        }
                    }

                    // Recent Threat Activity List
                    item {
                        Text("Recent Threat Log", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }

                    if (uiState.threatHistory.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("No threat activity detected today.", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    } else {
                        items(uiState.threatHistory.take(5), key = { it.id }) { threat ->
                            ThreatRecordItemCard(
                                threat = threat,
                                onClick = { selectedThreat = threat },
                                onDelete = onDeleteThreat
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThreatHistoryView(
    threats: List<ThreatRecord>,
    onDeleteThreat: (String) -> Unit,
    onThreatClicked: (ThreatRecord) -> Unit,
    onNavigateToSmsMonitoring: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Threat History Log",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${threats.size} Recorded",
                    fontSize = 13.sp,
                    color = Color(0xFF38BDF8)
                )
            }
        }

        if (threats.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color(0xFF34D399)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Threats Recorded",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your device is clean. Incoming smishing SMS and malicious QR codes will automatically log threats here.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToSmsMonitoring,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                        ) {
                            Text("Open SMS Monitor")
                        }
                    }
                }
            }
        } else {
            items(threats, key = { it.id }) { threat ->
                ThreatRecordItemCard(
                    threat = threat,
                    onClick = { onThreatClicked(threat) },
                    onDelete = onDeleteThreat
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatRecordItemCard(
    threat: ThreatRecord,
    onClick: () -> Unit = {},
    onDelete: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val timeStr = remember(threat.timestamp) { dateFormat.format(Date(threat.timestamp)) }

    val isHigh = threat.category.contains("HIGH", ignoreCase = true) ||
            threat.category.contains("SMISHING", ignoreCase = true) ||
            threat.category.contains("QUISHING", ignoreCase = true) ||
            threat.riskScore >= 75
    val isMedium = !isHigh && (threat.category.contains("MEDIUM", ignoreCase = true) ||
            threat.category.contains("SPAM", ignoreCase = true) ||
            threat.riskScore >= 50)
    val isLow = !isHigh && !isMedium && (threat.category.contains("LOW", ignoreCase = true) || threat.riskScore >= 25)

    val (categoryLabel, categoryColor, categoryBg) = when {
        isHigh -> Triple("HIGH RISK", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f))
        isMedium -> Triple("MEDIUM RISK", Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.15f))
        isLow -> Triple("LOW RISK", Color(0xFF38BDF8), Color(0xFF38BDF8).copy(alpha = 0.15f))
        else -> Triple("SAFE", Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.15f))
    }

    val icon = when {
        threat.source.contains("QR", ignoreCase = true) -> Icons.Default.QrCodeScanner
        isHigh -> Icons.Default.Dangerous
        isMedium -> Icons.Default.Warning
        else -> Icons.Default.Sms
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Sender & Category + Risk Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = threat.sender,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Category Badge
                    Surface(
                        color = categoryBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = categoryLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Risk Score Badge
                    Surface(
                        color = categoryBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${threat.riskScore}% Risk",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Action Badge (if present)
            if (threat.actionTaken.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF334155).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Action: ${threat.actionTaken}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val cleanedDetails = remember(threat.details) {
                var d = threat.details.trim()
                if (d.contains("keywords:", ignoreCase = true)) {
                    val idx = d.indexOf("keywords:", ignoreCase = true)
                    val prefix = d.substring(0, idx).trim().trim(',', ' ')
                    val kw = d.substring(idx + 9).trim().trim(',', ' ')
                    d = if (kw.isBlank() || kw.equals("none", ignoreCase = true)) {
                        prefix
                    } else if (prefix.isNotBlank()) {
                        "$prefix • Keywords: $kw"
                    } else {
                        "Keywords: $kw"
                    }
                }
                if (d.startsWith("Detected 1 URLs", ignoreCase = true)) {
                    d = d.replace("Detected 1 URLs", "Detected 1 external URL")
                }
                d.ifBlank { "Suspicious pattern flagged by AI engine" }
            }
            Text(
                text = cleanedDetails,
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$timeStr • ${threat.source}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Inspect",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF38BDF8)
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Inspect",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onDelete(threat.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThreatDetailDialog(
    threat: ThreatRecord,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(threat.timestamp) { dateFormat.format(Date(threat.timestamp)) }

    val isHigh = threat.category.contains("HIGH", ignoreCase = true) ||
            threat.category.contains("SMISHING", ignoreCase = true) ||
            threat.category.contains("QUISHING", ignoreCase = true) ||
            threat.riskScore >= 75
    val isMedium = !isHigh && (threat.category.contains("MEDIUM", ignoreCase = true) ||
            threat.category.contains("SPAM", ignoreCase = true) ||
            threat.riskScore >= 50)
    val isLow = !isHigh && !isMedium && (threat.category.contains("LOW", ignoreCase = true) || threat.riskScore >= 25)

    val (categoryTitle, categoryColor, categoryBg) = when {
        isHigh -> Triple("CRITICAL THREAT (HIGH)", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f))
        isMedium -> Triple("SECURITY WARNING (MEDIUM)", Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.15f))
        isLow -> Triple("MINOR CAUTION (LOW)", Color(0xFF38BDF8), Color(0xFF38BDF8).copy(alpha = 0.15f))
        else -> Triple("VERIFIED SAFE", Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.15f))
    }

    val actionText = threat.actionTaken.ifBlank {
        when {
            isHigh -> "BLOCK_AND_DELETE"
            isMedium -> "WARN_USER"
            else -> "PROCEED_SAFELY"
        }
    }

    val guidanceText = when {
        isHigh -> "Critical threat detected. On-device defense quarantined the payload to prevent credential theft or exploitation. Do not interact."
        isMedium -> "Suspicious indicators observed. Verify sender credentials through official verified channels before opening links."
        isLow -> "Minor heuristic anomaly noted. Standard caution advised."
        else -> "All neural NLP and heuristic checks passed. Safe to interact."
    }

    val reasonsList = remember(threat.details, threat.riskScore) {
        val parts = threat.details.split(";").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isNotEmpty()) {
            parts
        } else {
            listOf(
                "Risk evaluation score: ${threat.riskScore}/100",
                "Flagged by on-device real-time detection pipeline"
            )
        }
    }

    val isQr = threat.source.contains("QR", ignoreCase = true)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(categoryBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isQr) Icons.Default.QrCodeScanner else Icons.Default.Security,
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Threat AI Analysis",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "On-Device Inference Report",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. RISK SCORE & CATEGORY
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Risk Score", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text(
                                    text = "${threat.riskScore} / 100",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = categoryColor
                                )
                            }

                            Surface(
                                color = categoryBg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = categoryTitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = categoryColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (threat.riskScore / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = categoryColor,
                            trackColor = Color(0xFF334155),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. SENDER / TARGET ORIGIN
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sender / Target Identifier", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = threat.sender,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Sender", threat.sender))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. RECOMMENDED ACTION & DEFENSIVE POLICY
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recommended Action", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = actionText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = guidanceText,
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. REASONS & EXPLAINABLE EVIDENCE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Reasons & Evidence", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(6.dp))
                        reasonsList.forEach { reason ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("•", color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = reason,
                                    fontSize = 12.sp,
                                    color = Color(0xFFE2E8F0),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. MODEL RESULTS & PIPELINE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Model Results & Pipeline", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Channel Vector", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(threat.source, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Inference Engine", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(
                                if (isQr) "XGBoost + Isolation Forest" else "TinyBERT Smishing NLP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Execution Mode", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("On-Device Edge (<30ms)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF34D399))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6. TIMESTAMP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Timestamp", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(formattedTime, fontSize = 11.sp, color = Color(0xFF94A3B8))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                    ) {
                        Text("Close", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatCard(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        }
    }
}
