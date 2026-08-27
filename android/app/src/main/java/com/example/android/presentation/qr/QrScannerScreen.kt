package com.example.android.presentation.qr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.android.domain.model.QrAnalysisResult
import com.example.android.domain.model.UrlFeatures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

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
    val extractedFeatures by viewModel.extractedFeatures.collectAsState()
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
                title = { Text("QR Scanner", fontWeight = FontWeight.Bold) },
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
                        onQrDetected = { rawPayload ->
                            if (scanResult == null) {
                                viewModel.onQrCodeScanned(rawPayload, source = "CAMERA")
                            }
                        }
                    )

                    // Finder Overlay Mask
                    ScannerOverlay(
                        modifier = Modifier.fillMaxSize(),
                        isProcessing = isProcessing
                    )

                    // Camera Controls Bottom Bar
                    CameraControlsOverlay(
                        isFlashEnabled = isFlashEnabled,
                        zoomRatio = zoomRatio,
                        onToggleFlash = { viewModel.toggleFlash() },
                        onZoomChange = { viewModel.setZoomRatio(it) },
                        onOpenGallery = {
                            galleryPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                    )
                }
                else -> {
                    // Permission Denied / Required View
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

            // Error Banner Snack
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

            // Decoded Scan Result Card
            scanResult?.let { result ->
                QrAnalysisResultBottomSheet(
                    result = result,
                    features = extractedFeatures,
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

    // Explicit Safe URL Open Warning Dialog
    if (showUrlWarningDialog && pendingUrlToOpen.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showUrlWarningDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("External URL Warning", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You are about to open an external web address:\n\n$pendingUrlToOpen\n\nSquish Shield does not automatically load external URLs. Ensure you trust this domain before proceeding."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUrlWarningDialog = false
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingUrlToOpen))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Open Browser")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUrlWarningDialog = false }) {
                    Text("Cancel")
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
                            .heightIn(max = 350.dp)
                    ) {
                        items(scanHistory) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (item.extractedUrl.isNotBlank()) Icons.Default.Link else Icons.Default.Article,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.extractedUrl.ifBlank { item.rawContent },
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
    onQrDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor, QrCodeAnalyzer(onQrDetected))

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    camera.cameraControl.enableTorch(isFlashEnabled)
                    camera.cameraControl.setZoomRatio(zoomRatio)
                } catch (e: Exception) {
                    // Handle camera binding failure safely
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            if (cameraProviderFuture.isDone) {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA
                    )
                    camera.cameraControl.enableTorch(isFlashEnabled)
                    camera.cameraControl.setZoomRatio(zoomRatio)
                } catch (e: Exception) {
                    // Update error handling
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Target Box Frame
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 3.dp,
                    color = if (isProcessing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(16.dp)
                )
        )
        if (isProcessing) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
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
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(32.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Flash Toggle
            IconButton(
                onClick = onToggleFlash,
                modifier = Modifier
                    .background(if (isFlashEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Toggle Flash",
                    tint = if (isFlashEnabled) Color.Black else Color.White
                )
            }

            // Zoom 1x
            FilterChip(
                selected = zoomRatio == 1.0f,
                onClick = { onZoomChange(1.0f) },
                label = { Text("1x") }
            )

            // Zoom 2x
            FilterChip(
                selected = zoomRatio == 2.0f,
                onClick = { onZoomChange(2.0f) },
                label = { Text("2x") }
            )

            // Gallery Import Button
            IconButton(
                onClick = onOpenGallery,
                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery Import", tint = Color.White)
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
                "Squish Shield uses your camera strictly on-device to scan and detect QR codes for quishing prevention. Camera access can be revoked at any time.",
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

@Composable
fun QrAnalysisResultBottomSheet(
    result: QrAnalysisResult,
    features: UrlFeatures?,
    onDismiss: () -> Unit,
    onOpenUrlRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isWebUrl = result.extractedUrl.isNotBlank()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isWebUrl) Icons.Default.Link else Icons.Default.Article,
                        contentDescription = null,
                        tint = if (isWebUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isWebUrl) "Web URL QR Code" else "Plain Text QR Code",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Extracted Payload Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isWebUrl) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isWebUrl) "Extracted Web URL" else "Raw Plain Text Payload",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isWebUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.extractedUrl.ifBlank { result.rawContent },
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Analysis Findings or Plain Text Info
            if (isWebUrl) {
                features?.let { feat ->
                    Text(
                        text = "Preliminary Analysis Findings:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (feat.suspiciousCharacteristics.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Standard URL format (HTTPS enabled, single domain level)", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        feat.suspiciousCharacteristics.forEach { char ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(char, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Safe (Plain text payload — no web links or remote code execution)", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Scan Another")
                }

                if (isWebUrl) {
                    Button(
                        onClick = { onOpenUrlRequest(result.extractedUrl) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Link...")
                    }
                }
            }
        }
    }
}
