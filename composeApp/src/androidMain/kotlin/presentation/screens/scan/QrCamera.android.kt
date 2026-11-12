package io.github.vrcmteam.vrcm.presentation.screens.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import io.github.vrcmteam.vrcm.presentation.extensions.findActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.LuminanceSource
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.Decoder
import java.io.ByteArrayOutputStream

internal actual val qrScanningSupported = true

@Composable
internal actual fun QrScannerAppearance() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (controller != null) {
                controller.isAppearanceLightStatusBars = previousStatus == true
                controller.isAppearanceLightNavigationBars = previousNavigation == true
            }
        }
    }
}

@Composable
internal actual fun QrCamera(modifier: Modifier, onScanned: (String) -> Unit, onFailure: () -> Unit,
    onMyCode: () -> Unit, onAlbum: () -> Unit, onCandidates: (QrScanBatch) -> Unit,
    controls: @Composable () -> Unit) {
    Box(modifier) {
        QrCameraPreview(Modifier.fillMaxSize(), onScanned, onFailure, onCandidates)
        controls()
    }
}

@Composable
private fun QrCameraPreview(modifier: Modifier, onScanned: (String) -> Unit, onFailure: () -> Unit,
    onCandidates: (QrScanBatch) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resultCallback by rememberUpdatedState(onScanned)
    val failureCallback by rememberUpdatedState(onFailure)
    val candidatesCallback by rememberUpdatedState(onCandidates)
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) failureCallback()
    }
    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }
    if (!granted) return
    val decoder = remember(context) { MultiQrCameraDecoder() }
    val view = remember(context) {
        object : BarcodeView(context) {
            override fun calculateFramingRect(container: Rect, surface: Rect): Rect =
                Rect(container).apply { intersect(surface) }
        }.apply {
            decoderFactory = com.journeyapps.barcodescanner.DecoderFactory { decoder }
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
    DisposableEffect(view, lifecycle) {
        var active = true
        val listener = object : CameraPreview.StateListener {
            override fun previewSized() = Unit
            override fun previewStarted() = Unit
            override fun previewStopped() = Unit
            override fun cameraClosed() = Unit
            override fun cameraError(error: Exception) { if (active) failureCallback() }
        }
        view.addStateListener(listener)
        view.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (!active) return
                view.pause()
                val batch = result.result.resultMetadata?.get(ResultMetadataType.OTHER) as? QrScanBatch
                if (batch != null) candidatesCallback(batch) else resultCallback(result.text)
            }
        })
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.resume()
                Lifecycle.Event.ON_PAUSE -> view.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume()
        onDispose {
            active = false
            lifecycle.removeObserver(observer)
            view.stopDecoding()
            view.pause()
        }
    }
}

/** Freezes the same upright crop used by ZXing, so selection dots cannot drift from the image. */
private class MultiQrCameraDecoder : Decoder(QRCodeMultiReader()) {
    override fun decode(source: LuminanceSource): Result? {
        val results = decodeQrSource(source)
        val result = results.firstOrNull() ?: return null
        if (results.size > 1) {
            val luminance = source.matrix
            val pixels = IntArray(source.width * source.height) { index ->
                val gray = luminance[index].toInt() and 0xff
                (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
            val bitmap = Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
            try {
                val bytes = ByteArrayOutputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    stream.toByteArray()
                }
                // Bind the image to this result; another camera frame cannot replace its candidates.
                result.putMetadata(ResultMetadataType.OTHER, QrScanBatch(results.map { it.detection(source.width, source.height) },
                    bytes, source.width, source.height))
            } finally {
                bitmap.recycle()
            }
        }
        return result
    }
}
