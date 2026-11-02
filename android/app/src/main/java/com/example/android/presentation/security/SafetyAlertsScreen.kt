package com.example.android.presentation.security

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.data.source.FirestoreDataSource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyAlertsScreen(
    repository: ThreatHistoryRepository,
    userId: String?,
    firestoreDataSource: FirestoreDataSource,
    onNavigateBack: () -> Unit,
    onNavigateToThreatDetails: (ThreatRecord) -> Unit,
    onNavigateToSenderDetails: (String) -> Unit
) {
    val threats by repository.observeAllThreats().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("ALL") }
    val tabs = listOf("ALL", "ACTIVE", "ACKNOWLEDGED", "RESOLVED")
    val timeFormatter = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    // Filter threats according to alert status
    val filteredAlerts = remember(threats, selectedTab) {
        val alertsOnly = threats.filter { it.riskScore >= 40 || it.category == "HIGH" || it.category == "CRITICAL" || it.actionTaken == "RESOLVED" }
        when (selectedTab) {
            "ACTIVE" -> alertsOnly.filter { it.actionTaken != "RESOLVED" && it.actionTaken != "ACKNOWLEDGED" && it.actionTaken != "BLOCK" }
            "ACKNOWLEDGED" -> alertsOnly.filter { it.actionTaken == "ACKNOWLEDGED" }
            "RESOLVED" -> alertsOnly.filter { it.actionTaken == "RESOLVED" }
            else -> alertsOnly
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Important Safety Alerts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Active threats & urgent advisories", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF090D16)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tab == "ALL") "All" else tab.lowercase().replaceFirstChar { it.uppercase() },
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredAlerts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Alerts in this Category", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("All transactions and incoming SMS are operating safely.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAlerts, key = { it.id }) { alert ->
                        val isResolved = alert.actionTaken == "RESOLVED"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isResolved) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFF2D1517)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(if (isResolved) Color(0xFF10B981) else Color(0xFFF87171), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = alert.sender.ifBlank { "Suspicious Alert" },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = if (isResolved) "RESOLVED" else "Risk: ${alert.riskScore}/100",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isResolved) Color(0xFF10B981) else Color(0xFFF87171)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = alert.details.ifBlank { "High threat score smishing indicators identified by TinyBERT model." },
                                    fontSize = 12.sp,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!alert.rawContent.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = alert.rawContent,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = timeFormatter.format(Date(alert.timestamp)),
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (!isResolved) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        repository.updateThreatAction(alert.id, "RESOLVED")
                                                        if (!userId.isNullOrBlank()) {
                                                            firestoreDataSource.updateThreatAction(userId, alert.id, "RESOLVED")
                                                        }
                                                        Toast.makeText(context, "Alert resolved successfully", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Resolve", fontSize = 11.sp)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { onNavigateToThreatDetails(alert) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                                        ) {
                                            Text("Details", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}
