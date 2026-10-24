package com.mycards.wallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.mycards.wallet.data.Logos
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycards.wallet.data.CardFormat
import com.mycards.wallet.data.CardText
import com.mycards.wallet.data.LoyaltyCard

/** Цифровая копия карты: градиент бренда, название, монограмма, номер. */
@Composable
fun WalletCard(
    name: String,
    number: String,
    format: CardFormat,
    colorStart: Color,
    colorEnd: Color,
    modifier: Modifier = Modifier,
    showFullNumber: Boolean = false,
    logoFile: String? = null,
    pinned: Boolean = false,
) {
    val shape = RoundedCornerShape(24.dp)
    val logo = remember(logoFile) { Logos.image(logoFile) }
    val mid = lerp(colorStart, colorEnd, 0.5f)
    val onCard = if (mid.luminance() > 0.5f) Color(0xFF17181C) else Color.White
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f) // пропорции настоящей пластиковой карты (ISO ID-1)
            .shadow(12.dp, shape, ambientColor = colorEnd, spotColor = colorEnd)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colorStart, colorEnd))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawCircle(Color.White.copy(alpha = 0.10f), w * 0.55f, Offset(w * 1.02f, -h * 0.08f))
            drawCircle(Color.White.copy(alpha = 0.07f), w * 0.36f, Offset(w * 0.88f, h * 1.12f))
            drawCircle(Color.Black.copy(alpha = 0.05f), w * 0.30f, Offset(-w * 0.04f, h * 1.02f))
        }
        Column(
            Modifier
                .matchParentSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) {
                    GlyphIcon(Glyph.Pin, onCard, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    name.ifBlank { "Новая карта" },
                    color = onCard,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (logo != null) LogoBadge(logo) else Monogram(name, onCard)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Номер карты", color = onCard.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text(
                        if (showFullNumber) CardText.pretty(number) else CardText.masked(number),
                        color = onCard,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    format.label,
                    color = onCard.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(onCard.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun WalletCard(card: LoyaltyCard, modifier: Modifier = Modifier, showFullNumber: Boolean = false) =
    WalletCard(card.name, card.number, card.format, Color(card.colorStart), Color(card.colorEnd), modifier, showFullNumber, card.logoFile, card.pinned)

/** Логотип магазина на белой «плашке» — так любой логотип хорошо читается на цветной карте. */
@Composable
fun LogoBadge(logo: ImageBitmap, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Color.White)
            .padding(size * 0.12f),
        contentAlignment = Alignment.Center,
    ) {
        Image(logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Monogram(name: String, color: Color) {
    val initials = name.split(' ', '-', '.', '&')
        .filter { it.isNotBlank() && it[0].isLetterOrDigit() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "•" }
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * «Кошелёк»: карты лежат стопкой, у каждой видна верхняя полоска, последняя — целиком.
 * Долгое нажатие — меню рядом с пальцем. Долгое нажатие и перетаскивание —
 * смена порядка закреплённых карт (они всегда в начале списка).
 */
@Composable
fun CardStack(
    cards: List<LoyaltyCard>,
    onClick: (LoyaltyCard) -> Unit,
    modifier: Modifier = Modifier,
    menuActions: (LoyaltyCard) -> List<SheetAction> = { emptyList() },
    reorderEnabled: Boolean = true,
    onReorderPinned: (List<String>) -> Unit = {},
) {
    val peek = 74.dp
    val peekPx = with(LocalDensity.current) { peek.toPx() }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var menuAt by remember { mutableStateOf(IntOffset.Zero) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val live = remember { mutableStateListOf<String>() }
    val pinnedCount = cards.count { it.pinned }
    val shown = if (draggingId != null) live.mapNotNull { id -> cards.firstOrNull { it.id == id } } else cards

    Layout(
        modifier = modifier,
        content = {
            shown.forEach { card ->
                key(card.id) {
                    val dragging = card.id == draggingId
                    Box(
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) dragOffset else 0f
                                val s = if (dragging) 1.03f else 1f
                                scaleX = s
                                scaleY = s
                            },
                    ) {
                        WalletCard(
                            card,
                            Modifier
                                .bounceClick { onClick(card) }
                                .longPressMenuAndDrag(
                                    key = card.id,
                                    canDrag = reorderEnabled && card.pinned && pinnedCount >= 2,
                                    onLongPress = { pos ->
                                        menuAt = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                        menuFor = card.id
                                    },
                                    onDragStart = {
                                        menuFor = null
                                        live.clear()
                                        live.addAll(cards.map { it.id })
                                        draggingId = card.id
                                        dragOffset = 0f
                                    },
                                    onDrag = { dy ->
                                        dragOffset += dy
                                        val from = live.indexOf(card.id)
                                        if (dragOffset > peekPx / 2 && from in 0 until pinnedCount - 1) {
                                            live.add(from + 1, live.removeAt(from))
                                            dragOffset -= peekPx
                                        } else if (dragOffset < -peekPx / 2 && from > 0) {
                                            live.add(from - 1, live.removeAt(from))
                                            dragOffset += peekPx
                                        }
                                    },
                                    onDragEnd = {
                                        onReorderPinned(live.take(pinnedCount))
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                ),
                        )
                        Box(Modifier.offset { menuAt }) {
                            ItemPopupMenu(
                                expanded = menuFor == card.id,
                                actions = menuActions(card),
                                onDismiss = { menuFor = null },
                            )
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val step = peek.roundToPx()
        val height = if (placeables.isEmpty()) 0
        else step * (placeables.size - 1) + placeables.last().height
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, pl -> pl.place(0, i * step) }
        }
    }
}
