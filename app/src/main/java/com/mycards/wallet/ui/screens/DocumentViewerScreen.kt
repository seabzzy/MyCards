package com.mycards.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycards.wallet.data.PdfDocument
import com.mycards.wallet.pdf.PdfSession
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.components.TopBar
import com.mycards.wallet.ui.theme.LocalOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

/** Просмотр PDF прямо в приложении: прокрутка, масштаб щипком, номер страницы. */
@Composable
fun DocumentViewerScreen(vm: AppViewModel, docId: String, onBack: () -> Unit) {
    val p = LocalOrigin.current
    val docs by vm.documents.collectAsStateWithLifecycle()
    val doc: PdfDocument? = docs.firstOrNull { it.id == docId }
    val file = remember(docId) { vm.documentFile(docId) }

    val session by produceState<Result<PdfSession>?>(null, file) {
        value = if (file == null) Result.failure(IOException("Файл не найден"))
        else withContext(Dispatchers.IO) { runCatching { PdfSession.open(file) } }
    }
    androidx.compose.runtime.DisposableEffect(session) {
        onDispose { session?.getOrNull()?.close() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(doc?.title ?: "Документ", onBack)
        val current = session
        when {
            current == null -> Text(
                "Открываю документ…", color = p.textSecondary, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                textAlign = TextAlign.Center,
            )
            current.isFailure -> Text(
                "Не удалось открыть документ. Возможно, он защищён паролем или повреждён.",
                color = p.warning, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                textAlign = TextAlign.Center,
            )
            else -> PdfPages(current.getOrThrow())
        }
    }
}

@Composable
private fun PdfPages(session: PdfSession) {
    val p = LocalOrigin.current
    var zoom by remember { mutableFloatStateOf(1f) }
    val listState = rememberLazyListState()
    // Масштаб округляем до шагов 0.25, чтобы не перерисовывать страницы на каждом движении пальцев.
    val scale = (zoom * 4).roundToInt() / 4f

    Box(Modifier.fillMaxSize()) {
        BoxWithScreenWidth { screenPx ->
            val widthPx = min((screenPx * scale).roundToInt(), MAX_RENDER_WIDTH_PX)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pinchZoom { factor -> zoom = (zoom * factor).coerceIn(1f, 4f) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(session.pageCount) { index ->
                    PdfPage(session, index, widthPx, zoomed = scale > 1f)
                }
            }
        }

        Text(
            "${listState.firstVisibleItemIndex + 1} / ${session.pageCount}",
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PdfPage(session: PdfSession, index: Int, widthPx: Int, zoomed: Boolean) {
    val density = LocalDensity.current
    val bitmap by produceState<ImageBitmap?>(null, session, index, widthPx) {
        value = withContext(Dispatchers.IO) {
            runCatching { session.render(index, widthPx).asImageBitmap() }.getOrNull()
        }
    }
    val widthDp = with(density) { widthPx.toDp() }
    val scroll = rememberScrollState()
    val pageModifier = Modifier
        .width(widthDp)
        .shadow(3.dp)
        .background(androidx.compose.ui.graphics.Color.White)

    Box(
        Modifier
            .fillMaxWidth()
            .then(if (zoomed) Modifier.horizontalScroll(scroll) else Modifier),
    ) {
        val bm = bitmap
        if (bm == null) {
            // Пока страница рисуется — пустой лист A4 той же ширины.
            Box(pageModifier.aspectRatio(1f / 1.414f))
        } else {
            Image(
                bitmap = bm,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = pageModifier,
            )
        }
    }
}

/** Даёт ширину экрана в пикселях, чтобы рисовать страницы в нужном разрешении. */
@Composable
private fun BoxWithScreenWidth(content: @Composable (Int) -> Unit) {
    val density = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        content(with(density) { maxWidth.roundToPx() })
    }
}

/** Масштабирование щипком. Срабатывает только при двух пальцах, прокрутка одним пальцем не мешает. */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                val factor = event.calculateZoom()
                if (factor != 1f) onZoom(factor)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private const val MAX_RENDER_WIDTH_PX = 1600
