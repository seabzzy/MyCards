package com.mycards.wallet.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycards.wallet.data.Brand
import com.mycards.wallet.data.CardDraft
import com.mycards.wallet.data.CardFormat
import com.mycards.wallet.data.OnlineStatus
import com.mycards.wallet.data.Logos
import com.mycards.wallet.data.StoreDesign
import com.mycards.wallet.ui.components.LogoBadge
import kotlinx.coroutines.delay
import com.mycards.wallet.data.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycards.wallet.scan.BarcodeRenderer
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.components.GlyphIcon
import com.mycards.wallet.ui.components.Glyph
import com.mycards.wallet.ui.components.GroupBlock
import com.mycards.wallet.ui.components.PillButton
import com.mycards.wallet.ui.components.SectionLabel
import com.mycards.wallet.ui.components.TopBar
import com.mycards.wallet.ui.components.WalletCard
import com.mycards.wallet.ui.components.bounceClick
import com.mycards.wallet.ui.theme.LocalOrigin

/** Проверка распознанной карты / ручной ввод / редактирование. */
@Composable
fun ReviewScreen(
    vm: AppViewModel,
    initial: CardDraft,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val p = LocalOrigin.current
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var number by rememberSaveable { mutableStateOf(initial.number) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var format by remember { mutableStateOf(initial.format) }
    var brandId by remember { mutableStateOf(initial.brandId) }
    var colorStart by remember { mutableIntStateOf(initial.colorStart) }
    var colorEnd by remember { mutableIntStateOf(initial.colorEnd) }
    var showPicker by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var logoFile by remember { mutableStateOf(initial.logoFile) }
    var designSite by remember { mutableStateOf<String?>(null) }
    var designColors by remember {
        mutableStateOf(if (initial.logoFile != null) initial.colorStart to initial.colorEnd else null)
    }

    val recognition = initial.recognition
    val isNew = initial.id == null
    val brand = vm.brand(brandId)

    fun applyBrand(b: Brand?) {
        val oldBrandName = vm.brand(brandId)?.name
        brandId = b?.id
        if (b != null) {
            if (name.isBlank() || name == oldBrandName) name = b.name
            val saved = vm.savedDesign(b)
            colorStart = saved?.colorStart ?: b.colorStart
            colorEnd = saved?.colorEnd ?: b.colorEnd
            logoFile = saved?.logoFile
            designSite = saved?.website
            designColors = saved?.let { it.colorStart to it.colorEnd }
        } else if (name == oldBrandName) {
            name = ""
            logoFile = null
        }
    }

    fun applyDesign(d: StoreDesign) {
        if (name.isBlank()) name = d.name
        colorStart = d.colorStart
        colorEnd = d.colorEnd
        logoFile = d.logoFile
        designSite = d.website
        designColors = d.colorStart to d.colorEnd
    }

    // Название набрано вручную и оформление этого магазина уже сохранено — применяем без сети.
    LaunchedEffect(name) {
        delay(350)
        if (logoFile == null) vm.savedDesign(name)?.let { applyDesign(it) }
    }

    // Автоподбор: для новой карты сами ищем магазин в интернете, логотип и фирменные цвета.
    var autoStatus by remember { mutableStateOf<String?>(null) }
    var autoDisabled by remember { mutableStateOf(false) }
    var colorsTouched by remember { mutableStateOf(false) }
    LaunchedEffect(name) {
        if (!isNew || autoDisabled || logoFile != null || !vm.settings.onlineEnabled) return@LaunchedEffect
        val query = name.trim()
        if (query.length < 2) { autoStatus = null; return@LaunchedEffect }
        delay(900) // ждём, пока пользователь допишет название
        if (logoFile != null) return@LaunchedEffect
        vm.savedDesign(query)?.let { applyDesign(it); autoStatus = null; return@LaunchedEffect }
        autoStatus = "Ищу логотип и цвета «$query»…"
        val found = vm.autoDesign(query, vm.brand(brandId))
        if (found != null && logoFile == null && !autoDisabled) {
            val keepStart = colorStart
            val keepEnd = colorEnd
            applyDesign(found)
            // цвет, выбранный вручную, не перебиваем
            if (colorsTouched) { colorStart = keepStart; colorEnd = keepEnd }
            autoStatus = null
        } else if (logoFile == null) {
            autoStatus = "Автоматически не нашёл — нажмите, чтобы выбрать магазин"
        }
    }

    // Онлайн-уточнение: если пользователь ещё ничего не менял — подставляем найденный магазин.
    val lookup by vm.online.collectAsStateWithLifecycle()
    val online = lookup?.takeIf { initial.lookupId != null && it.id == initial.lookupId }?.status
    var touched by remember { mutableStateOf(false) }
    fun applyOnline(f: OnlineStatus.Found) {
        val b = vm.brand(f.brandId)
        if (b != null) applyBrand(b) else {
            brandId = null
            name = f.name
            f.colorStart?.let { colorStart = it }
            f.colorEnd?.let { colorEnd = it }
        }
    }
    LaunchedEffect(online) {
        val f = online as? OnlineStatus.Found ?: return@LaunchedEffect
        if (!touched) applyOnline(f)
    }

    val encodable = remember(number, format) { number.isNotBlank() && BarcodeRenderer.canEncode(number.trim(), format) }
    val canSave = number.isNotBlank() && name.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        TopBar(
            title = when {
                !isNew -> "Изменение карты"
                recognition != null -> "Карта отсканирована"
                else -> "Новая карта"
            },
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            WalletCard(
                name, number.trim(), format, Color(colorStart), Color(colorEnd),
                showFullNumber = true,
                logoFile = logoFile,
            )

            if (recognition != null) {
                Spacer(Modifier.height(16.dp))
                if (online is OnlineStatus.Found) {
                    OnlineFoundBanner(online, applied = online.name == name || (online.brandId != null && online.brandId == brandId)) {
                        touched = false; applyOnline(online)
                    }
                } else {
                    RecognitionBanner(
                        recognizedName = recognition.best?.brand?.name,
                        reason = recognition.best?.reason,
                        confidence = recognition.best?.score,
                        matchesChoice = recognition.best != null && recognition.best.brand.id == brandId,
                        similarName = recognition.similarCard?.name,
                    )
                    if (online != null) OnlineStatusLine(online)
                }
                val chips = recognition.suggestions.filter { it.brand.id != brandId }.map { it.brand }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { b -> Chip(b.name, Color(b.colorStart)) { touched = true; applyBrand(b) } }
                    Chip("Все магазины…", null) { showPicker = true }
                }
            }

            SectionLabel("Магазин")
            GroupBlock {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bounceClick { showPicker = true }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(colorStart), Color(colorEnd)))),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(brand?.name ?: "Своя карта", color = p.text, fontSize = 16.sp)
                        Text(
                            brand?.category ?: "Название и цвет задаёте сами",
                            color = p.textSecondary, fontSize = 13.sp,
                        )
                    }
                    Text("Изменить", color = p.accent, fontSize = 15.sp)
                }
            }

            SectionLabel("Оформление")
            DesignBlock(
                logoFile = logoFile,
                website = designSite,
                online = vm.settings.onlineEnabled,
                onSearch = { showSearch = true },
                onRemoveLogo = { logoFile = null; designSite = null; autoDisabled = true },
                status = autoStatus,
            )

            SectionLabel("Данные карты")
            GroupBlock {
                Field("Название", name, { name = it; touched = true }, "Например, «Магнит»")
                HorizontalDivider(Modifier.padding(start = 18.dp), color = p.divider)
                Field(
                    "Номер / содержимое кода", number, { number = it }, "Цифры с карты",
                    keyboard = if (format.is2D) KeyboardType.Text else KeyboardType.Ascii,
                    monospace = true,
                )
                HorizontalDivider(Modifier.padding(start = 18.dp), color = p.divider)
                FormatRow(format) { format = it }
                HorizontalDivider(Modifier.padding(start = 18.dp), color = p.divider)
                Field("Заметка", note, { note = it }, "Необязательно")
            }
            if (number.isNotBlank() && !encodable) {
                Text(
                    "Номер не подходит под формат ${format.label} — карта будет показана в формате Code 128.",
                    color = p.warning, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp),
                )
            }

            SectionLabel("Цвет карты")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val swatches = buildList {
                    designColors?.let { add(it) }
                    brand?.let { add(it.colorStart to it.colorEnd) }
                    addAll(Palette.presets)
                }
                swatches.distinct().forEach { (s, e) ->
                    val selected = s == colorStart && e == colorEnd
                    val ring by animateColorAsState(if (selected) p.accent else Color.Transparent, label = "ring")
                    Box(
                        Modifier
                            .size(46.dp)
                            .border(2.5.dp, ring, CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(s), Color(e))))
                            .bounceClick { colorStart = s; colorEnd = e; colorsTouched = true },
                    )
                }
            }

            if (number.isNotBlank()) {
                SectionLabel("Код для кассы")
                BarcodePreview(number.trim(), format)
            }
            Spacer(Modifier.height(24.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PillButton(
                if (isNew) "Сохранить карту" else "Сохранить изменения",
                onClick = {
                    val id = vm.save(
                        initial.copy(
                            name = name, number = number, note = note, format = format,
                            brandId = brandId, colorStart = colorStart, colorEnd = colorEnd,
                        )
                    )
                    onSaved(id)
                },
                enabled = canSave,
                glyph = Glyph.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showSearch) {
        StoreSearchSheet(
            vm = vm,
            initialQuery = name.ifBlank { brand?.name.orEmpty() },
            onDismiss = { showSearch = false },
            onDesign = { applyDesign(it); touched = true; showSearch = false },
        )
    }

    if (showPicker) {
        BrandPickerSheet(
            brands = vm.brands,
            onDismiss = { showPicker = false },
            onPick = { touched = true; applyBrand(it); showPicker = false },
        )
    }
}

@Composable
private fun RecognitionBanner(
    recognizedName: String?,
    reason: String?,
    confidence: Float?,
    matchesChoice: Boolean,
    similarName: String?,
) {
    val p = LocalOrigin.current
    val ok = recognizedName != null && matchesChoice
    val bg = if (ok) p.successSoft else p.warningSoft
    val fg = if (ok) p.success else p.warning
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(fg),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(if (ok) Glyph.Check else Glyph.Scan, Color.White, size = 18.dp) }
        Spacer(Modifier.width(12.dp))
        Column {
            val title = when {
                ok -> "Распознано: $recognizedName"
                recognizedName != null -> "Похоже на «$recognizedName»"
                similarName != null -> "Похоже на вашу карту «$similarName»"
                else -> "Магазин не определён"
            }
            Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val sub = when {
                recognizedName != null && reason != null && confidence != null ->
                    "Определено $reason · ${(confidence * 100).toInt()}%"
                else -> "Выберите магазин из списка или задайте своё название"
            }
            Text(sub, color = p.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DesignBlock(
    logoFile: String?,
    website: String?,
    online: Boolean,
    onSearch: () -> Unit,
    onRemoveLogo: () -> Unit,
    status: String? = null,
) {
    val p = LocalOrigin.current
    val logo = remember(logoFile) { Logos.image(logoFile) }
    GroupBlock {
        Row(
            Modifier
                .fillMaxWidth()
                .bounceClick(enabled = online, onClick = onSearch)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (logo != null) {
                LogoBadge(logo, size = 44.dp)
            } else {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(p.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.Search, p.accent, size = 22.dp) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (logo != null) "Логотип и цвета магазина" else "Найти магазин в интернете",
                    color = p.text, fontSize = 16.sp,
                )
                Text(
                    when {
                        logo != null && website != null -> website.removePrefix("https://").removePrefix("http://").trimEnd('/')
                        logo != null -> "Сохранено на телефоне"
                        status != null -> status
                        !online -> "Интернет выключен в настройках"
                        else -> "Напишите название — загрузим логотип и цвета"
                    },
                    color = p.textSecondary, fontSize = 13.sp, maxLines = 1,
                )
            }
            if (online) Text(if (logo != null) "Обновить" else "Найти", color = p.accent, fontSize = 15.sp)
        }
        if (logo != null) {
            HorizontalDivider(Modifier.padding(start = 18.dp), color = p.divider)
            Text(
                "Убрать логотип", color = p.danger, fontSize = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(onClick = onRemoveLogo)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun OnlineFoundBanner(f: OnlineStatus.Found, applied: Boolean, onApply: () -> Unit) {
    val p = LocalOrigin.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.successSoft)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(p.success),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.Check, Color.White, size = 18.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Распознано онлайн: ${f.name}", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Определено ${f.source} · ${(f.confidence * 100).toInt()}%", color = p.textSecondary, fontSize = 13.sp)
        }
        if (!applied) {
            Text(
                "Применить", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.bounceClick(onClick = onApply).padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun OnlineStatusLine(status: OnlineStatus) {
    val p = LocalOrigin.current
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            OnlineStatus.Loading -> {
                CircularProgressIndicator(Modifier.size(14.dp), color = p.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Проверяю ссылку из QR-кода…", color = p.textSecondary, fontSize = 13.sp)
            }
            is OnlineStatus.NotFound -> Text(status.hint, color = p.textSecondary, fontSize = 13.sp)
            is OnlineStatus.Failed -> Text("Онлайн: ${status.message}", color = p.warning, fontSize = 13.sp)
            is OnlineStatus.Found -> Unit
        }
    }
}

@Composable
private fun Chip(text: String, dot: Color?, onClick: () -> Unit) {
    val p = LocalOrigin.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(p.surface)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (dot == null) p.accent else p.text, fontSize = 14.sp)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    monospace: Boolean = false,
) {
    val p = LocalOrigin.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(label, color = p.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box {
            if (value.isEmpty()) Text(placeholder, color = p.textSecondary.copy(alpha = 0.6f), fontSize = 17.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(
                    color = p.text, fontSize = 17.sp,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                ),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FormatRow(format: CardFormat, onChange: (CardFormat) -> Unit) {
    val p = LocalOrigin.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .bounceClick { open = true }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Формат кода", color = p.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(format.label, color = p.text, fontSize = 17.sp)
            }
            Text("Выбрать", color = p.accent, fontSize = 15.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CardFormat.entries.forEach { f ->
                DropdownMenuItem(text = { Text(f.label) }, onClick = { onChange(f); open = false })
            }
        }
    }
}

@Composable
fun BarcodePreview(content: String, format: CardFormat, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current.density
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val fullW = constraints.maxWidth
            val wide = !format.is2D || format == CardFormat.PDF_417
            val wPx = if (wide) fullW else minOf(fullW, (220 * density).toInt())
            val hPx = when {
                !format.is2D -> (120 * density).toInt()
                format == CardFormat.PDF_417 -> fullW / 3
                else -> wPx
            }
            val rendered = remember(content, format, wPx, hPx) { BarcodeRenderer.render(content, format, wPx, hPx) }
            if (rendered != null) {
                Image(
                    bitmap = rendered.bitmap.asImageBitmap(),
                    contentDescription = "Код карты",
                    filterQuality = FilterQuality.None,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(
                        width = (rendered.bitmap.width / density).dp,
                        height = (rendered.bitmap.height / density).dp,
                    ),
                )
            } else {
                Text("Не удалось построить код", color = Color(0xFF8B8E98))
            }
        }
    }
}
