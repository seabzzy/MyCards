package com.mycards.wallet.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.mycards.wallet.data.ScanResult
import com.mycards.wallet.scan.MlKit
import com.mycards.wallet.ui.components.CircleButton
import com.mycards.wallet.ui.components.Glyph
import com.mycards.wallet.ui.components.PillButton
import com.mycards.wallet.ui.theme.LocalOrigin
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    onResult: (ScanResult) -> Unit,
    onClose: () -> Unit,
    onPickImage: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalOrigin.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var torch by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraView(torch = torch, onResult = onResult)
            ScanOverlay(p.accent)
        } else if (asked) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Нужен доступ к камере",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Разрешите доступ, чтобы сканировать карты, или добавьте карту со скриншота.",
                    color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                PillButton("Разрешить", { permission.launch(Manifest.permission.CAMERA) })
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val glass = Color.White.copy(alpha = 0.16f)
            CircleButton(Glyph.Close, onClose, background = glass, tint = Color.White)
            Text(
                "Сканирование",
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            CircleButton(
                Glyph.Flash, { torch = !torch },
                background = if (torch) Color.White else glass,
                tint = if (torch) Color.Black else Color.White,
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Поместите штрихкод или QR-код в рамку.\nМагазин определится автоматически.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            PillButton(
                "Выбрать скриншот", onPickImage, glyph = Glyph.Image,
                color = Color.White.copy(alpha = 0.18f),
            )
        }
    }
}

@Composable
private fun CameraView(torch: Boolean, onResult: (ScanResult) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, CardAnalyzer { currentOnResult(it) })
            try {
                p.unbindAll()
                camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/**
 * Анализ кадров: код засчитывается, когда одинаково прочитан два кадра подряд
 * (защита от ошибочного чтения), затем с того же кадра считывается текст —
 * для определения магазина по надписи.
 */
private class CardAnalyzer(private val onResult: (ScanResult) -> Unit) : ImageAnalysis.Analyzer {
    @Volatile private var done = false
    private var lastValue: String? = null
    private var hits = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (done || media == null) {
            proxy.close(); return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        MlKit.barcodes.process(image)
            .addOnSuccessListener { codes ->
                val best = MlKit.pickBest(codes)
                if (best == null) {
                    hits = 0
                    proxy.close()
                    return@addOnSuccessListener
                }
                if (best.first == lastValue) hits++ else {
                    lastValue = best.first; hits = 1
                }
                if (hits >= 2 && !done) {
                    done = true
                    MlKit.text.process(image).addOnCompleteListener { t ->
                        val text = if (t.isSuccessful) t.result.text else ""
                        proxy.close()
                        onResult(ScanResult(best.first, best.second, text))
                    }
                } else {
                    proxy.close()
                }
            }
            .addOnFailureListener { proxy.close() }
    }
}

@Composable
private fun ScanOverlay(accent: Color) {
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "line",
    )
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val w = size.width * 0.84f
        val h = w / 1.586f
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2.2f
        val radius = CornerRadius(28.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, Offset(left, top), GSize(w, h), radius, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(left, top), GSize(w, h), radius, style = Stroke(2.5.dp.toPx()))
        val pad = 18.dp.toPx()
        val y = top + pad + (h - 2 * pad) * progress
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, accent, Color.White, accent, Color.Transparent),
                startX = left + pad, endX = left + w - pad,
            ),
            start = Offset(left + pad, y),
            end = Offset(left + w - pad, y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
