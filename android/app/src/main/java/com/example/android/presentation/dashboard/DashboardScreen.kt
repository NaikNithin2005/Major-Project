package com.example.android.presentation.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onNavigateToSettings: () -> Unit,
    onNavigateToSmsMonitoring: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {},
    onLogoutClicked: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    val greetingName = when {
        currentUser?.isGuest == true -> "User"
        !currentUser?.displayName.isNullOrBlank() -> currentUser?.displayName!!
        else -> "User"
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
                            ThreatRecordItemCard(threat = threat, onDelete = onDeleteThreat)
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
                ThreatRecordItemCard(threat = threat, onDelete = onDeleteThreat)
            }
        }
    }
}

@Composable
fun ThreatRecordItemCard(threat: ThreatRecord, onDelete: (String) -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val timeStr = remember(threat.timestamp) { dateFormat.format(Date(threat.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = threat.sender,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${threat.riskScore}% Risk",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = threat.details,
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1)
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
