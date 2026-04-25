package com.lap.mobile.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.lap.mobile.R
import com.lap.mobile.qr.QrPayload
import com.lap.mobile.qr.QrPayloadClassifier
import com.lap.mobile.ui.theme.LapTheme
import androidx.compose.ui.tooling.preview.Preview as ComposePreview

private val ScreenPadding = 24.dp
private val SectionSpacing = 16.dp

/**
 * QR scanner Compose screen (T178). Camera permission lives in the
 * Composable (not hoisted) because the request flow is a single-button
 * affair driven entirely by [ActivityResultContracts.RequestPermission],
 * and threading it through a parent ViewModel would just be ceremony.
 *
 * The actual ML Kit + CameraX scan loop is [PENDING: device-required] —
 * `PreviewView` only renders on a real `LifecycleOwner` with a real
 * surface, and `BarcodeScanning.getClient()` reaches into Google Play
 * Services. JVM unit tests can't exercise either, which is why
 * [QrPayloadClassifier] (the pure-Kotlin string→[QrPayload] kernel) is
 * the part with explicit JVM coverage. Once the device-side scan
 * succeeds, the classifier handles the same payload the camera path
 * produces.
 *
 * Composition flow:
 *   1. On enter, sync `cameraPermissionGranted` from
 *      [ContextCompat.checkSelfPermission] (cheap, just queries the
 *      app's already-granted set).
 *   2. If not granted, [LaunchedEffect] auto-launches the request once
 *      so the user sees the system dialog rather than an empty screen.
 *   3. When granted, the camera AndroidView mounts and ML Kit's
 *      barcode scanner walks each frame. The first recognized payload
 *      flows through [QrPayloadClassifier] and out via [onScanResult].
 *
 * The screen does NOT debounce repeated scans of the same QR — that's
 * T179's job (pairing flow only redeems once and the second scan is a
 * no-op via idempotency). Locking debounce here would just hide
 * problems in T179.
 */
@Composable
fun QrScannerScreen(onScanResult: (QrPayload) -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
        }
    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Text(
                text = stringResource(R.string.qr_scanner_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.qr_scanner_help),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (permissionGranted) {
                CameraPreviewWithBarcodeAnalysis(
                    onScanResult = onScanResult,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                Text(stringResource(R.string.qr_scanner_permission_required))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.qr_scanner_grant_permission))
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithBarcodeAnalysis(onScanResult: (QrPayload) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview =
                    Preview.Builder().build().also { p ->
                        p.setSurfaceProvider(previewView.surfaceProvider)
                    }
                val scanner =
                    BarcodeScanning.getClient(
                        BarcodeScannerOptions
                            .Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build(),
                    )
                val analysis =
                    ImageAnalysis
                        .Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                    val media = proxy.image
                    if (media == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val frame = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner
                        .process(frame)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull()?.rawValue?.let { raw ->
                                onScanResult(QrPayloadClassifier.classify(raw))
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

@ComposePreview(showBackground = true)
@Composable
private fun QrScannerScreenPermissionDeniedPreview() {
    // The granted-state preview can't render a real CameraX PreviewView in
    // @Preview's snapshot environment, so this fixture only locks the
    // permission-denied UX — which is what the user actually sees on first
    // entry to the screen anyway.
    LapTheme {
        QrScannerScreen(onScanResult = {})
    }
}
