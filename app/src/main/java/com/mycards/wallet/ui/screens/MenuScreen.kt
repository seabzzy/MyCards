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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycards.wallet.data.CardText
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.components.Glyph
import com.mycards.wallet.ui.components.GlyphIcon
import com.mycards.wallet.ui.components.bounceClick
import com.mycards.wallet.ui.theme.LocalOrigin

/** Стартовое меню: два раздела — скидочные карты и документы. */
@Composable
fun MenuScreen(vm: AppViewModel, onCards: () -> Unit, onDocuments: () -> Unit) {
    val p = LocalOrigin.current
    val cards by vm.cards.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            "Главная", color = p.text, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
        MenuTile(
            glyph = Glyph.Card,
            title = "Скидочные карты",
            subtitle = if (cards.isEmpty()) "Пока пусто" else CardText.cardsCount(cards.size),
            onClick = onCards,
        )
        Spacer(Modifier.height(14.dp))
        MenuTile(
            glyph = Glyph.Document,
            title = "Документы",
            subtitle = if (docs.isEmpty()) "Пока пусто" else documentsCount(docs.size),
            onClick = onDocuments,
        )
    }
}

@Composable
private fun MenuTile(glyph: Glyph, title: String, subtitle: String, onClick: () -> Unit) {
    val p = LocalOrigin.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(p.surface)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(p.accentSoft),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(glyph, p.accent, size = 28.dp) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = p.textSecondary, fontSize = 14.sp)
        }
        GlyphIcon(Glyph.Back, p.textSecondary, size = 20.dp)
    }
}

/** «1 документ», «2 документа», «5 документов». */
fun documentsCount(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val word = when {
        m10 == 1 && m100 != 11 -> "документ"
        m10 in 2..4 && m100 !in 12..14 -> "документа"
        else -> "документов"
    }
    return "$n $word"
}
