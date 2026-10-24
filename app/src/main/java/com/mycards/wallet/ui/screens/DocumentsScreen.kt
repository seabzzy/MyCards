package com.mycards.wallet.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycards.wallet.data.PdfDocument
import com.mycards.wallet.data.inListOrder
import com.mycards.wallet.ui.components.ConfirmDeleteDialog
import com.mycards.wallet.ui.components.RenameDialog
import com.mycards.wallet.ui.components.SheetAction
import com.mycards.wallet.ui.components.ItemPopupMenu
import com.mycards.wallet.ui.components.longPressMenuAndDrag
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.components.Glyph
import com.mycards.wallet.ui.components.GlyphIcon
import com.mycards.wallet.ui.components.PillButton
import com.mycards.wallet.ui.components.TopBar
import com.mycards.wallet.ui.components.bounceClick
import com.mycards.wallet.ui.theme.LocalOrigin
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Список PDF-документов и добавление нового файла. */
@Composable
fun DocumentsScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (PdfDocument) -> Unit) {
    val p = LocalOrigin.current
    val docs by vm.documents.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var menuAt by remember { mutableStateOf(IntOffset.Zero) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }
    val live = remember { mutableStateListOf<String>() }
    var renameTarget by remember { mutableStateOf<PdfDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<PdfDocument?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                adding = true
                vm.addDocument(uri)
                adding = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar("Документы", onBack)

        if (docs.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(p.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.Document, p.accent, size = 36.dp) }
                Spacer(Modifier.height(18.dp))
                Text("Документов пока нет", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Добавьте PDF-файл — он откроется прямо в приложении",
                    color = p.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(p.surface),
                    ) {
                        val sorted = docs.inListOrder()
                        val pinnedCount = sorted.count { it.pinned }
                        val shownDocs = if (draggingId != null) {
                            live.mapNotNull { id -> sorted.firstOrNull { it.id == id } }
                        } else sorted
                        shownDocs.forEachIndexed { i, doc ->
                            key(doc.id) {
                                val dragging = doc.id == draggingId
                                Column(
                                    Modifier
                                        .zIndex(if (dragging) 1f else 0f)
                                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                                        .background(if (dragging) p.surfaceHigh else p.surface)
                                        .onSizeChanged { if (!dragging) rowHeight = it.height.toFloat().coerceAtLeast(1f) },
                                ) {
                                    if (i > 0) HorizontalDivider(Modifier.padding(start = 76.dp), color = p.divider)
                                    Box {
                                        DocumentRow(
                                            doc,
                                            gesture = Modifier.longPressMenuAndDrag(
                                                key = doc.id,
                                                canDrag = doc.pinned && pinnedCount >= 2,
                                                onLongPress = { pos ->
                                                    menuAt = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                                    menuFor = doc.id
                                                },
                                                onDragStart = {
                                                    menuFor = null
                                                    live.clear()
                                                    live.addAll(sorted.map { it.id })
                                                    draggingId = doc.id
                                                    dragOffset = 0f
                                                },
                                                onDrag = { dy ->
                                                    dragOffset += dy
                                                    val from = live.indexOf(doc.id)
                                                    if (dragOffset > rowHeight / 2 && from in 0 until pinnedCount - 1) {
                                                        live.add(from + 1, live.removeAt(from))
                                                        dragOffset -= rowHeight
                                                    } else if (dragOffset < -rowHeight / 2 && from > 0) {
                                                        live.add(from - 1, live.removeAt(from))
                                                        dragOffset += rowHeight
                                                    }
                                                },
                                                onDragEnd = {
                                                    vm.reorderPinnedDocuments(live.take(pinnedCount))
                                                    draggingId = null
                                                    dragOffset = 0f
                                                },
                                            ),
                                        ) { onOpen(doc) }
                                        Box(Modifier.offset { menuAt }) {
                                            ItemPopupMenu(
                                                expanded = menuFor == doc.id,
                                                actions = listOf(
                                                    SheetAction(if (doc.pinned) "Открепить" else "Закрепить", Glyph.Pin) {
                                                        vm.setDocumentPinned(doc.id, !doc.pinned)
                                                    },
                                                    SheetAction("Переименовать", Glyph.Edit) { renameTarget = doc },
                                                    SheetAction("Удалить", Glyph.Trash, danger = true) { deleteTarget = doc },
                                                ),
                                                onDismiss = { menuFor = null },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            PillButton(
                if (adding) "Добавляю…" else "Добавить PDF",
                onClick = { picker.launch(arrayOf("application/pdf", "application/octet-stream")) },
                enabled = !adding,
                glyph = Glyph.Plus,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    renameTarget?.let { doc ->
        RenameDialog(
            current = doc.title,
            onDismiss = { renameTarget = null },
            onSave = { vm.renameDocument(doc.id, it); renameTarget = null },
        )
    }
    deleteTarget?.let { doc ->
        ConfirmDeleteDialog(
            title = "Удалить документ?",
            text = "«${doc.title}» будет удалён с телефона. Восстановить его будет нельзя.",
            onConfirm = { vm.deleteDocument(doc.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }

}

@Composable
private fun DocumentRow(doc: PdfDocument, gesture: Modifier, onClick: () -> Unit) {
    val p = LocalOrigin.current
    val date = remember(doc.addedAt) { SimpleDateFormat("d MMM yyyy", Locale("ru")).format(doc.addedAt) }
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .then(gesture)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(p.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Text("PDF", color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (doc.pinned) {
                    GlyphIcon(Glyph.Pin, p.accent, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    doc.title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${doc.pageCount} стр. · ${sizeText(doc.sizeBytes)} · $date",
                color = p.textSecondary, fontSize = 13.sp,
            )
        }
    }
}

private fun sizeText(bytes: Long): String =
    if (bytes < 1024 * 1024) "${(bytes / 1024).coerceAtLeast(1)} КБ"
    else String.format(Locale("ru"), "%.1f МБ", bytes / 1024.0 / 1024.0)
