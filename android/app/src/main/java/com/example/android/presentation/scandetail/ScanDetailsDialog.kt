package com.example.android.presentation.scandetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.android.data.parser.QrPayloadParser
import com.example.android.domain.analysis.DetailedMessageAnalysisEngine
import com.example.android.domain.analysis.DetailedMessageAnalysisEngine.DetectionVerdict
import com.example.android.domain.model.ThreatRecord

/**
 * Production Scan Details & Message Analysis Dialog.
 * Implements Phase 2 structured 6-section breakdown:
 * - Section A: Message Information & Raw Content
 * - Section B: Detection Result (Looks Safe / Needs Attention / Dangerous)
 * - Section C: Why Was This Flagged? (Plain English explanation of triggers)
 * - Section D: Detailed Breakdown Table / Evaluation Matrix
 * - Section E: Contextual Recommendations (Banking, Delivery, Government, Job, Safe)
 * - Section F: Reversible Actions & User Feedback Learning
 */
@Composable
fun ScanDetailsDialog(
    threat: ThreatRecord,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleQuarantine: ((Boolean) -> Unit)? = null,
    onUpdateAction: ((String) -> Unit)? = null,
    onExplainWithAi: ((ThreatRecord) -> Unit)? = null,
    onOpenSecurityCheck: ((url: String, source: String) -> Unit)? = null,
    isSenderTrusted: Boolean = false,
    onToggleTrustSender: ((Boolean) -> Unit)? = null,
    onSubmitFeedback: ((category: String, comment: String, correctedClassification: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showMarkSafeDialog by remember { mutableStateOf(false) }
    var showReportScamDialog by remember { mutableStateOf(false) }
    var showMatrixExpanded by remember { mutableStateOf(true) }
    var isPasswordRevealed by remember { mutableStateOf(false) }

    // Run Detailed Analysis Engine
    val analysis = remember(threat, isSenderTrusted) {
        DetailedMessageAnalysisEngine.analyze(threat, isSenderTrusted)
    }

    val isHigh = analysis.verdict == DetectionVerdict.DANGEROUS
    val isMedium = analysis.verdict == DetectionVerdict.NEEDS_ATTENTION
    val isSafe = analysis.verdict == DetectionVerdict.LOOKS_SAFE

    val (verdictTitle, verdictColor, verdictBg) = when (analysis.verdict) {
        DetectionVerdict.DANGEROUS -> Triple("DANGEROUS", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f))
        DetectionVerdict.NEEDS_ATTENTION -> Triple("NEEDS ATTENTION", Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.15f))
        DetectionVerdict.LOOKS_SAFE -> Triple("LOOKS SAFE", Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.15f))
    }

    val isQr = threat.source.contains("QR", ignoreCase = true)
    val isSms = threat.source.contains("SMS", ignoreCase = true)

    val parsedQr = remember(threat.rawContent) {
        if (isQr && !threat.rawContent.isNullOrBlank()) {
            QrPayloadParser.parse(threat.rawContent)
        } else {
            null
        }
    }

    // Interactive Mark Safe Feedback Dialog
    if (showMarkSafeDialog) {
        var selectedSafeReason by remember { mutableStateOf("Legitimate message from my bank or service") }
        val safeOptions = listOf(
            "Legitimate message from my bank or service",
            "I know and trust this sender",
            "This is a normal personal message",
            "Standard delivery / service notification"
        )

        AlertDialog(
            onDismissRequest = { showMarkSafeDialog = false },
            title = {
                Text("Mark as Safe & Teach Squish", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Help Squish adapt to your messages. Why do you consider this message safe?",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    safeOptions.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedSafeReason = opt }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSafeReason == opt,
                                onClick = { selectedSafeReason = opt },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(opt, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMarkSafeDialog = false
                        onSubmitFeedback?.invoke("Safe / Legitimate", selectedSafeReason, "SAFE")
                        onUpdateAction?.invoke("MARK_SAFE")
                        Toast.makeText(context, "Marked as safe. Squish updated your local safety profile.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Confirm Safe", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkSafeDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Interactive Report Scam Feedback Dialog
    if (showReportScamDialog) {
        var selectedScamReason by remember { mutableStateOf("Dangerous scam / phishing attempt") }
        val scamOptions = listOf(
            "Dangerous scam / phishing attempt",
            "Suspicious link / unsolicited payment demand",
            "Impersonating my bank or government agency",
            "Spam / Unsolicited marketing"
        )

        AlertDialog(
            onDismissRequest = { showReportScamDialog = false },
            title = {
                Text("Report Threat & Protect Others", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Help Squish identify scam patterns. Why are you flagging this message as dangerous?",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    scamOptions.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedScamReason = opt }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedScamReason == opt,
                                onClick = { selectedScamReason = opt },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(opt, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportScamDialog = false
                        onSubmitFeedback?.invoke("Scam / Threat", selectedScamReason, "DANGEROUS")
                        onUpdateAction?.invoke("CONFIRMED_THREAT")
                        Toast.makeText(context, "Reported as dangerous. Squish reinforced your shield.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Report Scam", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportScamDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Confirmation dialog for deletion
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text("Delete Record?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "This will delete this scan record from your device database. Safe messages and verified senders can still be managed in your settings.",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Confirmation dialog before restoring / releasing quarantined threat
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = {
                Text("Release from Quarantine?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "Are you sure you want to release this message from Quarantine? This marks the record as reviewed.",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        onToggleQuarantine?.invoke(false)
                        Toast.makeText(context, "Released from Quarantine", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Confirm Release", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(verdictBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isQr -> Icons.Default.QrCodeScanner
                                    isSms -> Icons.Default.Sms
                                    else -> Icons.Default.Link
                                },
                                contentDescription = null,
                                tint = verdictColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Safety Check Analysis",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = analysis.formattedTimestamp,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION B: DETECTION RESULT BANNER (Prominent Visual Indicator)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(verdictColor.copy(alpha = 0.4f)))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Overall Safety Status", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = verdictTitle,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = verdictColor
                            )
                            Text(
                                text = "Category: ${analysis.detectedCategory}",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            color = verdictBg,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Safety Rating", fontSize = 9.sp, color = verdictColor, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${threat.riskScore}/100",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = verdictColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SECTION A: MESSAGE INFORMATION & ORIGINAL CONTENT
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Sender row with copy button and trust indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sender Identifier", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = analysis.sender,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (isSenderTrusted) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "Trusted",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Sender", analysis.sender))
                                        Toast.makeText(context, "Copied sender", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Sender", tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Message Text Box
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSms) "Original Text Message" else "Original Scanned Payload",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Message Text", analysis.originalContent))
                                    Toast.makeText(context, "Copied message text", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Text", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    text = analysis.originalContent,
                                    fontSize = 12.sp,
                                    color = Color(0xFFF1F5F9),
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Extracted Link Preview
                        val targetUrl = threat.extractedUrl ?: parsedQr?.extractedUrl
                        if (!targetUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Web Link in Message:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(targetUrl, fontSize = 11.sp, color = Color.White, maxLines = 2)

                                    if (onOpenSecurityCheck != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                onDismiss()
                                                onOpenSecurityCheck(targetUrl, threat.source)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(32.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Inspect in Security Check", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SECTION C: WHY WAS THIS MESSAGE FLAGGED?
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Why Was This Message Flagged?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = analysis.whyFlaggedExplanation,
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SECTION D: DETAILED BREAKDOWN TABLE (Evaluation Matrix)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMatrixExpanded = !showMatrixExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Detailed Evaluation Matrix", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            IconButton(onClick = { showMatrixExpanded = !showMatrixExpanded }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (showMatrixExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle Matrix",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }

                        AnimatedVisibility(visible = showMatrixExpanded) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                val matrixRows = listOf(
                                    analysis.senderVerification,
                                    analysis.languageUrgency,
                                    analysis.linkSafety,
                                    analysis.personalInfoRequest,
                                    analysis.financialAction,
                                    analysis.overallAssessment
                                )

                                matrixRows.forEachIndexed { index, row ->
                                    val rowBg = if (row.isFavorable) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f)
                                    val rowColor = if (row.isFavorable) Color(0xFF10B981) else Color(0xFFEF4444)

                                    Surface(
                                        color = Color(0xFF0F172A),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(row.dimension, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                                Surface(color = rowBg, shape = RoundedCornerShape(6.dp)) {
                                                    Text(
                                                        text = row.status,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = rowColor,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = row.description,
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SECTION E: CONTEXTUAL RECOMMENDATIONS
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Contextual Safety Guidance", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        analysis.contextualRecommendations.forEachIndexed { idx, guide ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = guide,
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // EXPLAIN WITH SQUISH AI BUTTON
                if (onExplainWithAi != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onExplainWithAi(threat)
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Explain with Squish AI Assistant", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // SECTION F: ACTION BUTTONS (Reversible Feedback, Trust Sender, Quarantine, Delete)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mark Safe with feedback dialog
                    OutlinedButton(
                        onClick = { showMarkSafeDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark Safe", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Report Scam with feedback dialog
                    OutlinedButton(
                        onClick = { showReportScamDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Report Scam", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Trust Sender Toggle
                if (onToggleTrustSender != null && analysis.sender.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val newTrust = !isSenderTrusted
                            onToggleTrustSender(newTrust)
                            Toast.makeText(
                                context,
                                if (newTrust) "Sender added to your trusted list" else "Sender removed from trusted list",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isSenderTrusted) Color(0xFFF59E0B) else Color(0xFF38BDF8)
                        )
                    ) {
                        Icon(
                            imageVector = if (isSenderTrusted) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSenderTrusted) "Remove from Trusted Senders" else "Trust This Sender in Future",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Quarantine Toggle
                if (onToggleQuarantine != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (threat.isQuarantined) {
                                showRestoreConfirmDialog = true
                            } else {
                                onToggleQuarantine(true)
                                Toast.makeText(context, "Threat quarantined locally", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (threat.isQuarantined) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    ) {
                        Icon(
                            imageVector = if (threat.isQuarantined) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (threat.isQuarantined) "Release from Quarantine" else "Quarantine Threat",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Delete & Close Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text("Done", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
