package com.example.android.presentation.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.android.domain.model.QrAnalysisResult
import com.example.android.domain.model.UrlFeatures
import com.example.android.domain.riskengine.RecommendedAction
import com.example.android.domain.riskengine.RiskAssessment
import com.example.android.domain.riskengine.ThreatCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    viewModel: QrScannerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val permissionState by viewModel.permissionState.collectAsState()
    val isFlashEnabled by viewModel.isFlashEnabled.collectAsState()
    val zoomRatio by viewModel.zoomRatio.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val scanResult by viewModel.scanResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scanHistory by viewModel.scanHistory.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }
    var showUrlWarningDialog by remember { mutableStateOf(false) }
    var pendingUrlToOpen by remember { mutableStateOf("") }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onCameraPermissionResult(isGranted)
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onGalleryImageSelected(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { galleryPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Import from Gallery")
                    }
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(Icons.Default.History, contentDescription = "Scan History")
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
                .background(Color.Black)
        ) {
            when (permissionState) {
                is CameraPermissionState.Granted -> {
                    CameraXViewfinder(
                        isFlashEnabled = isFlashEnabled,
                        zoomRatio = zoomRatio,
                        isPaused = scanResult != null || isProcessing,
                        onQrDetected = { rawPayload ->
                            if (scanResult == null && !isProcessing) {
                                viewModel.onQrCodeScanned(rawPayload, source = "CAMERA")
                            }
                        }
                    )

                    // Cyber Security Target Viewfinder Overlay
                    ScannerOverlay(
                        modifier = Modifier.fillMaxSize(),
                        isProcessing = isProcessing
                    )

                    // Helper label & Gallery Quick Action when not showing result
                    if (scanResult == null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Align the QR code inside the frame",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )

                            // Controls Bar (Flash, Zoom, Gallery)
                            CameraControlsOverlay(
                                isFlashEnabled = isFlashEnabled,
                                zoomRatio = zoomRatio,
                                onToggleFlash = { viewModel.toggleFlash() },
                                onZoomChange = { viewModel.setZoomRatio(it) },
                                onOpenGallery = {
                                    galleryPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                        }
                    }
                }
                else -> {
                    CameraPermissionRationaleCard(
                        onGrantPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        onOpenGallery = {
                            galleryPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                }
            }

            // Error Banner
            errorMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            // Full Dynamic Risk Assessment Result Bottom Sheet
            scanResult?.let { result ->
                QrAnalysisResultBottomSheet(
                    result = result,
                    onDismiss = { viewModel.dismissResult() },
                    onOpenUrlRequest = { url ->
                        pendingUrlToOpen = url
                        showUrlWarningDialog = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }
        }
    }

    // High Risk Explicit Confirmation Warning Dialog
    if (showUrlWarningDialog && pendingUrlToOpen.isNotBlank()) {
        val currentCategory = scanResult?.assessment?.category ?: ThreatCategory.MEDIUM
        val isHigh = currentCategory == ThreatCategory.HIGH

        AlertDialog(
            onDismissRequest = { showUrlWarningDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isHigh) MaterialTheme.colorScheme.error else Color(0xFFF57C00)
                )
            },
            title = {
                Text(
                    if (isHigh) "Dangerous Link Blocked" else "External URL Caution",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Destination:\n$pendingUrlToOpen\n\n" +
                    if (isHigh) {
                        "Squish Shield strongly advises against opening this URL. Machine learning analysis detected strong indicators of quishing/credential harvesting."
                    } else {
                        "Squish Shield does not automatically open links. Ensure you trust the sender before continuing."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUrlWarningDialog = false
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingUrlToOpen))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHigh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isHigh) "Open Anyway (Unsafe)" else "Proceed to Browser")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUrlWarningDialog = false }) {
                    Text("Cancel / Stay Safe")
                }
            }
        )
    }

    // Scan History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("QR Scan History", fontWeight = FontWeight.Bold) },
            text = {
                if (scanHistory.isEmpty()) {
                    Text("No local QR scan records stored.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scanHistory, key = { it.id }) { item ->
                            val scoreColor = when {
                                item.riskScore >= 76 -> MaterialTheme.colorScheme.error
                                item.riskScore >= 51 -> Color(0xFFE65100)
                                item.riskScore >= 26 -> Color(0xFFF9A825)
                                else -> Color(0xFF2E7D32)
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.domain.ifBlank { "QR Code Payload" },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Risk: ${item.riskScore}/100 • ${if (item.isQuishing) "QUISHING" else "SAFE"}",
                                            color = scoreColor,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteHistoryRecord(item.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun CameraXViewfinder(
    isFlashEnabled: Boolean,
    zoomRatio: Float,
    isPaused: Boolean,
    onQrDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraState = remember { mutableStateOf<Camera?>(null) }
    val analyzer = remember { QrCodeAnalyzer(onQrDetected) }

    LaunchedEffect(isPaused) {
        analyzer.setPaused(isPaused)
    }

    LaunchedEffect(isFlashEnabled) {
        cameraState.value?.cameraControl?.enableTorch(isFlashEnabled)
    }

    LaunchedEffect(zoomRatio) {
        cameraState.value?.cameraControl?.setZoomRatio(zoomRatio)
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor, analyzer)

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    cameraState.value = camera
                    camera.cameraControl.enableTorch(isFlashEnabled)
                    camera.cameraControl.setZoomRatio(zoomRatio)
                } catch (e: Exception) {
                    // Safe error handling for camera bind
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Semi-transparent cutout canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSize = 270.dp.toPx()
            val left = (canvasWidth - boxSize) / 2
            val top = (canvasHeight - boxSize) / 2

            // Draw targeting corner brackets
            val cornerLength = 36.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            val bracketColor = if (isProcessing) Color(0xFF64B5F6) else Color(0xFF00E676)

            // Top-Left
            drawLine(bracketColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
            drawLine(bracketColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)

            // Top-Right
            drawLine(bracketColor, Offset(left + boxSize - cornerLength, top), Offset(left + boxSize, top), strokeWidth)
            drawLine(bracketColor, Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLength), strokeWidth)

            // Bottom-Left
            drawLine(bracketColor, Offset(left, top + boxSize), Offset(left + cornerLength, top + boxSize), strokeWidth)
            drawLine(bracketColor, Offset(left, top + boxSize - cornerLength), Offset(left, top + boxSize), strokeWidth)

            // Bottom-Right
            drawLine(bracketColor, Offset(left + boxSize - cornerLength, top + boxSize), Offset(left + boxSize, top + boxSize), strokeWidth)
            drawLine(bracketColor, Offset(left + boxSize, top + boxSize - cornerLength), Offset(left + boxSize, top + boxSize), strokeWidth)

            // Animated scanning laser line
            if (!isProcessing) {
                val laserY = top + (boxSize * laserPosition)
                drawLine(
                    color = Color(0x9900E676),
                    start = Offset(left + 8.dp.toPx(), laserY),
                    end = Offset(left + boxSize - 8.dp.toPx(), laserY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        if (isProcessing) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Analyzing Threat Vectors...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun CameraControlsOverlay(
    isFlashEnabled: Boolean,
    zoomRatio: Float,
    onToggleFlash: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(32.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Flash Toggle
            IconButton(
                onClick = onToggleFlash,
                modifier = Modifier
                    .size(42.dp)
                    .background(if (isFlashEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Toggle Flash",
                    tint = if (isFlashEnabled) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom 1x
            FilterChip(
                selected = zoomRatio == 1.0f,
                onClick = { onZoomChange(1.0f) },
                label = { Text("1x") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.Black
                )
            )

            // Zoom 2x
            FilterChip(
                selected = zoomRatio == 2.0f,
                onClick = { onZoomChange(2.0f) },
                label = { Text("2x") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.Black
                )
            )

            // Gallery Import Button
            OutlinedButton(
                onClick = onOpenGallery,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.5f)))
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Gallery", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CameraPermissionRationaleCard(
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Camera Access Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Squish Shield uses your camera strictly on-device to scan and detect QR codes for quishing prevention. Camera frames are processed in memory and never leave your device.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Camera Permission")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open App Settings")
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onOpenGallery,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Image from Gallery (No Camera Required)")
            }
        }
    }
}

/**
 * Polished Cybersecurity QR Analysis Bottom Sheet.
 * Displays dynamic Risk Score (0-100), Threat Category Badge, AI Model Breakdown,
 * Explainable AI (XAI) Reasons, and Recommended Action.
 */
@Composable
fun QrAnalysisResultBottomSheet(
    result: QrAnalysisResult,
    onDismiss: () -> Unit,
    onOpenUrlRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fallbackCategory = when {
        result.riskScore >= 76 -> ThreatCategory.HIGH
        result.riskScore >= 51 -> ThreatCategory.MEDIUM
        result.riskScore >= 26 -> ThreatCategory.LOW
        else -> ThreatCategory.SAFE
    }
    val assessment = result.assessment ?: RiskAssessment(
        riskScore = result.riskScore,
        category = fallbackCategory,
        recommendedAction = RecommendedAction.fromCategory(fallbackCategory),
        reasons = if (result.isQuishing) listOf("Detected potential quishing threat") else listOf("Payload verified with normal parameters")
    )
    val isWebUrl = result.targetUrl.isNotBlank()
    val scrollState = rememberScrollState()

    val score = assessment.riskScore
    val category = assessment.category

    val categoryColor = when (category) {
        ThreatCategory.SAFE -> Color(0xFF16A34A) // Emerald Green
        ThreatCategory.LOW -> Color(0xFFD97706) // Warm Amber
        ThreatCategory.MEDIUM -> Color(0xFFEA580C) // Bold Orange
        ThreatCategory.HIGH -> Color(0xFFDC2626) // Vivid Crimson Red
    }

    val categoryContainerColor = categoryColor.copy(alpha = 0.12f)
    val categoryBorderColor = categoryColor.copy(alpha = 0.45f)

    val categoryBadgeText = when (category) {
        ThreatCategory.SAFE -> "SAFE"
        ThreatCategory.LOW -> "LOW RISK"
        ThreatCategory.MEDIUM -> "MEDIUM RISK"
        ThreatCategory.HIGH -> "HIGH RISK"
    }

    val headlineText = when {
        category == ThreatCategory.HIGH && isWebUrl -> "⚠ Dangerous Phishing / Quishing URL Detected"
        category == ThreatCategory.HIGH && !isWebUrl -> "⚠ Dangerous Executable Payload Detected"
        category == ThreatCategory.MEDIUM -> "⚠ Suspicious Indicators Detected"
        category == ThreatCategory.LOW -> "ℹ Low Risk Indicators Present"
        else -> "✔ Verified Safe QR Code"
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(result.timestamp) { dateFormat.format(Date(result.timestamp)) }

    val breakdown = assessment.scoreBreakdown
    val eventSummaryText = remember(score, categoryBadgeText, formattedTime, result, assessment) {
        val urlModelText = if (result.isModelAvailable) "${breakdown.urlScore.roundToInt()}%" else "Unavailable (Rules active)"
        val modelLines = buildList {
            add("URL Model (XGBoost): $urlModelText")
            add("QR Payload Analysis: ${breakdown.qrScore.roundToInt()}%")
            add("Anomaly Model (Isolation Forest): ${breakdown.anomalyScore.roundToInt()}%")
            add("Context & Threat Correlation Engine: ${breakdown.contextScore.roundToInt()}%")
        }.joinToString("\n  - ", prefix = "\n  - ")

        val reasonsSummary = if (assessment.reasons.isNotEmpty()) {
            assessment.reasons.take(5).joinToString("\n  - ", prefix = "\n  - ")
        } else {
            "\n  - No anomalous indicators detected."
        }

        """
Risk Score: $score / 100
Threat Category: $categoryBadgeText
Model Results:$modelLines
Reasons:$reasonsSummary
Recommended Action: ${assessment.recommendedAction.actionName.uppercase()} - ${assessment.recommendedAction.description}
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
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(scrollState)
                .fillMaxWidth()
        ) {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (category == ThreatCategory.SAFE) "QR CODE SECURITY ASSESSMENT" else "QR CODE THREAT INTELLIGENCE",
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
                                    text = categoryBadgeText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Linear Risk Gauge
                    LinearProgressIndicator(
                        progress = { (score / 100f).coerceIn(0f, 1f) },
                        color = categoryColor,
                        trackColor = categoryColor.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = headlineText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor
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
                    val urlModelText = if (result.isModelAvailable) "${breakdown.urlScore.roundToInt()}%" else "Unavailable (Rules active)"
                    MetricRow(label = "URL Model (XGBoost)", value = urlModelText, isThreat = breakdown.urlScore >= 50f)
                    MetricRow(label = "QR Payload Analysis", value = "${breakdown.qrScore.roundToInt()}%", isThreat = breakdown.qrScore >= 50f)
                    MetricRow(label = "Anomaly Model (Isolation Forest)", value = "${breakdown.anomalyScore.roundToInt()}%", isThreat = breakdown.anomalyScore >= 50f)
                    MetricRow(label = "Context & Threat Correlation Engine", value = "${breakdown.contextScore.roundToInt()}%", isThreat = breakdown.contextScore >= 50f)
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

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (assessment.reasons.isEmpty()) {
                    Text(
                        text = "• No anomalous indicators detected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    assessment.reasons.take(5).forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text("• ", fontWeight = FontWeight.Bold, color = categoryColor)
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val actionIcon = when (category) {
                        ThreatCategory.HIGH -> Icons.Default.Block
                        ThreatCategory.MEDIUM -> Icons.Default.Warning
                        ThreatCategory.LOW -> Icons.Default.Info
                        ThreatCategory.SAFE -> Icons.Default.CheckCircle
                    }
                    Icon(
                        actionIcon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = assessment.recommendedAction.actionName.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = assessment.recommendedAction.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Timestamp & Payload Details Card
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
                                text = if (isWebUrl) "Web URL" else "Raw Data",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                            text = if (isWebUrl) "Target URL" else "Raw Payload",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    val textToCopy = result.targetUrl.ifBlank { result.rawPayload }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("QR Payload", textToCopy))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.targetUrl.ifBlank { result.rawPayload },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Event Summary", eventSummaryText))
                        Toast.makeText(context, "Event summary copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1.1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Scan Another", fontSize = 12.sp)
                }

                if (isWebUrl) {
                    Button(
                        onClick = { onOpenUrlRequest(result.targetUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (category == ThreatCategory.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (category == ThreatCategory.HIGH) "Review..." else "Open...", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, isThreat: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isThreat) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
