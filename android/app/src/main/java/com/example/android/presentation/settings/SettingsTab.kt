package com.example.android.presentation.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsTab(
    currentUser: User?,
    threats: List<ThreatRecord>,
    onClearAllThreats: () -> Unit,
    onOpenEducation: () -> Unit,
    onLogoutClicked: () -> Unit
) {
    val context = LocalContext.current

    // Preference States (persisted via UI toggles)
    var isSmsMonitoringEnabled by remember { mutableStateOf(true) }
    var isQrProtectionEnabled by remember { mutableStateOf(true) }
    var isUrlAnalysisEnabled by remember { mutableStateOf(true) }
    var isCloudSyncEnabled by remember { mutableStateOf(true) }
    var isLocalOnlyMode by remember { mutableStateOf(false) }

    var notifyHighSeverity by remember { mutableStateOf(true) }
    var notifyMediumSeverity by remember { mutableStateOf(true) }
    var notifyDailyTips by remember { mutableStateOf(true) }
    var showNotificationPreviews by remember { mutableStateOf(false) }

    var optInAiLearning by remember { mutableStateOf(false) }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showModelMetadataDialog by remember { mutableStateOf(false) }

    val hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Local Threat History?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "This will delete all stored threat records from local device storage. Cloud synchronizations and audit logs on the server remain unaffected.",
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllThreats()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "Local history cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Clear Local History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text("Squish Privacy & Data Protection", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "• On-Device Processing: Machine learning inference (TinyBERT & XGBoost) executes locally on your device.\n\n" +
                        "• Data Minimization: Raw SMS message bodies and phone numbers are never stored in plain text on cloud servers.\n\n" +
                        "• Synchronization: Only anonymized detection hashes, risk scores, and threat indicators are transmitted.\n\n" +
                        "• Consent: Telemetry and model training feedback are strictly opt-in and can be withdrawn anytime.",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicyDialog = false }) {
                    Text("Close", color = Color(0xFF38BDF8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    if (showModelMetadataDialog) {
        AlertDialog(
            onDismissRequest = { showModelMetadataDialog = false },
            title = { Text("AI Model & Engine Metadata", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Smishing Model: TinyBERT (tinybert-smishing-v2)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("  Architecture: 4-Layer Transformer, 312 Hidden Size, ONNX Runtime 1.22.0", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text("• URL Model: XGBoost Phishing Classifier (xgboost-url-v1)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("  Features: 18 lexical & structural domain indicators, ONNX format", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text("• Anomaly Detector: Isolation Forest (isolation_forest-v1)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("• Execution Mode: 100% On-Device (Zero Cloud Latency)", fontSize = 12.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                    Text("• Page-Size Compatibility: 16 KB Page Aligned (Android 15+ Ready)", fontSize = 12.sp, color = Color(0xFF38BDF8))
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelMetadataDialog = false }) {
                    Text("Done", color = Color(0xFF38BDF8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Settings & Privacy", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Manage protection modules, notifications, AI preferences, and account", fontSize = 12.sp, color = Color(0xFF94A3B8))
        }

        // Section G: Account Management Card
        item {
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
                                    .background(Color(0xFF0EA5E9).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (currentUser?.isGuest == true) "Guest User" else currentUser?.displayName ?: "User",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (currentUser?.isGuest == true) "Local Session" else currentUser?.email ?: "Authenticated",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Button(
                            onClick = onLogoutClicked,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Sign Out", fontSize = 11.sp, color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }

        // Section A: Protection Preferences
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Protection Preferences", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Real-Time SMS Smishing Shield",
                        subtitle = "Evaluates incoming text messages upon receipt",
                        checked = isSmsMonitoringEnabled,
                        onCheckedChange = { isSmsMonitoringEnabled = it }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "QR Code Quishing Guard",
                        subtitle = "Validates destination URLs before opening",
                        checked = isQrProtectionEnabled,
                        onCheckedChange = { isQrProtectionEnabled = it }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "Phishing URL Heuristic Analysis",
                        subtitle = "Checks domain homoglyphs, IP hosts, and subdomains",
                        checked = isUrlAnalysisEnabled,
                        onCheckedChange = { isUrlAnalysisEnabled = it }
                    )
                }
            }
        }

        // Section C: Communication Channels & Permissions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Communication Channels & Permissions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))

                    ChannelStatusRow(
                        channel = "SMS Monitoring",
                        isGranted = hasSmsPermission,
                        explanation = "Required to inspect incoming smishing messages.",
                        onFix = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    ChannelStatusRow(
                        channel = "Camera & QR Code",
                        isGranted = hasCameraPermission,
                        explanation = "Required for live optical QR scanning.",
                        onFix = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    ChannelStatusRow(
                        channel = "Security Notifications",
                        isGranted = hasNotificationPermission,
                        explanation = "Required to display high-risk threat alerts.",
                        onFix = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // Section B: Notification Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Notification Settings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Critical & High-Risk Alerts",
                        subtitle = "Immediate alerts for confirmed smishing or quishing attacks",
                        checked = notifyHighSeverity,
                        onCheckedChange = { notifyHighSeverity = it }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "Suspicious Activity Warnings",
                        subtitle = "Notifications for unverified senders and medium-risk links",
                        checked = notifyMediumSeverity,
                        onCheckedChange = { notifyMediumSeverity = it }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "Daily Security Tips",
                        subtitle = "Periodic anti-phishing education alerts",
                        checked = notifyDailyTips,
                        onCheckedChange = { notifyDailyTips = it }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "Show Message Preview on Lock Screen",
                        subtitle = "When disabled, masks sender and message snippets for privacy",
                        checked = showNotificationPreviews,
                        onCheckedChange = { showNotificationPreviews = it }
                    )
                }
            }
        }

        // Section D & E: AI Model & Learning Preferences
        item {
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
                        Text("AI Models & Learning Preferences", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = "View Info",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.clickable { showModelMetadataDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Contribute Anonymized Threat Telemetry",
                        subtitle = "Helps evaluate false-positive rates for future model training. Personal messages are never sent.",
                        checked = optInAiLearning,
                        onCheckedChange = {
                            optInAiLearning = it
                            Toast.makeText(
                                context,
                                if (it) "Opted in to anonymous threat telemetry" else "Opted out. All processing remains local.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }

        // Section F: Privacy, Export & Data Management
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Privacy & Data Management", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Cloud Synchronization",
                        subtitle = "Sync detection events with PostgreSQL & Firestore",
                        checked = isCloudSyncEnabled,
                        onCheckedChange = { isCloudSyncEnabled = it }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    SettingActionRow(
                        title = "Export Scan History (JSON)",
                        subtitle = "Copies all stored threat events to clipboard",
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            val jsonBuilder = StringBuilder("[\n")
                            threats.forEachIndexed { i, t ->
                                jsonBuilder.append("  {\"id\":\"${t.id}\",\"timestamp\":${t.timestamp},\"source\":\"${t.source}\",\"riskScore\":${t.riskScore},\"category\":\"${t.category}\"}")
                                if (i < threats.size - 1) jsonBuilder.append(",")
                                jsonBuilder.append("\n")
                            }
                            jsonBuilder.append("]")
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Threat Export", jsonBuilder.toString()))
                            Toast.makeText(context, "Exported ${threats.size} records to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    SettingActionRow(
                        title = "Clear Local History",
                        subtitle = "Purges local SQLite records on this device",
                        icon = Icons.Default.DeleteForever,
                        tint = Color(0xFFF87171),
                        onClick = { showClearHistoryDialog = true }
                    )

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    SettingActionRow(
                        title = "Privacy Policy & Disclosure",
                        subtitle = "Review our encryption and data handling guarantees",
                        icon = Icons.Default.Policy,
                        onClick = { showPrivacyPolicyDialog = true }
                    )
                }
            }
        }

        // Section H & Education: About Squish
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About Squish", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Squish — Real-Time AI/ML-Based Quishing & Smishing Detection", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
                    Text("Version 1.0 (API 35/37 • 16 KB Page Aligned Universal Build)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onOpenEducation,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Security Academy Lessons", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF94A3B8), lineHeight = 14.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF38BDF8),
                checkedTrackColor = Color(0xFF0EA5E9).copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun SettingActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color = Color(0xFF38BDF8),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(subtitle, fontSize = 11.sp, color = Color(0xFF94A3B8), lineHeight = 14.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF64748B))
    }
}

@Composable
fun ChannelStatusRow(
    channel: String,
    isGranted: Boolean,
    explanation: String,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        if (isGranted) "Granted" else "Missing",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) Color(0xFF34D399) else Color(0xFFF87171),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Text(explanation, fontSize = 11.sp, color = Color(0xFF94A3B8))
        }

        if (!isGranted) {
            TextButton(onClick = onFix) {
                Text("Fix", fontSize = 11.sp, color = Color(0xFF38BDF8))
            }
        }
    }
}
