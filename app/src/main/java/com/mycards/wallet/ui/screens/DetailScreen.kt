package com.mycards.wallet.ui.screens

import android.content.ClipData
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycards.wallet.data.CardText
import com.mycards.wallet.data.LoyaltyCard
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.components.CircleButton
import com.mycards.wallet.ui.components.Glyph
import com.mycards.wallet.ui.components.GlyphIcon
import com.mycards.wallet.ui.components.MaxBrightness
import com.mycards.wallet.ui.components.TopBar
import com.mycards.wallet.ui.components.WalletCard
import com.mycards.wallet.ui.components.bounceClick
import com.mycards.wallet.ui.theme.LocalOrigin
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    vm: AppViewModel,
    cardId: String,
    onBack: () -> Unit,
    onEdit: (LoyaltyCard) -> Unit,
) {
    val p = LocalOrigin.current
    val cards by vm.cards.collectAsStateWithLifecycle()
    val card = cards.firstOrNull { it.id == cardId }
    if (card == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    LaunchedEffect(cardId) { vm.markUsed(cardId) }
    MaxBrightness()

    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(card.name, onBack) {
            Box {
                CircleButton(Glyph.More, { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Изменить") },
                        leadingIcon = { GlyphIcon(Glyph.Edit, p.text, size = 20.dp) },
                        onClick = { menu = false; onEdit(card) },
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить", color = p.danger) },
                        leadingIcon = { GlyphIcon(Glyph.Trash, p.danger, size = 20.dp) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            WalletCard(card)
            Spacer(Modifier.height(20.dp))

            BarcodePreview(card.number, card.format)

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(p.surface)
                    .bounceClick {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Номер карты", card.number)))
                        }
                        copied = true
                    }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Номер карты", color = p.textSecondary, fontSize = 12.sp)
                    Text(
                        CardText.pretty(card.number),
                        color = p.text, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.width(10.dp))
                GlyphIcon(if (copied) Glyph.Check else Glyph.Copy, if (copied) p.success else p.accent)
            }

            if (card.note.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.surface)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text("Заметка", color = p.textSecondary, fontSize = 12.sp)
                    Text(card.note, color = p.text, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Sun, p.textSecondary, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Яркость увеличена — покажите код кассиру",
                    color = p.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = p.surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Удалить карту?") },
            text = { Text("Карта «${card.name}» будет удалена с телефона.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(card.id) // экран закроется сам, когда карта исчезнет из списка
                }){ Text("Удалить", color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = p.text) }
            },
        )
    }
}
