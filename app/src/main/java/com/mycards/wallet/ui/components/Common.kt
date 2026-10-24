package com.mycards.wallet.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycards.wallet.ui.theme.LocalOrigin

/** Упругое нажатие, как в OriginOS: элемент слегка «проседает» и пружинит обратно. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "bounce",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

@Composable
fun CircleButton(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = LocalOrigin.current.surface,
    tint: Color = LocalOrigin.current.text,
    size: Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, tint, size = 22.dp) }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    enabled: Boolean = true,
    color: Color = LocalOrigin.current.accent,
    contentColor: Color = Color.White,
) {
    Row(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .bounceClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, contentColor, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Скруглённый блок-группа, как в настройках OriginOS. */
@Composable
fun GroupBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LocalOrigin.current.surface),
        content = content,
    )
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val p = LocalOrigin.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(Glyph.Back, onBack)
        Text(
            title,
            color = p.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        actions()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        color = LocalOrigin.current.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Пока экран открыт — максимальная яркость и экран не гаснет (для кассового сканера). */
@Composable
fun MaxBrightness() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
