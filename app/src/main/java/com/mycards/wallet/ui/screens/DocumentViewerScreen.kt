package com.mycards.wallet.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycards.wallet.data.PdfDocument
import com.mycards.wallet.pdf.PdfSession
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.components.CircleButton
import com.mycards.wallet.ui.components.Glyph
import com.mycards.wallet.ui.components.GlyphIcon
import com.mycards.wallet.ui.components.TopBar
import com.mycards.wallet.ui.theme.LocalOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

/** Просмотр PDF прямо в приложении: прокрутка, масштаб щипком, номер страницы, переименование и удаление. */
@Composable
fun DocumentViewerScreen(vm: AppViewModel, docId: String, onBack: () -> Unit) {
    val p = LocalOrigin.current
    val docs by vm.documents.collectAsStateWithLifecycle()
    val doc: PdfDocument? = docs.firstOrNull { it.id == docId }
    val file = remember(docId) { vm.documentFile(docId) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val session by produceState<Result<PdfSession>?>(null, file) {
        value = if (file == null) Result.failure(IOException("Файл не найден"))
        else withContext(Dispatchers.IO) { runCatching { PdfSession.open(file) } }
    }
    // Закрываем именно ту сессию, которая была открыта в этом эффекте
    // (раньше здесь читалось уже новое значение, и только что открытый документ сразу закрывался).
    val openedSession = session
    DisposableEffect(openedSession) {
        onDispose { openedSession?.getOrNull()?.close() }
    }

    // Документ удалён — возвращаемся к списку.
    if (doc == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(doc.title, onBack) {
            Box {
                CircleButton(Glyph.More, { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = { GlyphIcon(Glyph.Edit, p.text, size = 20.dp) },
                        onClick = { menu = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить", color = p.danger) },
                        leadingIcon = { GlyphIcon(Glyph.Trash, p.danger, size = 20.dp) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        }

        when (val current = session) {
            null -> Text(
                "Открываю документ…", color = p.textSecondary, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                textAlign = TextAlign.Center,
            )
            else -> if (current.isFailure) {
                Text(
                    "Не удалось открыть документ: ${current.exceptionOrNull()?.message ?: "неизвестная ошибка"}. " +
                        "Возможно, он защищён паролем или повреждён.",
                    color = p.warning, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                PdfPages(current.getOrThrow())
            }
        }
    }

    if (renaming) {
        RenameDialog(
            current = doc.title,
            onDismiss = { renaming = false },
            onSave = { title -> vm.renameDocument(doc.id, title); renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = p.surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Удалить документ?") },
            text = { Text("«${doc.title}» будет удалён с телефона. Восстановить его будет нельзя.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteDocument(doc.id) }) {
                    Text("Удалить", color = p.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = p.text) }
            },
        )
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val p = LocalOrigin.current
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Переименовать") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()) }) {
                Text("Сохранить", color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = p.text) }
        },
    )
}

@Composable
private fun PdfPages(session: PdfSession) {
    val p = LocalOrigin.current
    var zoom by remember { mutableFloatStateOf(1f) }
    val listState = rememberLazyListState()
    // Масштаб округляем до шагов 0.25, чтобы не перерисовывать страницы на каждом движении пальцев.
    val scale = (zoom * 4).roundToInt() / 4f

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            val widthPx = min((screenPx * scale).roundToInt(), MAX_RENDER_WIDTH_PX)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pinchZoom { factor -> zoom = (zoom * factor).coerceIn(1f, 4f) },
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(session.pageCount) { index ->
                    PdfPage(session, index, widthPx, zoomed = scale > 1f)
                }
            }
        }

        Text(
            "${listState.firstVisibleItemIndex + 1} / ${session.pageCount}",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PdfPage(session: PdfSession, index: Int, widthPx: Int, zoomed: Boolean) {
    val density = LocalDensity.current
    val state by produceState<Result<ImageBitmap>?>(null, session, index, widthPx) {
        value = withContext(Dispatchers.IO) {
            runCatching { session.render(index, widthPx).asImageBitmap() }
        }
    }
    val pageWidth = with(density) { widthPx.toDp() }
    val scroll = rememberScrollState()

    Box(
        Modifier
            .fillMaxWidth()
            .then(if (zoomed) Modifier.horizontalScroll(scroll) else Modifier),
    ) {
        when (val result = state) {
            // Пока страница рисуется — белый лист A4 нужной ширины.
            null -> Box(
                Modifier
                    .width(pageWidth)
                    .height(pageWidth * 1.414f)
                    .shadow(3.dp)
                    .background(Color.White),
            )
            else -> if (result.isFailure) {
                Box(
                    Modifier
                        .width(pageWidth)
                        .height(160.dp)
                        .background(Color.White)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Страница ${index + 1} не отрисовалась: ${result.exceptionOrNull()?.message ?: "ошибка"}",
                        color = Color(0xFF8B8E98), fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            } else {
                val bitmap = result.getOrThrow()
                // Размер задаём точно по растру: без этого страница может схлопнуться в нулевую высоту.
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(pageWidth, with(density) { bitmap.height.toDp() })
                        .shadow(3.dp)
                        .background(Color.White),
                )
            }
        }
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
