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
fun QuarantinedSmsScreen(
    quarantinedThreats: List<ThreatRecord>,
    userId: String?,
    repository: ThreatHistoryRepository,
    firestoreDataSource: FirestoreDataSource,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (ThreatRecord) -> Unit,
    onNavigateToTab: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeFormatter = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Security Center", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Quarantined SMS Messages", fontSize = 11.sp, color = Color(0xFF94A3B8))
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
                        selectedTabIndex = 2,
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

            // Invariant Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1C0C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Quarantine Policy: Releasing an SMS restores access to view the text, but NEVER automatically whitelists the sender.",
                        fontSize = 11.sp,
                        color = Color(0xFFFDE68A),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (quarantinedThreats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Quarantined Messages", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Your quarantine vault is empty. No messages are currently isolated.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(quarantinedThreats, key = { it.id }) { msg ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "From: ${msg.sender.ifBlank { "Unknown" }}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Risk: ${msg.riskScore}/100",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B)
                                    )
                                }

                                if (!msg.rawContent.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0F172A))
                                            .padding(10.dp)
                                    ) {
                                        Text(msg.rawContent, fontSize = 12.sp, color = Color(0xFFCBD5E1))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Reason: ${msg.details.ifBlank { "Suspicious smishing pattern" }}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = timeFormatter.format(Date(msg.timestamp)),
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    repository.updateQuarantineStatus(msg.id, false)
                                                    repository.updateThreatAction(msg.id, "RELEASED")
                                                    Toast.makeText(context, "Released from quarantine. Sender remains unverified.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Release", fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    repository.updateQuarantineStatus(msg.id, false)
                                                    repository.updateThreatAction(msg.id, "MARK_SAFE")
                                                    if (!userId.isNullOrBlank()) {
                                                        firestoreDataSource.updateThreatAction(userId, msg.id, "MARK_SAFE")
                                                    }
                                                    Toast.makeText(context, "Marked as safe", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Mark Safe", fontSize = 11.sp)
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

