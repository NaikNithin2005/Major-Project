package com.example.android.presentation.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.data.classifier.OnnxTinyBertSmishingClassifier
import com.example.android.data.classifier.OnnxUrlClassifier
import com.example.android.data.parser.UrlExtractor
import com.example.android.data.parser.UrlFeatureExtractor
import com.example.android.data.scanner.GalleryQrScanner
import com.example.android.domain.classifier.SmishingClassifier
import com.example.android.domain.classifier.UrlClassifier
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.UrlAnalysisInput
import com.example.android.domain.rules.RuleEngine
import com.example.android.domain.usecase.ProcessQrCodeUseCase
import com.example.android.domain.xai.ExplainableAiEngine
import com.example.android.sync.SyncScheduler
import kotlinx.coroutines.launch
import java.util.UUID

data class ManualScanResult(
    val verdict: String, // "SAFE", "SUSPICIOUS", "MALICIOUS"
    val severity: String,
    val score: Int,
    val confidence: Float,
    val category: String,
    val reasons: List<String>,
    val recommendedActions: List<String>,
    val scannedContent: String,
    val source: String
)

@Composable
fun ScannerTab(
    smishingClassifier: SmishingClassifier,
    urlClassifier: UrlClassifier,
    ruleEngine: RuleEngine,
    explainableAiEngine: ExplainableAiEngine,
    processQrCodeUseCase: ProcessQrCodeUseCase,
    onSaveThreatToHistory: (ThreatRecord) -> Unit,
    onOpenLiveCameraQr: () -> Unit,
    onAskAiAssistant: (String) -> Unit,
    onOpenSecurityCheck: ((url: String, source: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(0) } // 0=Message, 1=URL, 2=QR Code
    val modes = listOf("💬 Message", "🔗 URL Link", "📷 QR Code")

    var messageInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<ManualScanResult?>(null) }
    var isBackgroundScheduled by remember { mutableStateOf(true) }

    // Gallery Picker for QR Code Images
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isAnalyzing = true
            coroutineScope.launch {
                try {
                    val rawDecoded = GalleryQrScanner.scanGalleryUri(context, uri)
                    if (rawDecoded != null) {
                        val qrRes = processQrCodeUseCase(rawDecoded, source = "GALLERY").getOrNull()
                        val score = qrRes?.riskScore ?: 0
                        val isMalicious = qrRes?.isQuishing == true
                        val verdict = if (isMalicious) "MALICIOUS" else if (score >= 40) "SUSPICIOUS" else "SAFE"
                        scanResult = ManualScanResult(
                            verdict = verdict,
                            severity = if (isMalicious) "HIGH" else if (score >= 40) "MEDIUM" else "SAFE",
                            score = score,
                            confidence = qrRes?.confidence ?: 0.9f,
                            category = "QR Code / Quishing",
                            reasons = listOf(
                                "Decoded Payload: ${qrRes?.extractedUrl ?: rawDecoded}",
                                "Domain: ${qrRes?.domain?.ifBlank { "Text / Data payload" } ?: "Text / Data payload"}"
                            ),
                            recommendedActions = if (isMalicious) {
                                listOf("Do NOT open the decoded URL in your browser", "Delete the image containing this QR code")
                            } else {
                                listOf("Decoded safely. You can copy the raw content below without auto-opening.")
                            },
                            scannedContent = qrRes?.extractedUrl ?: rawDecoded,
                            source = "QR_GALLERY"
                        )
                    } else {
                        Toast.makeText(context, "No valid QR code found in selected image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error scanning image: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isAnalyzing = false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Manual Security Scanner",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Analyze messages, URLs, and QR codes before opening them",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Mode Switcher Tabs
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    modes.forEachIndexed { index, modeTitle ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedMode = index
                                    scanResult = null
                                },
                            color = if (selectedMode == index) Color(0xFF0EA5E9) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeTitle,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedMode == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedMode == index) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mode Content 0: Message Scanner
        if (selectedMode == 0) {
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
                            Text("Paste SMS / Message Text", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            TextButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = cb.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        messageInput = clip.getItemAt(0).text?.toString() ?: ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            }
                        }

                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("Paste suspicious message, bank alert, or job offer text here...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Test Samples
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Sample Scams:",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF334155),
                                modifier = Modifier.clickable {
                                    messageInput = "Your FedEx parcel #4901 is held at customs. Pay $1.99 redelivery fee now at bit.ly/fedx-track or it will be destroyed."
                                }
                            ) {
                                Text("Parcel SMS", fontSize = 10.sp, color = Color(0xFF38BDF8), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF334155),
                                modifier = Modifier.clickable {
                                    messageInput = "Dear user, your HDFC bank access will expire today. Update your PAN card immediately at hdfc-verify-kyc.com."
                                }
                            ) {
                                Text("Bank KYC", fontSize = 10.sp, color = Color(0xFF38BDF8), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (messageInput.isNotBlank()) {
                                    isAnalyzing = true
                                    coroutineScope.launch {
                                        try {
                                            var smishingProb = 0.1f
                                            if (smishingClassifier is OnnxTinyBertSmishingClassifier) {
                                                val eval = smishingClassifier.evaluateSms(messageInput)
                                                smishingProb = eval.smishingProbability
                                            }
                                            val xaiReasons = explainableAiEngine.generateExplanations(smsProbability = smishingProb)
                                            val score = (smishingProb * 100).toInt().coerceIn(0, 100)
                                            val isHigh = score >= 70
                                            val isMed = score in 40..69
                                            scanResult = ManualScanResult(
                                                verdict = if (isHigh) "MALICIOUS" else if (isMed) "SUSPICIOUS" else "SAFE",
                                                severity = if (isHigh) "CRITICAL" else if (isMed) "MEDIUM" else "SAFE",
                                                score = score,
                                                confidence = smishingProb,
                                                category = "Smishing Detection",
                                                reasons = xaiReasons.map { "${it.title}: ${it.description}" },
                                                recommendedActions = if (isHigh) {
                                                    listOf("Do not click links or reply", "Delete message immediately", "Block the sender")
                                                } else {
                                                    listOf("Verify directly through the official provider app before taking action")
                                                },
                                                scannedContent = messageInput,
                                                source = "MANUAL_SMS"
                                            )
                                        } finally {
                                            isAnalyzing = false
                                        }
                                    }
                                }
                            },
                            enabled = messageInput.isNotBlank() && !isAnalyzing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing with TinyBERT AI...")
                            } else {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyze Message with On-Device AI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Mode Content 1: URL Scanner
        if (selectedMode == 1) {
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
                            Text("Paste URL / Link", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            TextButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = cb.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        urlInput = clip.getItemAt(0).text?.toString() ?: ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            }
                        }

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = { Text("https://example-phish-domain.com/login", fontSize = 12.sp, color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Test Samples
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Sample URLs:", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.align(Alignment.CenterVertically))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF334155),
                                modifier = Modifier.clickable {
                                    urlInput = "http://192.168.1.55/secure-apple-login/auth.php"
                                }
                            ) {
                                Text("IP Phish", fontSize = 10.sp, color = Color(0xFF38BDF8), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF334155),
                                modifier = Modifier.clickable {
                                    urlInput = "https://paypal-security-verification.com/signin"
                                }
                            ) {
                                Text("Lookalike", fontSize = 10.sp, color = Color(0xFF38BDF8), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    isAnalyzing = true
                                    coroutineScope.launch {
                                        try {
                                            val normalized = UrlExtractor.normalizeUrl(urlInput.trim())
                                            val features = UrlFeatureExtractor.extractFeatures(normalized)
                                            var urlProb = 0.05f
                                            var anomalyVal = 0.0f
                                            if (urlClassifier is OnnxUrlClassifier) {
                                                val eval = urlClassifier.evaluateUrl(normalized, features)
                                                urlProb = eval.phishingProbability
                                                anomalyVal = eval.anomalyScore
                                            } else {
                                                val res = urlClassifier.classify(
                                                    UrlAnalysisInput(rawPayload = normalized, extractedUrl = normalized, features = features, source = "MANUAL")
                                                )
                                                urlProb = res.confidence
                                            }
                                            val ruleRes = ruleEngine.evaluate(urlFeatures = features)
                                            val xaiReasons = explainableAiEngine.generateExplanations(
                                                urlProbability = urlProb,
                                                anomalyScore = anomalyVal,
                                                ruleResults = ruleRes
                                            )
                                            val score = (urlProb * 100).toInt().coerceIn(0, 100)
                                            val isHigh = score >= 70
                                            val isMed = score in 40..69
                                            scanResult = ManualScanResult(
                                                verdict = if (isHigh) "MALICIOUS" else if (isMed) "SUSPICIOUS" else "SAFE",
                                                severity = if (isHigh) "CRITICAL" else if (isMed) "MEDIUM" else "SAFE",
                                                score = score,
                                                confidence = urlProb,
                                                category = "URL Phishing Classifier",
                                                reasons = xaiReasons.map { "${it.title}: ${it.description}" },
                                                recommendedActions = if (isHigh) {
                                                    listOf("Do NOT open this link in any browser", "If credentials were typed, change passwords immediately")
                                                } else {
                                                    listOf("Verify the domain extension carefully before entering credentials")
                                                },
                                                scannedContent = normalized,
                                                source = "MANUAL_URL"
                                            )
                                        } finally {
                                            isAnalyzing = false
                                        }
                                    }
                                }
                            },
                            enabled = urlInput.isNotBlank() && !isAnalyzing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing with XGBoost AI...")
                            } else {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyze URL with On-Device AI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Mode Content 2: QR Code Scanner
        if (selectedMode == 2) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(32.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Scan or Import QR Code", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Squish analyzes the encoded URL using XGBoost and rule engines without auto-opening it.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenLiveCameraQr,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Live Camera", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { galleryPicker.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick Image", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Scan Result View
        if (scanResult != null) {
            item {
                ScanResultDisplayCard(
                    result = scanResult!!,
                    onSaveThreat = {
                        val isUrl = scanResult!!.source.contains("URL") ||
                                scanResult!!.scannedContent.startsWith("http://", ignoreCase = true) ||
                                scanResult!!.scannedContent.startsWith("https://", ignoreCase = true)
                        val extractedUrl = if (isUrl) scanResult!!.scannedContent else UrlExtractor.extractUrls(scanResult!!.scannedContent).firstOrNull()
                        val payloadType = when {
                            scanResult!!.source.contains("QR", ignoreCase = true) -> "QR_CODE"
                            scanResult!!.source.contains("SMS", ignoreCase = true) || scanResult!!.source.contains("MESSAGE", ignoreCase = true) -> "SMS"
                            isUrl -> "URL"
                            else -> "TEXT"
                        }
                        val tr = ThreatRecord(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            source = scanResult!!.source,
                            sender = if (payloadType == "SMS") "Manual Submission" else scanResult!!.scannedContent.take(40),
                            riskScore = scanResult!!.score,
                            category = scanResult!!.category,
                            actionTaken = if (scanResult!!.verdict == "MALICIOUS") "BLOCK" else "WARN_USER",
                            details = scanResult!!.reasons.joinToString("; "),
                            rawContent = scanResult!!.scannedContent,
                            extractedUrl = extractedUrl,
                            payloadType = payloadType,
                            severity = scanResult!!.severity,
                            modelVersion = "v2.1-edge",
                            isQuarantined = scanResult!!.verdict == "MALICIOUS",
                            reviewStatus = "UNREVIEWED",
                            contentRetentionMode = "LOCAL_SAVED"
                        )
                        onSaveThreatToHistory(tr)
                        Toast.makeText(context, "Saved to History & Local DB", Toast.LENGTH_SHORT).show()
                    },
                    onAskAiAssistant = { onAskAiAssistant(scanResult!!.scannedContent) },
                    onCopyContent = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Scanned Content", scanResult!!.scannedContent))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onOpenSecurityCheck = onOpenSecurityCheck
                )
            }
        }

        // Section F: Automated Background Scanning & Scheduler Settings
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scheduled Background Checks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Periodic WorkManager tasks synchronize threat intelligence and evaluate offline queues.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = isBackgroundScheduled,
                            onCheckedChange = {
                                isBackgroundScheduled = it
                                if (it) {
                                    SyncScheduler.schedulePeriodicSync(context)
                                    Toast.makeText(context, "Background sync scheduled", Toast.LENGTH_SHORT).show()
                                } else {
                                    SyncScheduler.cancelPeriodicSync(context)
                                    Toast.makeText(context, "Background sync paused", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8), checkedTrackColor = Color(0xFF0EA5E9).copy(alpha = 0.3f))
                        )
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
fun ScanResultDisplayCard(
    result: ManualScanResult,
    onSaveThreat: () -> Unit,
    onAskAiAssistant: () -> Unit,
    onCopyContent: () -> Unit,
    onOpenSecurityCheck: ((url: String, source: String) -> Unit)? = null
) {
    val borderColor = when (result.verdict) {
        "MALICIOUS" -> Color(0xFFEF4444)
        "SUSPICIOUS" -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val badgeBg = borderColor.copy(alpha = 0.2f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(borderColor, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = result.verdict,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = borderColor
                        )
                    }
                }

                Text(
                    text = "Risk Score: ${result.score}/100",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Original Scanned Content:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Copy", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    IconButton(
                        onClick = onCopyContent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy content",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = result.scannedContent,
                        fontSize = 12.sp,
                        color = Color(0xFFF1F5F9),
                        maxLines = 6,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Plain text rendering (links not automatically visited)",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Key Threat Indicators (XAI):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            Spacer(modifier = Modifier.height(4.dp))
            result.reasons.forEach { reason ->
                Text("• $reason", fontSize = 11.sp, color = Color(0xFF94A3B8), lineHeight = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Recommended Action Steps:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
            Spacer(modifier = Modifier.height(4.dp))
            result.recommendedActions.forEach { act ->
                Text("✓ $act", fontSize = 11.sp, color = Color(0xFFCBD5E1), lineHeight = 15.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveThreat,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Text("Save to History", fontSize = 11.sp)
                }

                Button(
                    onClick = onAskAiAssistant,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ask AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (onOpenSecurityCheck != null && (result.scannedContent.startsWith("http://", ignoreCase = true) || result.scannedContent.startsWith("https://", ignoreCase = true))) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onOpenSecurityCheck(result.scannedContent, result.source) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Full Squish Security Check", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
