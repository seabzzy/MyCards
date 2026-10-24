package com.mycards.wallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycards.wallet.data.CardFormat
import com.mycards.wallet.data.Logos
import com.mycards.wallet.data.StoreDesign
import com.mycards.wallet.ui.components.LogoBadge
import com.mycards.wallet.ui.components.WalletCard
import com.mycards.wallet.ui.theme.LocalOrigin

/**
 * Проверка найденного оформления: крупный логотип и превью карты в найденных цветах.
 * Ничего не применяется, пока пользователь не подтвердит.
 */
@Composable
fun ConfirmDesignDialog(
    design: StoreDesign,
    cardName: String,
    number: String,
    format: CardFormat,
    onConfirm: () -> Unit,
    onOther: () -> Unit,
    onReject: () -> Unit,
) {
    val p = LocalOrigin.current
    val logo = remember(design.logoFile) { Logos.image(design.logoFile) }
    AlertDialog(
        onDismissRequest = onReject,
        containerColor = p.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Это ваш магазин?") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (logo != null) {
                    LogoBadge(logo, size = 88.dp)
                    Spacer(Modifier.height(14.dp))
                }
                WalletCard(
                    name = cardName,
                    number = number,
                    format = format,
                    colorStart = Color(design.colorStart),
                    colorEnd = Color(design.colorEnd),
                    logoFile = design.logoFile,
                )
                Spacer(Modifier.height(12.dp))
                val site = design.website?.removePrefix("https://")?.removePrefix("http://")?.removePrefix("www.")?.trimEnd('/')
                Text(
                    buildString {
                        if (site != null) append("Сайт: $site")
                        if (logo == null) {
                            if (isNotEmpty()) append("\n")
                            append("Логотип не найден — только цвета")
                        }
                    },
                    color = p.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Да, это он", color = p.accent) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOther) { Text("Другой", color = p.text) }
                TextButton(onClick = onReject) { Text("Нет", color = p.textSecondary) }
            }
        },
    )
}
