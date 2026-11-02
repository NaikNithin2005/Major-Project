package com.example.android.presentation.security

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
fun SmsDetailsScreen(
    threatId: String,
    repository: ThreatHistoryRepository,
    trustedSenderRepository: TrustedSenderRepository,
    onNavigateBack: () -> Unit,
    onNavigateToSenderDetails: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var threat by remember { mutableStateOf<ThreatRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val timeFormatter = remember { SimpleDateFormat("MMMM dd, yyyy • hh:mm:ss a", Locale.getDefault()) }

    LaunchedEffect(threatId) {
        threat = repository.getThreatById(threatId).getOrNull()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SMS Threat Forensics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(threatId, fontSize = 10.sp, color = Color(0xFF94A3B8))
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
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF38BDF8))
            }
        } else if (threat == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Record not found", color = Color(0xFF94A3B8))
            }
        } else {
            val record = threat!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Card
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
                            Text(
                                text = record.category,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (record.riskScore >= 70) Color(0xFFEF4444) else if (record.riskScore >= 35) Color(0xFFF59E0B) else Color(0xFF10B981)
                            )
                            Text(
                                text = "Risk: ${record.riskScore}/100",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (record.riskScore >= 70) Color(0xFFEF4444) else if (record.riskScore >= 35) Color(0xFFF59E0B) else Color(0xFF10B981)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Sender: ${record.sender}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Detected: ${timeFormatter.format(Date(record.timestamp))}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Message Content
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Message Body", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = record.rawContent ?: "No message content",
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }

                // AI Detection Heuristics
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("AI TinyBERT Analysis & Reasons", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(record.details, fontSize = 12.sp, color = Color(0xFFCBD5E1))

                        if (!record.extractedUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Extracted URL: ${record.extractedUrl}", fontSize = 11.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                repository.updateThreatAction(record.id, "RESOLVED")
                                Toast.makeText(context, "Marked as Safe", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Mark Safe", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                repository.updateThreatAction(record.id, "BLOCK")
                                Toast.makeText(context, "Message Blocked", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Block Message", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
