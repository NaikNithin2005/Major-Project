package com.example.android.presentation.alerts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.window.Dialog
import com.example.android.domain.model.ThreatRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsTab(
    threats: List<ThreatRecord>,
    onThreatClicked: ((ThreatRecord) -> Unit)? = null,
    onUpdateThreatAction: (String, String) -> Unit,
    onSubmitFeedback: (threatId: String, category: String, comment: String) -> Unit,
    onExplainWithAi: (ThreatRecord) -> Unit,
    onOpenEducation: () -> Unit
) {
    val context = LocalContext.current
    var selectedSeverityFilter by remember { mutableStateOf("All") }
    var selectedStatusFilter by remember { mutableStateOf("Unreviewed") }
    var feedbackThreat by remember { mutableStateOf<ThreatRecord?>(null) }

    val severityOptions = listOf("All", "Critical/High", "Medium", "Low")
    val statusOptions = listOf("Unreviewed", "Reviewed", "All")

    val filteredAlerts = remember(threats, selectedSeverityFilter, selectedStatusFilter) {
        threats.filter { threat ->
            val matchesSeverity = when (selectedSeverityFilter) {
                "Critical/High" -> threat.riskScore >= 70
                "Medium" -> threat.riskScore in 40..69
                "Low" -> threat.riskScore < 40
                else -> true
            }

            val matchesStatus = when (selectedStatusFilter) {
                "Unreviewed" -> threat.actionTaken != "RESOLVED" && threat.actionTaken != "FALSE_POSITIVE"
                "Reviewed" -> threat.actionTaken == "RESOLVED" || threat.actionTaken == "FALSE_POSITIVE"
                else -> true
            }

            matchesSeverity && matchesStatus
        }
    }

    val unreviewedCount = remember(threats) {
        threats.count { it.actionTaken != "RESOLVED" && it.actionTaken != "FALSE_POSITIVE" }
    }

    if (feedbackThreat != null) {
        FeedbackSubmissionDialog(
            threat = feedbackThreat!!,
            onDismiss = { feedbackThreat = null },
            onSubmit = { category, comment ->
                onSubmitFeedback(feedbackThreat!!.id, category, comment)
                onUpdateThreatAction(feedbackThreat!!.id, if (category.contains("False Positive", ignoreCase = true)) "FALSE_POSITIVE" else "CONFIRMED")
                Toast.makeText(context, "Feedback recorded. Thank you!", Toast.LENGTH_SHORT).show()
                feedbackThreat = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Unresolved threats summary banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (unreviewedCount > 0) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (unreviewedCount > 0) Color(0xFFEF4444).copy(alpha = 0.25f) else Color(0xFF10B981).copy(alpha = 0.25f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (unreviewedCount > 0) Icons.Default.Warning else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (unreviewedCount > 0) Color(0xFFF87171) else Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (unreviewedCount > 0) "$unreviewedCount Actionable Alerts" else "All Clear!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (unreviewedCount > 0) "Immediate user caution recommended" else "No unreviewed threat detections",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                if (unreviewedCount > 0) {
                    Surface(
                        color = Color(0xFFEF4444),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "$unreviewedCount new",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(statusOptions) { opt ->
                FilterChip(
                    selected = selectedStatusFilter == opt,
                    onClick = { selectedStatusFilter = opt },
                    label = { Text(opt, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0EA5E9).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFF38BDF8),
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }

            items(severityOptions) { opt ->
                FilterChip(
                    selected = selectedSeverityFilter == opt,
                    onClick = { selectedSeverityFilter = opt },
                    label = { Text(opt, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEF4444).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFF87171),
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredAlerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No alerts to display",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "All detected items in this view are resolved, or no threats have been logged.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredAlerts, key = { it.id }) { threat ->
                    ThreatAlertCard(
                        threat = threat,
                        onClick = { onThreatClicked?.invoke(threat) },
                        onMarkReviewed = {
                            onUpdateThreatAction(threat.id, "RESOLVED")
                            Toast.makeText(context, "Marked as reviewed", Toast.LENGTH_SHORT).show()
                        },
                        onOpenFeedback = { feedbackThreat = threat },
                        onExplainWithAi = { onExplainWithAi(threat) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ThreatAlertCard(
    threat: ThreatRecord,
    onClick: (() -> Unit)? = null,
    onMarkReviewed: () -> Unit,
    onOpenFeedback: () -> Unit,
    onExplainWithAi: () -> Unit
) {
    val isCritical = threat.riskScore >= 70
    val isSuspicious = threat.riskScore in 40..69

    val borderColor = when {
        isCritical -> Color(0xFFEF4444)
        isSuspicious -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    // Step-by-step context sensitive instructions
    val stepGuidance = remember(threat.source) {
        when {
            threat.source.contains("SMS", ignoreCase = true) -> listOf(
                "Do not tap links or reply to the SMS.",
                "Verify the sender through an official phone number or app.",
                "Never share OTPs, passwords, or banking details.",
                "Delete the message and block the sender."
            )
            threat.source.contains("QR", ignoreCase = true) -> listOf(
                "Do not open the decoded URL in your browser.",
                "Check the physical QR sticker for tampering.",
                "Never input passwords after scanning a QR code.",
                "Pay only through official verified store apps."
            )
            else -> listOf(
                "Avoid opening or forwarding this link.",
                "Verify the domain on the official website.",
                "Change passwords if credentials were typed."
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable { onClick?.invoke() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = borderColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when {
                                isCritical -> "HIGH RISK"
                                isSuspicious -> "SUSPICIOUS"
                                else -> "LOW RISK"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = borderColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Score: ${threat.riskScore}/100",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (threat.isQuarantined) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "🛡️ Quarantined",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault()).format(Date(threat.timestamp)),
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${threat.category} Alert from ${threat.sender}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Scanned Content Preview
            if (!threat.rawContent.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Scanned Content Preview:", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            Text("Tap to view details", fontSize = 10.sp, color = Color(0xFF38BDF8))
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = threat.rawContent,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = threat.details.ifBlank { "Unverified suspicious activity detected on device." },
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step-by-step Recommended Actions
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Recommended Safety Steps:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    stepGuidance.forEachIndexed { i, step ->
                        Text(
                            text = "${i + 1}. $step",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMarkReviewed,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resolve", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onOpenFeedback,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Feedback", fontSize = 11.sp)
                }

                Button(
                    onClick = onExplainWithAi,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Explain AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FeedbackSubmissionDialog(
    threat: ThreatRecord,
    onDismiss: () -> Unit,
    onSubmit: (category: String, comment: String) -> Unit
) {
    var selectedOption by remember { mutableStateOf("Correctly Detected Threat") }
    var commentText by remember { mutableStateOf("") }

    val options = listOf(
        "Correctly Detected Threat",
        "False Positive (Legitimate Message/URL)",
        "Missed Threat Indicators",
        "Unsure"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Threat Accuracy Feedback", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Help improve Squish on-device detection models.", fontSize = 12.sp, color = Color(0xFF94A3B8))

                Spacer(modifier = Modifier.height(14.dp))

                options.forEach { opt ->
                    val isSelected = selectedOption == opt
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedOption = opt },
                        color = if (isSelected) Color(0xFF0EA5E9).copy(alpha = 0.25f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedOption = opt },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(opt, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Optional comment (e.g. sender is my local electricity provider)", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(selectedOption, commentText) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Submit Feedback", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
