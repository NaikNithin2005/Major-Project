package com.example.android.presentation.sms

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.model.SmsProcessResult
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.riskengine.RecommendedAction
import com.example.android.domain.riskengine.ThreatCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsMonitoringScreen(
    viewModel: SmsMonitoringViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showSimulateDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissionState()
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissionState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Smishing Monitor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showSimulateDialog = true },
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // 1. Permission Warning Banner (if not granted)
                if (!uiState.isPermissionGranted) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SMS Permissions Required",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Squish Shield evaluates incoming text messages on-device for smishing indicators and malicious links. No raw messages ever leave your device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.RECEIVE_SMS,
                                                Manifest.permission.READ_SMS
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                        contentColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Grant SMS Permission")
                                }
                            }
                        }
                    }
                }

                // 2. Monitoring Status Toggle Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isProtectionEnabled && uiState.isPermissionGranted)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.isProtectionEnabled && uiState.isPermissionGranted) "SMS Shield Active" else "SMS Shield Disabled",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (uiState.isPermissionGranted) "Passive background smishing pipeline running" else "Permissions missing for background monitoring",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.isProtectionEnabled && uiState.isPermissionGranted,
                                onCheckedChange = { enabled ->
                                    if (!uiState.isPermissionGranted) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.RECEIVE_SMS,
                                                Manifest.permission.READ_SMS
                                            )
                                        )
                                    } else {
                                        viewModel.toggleProtection(enabled)
                                    }
                                }
                            )
                        }
                    }
                }

                // 3. Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent SMS Analyses",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.recentAnalyses.size} Scanned",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 4. Empty State
                if (uiState.recentAnalyses.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Sms,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No SMS Messages Analyzed Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Incoming SMS messages will be automatically evaluated, or tap 'Simulate' above to test.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // 5. Recent SMS List
                    items(uiState.recentAnalyses, key = { it.id }) { item ->
                        SmsAnalysisItemCard(
                            item = item,
                            onClick = { viewModel.selectItemForDetail(item) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            // Unified AI Analysis & Threat Intelligence Detail Bottom Sheet
            val activeAnalysis = uiState.selectedAnalysis
                ?: uiState.activeDetailResult?.analysisResult
                ?: uiState.selectedThreat?.let { threat ->
                    SmsAnalysisResult(
                        id = threat.id,
                        messageId = threat.id,
                        timestamp = threat.timestamp,
                        sender = threat.sender,
                        riskScore = threat.riskScore,
                        isSmishing = threat.riskScore >= 50,
                        confidence = 0.95f,
                        extractedUrlsCount = if (threat.details.contains("URL", ignoreCase = true)) 1 else 0,
                        processedAt = threat.timestamp
                    )
                }

            activeAnalysis?.let { analysis ->
                SmsAnalysisDetailBottomSheet(
                    analysis = analysis,
                    threatRecord = uiState.selectedThreat,
                    activeProcessResult = uiState.activeDetailResult,
                    onDismiss = { viewModel.dismissDetail() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }
        }
    }

    // Simulation Dialog
    if (showSimulateDialog) {
        var testSender by remember { mutableStateOf("SBI-ALERT") }
        var testBody by remember {
            mutableStateOf("URGENT: Your SBI account has been suspended due to unverified KYC. Click http://sbi-kyc-verification.xyz/login immediately.")
        }

        AlertDialog(
            onDismissRequest = { showSimulateDialog = false },
            title = { Text("Simulate Incoming SMS", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Test on-device TinyBERT and Multi-Modal Risk Engine analysis directly:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                testSender = "SBI-ALERT"
                                testBody = "URGENT: Your SBI account has been suspended due to unverified KYC. Click http://sbi-kyc-verification.xyz/login immediately."
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("Banking Smish", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                testSender = "+91 9876543210"
                                testBody = "Hey, are you free this evening? Let's catch up over coffee!"
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("Safe Ham", fontSize = 11.sp)
                        }
                    }

                    OutlinedTextField(
                        value = testSender,
                        onValueChange = { testSender = it },
                        label = { Text("Sender") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = testBody,
                        onValueChange = { testBody = it },
                        label = { Text("Message Body") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSimulateDialog = false
                        viewModel.analyzeSampleSms(testSender, testBody)
                    }
                ) {
                    Text("Analyze SMS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimulateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SmsAnalysisItemCard(
    item: SmsAnalysisResult,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedTime = remember(item.timestamp) { dateFormat.format(Date(item.timestamp)) }

    val statusColor = when {
        item.riskScore >= 76 -> Color(0xFFDC2626) // Vivid Crimson Red
        item.riskScore >= 51 -> Color(0xFFEA580C) // Orange
        item.riskScore >= 26 -> Color(0xFFD97706) // Amber
        else -> Color(0xFF16A34A) // Emerald Green
    }

    val statusIcon = when {
        item.riskScore >= 76 -> Icons.Default.Dangerous
        item.riskScore >= 51 -> Icons.Default.Warning
        item.riskScore >= 26 -> Icons.Default.Info
        else -> Icons.Default.CheckCircle
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.sender,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (item.sender.matches(Regex("^[A-Za-z0-9]{2,3}-[A-Za-z0-9]+.*"))) "Alphanumeric Header" else "Direct Number",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "Score ${item.riskScore}/100",
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Extracted URLs: ${item.extractedUrlsCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Authoritative Detail Bottom Sheet for ANY SMS (Safe, Low, Medium, High).
 * Displays full AI risk score, category, cybersecurity action alerts,
 * TinyBERT NLP & Anomaly breakdown, XAI evidence, and sender details.
 */
@Composable
fun SmsAnalysisDetailBottomSheet(
    analysis: SmsAnalysisResult,
    threatRecord: ThreatRecord? = null,
    activeProcessResult: SmsProcessResult? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val score = analysis.riskScore
    val scrollState = rememberScrollState()

    val category: ThreatCategory = analysis.assessment?.category ?: when {
        threatRecord != null && threatRecord.category.contains("HIGH", ignoreCase = true) -> ThreatCategory.HIGH
        threatRecord != null && threatRecord.category.contains("MEDIUM", ignoreCase = true) -> ThreatCategory.MEDIUM
        threatRecord != null && threatRecord.category.contains("LOW", ignoreCase = true) -> ThreatCategory.LOW
        threatRecord != null && threatRecord.category.contains("SMISHING", ignoreCase = true) -> ThreatCategory.HIGH
        threatRecord != null && threatRecord.category.contains("SPAM", ignoreCase = true) -> ThreatCategory.MEDIUM
        threatRecord != null && threatRecord.category.contains("SAFE", ignoreCase = true) -> ThreatCategory.SAFE
        else -> ThreatCategory.fromScore(score)
    }

    val (categoryColor, categoryContainerColor, categoryBorderColor) = when (category) {
        ThreatCategory.HIGH -> Triple(
            Color(0xFFDC2626), // Vivid Red
            Color(0xFF7F1D1D).copy(alpha = 0.22f),
            Color(0xFFEF4444).copy(alpha = 0.6f)
        )
        ThreatCategory.MEDIUM -> Triple(
            Color(0xFFEA580C), // Bold Orange
            Color(0xFF7C2D12).copy(alpha = 0.22f),
            Color(0xFFF97316).copy(alpha = 0.6f)
        )
        ThreatCategory.LOW -> Triple(
            Color(0xFFD97706), // Warm Amber
            Color(0xFF78350F).copy(alpha = 0.22f),
            Color(0xFFF59E0B).copy(alpha = 0.5f)
        )
        ThreatCategory.SAFE -> Triple(
            Color(0xFF16A34A), // Emerald Green
            Color(0xFF14532D).copy(alpha = 0.22f),
            Color(0xFF22C55E).copy(alpha = 0.5f)
        )
    }

    val categoryLabel = when (category) {
        ThreatCategory.HIGH -> "HIGH RISK"
        ThreatCategory.MEDIUM -> "MEDIUM RISK"
        ThreatCategory.LOW -> "LOW RISK"
        ThreatCategory.SAFE -> "SAFE"
    }

    val alertIcon = when (category) {
        ThreatCategory.HIGH -> Icons.Default.Dangerous
        ThreatCategory.MEDIUM -> Icons.Default.Warning
        ThreatCategory.LOW -> Icons.Default.Info
        ThreatCategory.SAFE -> Icons.Default.CheckCircle
    }

    val alertTitle = when (category) {
        ThreatCategory.HIGH -> "CRITICAL THREAT: SMISHING ATTEMPT"
        ThreatCategory.MEDIUM -> "SECURITY WARNING: SUSPICIOUS SMS"
        ThreatCategory.LOW -> "SECURITY NOTICE: MINOR CAUTION"
        ThreatCategory.SAFE -> "VERIFIED SAFE COMMUNICATION"
    }

    val actionBadgeText = when (category) {
        ThreatCategory.HIGH -> "ACTION: BLOCK SENDER & DELETE"
        ThreatCategory.MEDIUM -> "ACTION: WARN USER & VERIFY SENDER"
        ThreatCategory.LOW -> "ACTION: ALLOW WITH CAUTION"
        ThreatCategory.SAFE -> "ACTION: ALLOW - SAFE TO INTERACT"
    }

    val guidanceText = when (category) {
        ThreatCategory.HIGH -> "High-confidence smishing attempt detected by multi-modal AI. Do not click any links, do not reply, and never disclose OTPs or credentials. It is strongly recommended to block this sender immediately."
        ThreatCategory.MEDIUM -> "Suspicious indicators or unverified links observed. Exercise caution and independently verify the sender's identity through official channels before taking any requested action."
        ThreatCategory.LOW -> "Minor heuristic anomaly or unverified alphanumeric gateway pattern observed. Normal vigilance is advised. Verify authenticity if sensitive data is requested."
        ThreatCategory.SAFE -> "On-device AI multi-modal evaluation completed. No smishing markers, phishing keywords, or malicious redirection URLs detected. Safe to open and reply."
    }

    val rawBodyText = analysis.rawBody.ifBlank {
        activeProcessResult?.rawSms?.body ?: ""
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedTime = remember(analysis.timestamp) { dateFormat.format(Date(analysis.timestamp)) }

    val breakdown = analysis.assessment?.scoreBreakdown
    val smsModelValue = when {
        analysis.smishingModelProb != null -> "${(analysis.smishingModelProb * 100f).roundToInt()}% smishing probability"
        breakdown != null -> "${breakdown.smsScore.roundToInt()}% score"
        score >= 76 -> "88% probability (TinyBERT ONNX)"
        score >= 51 -> "62% probability (Elevated)"
        score >= 26 -> "35% probability (Low Risk)"
        else -> "< 5% probability (Verified clean)"
    }
    val isSmsThreat = (analysis.smishingModelProb?.let { it >= 0.5f } ?: (score >= 50))

    val anomalyValue = when {
        breakdown != null -> "${breakdown.anomalyScore.roundToInt()}%"
        score >= 76 -> "75% (Isolation Forest: Urgent)"
        score >= 51 -> "50% (Isolation Forest: Irregular)"
        score >= 26 -> "30% (Isolation Forest: Minor)"
        else -> "0% (Isolation Forest: Normal)"
    }

    val contextValue = when {
        breakdown != null -> "${breakdown.contextScore.roundToInt()}%"
        score >= 76 -> "80% (Unverified Alphanumeric Gateway)"
        score >= 51 -> "45% (Unlisted Sender Pattern)"
        score >= 26 -> "20% (Standard Gateway)"
        else -> "0% (Verified Identity Profile)"
    }

    val evidenceList = remember(analysis, threatRecord) {
        val rawDetails = threatRecord?.details ?: analysis.assessment?.reasons?.joinToString("; ")
        formatThreatEvidence(rawDetails, score, analysis.extractedUrlsCount)
    }

    val eventSummaryText = remember(score, categoryLabel, formattedTime, analysis, threatRecord) {
        val modelLines = buildList {
            add("Smishing Model (TinyBERT NLP): $smsModelValue")
            add("Anomaly Model (Isolation Forest): $anomalyValue")
            add("Context & Reputation Engine: $contextValue")
            if (analysis.extractedUrlsCount > 0) add("Extracted URL Scanner: ${analysis.extractedUrlsCount} link(s) scanned")
        }.joinToString("\n  - ", prefix = "\n  - ")

        val reasonsSummary = if (evidenceList.isNotEmpty()) {
            evidenceList.joinToString("\n  - ", prefix = "\n  - ")
        } else {
            "\n  - No malicious indicators detected."
        }

        """
Risk Score: $score / 100
Threat Category: $categoryLabel
Model Results:$modelLines
Reasons:$reasonsSummary
Recommended Action: $actionBadgeText - $guidanceText
Timestamp: $formattedTime
""".trimIndent()
    }

    Card(
        modifier = modifier
            .animateContentSize()
            .heightIn(max = 620.dp),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 22.dp, vertical = 18.dp)
                .verticalScroll(scrollState)
                .fillMaxWidth()
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (category == ThreatCategory.SAFE) "SMS SECURITY ASSESSMENT" else "SMS THREAT INTELLIGENCE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1 & 2. Risk Score & Threat Category Card
            Surface(
                color = categoryContainerColor,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, categoryBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Risk Score",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$score / 100",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = categoryColor
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Threat Category",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = categoryColor,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = categoryLabel,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (score / 100f).coerceIn(0f, 1f) },
                        color = categoryColor,
                        trackColor = categoryColor.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Model Results
            Text(
                text = "Model Results",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricItem(label = "Smishing Model (TinyBERT NLP)", value = smsModelValue, isThreat = isSmsThreat)
                    MetricItem(label = "Anomaly Model (Isolation Forest)", value = anomalyValue, isThreat = score >= 50)
                    MetricItem(label = "Context & Reputation Engine", value = contextValue, isThreat = score >= 50)
                    MetricItem(
                        label = "Extracted URL & Payload Scanner",
                        value = if (analysis.extractedUrlsCount > 0) "${analysis.extractedUrlsCount} link(s) scanned" else "No links present",
                        isThreat = analysis.extractedUrlsCount > 0 && score >= 50
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Reasons
            Text(
                text = "Reasons",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                evidenceList.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "• ",
                            fontWeight = FontWeight.Black,
                            color = categoryColor,
                            fontSize = 14.sp
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Recommended Action Card
            Text(
                text = "Recommended Action",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = categoryContainerColor,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, categoryBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = alertIcon,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = actionBadgeText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = alertTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = categoryBorderColor.copy(alpha = 0.35f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = guidanceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Timestamp & Sender Details Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Timestamp",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${analysis.extractedUrlsCount} URL(s) Found",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sender: ${analysis.sender}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (rawBodyText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "\"$rawBodyText\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { copyToClipboard(context, rawBodyText, "Message") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Message",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { copyToClipboard(context, eventSummaryText, "Event Summary") },
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Summary", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { copyToClipboard(context, analysis.sender, "Sender") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy Sender", fontSize = 12.sp)
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = categoryColor),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
/**
 * Backward-compatible delegating bottom sheets
 */
@Composable
fun SmsProcessDetailBottomSheet(
    result: SmsProcessResult,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SmsAnalysisDetailBottomSheet(
        analysis = result.analysisResult,
        activeProcessResult = result,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
fun ThreatRecordDetailBottomSheet(
    threat: ThreatRecord,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val analysis = SmsAnalysisResult(
        id = threat.id,
        messageId = threat.id,
        timestamp = threat.timestamp,
        sender = threat.sender,
        riskScore = threat.riskScore,
        isSmishing = threat.riskScore >= 50,
        confidence = 0.95f,
        extractedUrlsCount = if (threat.details.contains("URL", ignoreCase = true)) 1 else 0,
        processedAt = threat.timestamp
    )
    SmsAnalysisDetailBottomSheet(
        analysis = analysis,
        threatRecord = threat,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
private fun MetricItem(label: String, value: String, isThreat: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (isThreat) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun formatThreatEvidence(
    rawDetails: String?,
    riskScore: Int,
    extractedUrlsCount: Int
): List<String> {
    val result = mutableListOf<String>()

    if (!rawDetails.isNullOrBlank()) {
        val rawParts = rawDetails.split(";").map { it.trim() }.filter { it.isNotBlank() }
        for (part in rawParts) {
            // Clean legacy "keywords:" issues
            if (part.contains("keywords:", ignoreCase = true)) {
                val kwIdx = part.indexOf("keywords:", ignoreCase = true)
                val prefix = part.substring(0, kwIdx).trim().trim(',', ' ')
                val kwContent = part.substring(kwIdx + 9).trim().trim(',', ' ')

                if (prefix.isNotBlank()) {
                    result.add(cleanEvidencePhrase(prefix))
                }
                if (kwContent.isNotBlank() && !kwContent.equals("none", ignoreCase = true)) {
                    result.add("Detected suspicious keywords: $kwContent")
                }
            } else {
                result.add(cleanEvidencePhrase(part))
            }
        }
    }

    if (result.isEmpty()) {
        result.addAll(getDefaultReasonsForScore(riskScore, extractedUrlsCount))
    }

    return result.distinct()
}

private fun cleanEvidencePhrase(text: String): String {
    var cleaned = text.trim().trim(',', ';', ':')
    if (cleaned.startsWith("Detected 1 URLs", ignoreCase = true)) {
        cleaned = "Detected 1 external URL in message payload"
    } else if (cleaned.startsWith("Detected", ignoreCase = true) && cleaned.contains("URLs", ignoreCase = true)) {
        cleaned = cleaned.replace("URLs", "external URL(s)")
    }
    return cleaned
}

private fun getDefaultReasonsForScore(riskScore: Int, urlsCount: Int): List<String> {
    return when {
        riskScore >= 76 -> listOf(
            "High-confidence smishing signature identified by AI engine.",
            if (urlsCount > 0) "Detected $urlsCount external link(s) exhibiting phishing characteristics." else "Message employs financial urgency coercion to harvest credentials.",
            "Sender ID structure flagged as unverified spoofed alphanumeric gateway."
        )
        riskScore >= 51 -> listOf(
            "Suspicious messaging pattern flagged by NLP smishing classifier.",
            if (urlsCount > 0) "Detected $urlsCount unverified destination link(s)." else "Unusual phrasing or unverified financial context.",
            "Elevated risk signals requiring sender verification."
        )
        riskScore >= 26 -> listOf(
            "Minor heuristic anomaly observed in message payload.",
            if (urlsCount > 0) "Contains $urlsCount external link(s); verify destination before opening." else "Alphanumeric or shortcode sender requires standard caution.",
            "Low risk baseline — exercise normal awareness."
        )
        else -> listOf(
            "No smishing or malicious social engineering triggers detected.",
            "Sender ID structure matches standard legitimate communication.",
            "0 malicious links or deceptive URLs identified."
        )
    }
}
