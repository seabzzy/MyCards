package com.mycards.wallet.ui.screens

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycards.wallet.data.CardFormat
import com.mycards.wallet.data.CardText
import com.mycards.wallet.data.LoyaltyCard
import com.mycards.wallet.data.inWalletOrder
import com.mycards.wallet.ui.components.ActionSheet
import com.mycards.wallet.ui.components.ConfirmDeleteDialog
import com.mycards.wallet.ui.components.RenameDialog
import com.mycards.wallet.ui.components.ReorderDialog
import com.mycards.wallet.ui.components.ReorderItem
import com.mycards.wallet.ui.components.SheetAction
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.components.CardStack
import com.mycards.wallet.ui.components.CircleButton
import com.mycards.wallet.ui.components.Glyph
import com.mycards.wallet.ui.components.GlyphIcon
import com.mycards.wallet.ui.components.PillButton
import com.mycards.wallet.ui.components.WalletCard
import com.mycards.wallet.ui.components.bounceClick
import com.mycards.wallet.ui.theme.LocalOrigin
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpen: (LoyaltyCard) -> Unit,
    onScan: () -> Unit,
    onPickImage: () -> Unit,
    onManual: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalOrigin.current
    val cards by vm.cards.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var actionCard by remember { mutableStateOf<LoyaltyCard?>(null) }
    var renameTarget by remember { mutableStateOf<LoyaltyCard?>(null) }
    var deleteTarget by remember { mutableStateOf<LoyaltyCard?>(null) }
    var reorder by remember { mutableStateOf(false) }

    val shown = remember(cards, query) {
        cards.inWalletOrder()
            .filter { query.isBlank() || it.name.contains(query.trim(), true) || it.number.contains(query.trim()) }
    }

    Box(Modifier.fillMaxSize().background(p.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Glyph.Back, onBack)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Карты", color = p.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (cards.isEmpty()) "Скидочные и бонусные карты" else CardText.cardsCount(cards.size),
                        color = p.textSecondary, fontSize = 14.sp,
                    )
                }
                Box {
                    CircleButton(Glyph.More, { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Настройки") }, onClick = { menu = false; onSettings() })
                        DropdownMenuItem(text = { Text("Резервная копия в файл") }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text("Восстановить из файла") }, onClick = { menu = false; onImport() })
                    }
                }
            }

            if (cards.size > 3) {
                Spacer(Modifier.height(16.dp))
                SearchPill(query) { query = it }
            }
            Spacer(Modifier.height(22.dp))

            when {
                cards.isEmpty() -> EmptyState { showAdd = true }
                shown.isEmpty() -> Text(
                    "Ничего не найдено",
                    color = p.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    textAlign = TextAlign.Center,
                )
                else -> CardStack(shown, onOpen, onLongClick = { actionCard = it })
            }
            Spacer(Modifier.navigationBarsPadding().height(130.dp))
        }

        // мягкое затухание контента под плавающей кнопкой
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, p.background))),
        )
        if (cards.isNotEmpty()) {
            PillButton(
                "Добавить карту",
                onClick = { showAdd = true },
                glyph = Glyph.Plus,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            )
        }
    }

    if (showAdd) {
        AddCardSheet(
            onDismiss = { showAdd = false },
            onScan = onScan,
            onPickImage = onPickImage,
            onManual = onManual,
        )
    }

    actionCard?.let { card ->
        val pinnedCount = cards.count { it.pinned }
        ActionSheet(
            title = card.name,
            actions = buildList {
                add(SheetAction(if (card.pinned) "Открепить" else "Закрепить", Glyph.Pin) {
                    vm.setCardPinned(card.id, !card.pinned)
                })
                if (card.pinned && pinnedCount >= 2) {
                    add(SheetAction("Изменить порядок закреплённых", Glyph.Drag) { reorder = true })
                }
                add(SheetAction("Переименовать", Glyph.Edit) { renameTarget = card })
                add(SheetAction("Удалить", Glyph.Trash, danger = true) { deleteTarget = card })
            },
            onDismiss = { actionCard = null },
        )
    }
    renameTarget?.let { card ->
        RenameDialog(
            current = card.name,
            onDismiss = { renameTarget = null },
            onSave = { vm.renameCard(card.id, it); renameTarget = null },
        )
    }
    deleteTarget?.let { card ->
        ConfirmDeleteDialog(
            title = "Удалить карту?",
            text = "Карта «${card.name}» будет удалена с телефона.",
            onConfirm = { vm.delete(card.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
    if (reorder) {
        ReorderDialog(
            title = "Порядок закреплённых",
            items = cards.filter { it.pinned }.sortedBy { it.pinOrder }
                .map { ReorderItem(it.id, it.name, it.colorStart, it.colorEnd) },
            onDismiss = { reorder = false },
            onSave = { vm.reorderPinnedCards(it); reorder = false },
        )
    }
}

@Composable
private fun SearchPill(value: String, onChange: (String) -> Unit) {
    val p = LocalOrigin.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(p.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Search, p.textSecondary, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Поиск карты", color = p.textSecondary, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 16.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            GlyphIcon(Glyph.Close, p.textSecondary, Modifier.bounceClick { onChange("") }, size = 20.dp)
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val p = LocalOrigin.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(0.8f)) {
            WalletCard(
                "", "", CardFormat.QR_CODE,
                p.accent.copy(alpha = 0.25f), p.accent.copy(alpha = 0.15f),
                Modifier.offset(y = (-18).dp).padding(horizontal = 18.dp),
            )
            WalletCard("Ваша карта", "000000001234", CardFormat.EAN_13, Color(0xFF5B7CFF), Color(0xFF3448D6))
        }
        Spacer(Modifier.height(36.dp))
        Text("Все карты в одном месте", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Отсканируйте штрихкод или QR-код камерой\nили выберите скриншот — магазин определится сам",
            color = p.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PillButton("Добавить карту", onAdd, glyph = Glyph.Plus)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardSheet(
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onPickImage: () -> Unit,
    onManual: () -> Unit,
) {
    val p = LocalOrigin.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun pick(action: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); action() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = p.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Добавить карту",
                color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )
            AddOption(Glyph.Camera, "Сканировать камерой", "Наведите на штрихкод или QR-код карты") { pick(onScan) }
            AddOption(Glyph.Image, "Из скриншота или фото", "Выберите изображение из галереи") { pick(onPickImage) }
            AddOption(Glyph.Keyboard, "Ввести вручную", "Если код не читается — введите номер") { pick(onManual) }
        }
    }
}

@Composable
private fun AddOption(glyph: Glyph, title: String, subtitle: String, onClick: () -> Unit) {
    val p = LocalOrigin.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.surface)
            .bounceClick(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(p.accentSoft),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(glyph, p.accent) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = p.textSecondary, fontSize = 13.sp)
        }
    }
}
