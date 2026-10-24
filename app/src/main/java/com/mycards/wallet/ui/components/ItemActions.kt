package com.mycards.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.mycards.wallet.ui.theme.LocalOrigin

/** Пункт меню долгого нажатия. */
class SheetAction(
    val label: String,
    val glyph: Glyph,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/** Меню долгого нажатия в стиле OriginOS: шторка снизу со списком действий. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val p = LocalOrigin.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
            )
            GroupBlock {
                actions.forEachIndexed { i, action ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = p.divider)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .bounceClick { onDismiss(); action.onClick() }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphIcon(action.glyph, if (action.danger) p.danger else p.accent, size = 22.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(action.label, color = if (action.danger) p.danger else p.text, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
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
fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalOrigin.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить", color = p.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = p.text) } },
    )
}

/** Элемент для окна изменения порядка. Цвета — градиент карты; для документов null. */
class ReorderItem(val id: String, val title: String, val colorStart: Int? = null, val colorEnd: Int? = null)

private val ROW_HEIGHT = 60.dp

/** Изменение порядка закреплённых: перетаскивание пальцем за значок «≡» справа. */
@Composable
fun ReorderDialog(
    title: String,
    items: List<ReorderItem>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val p = LocalOrigin.current
    val list = remember { items.toMutableStateList() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(p.background)
                .padding(16.dp),
        ) {
            Text(
                title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                "Перетащите за значок справа", color = p.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 12.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(p.surface),
            ) {
                list.forEach { item ->
                    key(item.id) {
                        val dragging = item.id == draggingId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT)
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                                .background(if (dragging) p.surfaceHigh else p.surface)
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val start = item.colorStart
                            val end = item.colorEnd
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (start != null && end != null) Brush.linearGradient(listOf(Color(start), Color(end)))
                                        else SolidColor(p.accentSoft)
                                    ),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                item.title, color = p.text, fontSize = 16.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .pointerInput(item.id) {
                                        detectDragGestures(
                                            onDragStart = { draggingId = item.id; dragOffset = 0f },
                                            onDragEnd = { draggingId = null; dragOffset = 0f },
                                            onDragCancel = { draggingId = null; dragOffset = 0f },
                                        ) { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val from = list.indexOfFirst { it.id == item.id }
                                            if (dragOffset > rowPx / 2 && from in 0 until list.lastIndex) {
                                                list.add(from + 1, list.removeAt(from))
                                                dragOffset -= rowPx
                                            } else if (dragOffset < -rowPx / 2 && from > 0) {
                                                list.add(from - 1, list.removeAt(from))
                                                dragOffset += rowPx
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { GlyphIcon(Glyph.Drag, p.textSecondary, size = 22.dp) }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Отмена", color = p.text) }
                TextButton(onClick = { onSave(list.map { it.id }) }) { Text("Готово", color = p.accent) }
            }
        }
    }
}
