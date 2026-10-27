package com.example.android.presentation.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.android.domain.classifier.SmishingClassifier
import com.example.android.domain.classifier.UrlClassifier
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.User
import com.example.android.domain.rules.RuleEngine
import com.example.android.domain.usecase.ProcessQrCodeUseCase
import com.example.android.domain.xai.ExplainableAiEngine
import com.example.android.presentation.ai.AssistantContext
import com.example.android.presentation.ai.AssistantContextType
import com.example.android.presentation.ai.SquishAiAssistantSheet
import com.example.android.presentation.ai.SquishAiAssistantViewModel
import com.example.android.presentation.ai.SquishAiFab
import com.example.android.presentation.alerts.AlertsTab
import com.example.android.presentation.history.HistoryTab
import com.example.android.presentation.scandetail.ScanDetailsDialog
import com.example.android.presentation.scanner.ScannerTab
import com.example.android.presentation.settings.SettingsTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    currentUser: User?,
    uiState: DashboardUiState = DashboardUiState(),
    onDeleteThreat: (String) -> Unit = {},
    onUpdateThreatAction: (String, String) -> Unit = { _, _ -> },
    onToggleQuarantine: (String, Boolean) -> Unit = { _, _ -> },
    onSaveThreat: (ThreatRecord) -> Unit = {},
    onClearAllThreats: () -> Unit = {},
    onSubmitFeedback: (String, String, String) -> Unit = { _, _, _ -> },
    onTriggerManualSync: () -> Unit = {},
    onOpenEducation: () -> Unit = {},
    onNavigateToSmsMonitoring: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {},
    onLogoutClicked: () -> Unit,
    onOpenSecurityCheck: (url: String, source: String) -> Unit = { _, _ -> },
    smishingClassifier: SmishingClassifier,
    urlClassifier: UrlClassifier,
    ruleEngine: RuleEngine,
    explainableAiEngine: ExplainableAiEngine,
    processQrCodeUseCase: ProcessQrCodeUseCase,
    aiViewModel: SquishAiAssistantViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }
    var selectedThreatForDialog by remember { mutableStateOf<ThreatRecord?>(null) }
    var isAiAssistantOpen by remember { mutableStateOf(false) }

    val unreviewedThreatsCount = remember(uiState.threatHistory) {
        uiState.threatHistory.count { it.actionTaken != "RESOLVED" && it.actionTaken != "FALSE_POSITIVE" }
    }

    if (selectedThreatForDialog != null) {
        ScanDetailsDialog(
            threat = selectedThreatForDialog!!,
            onDismiss = { selectedThreatForDialog = null },
            onDelete = {
                onDeleteThreat(selectedThreatForDialog!!.id)
                selectedThreatForDialog = null
            },
            onToggleQuarantine = { isQuarantined ->
                onToggleQuarantine(selectedThreatForDialog!!.id, isQuarantined)
                selectedThreatForDialog = selectedThreatForDialog!!.copy(isQuarantined = isQuarantined)
            },
            onUpdateAction = { action ->
                onUpdateThreatAction(selectedThreatForDialog!!.id, action)
                selectedThreatForDialog = selectedThreatForDialog!!.copy(actionTaken = action)
            },
            onExplainWithAi = { threat ->
                aiViewModel.setContext(
                    AssistantContext(
                        title = threat.sender,
                        severity = threat.category,
                        type = AssistantContextType.ALERT_EXPLANATION,
                        details = "Threat: ${threat.details}; Score: ${threat.riskScore}; Source: ${threat.source}; Content: ${threat.rawContent ?: "N/A"}"
                    )
                )
                isAiAssistantOpen = true
            },
            onOpenSecurityCheck = onOpenSecurityCheck
        )
    }

    if (isAiAssistantOpen) {
        SquishAiAssistantSheet(
            viewModel = aiViewModel,
            onDismiss = { isAiAssistantOpen = false },
            onNavigateToScanner = {
                selectedTab = 2
                isAiAssistantOpen = false
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color(0xFF94A3B8)
            ) {
                val navItems = listOf(
                    Triple("Home", Icons.Default.Shield, 0),
                    Triple("History", Icons.Default.History, 1),
                    Triple("Scanner", Icons.Default.QrCodeScanner, 2),
                    Triple("Alerts", Icons.Default.Notifications, 3),
                    Triple("Settings", Icons.Default.Settings, 4)
                )

                navItems.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (index == 3 && unreviewedThreatsCount > 0) {
                                        Badge(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (unreviewedThreatsCount > 9) "9+" else unreviewedThreatsCount.toString(),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF38BDF8),
                            selectedTextColor = Color(0xFF38BDF8),
                            indicatorColor = Color(0xFF0EA5E9).copy(alpha = 0.2f),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            // Feature 19: Persistent Floating AI Assistant Button across Home, History, Scanner, and Alerts
            if (selectedTab != 4) {
                SquishAiFab(
                    hasUnresolvedAlerts = unreviewedThreatsCount > 0,
                    onClick = { isAiAssistantOpen = true }
                )
            }
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
            when (selectedTab) {
                0 -> HomeTab(
                    currentUser = currentUser,
                    uiState = uiState,
                    onNavigateToTab = { selectedTab = it },
                    onOpenEducation = onOpenEducation,
                    onThreatClicked = { selectedThreatForDialog = it },
                    onTriggerManualSync = onTriggerManualSync
                )
                1 -> HistoryTab(
                    threats = uiState.threatHistory,
                    onDeleteThreat = onDeleteThreat,
                    onThreatClicked = { selectedThreatForDialog = it },
                    onNavigateToScanner = { selectedTab = 2 }
                )
                2 -> ScannerTab(
                    smishingClassifier = smishingClassifier,
                    urlClassifier = urlClassifier,
                    ruleEngine = ruleEngine,
                    explainableAiEngine = explainableAiEngine,
                    processQrCodeUseCase = processQrCodeUseCase,
                    onSaveThreatToHistory = onSaveThreat,
                    onOpenLiveCameraQr = onNavigateToQrScanner,
                    onAskAiAssistant = { prompt ->
                        aiViewModel.sendMessage(prompt)
                        isAiAssistantOpen = true
                    },
                    onOpenSecurityCheck = onOpenSecurityCheck
                )
                3 -> AlertsTab(
                    threats = uiState.threatHistory,
                    onThreatClicked = { selectedThreatForDialog = it },
                    onUpdateThreatAction = onUpdateThreatAction,
                    onSubmitFeedback = onSubmitFeedback,
                    onExplainWithAi = { threat ->
                        aiViewModel.setContext(
                            AssistantContext(
                                title = threat.sender,
                                severity = threat.category,
                                type = AssistantContextType.ALERT_EXPLANATION,
                                details = "Threat: ${threat.details}; Score: ${threat.riskScore}; Source: ${threat.source}"
                            )
                        )
                        isAiAssistantOpen = true
                    },
                    onOpenEducation = onOpenEducation
                )
                4 -> SettingsTab(
                    currentUser = currentUser,
                    threats = uiState.threatHistory,
                    onClearAllThreats = onClearAllThreats,
                    onOpenEducation = onOpenEducation,
                    onLogoutClicked = onLogoutClicked
                )
            }
        }
    }
}

@Composable
fun ThreatDetailDialog(
    threat: ThreatRecord,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onUpdateAction: (String) -> Unit = {},
    onExplainWithAi: (ThreatRecord) -> Unit = {}
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(threat.timestamp) { dateFormat.format(Date(threat.timestamp)) }

    val isHigh = threat.category.contains("HIGH", ignoreCase = true) ||
            threat.category.contains("SMISHING", ignoreCase = true) ||
            threat.category.contains("QUISHING", ignoreCase = true) ||
            threat.riskScore >= 75
    val isMedium = !isHigh && (threat.category.contains("MEDIUM", ignoreCase = true) ||
            threat.category.contains("SPAM", ignoreCase = true) ||
            threat.riskScore >= 50)
    val isLow = !isHigh && !isMedium && (threat.category.contains("LOW", ignoreCase = true) || threat.riskScore >= 25)

    val (categoryTitle, categoryColor, categoryBg) = when {
        isHigh -> Triple("CRITICAL THREAT (HIGH)", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f))
        isMedium -> Triple("SECURITY WARNING (MEDIUM)", Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.15f))
        isLow -> Triple("MINOR CAUTION (LOW)", Color(0xFF38BDF8), Color(0xFF38BDF8).copy(alpha = 0.15f))
        else -> Triple("VERIFIED SAFE", Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.15f))
    }

    val actionText = threat.actionTaken.ifBlank {
        when {
            isHigh -> "BLOCK_AND_DELETE"
            isMedium -> "WARN_USER"
            else -> "PROCEED_SAFELY"
        }
    }

    val guidanceText = when {
        isHigh -> "Critical threat detected. On-device defense quarantined the payload to prevent credential theft or exploitation. Do not interact."
        isMedium -> "Suspicious indicators observed. Verify sender credentials through official verified channels before opening links."
        isLow -> "Minor heuristic anomaly noted. Standard caution advised."
        else -> "All neural NLP and heuristic checks passed. Safe to interact."
    }

    val reasonsList = remember(threat.details, threat.riskScore) {
        val parts = threat.details.split(";").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isNotEmpty()) {
            parts
        } else {
            listOf(
                "Risk evaluation score: ${threat.riskScore}/100",
                "Flagged by on-device real-time detection pipeline"
            )
        }
    }

    val isQr = threat.source.contains("QR", ignoreCase = true)

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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(categoryBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isQr) Icons.Default.QrCodeScanner else Icons.Default.Security,
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Threat AI Analysis",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "On-Device Inference Report",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. RISK SCORE & CATEGORY
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
                                Text("Risk Score", fontSize = 11.sp, color = Color(0xFF94A3B8))
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

                Spacer(modifier = Modifier.height(12.dp))

                // 2. SENDER / TARGET ORIGIN
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
                            Text("Sender / Target Identifier", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = threat.sender,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Sender", threat.sender))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. RECOMMENDED ACTION & DEFENSIVE POLICY
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
                            Text("Recommended Action", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = actionText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = guidanceText,
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. REASONS & EXPLAINABLE EVIDENCE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Reasons & Evidence", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(6.dp))
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

                Spacer(modifier = Modifier.height(12.dp))

                // 5. MODEL RESULTS & PIPELINE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Model Results & Pipeline", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Channel Vector", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(threat.source, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Inference Engine", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(
                                if (isQr) "XGBoost + Isolation Forest" else "TinyBERT Smishing NLP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Execution Mode", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("On-Device Edge (<30ms)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF34D399))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // EXPLAIN WITH SQUISH AI BUTTON
                Button(
                    onClick = {
                        onDismiss()
                        onExplainWithAi(threat)
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Explain with Squish AI", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Defensive User Action Buttons
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

                // Bottom Secondary Buttons (Delete & Close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
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
