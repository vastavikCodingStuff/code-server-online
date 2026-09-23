package com.vastavik.codeauth.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.vastavik.codeauth.data.NetworkClient
import com.vastavik.codeauth.data.QrPayload
import com.vastavik.codeauth.data.SecurePrefs
import com.vastavik.codeauth.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors

/**
 * ScannerScreen.kt — CameraX + ML Kit Barcode Scanning
 * - live preview inside sleek viewfinder frame
 * - parses JSON {"server":"...", "sessionId":"..."}
 * - POST to /api/app/approve with haptics + success modal
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    securePrefs: SecurePrefs
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    var isProcessing by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastScannedSession by remember { mutableStateOf<String?>(null) }
    var approvalLoading by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Copy of config observed for UI
    val config by securePrefs.configFlow.collectAsState()

    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(180)
        }
        // double tick for success feel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 40, 120), -1))
        }
    }

    fun handleScanned(rawValue: String) {
        if (isProcessing || approvalLoading) return
        isProcessing = true
        errorMessage = null

        // Parse JSON payload: {"server":"...", "sessionId":"..."}
        val sessionId: String? = try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val payload = json.decodeFromString<QrPayload>(rawValue)
            payload.resolvedSessionId().ifBlank { null }
        } catch (e: Exception) {
            // fallback: raw trimming, sometimes QR contains plain sessionId string
            Log.w("ScannerScreen", "QR not JSON, trying raw: $rawValue", e)
            // attempt to extract sessionId field via regex
            val regex = Regex("""["']sessionId["']\s*:\s*["']([^"']+)["']""")
            regex.find(rawValue)?.groupValues?.getOrNull(1)
                ?: rawValue.trim().takeIf { it.length in 8..256 } // if already sessionId
        }

        if (sessionId.isNullOrBlank()) {
            isProcessing = false
            errorMessage = "Invalid QR — missing sessionId.\nRaw: ${rawValue.take(120)}"
            scope.launch { snackbarHostState.showSnackbar(errorMessage!!) }
            // debounce reset
            scope.launch {
                kotlinx.coroutines.delay(2000)
                isProcessing = false
            }
            return
        }

        // debounce duplicate scan same session
        if (lastScannedSession == sessionId) {
            isProcessing = false
            return
        }
        lastScannedSession = sessionId

        // haptic immediately on valid scan
        vibrate(context)

        // POST approve
        approvalLoading = true
        scope.launch {
            val domain = config.vpsDomain
            val secret = config.secretKey
            if (secret.isBlank()) {
                errorMessage = "Secret key not set. Go to Settings."
                snackbarHostState.showSnackbar(errorMessage!!)
                approvalLoading = false
                isProcessing = false
                return@launch
            }
            val deviceName = Build.MODEL ?: "Android Device"
            val result = NetworkClient.approveSession(domain, secret, sessionId, deviceName)
            approvalLoading = false
            result.onSuccess { resp ->
                val ok = resp.success == true || resp.error == null
                if (ok) {
                    successMessage = resp.message ?: "Session approved! You can now use the browser."
                    showSuccess = true
                    vibrate(context)
                } else {
                    errorMessage = resp.error ?: resp.message ?: "Approval failed"
                    snackbarHostState.showSnackbar(errorMessage!!)
                }
            }.onFailure { ex ->
                errorMessage = ex.message ?: "Network error. Check domain/secret & internet."
                Log.e("ScannerScreen", "approve failed", ex)
                snackbarHostState.showSnackbar(errorMessage!!)
            }
            // allow re-scan after 2.5s
            kotlinx.coroutines.delay(2500)
            isProcessing = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundDark
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundDark)
        ) {
            when {
                !cameraPermission.status.isGranted -> {
                    // Permission rationale
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, null, tint = PrimaryIndigo, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Camera access needed", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Allow camera to scan QR codes and approve logins securely.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { cameraPermission.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Grant Camera Permission", modifier = Modifier.padding(4.dp)) }
                    }
                }
                else -> {
                    // Camera preview + overlay
                    CameraPreview(
                        onQrScanned = { handleScanned(it) },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Viewfinder overlay with cutout + animated scan line
                    ViewfinderOverlay(Modifier.fillMaxSize())

                    // Top bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Scan QR to Approve",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            config.vpsDomain,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // Bottom status
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (approvalLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(color = PrimaryIndigo, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Approving session…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (errorMessage != null) {
                            Text(errorMessage!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { errorMessage = null; isProcessing = false }) { Text("Dismiss", color = PrimaryIndigoLight) }
                        } else {
                            Text("Position the QR inside the frame", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text("Auto-approves on scan", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                        }
                        if (lastScannedSession != null && !approvalLoading) {
                            Spacer(Modifier.height(6.dp))
                            Text("Last: ${lastScannedSession!!.take(18)}…", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Success modal
            if (showSuccess) {
                AlertDialog(
                    onDismissRequest = { showSuccess = false; lastScannedSession = null },
                    containerColor = SurfaceCard,
                    icon = { Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(48.dp)) },
                    title = { Text("Approved!", color = TextPrimary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    text = { Text(successMessage, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = {
                        Button(
                            onClick = { showSuccess = false; lastScannedSession = null },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Done") }
                    }
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { ia ->
                        ia.setAnalyzer(executor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            barcode.rawValue?.let { raw ->
                                                if (raw.isNotBlank()) {
                                                    onQrScanned(raw)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose { try { executor.shutdown() } catch (_: Exception) {} }
    }
}

@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "scan")
    val scanOffset by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanLine"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val boxSize = minOf(w, h) * 0.68f
            val left = (w - boxSize) / 2f
            val top = (h - boxSize) / 2f - 32.dp.toPx()

            // dim background
            drawRect(color = Color.Black.copy(alpha = 0.55f))

            // clear cutout (punch hole)
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // corner brackets
            val stroke = 4.dp.toPx()
            val cornerLen = 32.dp.toPx()
            val radius = 24.dp.toPx()
            val bracketColor = PrimaryIndigo
            // top-left
            drawArc(bracketColor, 180f, 90f, false, Offset(left, top), Size(radius * 2, radius * 2), style = Stroke(stroke))
            drawLine(bracketColor, Offset(left + radius, top), Offset(left + cornerLen, top), stroke)
            drawLine(bracketColor, Offset(left, top + radius), Offset(left, top + cornerLen), stroke)
            // top-right
            drawArc(bracketColor, 270f, 90f, false, Offset(left + boxSize - radius * 2, top), Size(radius * 2, radius * 2), style = Stroke(stroke))
            drawLine(bracketColor, Offset(left + boxSize - radius, top), Offset(left + boxSize - cornerLen, top), stroke)
            drawLine(bracketColor, Offset(left + boxSize, top + radius), Offset(left + boxSize, top + cornerLen), stroke)
            // bottom-left
            drawArc(bracketColor, 90f, 90f, false, Offset(left, top + boxSize - radius * 2), Size(radius * 2, radius * 2), style = Stroke(stroke))
            drawLine(bracketColor, Offset(left + radius, top + boxSize), Offset(left + cornerLen, top + boxSize), stroke)
            drawLine(bracketColor, Offset(left, top + boxSize - radius), Offset(left, top + boxSize - cornerLen), stroke)
            // bottom-right
            drawArc(bracketColor, 0f, 90f, false, Offset(left + boxSize - radius * 2, top + boxSize - radius * 2), Size(radius * 2, radius * 2), style = Stroke(stroke))
            drawLine(bracketColor, Offset(left + boxSize - radius, top + boxSize), Offset(left + boxSize - cornerLen, top + boxSize), stroke)
            drawLine(bracketColor, Offset(left + boxSize, top + boxSize - radius), Offset(left + boxSize, top + boxSize - cornerLen), stroke)

            // animated scan line
            val lineY = top + 16.dp.toPx() + (boxSize - 32.dp.toPx()) * scanOffset
            drawLine(
                color = PrimaryIndigoLight.copy(alpha = 0.9f),
                start = Offset(left + 16.dp.toPx(), lineY),
                end = Offset(left + boxSize - 16.dp.toPx(), lineY),
                strokeWidth = 2.dp.toPx()
            )
            // glow under scan line
            drawLine(
                color = PrimaryIndigo.copy(alpha = 0.15f),
                start = Offset(left + 8.dp.toPx(), lineY),
                end = Offset(left + boxSize - 8.dp.toPx(), lineY),
                strokeWidth = 18.dp.toPx()
            )
        }
    }
}
