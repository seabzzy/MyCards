package com.mycards.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycards.wallet.data.StoreCandidate
import com.mycards.wallet.data.StoreDesign
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.components.Glyph
import com.mycards.wallet.ui.components.GlyphIcon
import com.mycards.wallet.ui.components.PillButton
import com.mycards.wallet.ui.components.bounceClick
import com.mycards.wallet.ui.theme.LocalOrigin
import kotlinx.coroutines.launch

/**
 * Пользователь пишет название магазина → поиск в интернете → выбор → приложение
 * скачивает логотип и цвета и возвращает готовое оформление (оно уже сохранено на телефоне).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSearchSheet(
    vm: AppViewModel,
    initialQuery: String,
    onDismiss: () -> Unit,
    onDesign: (StoreDesign) -> Unit,
) {
    val p = LocalOrigin.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<StoreCandidate>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var loadingDesign by remember { mutableStateOf<String?>(null) }
    var manualSite by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        scope.launch {
            results = vm.searchStores(q)
            searching = false
        }
    }

    fun load(candidate: StoreCandidate?, site: String?) {
        if (loadingDesign != null) return
        loadingDesign = candidate?.label ?: site
        scope.launch {
            val d = vm.fetchDesign(query.ifBlank { candidate?.label.orEmpty() }, candidate, site)
            loadingDesign = null
            if (d != null) onDesign(d)
        }
    }

    LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) search() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxHeight(0.88f).padding(horizontal = 16.dp)) {
            Text(
                "Найти магазин в интернете", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                "Логотип и фирменные цвета загрузятся один раз и сохранятся на телефоне",
                color = p.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 14.dp),
            )

            InputPill(
                value = query,
                onChange = { query = it },
                placeholder = "Название магазина",
                glyph = Glyph.Search,
                action = "Найти",
                onAction = { search() },
            )

            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loadingDesign != null -> CenterStatus(true, "Загружаю оформление «$loadingDesign»…")
                    searching -> CenterStatus(true, "Ищу магазин…")
                    results == null -> CenterStatus(false, "Введите название и нажмите «Найти»")
                    results!!.isEmpty() -> CenterStatus(false, "Ничего не найдено.\nПопробуйте другое написание или укажите сайт магазина.")
                    else -> LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(p.surface),
                    ) {
                        items(results!!, key = { it.id }) { c ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .bounceClick { load(c, null) }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(p.accentSoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(c.label.take(1).uppercase(), color = p.accent, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.label, color = p.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (c.description.isNotBlank()) {
                                        Text(
                                            c.description, color = p.textSecondary, fontSize = 13.sp,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (showManual) {
                InputPill(
                    value = manualSite,
                    onChange = { manualSite = it },
                    placeholder = "Сайт магазина, например magnit.ru",
                    glyph = Glyph.Search,
                    action = "Загрузить",
                    keyboard = KeyboardType.Uri,
                    onAction = { if (manualSite.isNotBlank()) load(null, manualSite) },
                )
            } else {
                Text(
                    "Нет нужного магазина? Указать сайт вручную",
                    color = p.accent, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick { showManual = true }
                        .padding(vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InputPill(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    glyph: Glyph,
    action: String,
    onAction: () -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val p = LocalOrigin.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(p.surface)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        GlyphIcon(glyph, p.textSecondary, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = p.textSecondary, fontSize = 16.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 16.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onAction() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PillButton(action, onAction, modifier = Modifier.height(38.dp))
    }
}

@Composable
private fun CenterStatus(loading: Boolean, text: String) {
    val p = LocalOrigin.current
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(28.dp), color = p.accent, strokeWidth = 2.5.dp)
            Spacer(Modifier.height(14.dp))
        }
        Text(text, color = p.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
