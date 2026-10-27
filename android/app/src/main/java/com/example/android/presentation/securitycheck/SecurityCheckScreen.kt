package com.example.android.presentation.securitycheck

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCheckScreen(
    rawUrl: String,
    source: String = "INCOMING_URL",
    viewModel: SecurityCheckViewModel,
    onNavigateBack: () -> Unit,
    onExplainWithAi: (targetUrl: String, domain: String, severity: String, reasons: String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showProceedWarningDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(rawUrl, source) {
        viewModel.analyzeUrl(rawUrl, source)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Squish Security Check",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Return safely",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
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
            when (val state = uiState) {
                is SecurityCheckUiState.Idle, is SecurityCheckUiState.Analyzing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Analyzing Threat Indicators...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Running On-Device XGBoost ML & Isolation Forest Anomaly Detection",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is SecurityCheckUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analysis Failed",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            fontSize = 13.sp,
                            color = Color(0xFFCBD5E1),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) {
                            Text("Return to App")
                        }
                    }
                }

                is SecurityCheckUiState.Success -> {
                    val result = state.result
                    val threatId = state.threatId

                    val (verdictLabel, verdictColor, verdictBg, icon) = when (result.classification) {
                        UrlClassification.MALICIOUS_INDICATORS_DETECTED -> Quad(
                            "MALICIOUS INDICATORS DETECTED",
                            Color(0xFFEF4444),
                            Color(0xFFEF4444).copy(alpha = 0.15f),
                            Icons.Default.Dangerous
                        )
                        UrlClassification.SUSPICIOUS -> Quad(
                            "SUSPICIOUS ACTIVITY DETECTED",
                            Color(0xFFF59E0B),
                            Color(0xFFF59E0B).copy(alpha = 0.15f),
                            Icons.Default.Warning
                        )
                        UrlClassification.SAFE_INDICATORS_FOUND -> Quad(
                            "SAFE INDICATORS FOUND",
                            Color(0xFF10B981),
                            Color(0xFF10B981).copy(alpha = 0.15f),
                            Icons.Default.CheckCircle
                        )
                        UrlClassification.UNKNOWN -> Quad(
                            "UNKNOWN THREAT PROFILE",
                            Color(0xFF38BDF8),
                            Color(0xFF38BDF8).copy(alpha = 0.15f),
                            Icons.Default.HelpOutline
                        )
                    }

                    if (showProceedWarningDialog) {
                        ProceedWarningDialog(
                            url = result.targetUrl,
                            classification = result.classification,
                            onDismiss = { showProceedWarningDialog = false },
                            onConfirm = {
                                showProceedWarningDialog = false
                                viewModel.proceedToUrl(context, result.targetUrl)
                            }
                        )
                    }

                    if (showFeedbackDialog) {
                        FalsePositiveDialog(
                            threatId = threatId,
                            url = result.targetUrl,
                            onDismiss = { showFeedbackDialog = false },
                            onSubmit = { category, comment ->
                                viewModel.submitFeedback(threatId, category, comment)
                                showFeedbackDialog = false
                                Toast.makeText(context, "Feedback recorded. Thank you for strengthening Squish defense.", Toast.LENGTH_LONG).show()
                            }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // 1. Prominent Verdict Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, verdictColor.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = verdictBg,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = verdictColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = verdictLabel,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = verdictColor
                                                )
                                            }
                                        }

                                        Text(
                                            text = "Score: ${result.riskScore}/100",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = verdictColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    LinearProgressIndicator(
                                        progress = { (result.riskScore / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = verdictColor,
                                        trackColor = Color(0xFF334155)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Category: ${result.threatCategory.name.replace("_", " ")}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Text(
                                            text = "${(result.confidence * 100).toInt()}% Model Confidence",
                                            fontSize = 11.sp,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Destination Domain Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Destination Domain", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = result.destinationDomain,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = result.targetUrl,
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = Color(0xFF334155).copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Source: ${result.source}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF94A3B8),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.copyUrlToClipboard(context, result.targetUrl)
                                                Toast.makeText(context, "URL copied to clipboard safely", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy URL", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Risk Indicators & Contributing Evidence
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Rule, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Risk Indicators & Evidence", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))

                                    result.riskIndicators.forEach { indicator ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text("•", color = verdictColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = indicator,
                                                fontSize = 12.sp,
                                                color = Color(0xFFE2E8F0),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Step-by-Step Recommended Actions
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Recommended Actions", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))

                                    result.recommendedActions.forEachIndexed { idx, action ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "${idx + 1}.",
                                                color = Color(0xFF34D399),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = action,
                                                fontSize = 12.sp,
                                                color = Color(0xFFCBD5E1),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Model & Execution Metadata
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Pipeline & Engine Metadata", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("AI Model", fontSize = 11.sp, color = Color(0xFF64748B))
                                        Text(result.modelVersion, fontSize = 11.sp, color = Color(0xFFE2E8F0), fontWeight = FontWeight.Medium)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Rule Engine", fontSize = 11.sp, color = Color(0xFF64748B))
                                        Text(result.rulesVersion, fontSize = 11.sp, color = Color(0xFFE2E8F0), fontWeight = FontWeight.Medium)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Execution Mode", fontSize = 11.sp, color = Color(0xFF64748B))
                                        Text("On-Device Edge (<30ms)", fontSize = 11.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // 6. Action Buttons: Explain with AI & Report False Positive
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onExplainWithAi(
                                            result.targetUrl,
                                            result.destinationDomain,
                                            result.classification.name,
                                            result.riskIndicators.joinToString("; ")
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Explain with AI", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = { showFeedbackDialog = true },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                                ) {
                                    Icon(Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Report Feedback", fontSize = 12.sp)
                                }
                            }
                        }

                        // 7. Defensive Exit / Optional Proceed Buttons
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Return Safely", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (result.classification == UrlClassification.MALICIOUS_INDICATORS_DETECTED ||
                                            result.classification == UrlClassification.SUSPICIOUS ||
                                            result.classification == UrlClassification.UNKNOWN
                                        ) {
                                            showProceedWarningDialog = true
                                        } else {
                                            viewModel.proceedToUrl(context, result.targetUrl)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (result.classification == UrlClassification.SAFE_INDICATORS_FOUND) Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                ) {
                                    Text(
                                        text = if (result.classification == UrlClassification.SAFE_INDICATORS_FOUND) "Open Link" else "Proceed (Risky)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun ProceedWarningDialog(
    url: String,
    classification: UrlClassification,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Security Warning", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Squish flagged this destination as ${classification.name.replace("_", " ")}. Opening this link in a browser may compromise your accounts, expose credentials, or trigger social engineering attacks.",
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Destination: $url",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text("Stay Safe")
                    }
                    OutlinedButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Text("Open Anyway")
                    }
                }
            }
        }
    }
}

@Composable
private fun FalsePositiveDialog(
    threatId: String,
    url: String,
    onDismiss: () -> Unit,
    onSubmit: (category: String, comment: String) -> Unit
) {
    var selectedOption by remember { mutableStateOf("FALSE_POSITIVE") }
    var userComment by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Submit Detection Feedback", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Help calibrate on-device detection by confirming or reporting discrepancies.", fontSize = 12.sp, color = Color(0xFF94A3B8))

                Spacer(modifier = Modifier.height(14.dp))

                val options = listOf(
                    "FALSE_POSITIVE" to "This URL is benign (False Positive)",
                    "CONFIRMED_THREAT" to "Correctly detected malicious threat",
                    "MISSED_THREAT" to "Threat severity was underestimated"
                )

                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = key }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == key,
                            onClick = { selectedOption = key },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, fontSize = 12.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = userComment,
                    onValueChange = { userComment = it },
                    label = { Text("Optional Context / Details", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSubmit(selectedOption, userComment) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}
