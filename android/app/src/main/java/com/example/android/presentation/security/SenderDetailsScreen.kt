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
import com.example.android.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderDetailsScreen(
    senderId: String,
    threatHistoryRepository: ThreatHistoryRepository,
    trustedSenderRepository: TrustedSenderRepository,
    onNavigateBack: () -> Unit,
    onNavigateToThreatDetails: (ThreatRecord) -> Unit
) {
    val threats by threatHistoryRepository.observeAllThreats().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeFormatter = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    var isTrusted by remember { mutableStateOf(false) }

    LaunchedEffect(senderId) {
        isTrusted = trustedSenderRepository.isTrusted(senderId)
    }

    val senderMessages = remember(threats, senderId) {
        threats.filter { it.sender.equals(senderId, ignoreCase = true) }
    }

    val dangerousCount = remember(senderMessages) {
        senderMessages.count { it.riskScore >= 70 }
    }

    val maxRisk = remember(senderMessages) {
        senderMessages.maxOfOrNull { it.riskScore } ?: 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(senderId, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Sender Security Profile", fontSize = 11.sp, color = Color(0xFF94A3B8))
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

            // Sender Overview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF0284C7).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = senderId.take(2).uppercase(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(senderId, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = if (isTrusted) "Status: TRUSTED" else if (maxRisk >= 70) "Status: HIGH RISK" else "Status: UNVERIFIED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isTrusted) Color(0xFF10B981) else if (maxRisk >= 70) Color(0xFFEF4444) else Color(0xFFF59E0B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Total Messages", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                Text("${senderMessages.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Dangerous Alerts", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                Text("$dangerousCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Peak Threat Score", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                Text("$maxRisk/100", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isTrusted) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        trustedSenderRepository.removeTrustedSender(senderId)
                                        isTrusted = false
                                        Toast.makeText(context, "Removed from trusted list", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                            ) {
                                Text("Remove Trust", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        trustedSenderRepository.addTrustedSender(senderId)
                                        isTrusted = true
                                        Toast.makeText(context, "Added $senderId to trusted senders", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Trust Sender", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    senderMessages.forEach { msg ->
                                        threatHistoryRepository.updateThreatAction(msg.id, "BLOCK")
                                    }
                                    Toast.makeText(context, "Sender $senderId messages blocked", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B))
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Block Sender", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Associated Message History (${senderMessages.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))

            if (senderMessages.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No message events stored for this sender.", fontSize = 12.sp, color = Color(0xFF64748B))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(senderMessages, key = { it.id }) { msg ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToThreatDetails(msg) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = msg.category,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.riskScore >= 70) Color(0xFFEF4444) else if (msg.riskScore >= 35) Color(0xFFF59E0B) else Color(0xFF10B981)
                                    )
                                    Text(
                                        text = timeFormatter.format(Date(msg.timestamp)),
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                if (!msg.rawContent.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.rawContent,
                                        fontSize = 11.sp,
                                        color = Color(0xFFCBD5E1),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
