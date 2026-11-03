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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UntrustedUsersScreen(
    threatHistoryRepository: ThreatHistoryRepository,
    trustedSenderRepository: TrustedSenderRepository,
    onNavigateBack: () -> Unit,
    onNavigateToSenderDetails: (String) -> Unit,
    onNavigateToTab: ((Int) -> Unit)? = null
) {
    val threats by threatHistoryRepository.observeAllThreats().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    // Group senders that are not trusted and have warnings/threats
    val untrustedSenders = remember(threats, searchQuery) {
        val groups = threats
            .filter { it.sender.isNotBlank() && it.source.contains("SMS", ignoreCase = true) }
            .groupBy { it.sender }
            .map { (sender, records) ->
                val dangerousCount = records.count { it.riskScore >= 70 }
                val suspiciousCount = records.count { it.riskScore in 35..69 }
                val maxRisk = records.maxOfOrNull { it.riskScore } ?: 0
                Triple(sender, records, maxRisk)
            }
            .filter { it.third >= 35 } // only senders with suspicious or dangerous history
            .sortedByDescending { it.third }

        if (searchQuery.isBlank()) groups
        else groups.filter { it.first.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Security Center", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Untrusted & Suspicious Senders", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                )
                if (onNavigateToTab != null) {
                    SecurityTabRow(
                        selectedTabIndex = 1,
                        onTabSelected = onNavigateToTab
                    )
                }
            }
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

            // Guidance Note
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Unverified senders are not automatically malicious. Squish separates first-time senders from confirmed smishing campaigns.",
                        fontSize = 11.sp,
                        color = Color(0xFFFDE68A),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search untrusted senders...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (untrustedSenders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Untrusted Senders Detected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("No senders currently have active threat warnings.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(untrustedSenders, key = { it.first }) { (sender, records, maxRisk) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToSenderDetails(sender) }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (maxRisk >= 70) Color(0xFFEF4444).copy(alpha = 0.2f)
                                                else Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (maxRisk >= 70) Icons.Default.Warning else Icons.Default.HelpOutline,
                                            contentDescription = null,
                                            tint = if (maxRisk >= 70) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(sender, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("${records.size} messages • Max Risk: $maxRisk/100", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                trustedSenderRepository.addTrustedSender(sender)
                                                Toast.makeText(context, "Marked $sender as trusted", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = "Trust", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
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
