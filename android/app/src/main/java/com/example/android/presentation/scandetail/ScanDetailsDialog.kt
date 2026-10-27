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
import com.example.android.domain.model.ThreatRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reusable Scan Details dialog displaying the original scanned content,
 * sender/payload info, detected URLs, security verdicts, explainable AI reasons,
 * and quarantine actions.
 */
@Composable
fun ScanDetailsDialog(
    threat: ThreatRecord,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleQuarantine: ((Boolean) -> Unit)? = null,
    onUpdateAction: ((String) -> Unit)? = null,
    onExplainWithAi: ((ThreatRecord) -> Unit)? = null,
    onOpenSecurityCheck: ((url: String, source: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(threat.timestamp) { dateFormat.format(Date(threat.timestamp)) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isPasswordRevealed by remember { mutableStateOf(false) }

    val isHigh = threat.category.contains("HIGH", ignoreCase = true) ||
            threat.category.contains("SMISHING", ignoreCase = true) ||
            threat.category.contains("QUISHING", ignoreCase = true) ||
            threat.riskScore >= 70
    val isMedium = !isHigh && (threat.category.contains("MEDIUM", ignoreCase = true) ||
            threat.category.contains("SPAM", ignoreCase = true) ||
            threat.riskScore >= 40)
    val isLow = !isHigh && !isMedium && (threat.category.contains("LOW", ignoreCase = true) || threat.riskScore >= 20)

    val (categoryTitle, categoryColor, categoryBg) = when {
        isHigh -> Triple("CRITICAL THREAT (HIGH)", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f))
        isMedium -> Triple("SECURITY WARNING (MEDIUM)", Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.15f))
        isLow -> Triple("MINOR CAUTION (LOW)", Color(0xFF38BDF8), Color(0xFF38BDF8).copy(alpha = 0.15f))
        else -> Triple("VERIFIED SAFE", Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.15f))
    }

    val isQr = threat.source.contains("QR", ignoreCase = true)
    val isSms = threat.source.contains("SMS", ignoreCase = true)

    // Parse structured payload if QR
    val parsedQr = remember(threat.rawContent) {
        if (isQr && !threat.rawContent.isNullOrBlank()) {
            QrPayloadParser.parse(threat.rawContent)
        } else {
            null
        }
    }

    val reasonsList = remember(threat.details, threat.riskScore) {
        val parts = threat.details.split(";").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isNotEmpty()) {
            parts
        } else {
            listOf(
                "Risk evaluation score: ${threat.riskScore}/100",
                "Evaluated by on-device real-time detection pipeline"
            )
        }
    }

    val actionText = threat.actionTaken.ifBlank {
        when {
            threat.isQuarantined -> "QUARANTINED"
            isHigh -> "BLOCK_AND_DELETE"
            isMedium -> "WARN_USER"
            else -> "PROCEED_SAFELY"
        }
    }

    val guidanceList = remember(isHigh, isMedium, isQr, isSms) {
        when {
            isQr && isHigh -> listOf(
                "Do NOT open the decoded URL or visit any web destination.",
                "Inspect the physical QR code sticker for signs of malicious tampering.",
                "Never enter banking credentials, passwords, or personal data.",
                "Discard or report the suspicious QR code."
            )
            isSms && isHigh -> listOf(
                "Do NOT reply, tap any links, or call numbers listed in the message.",
                "Bank/service officials will never ask for OTPs or passwords via SMS.",
                "Verify transactions only through the bank's official app or web portal.",
                "Keep this message in Quarantine to prevent accidental interactions."
            )
            isMedium -> listOf(
                "Exercise caution before clicking links or executing requested instructions.",
                "Cross-reference the sender identifier through verified official sources.",
                "Look for urgent social engineering language or suspicious domains."
            )
            else -> listOf(
                "Heuristic and AI models detected no malicious patterns.",
                "Content is safe to view. Standard caution is always recommended."
            )
        }
    }

    // Confirmation dialog for deletion
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete Scan Record?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "This will permanently delete this scan record and its retained content from your local device database.",
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Note: If Cloud Sync was enabled, only anonymized metadata was synchronized with Firestore. Full raw message contents are kept strictly on-device.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete Record", fontSize = 12.sp)
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
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(categoryBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isQr -> Icons.Default.QrCodeScanner
                                    isSms -> Icons.Default.Sms
                                    else -> Icons.Default.Link
                                },
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Scan Details Report",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = formattedTime,
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

                // Scan Metadata & Retention Info Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Retention Mode Badge
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = threat.contentRetentionMode.ifBlank { "LOCAL_SAVED" },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    // Quarantine Badge
                    Surface(
                        color = if (threat.isQuarantined) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (threat.isQuarantined) Icons.Default.Lock else Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (threat.isQuarantined) Color(0xFFEF4444) else Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (threat.isQuarantined) "Quarantined" else "Active",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (threat.isQuarantined) Color(0xFFEF4444) else Color(0xFF10B981)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 1: ORIGINAL SCANNED CONTENT (Plain Text, Safe, Copyable)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Article, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSms) "Full SMS Message Content" else if (isQr) "Decoded QR Payload" else "Original Scanned Content",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            if (!threat.rawContent.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Original Content", threat.rawContent))
                                        Toast.makeText(context, "Copied original content to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Text", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (!threat.rawContent.isNullOrBlank()) {
                            // Rendered as plain text, no HTML, no auto-clickable hyperlinks
                            SelectionContainer {
                                Text(
                                    text = threat.rawContent,
                                    fontSize = 12.sp,
                                    color = Color(0xFFF1F5F9),
                                    lineHeight = 18.sp,
                                    fontFamily = FontFamily.Default
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Length: ${threat.rawContent.length} characters",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "Plain text rendering (links not executed)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        } else {
                            // Missing Content State
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Original scanned content was not retained due to device privacy settings or permission policy. Squish does not generate fabricated text.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFCBD5E1),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 2: QR CODE STRUCTURED PAYLOAD BREAKDOWN (If QR)
                if (parsedQr != null && parsedQr.structuredFields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "QR Format: ${parsedQr.payloadType}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Surface(
                                    color = Color(0xFFA78BFA).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = parsedQr.displayTitle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFA78BFA),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            parsedQr.structuredFields.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(key, fontSize = 11.sp, color = Color(0xFF94A3B8))

                                    if (key.equals("Password", ignoreCase = true)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isPasswordRevealed) value else "••••••••",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            IconButton(
                                                onClick = { isPasswordRevealed = !isPasswordRevealed },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPasswordRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle password",
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = value,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            if (parsedQr.payloadType == "WIFI") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Squish will not automatically connect to Wi-Fi networks.",
                                    fontSize = 10.sp,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }
                }

                // SECTION 3: SENDER / TARGET ORIGIN
                Spacer(modifier = Modifier.height(12.dp))
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
                            Text(
                                text = if (isSms) "Sender / Originating Address" else "Target / Payload Label",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = threat.sender.ifBlank { "Unknown Origin" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Sender", threat.sender))
                                Toast.makeText(context, "Copied identifier to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // SECTION 4: EXTRACTED URL (If present)
                val targetUrl = threat.extractedUrl ?: parsedQr?.extractedUrl
                if (!targetUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Detected Destination URL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Detected URL", targetUrl))
                                        Toast.makeText(context, "Copied URL to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = targetUrl,
                                fontSize = 12.sp,
                                color = Color(0xFF38BDF8),
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Auto-navigation is disabled to prevent drive-by attacks.",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )

                            if (onOpenSecurityCheck != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenSecurityCheck(targetUrl, threat.source)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Inspect in Security Check", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // SECTION 5: SECURITY VERDICT & RISK SCORE
                Spacer(modifier = Modifier.height(12.dp))
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
                                Text("Risk Evaluation Score", fontSize = 11.sp, color = Color(0xFF94A3B8))
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

                // SECTION 6: WHY SQUISH FLAGGED THIS CONTENT (XAI REASONS)
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Why Squish Flagged This Content", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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

                // SECTION 7: RECOMMENDED ACTION STEPS
                Spacer(modifier = Modifier.height(12.dp))
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
                            Text("Recommended Next Steps", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = actionText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        guidanceList.forEachIndexed { idx, guide ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("${idx + 1}.", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = guide,
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // SECTION 8: MODEL PIPELINE & ON-DEVICE ENGINE
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Model Pipeline & Engine", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Channel Source", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(threat.source, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Model Version", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(threat.modelVersion ?: "v2.1-edge", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Execution Mode", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("100% On-Device Edge (<30ms)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF34D399))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // EXPLAIN WITH SQUISH AI BUTTON
                if (onExplainWithAi != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onExplainWithAi(threat)
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Explain with Squish AI", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // QUARANTINE TOGGLE BUTTON
                if (onToggleQuarantine != null) {
                    OutlinedButton(
                        onClick = {
                            val nextState = !threat.isQuarantined
                            onToggleQuarantine(nextState)
                            Toast.makeText(
                                context,
                                if (nextState) "Threat placed in Quarantine" else "Released from Quarantine",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (threat.isQuarantined) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    ) {
                        Icon(
                            imageVector = if (threat.isQuarantined) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (threat.isQuarantined) "Release from Quarantine" else "Quarantine Threat",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // USER ACTIONS: BLOCK / MARK SAFE
                if (onUpdateAction != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onUpdateAction("BLOCKED")
                                Toast.makeText(context, "Marked as Blocked", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Block", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onUpdateAction("MARK_SAFE")
                                Toast.makeText(context, "Marked as Safe", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mark Safe", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // BOTTOM CONTROLS: DELETE & CLOSE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text("Close", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
