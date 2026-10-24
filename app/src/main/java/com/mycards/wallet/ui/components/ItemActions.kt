package com.mycards.wallet.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycards.wallet.ui.theme.LocalOrigin
import kotlin.math.abs

/** Пункт меню долгого нажатия. */
class SheetAction(
    val label: String,
    val glyph: Glyph,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Небольшое всплывающее меню рядом с пальцем (вместо шторки снизу).
 * Размещается внутри элемента, в точке долгого нажатия.
 */
@Composable
fun ItemPopupMenu(expanded: Boolean, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val p = LocalOrigin.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = p.surface,
        shadowElevation = 12.dp,
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = {
                    Text(action.label, color = if (action.danger) p.danger else p.text, fontSize = 16.sp)
                },
                leadingIcon = {
                    GlyphIcon(action.glyph, if (action.danger) p.danger else p.accent, size = 20.dp)
                },
                onClick = { onDismiss(); action.onClick() },
            )
        }
    }
}

/**
 * Долгое нажатие и перетаскивание одним жестом, как на рабочем столе:
 *  • зажали и отпустили — [onLongPress] (показать меню) в точке касания;
 *  • зажали и повели — [onDragStart], затем [onDrag] со сдвигом по вертикали, по отпусканию [onDragEnd].
 * Перетаскивание работает, только если [canDrag]. Обычное касание и прокрутка списка не затрагиваются.
 */
fun Modifier.longPressMenuAndDrag(
    key: Any,
    canDrag: Boolean,
    onLongPress: (Offset) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit,
    /** Если задано — перетаскивание в любую сторону (для плитки), иначе только по вертикали. */
    onDragOffset: ((Offset) -> Unit)? = null,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val slop = with(LocalDensity.current) { 8.dp.toPx() }
    val dragAllowed by rememberUpdatedState(canDrag)
    val longPress by rememberUpdatedState(onLongPress)
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val moveOffset by rememberUpdatedState(onDragOffset)
    val end by rememberUpdatedState(onDragEnd)

    pointerInput(key) {
        var total = Offset.Zero
        var dragging = false
        detectDragGesturesAfterLongPress(
            onDragStart = { position ->
                total = Offset.Zero
                dragging = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                longPress(position)
            },
            onDragEnd = {
                if (dragging) end()
                dragging = false
            },
            onDragCancel = {
                if (dragging) end()
                dragging = false
            },
        ) { change, amount ->
            change.consume()
            if (!dragAllowed) return@detectDragGesturesAfterLongPress
            val free = moveOffset
            if (dragging) {
                if (free != null) free(amount) else move(amount.y)
            } else {
                total += amount
                val distance = if (free != null) total.getDistance() else abs(total.y)
                if (distance > slop) {
                    dragging = true
                    start()
                    if (free != null) free(total) else move(total.y)
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
