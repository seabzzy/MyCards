package com.mycards.wallet.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycards.wallet.ui.AppViewModel
import com.mycards.wallet.ui.components.Glyph
import com.mycards.wallet.ui.components.GroupBlock
import com.mycards.wallet.ui.components.PillButton
import com.mycards.wallet.ui.components.SectionLabel
import com.mycards.wallet.ui.components.TopBar
import com.mycards.wallet.ui.theme.LocalOrigin

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = LocalOrigin.current
    var enabled by remember { mutableStateOf(vm.settings.onlineEnabled) }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding(),
    ) {
        TopBar("Настройки", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("Интернет")
            GroupBlock {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Использовать интернет", color = p.text, fontSize = 16.sp)
                        Text(
                            "Поиск оформления магазинов и проверка ссылок из QR-кодов",
                            color = p.textSecondary, fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = p.accent,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = p.divider,
                            uncheckedBorderColor = Color.Transparent,
                            uncheckedThumbColor = Color.White,
                        ),
                    )
                }
            }

            Text(
                "Как это работает\n\n" +
                    "• При добавлении карты нажмите «Найти магазин в интернете» и напишите его название. " +
                    "Приложение найдёт магазин в открытой базе Wikidata, возьмёт официальный сайт и логотип, " +
                    "а с сайта — фирменный цвет. Регистрация и ключи не нужны.\n\n" +
                    "• Оформление загружается один раз и хранится на телефоне. Следующие карты этого магазина " +
                    "оформятся сами, без интернета.\n\n" +
                    "• Если в QR-коде карты есть ссылка, приложение проверит, на сайт какого магазина она ведёт.\n\n" +
                    "• Номера карт и сами карты никуда не отправляются — в интернет уходит только название магазина.",
                color = p.textSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
        Box(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PillButton(
                "Сохранить",
                onClick = { vm.saveSettings(enabled); onBack() },
                glyph = Glyph.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
