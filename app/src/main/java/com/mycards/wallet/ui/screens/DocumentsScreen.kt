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
import com.mycards.wallet.ui.components.ActionSheet
import com.mycards.wallet.ui.components.ConfirmDeleteDialog
import com.mycards.wallet.ui.components.RenameDialog
import com.mycards.wallet.ui.components.ReorderDialog
import com.mycards.wallet.ui.components.ReorderItem
import com.mycards.wallet.ui.components.SheetAction
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
    var actionDoc by remember { mutableStateOf<PdfDocument?>(null) }
    var renameTarget by remember { mutableStateOf<PdfDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<PdfDocument?>(null) }
    var reorder by remember { mutableStateOf(false) }

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
                        docs.inListOrder().forEachIndexed { i, doc ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 76.dp), color = p.divider)
                            DocumentRow(doc, onLongClick = { actionDoc = doc }) { onOpen(doc) }
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

    actionDoc?.let { doc ->
        val pinnedCount = docs.count { it.pinned }
        ActionSheet(
            title = doc.title,
            actions = buildList {
                add(SheetAction(if (doc.pinned) "Открепить" else "Закрепить", Glyph.Pin) {
                    vm.setDocumentPinned(doc.id, !doc.pinned)
                })
                if (doc.pinned && pinnedCount >= 2) {
                    add(SheetAction("Изменить порядок закреплённых", Glyph.Drag) { reorder = true })
                }
                add(SheetAction("Переименовать", Glyph.Edit) { renameTarget = doc })
                add(SheetAction("Удалить", Glyph.Trash, danger = true) { deleteTarget = doc })
            },
            onDismiss = { actionDoc = null },
        )
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
    if (reorder) {
        ReorderDialog(
            title = "Порядок закреплённых",
            items = docs.filter { it.pinned }.sortedBy { it.pinOrder }.map { ReorderItem(it.id, it.title) },
            onDismiss = { reorder = false },
            onSave = { vm.reorderPinnedDocuments(it); reorder = false },
        )
    }
}

@Composable
private fun DocumentRow(doc: PdfDocument, onLongClick: () -> Unit, onClick: () -> Unit) {
    val p = LocalOrigin.current
    val date = remember(doc.addedAt) { SimpleDateFormat("d MMM yyyy", Locale("ru")).format(doc.addedAt) }
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(onLongClick = onLongClick, onClick = onClick)
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
