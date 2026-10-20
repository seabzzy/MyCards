package com.mycards.wallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Тонкие линейные иконки в стиле OriginOS (рисуются кодом, без внешних ресурсов). */
enum class Glyph { Camera, Image, Keyboard, Flash, Scan, Copy, Back, Plus, More, Search, Check, Close, Trash, Edit, Sun }

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        fun sz(w: Float, h: Float) = Size(w * k, h * k)
        val st = Stroke(width = 1.8f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, o(x1, y1), o(x2, y2), strokeWidth = 1.8f * k, cap = StrokeCap.Round)
        fun poly(vararg pts: Float, close: Boolean = false) {
            val p = Path()
            p.moveTo(pts[0] * k, pts[1] * k)
            var i = 2
            while (i < pts.size) { p.lineTo(pts[i] * k, pts[i + 1] * k); i += 2 }
            if (close) p.close()
            drawPath(p, tint, style = st)
        }
        when (glyph) {
            Glyph.Camera -> {
                drawRoundRect(tint, o(3f, 7f), sz(18f, 13f), CornerRadius(4f * k), style = st)
                poly(8f, 7f, 9.5f, 4.5f, 14.5f, 4.5f, 16f, 7f)
                drawCircle(tint, 3.6f * k, o(12f, 13.5f), style = st)
            }
            Glyph.Image -> {
                drawRoundRect(tint, o(3f, 4f), sz(18f, 16f), CornerRadius(4f * k), style = st)
                drawCircle(tint, 1.7f * k, o(8.5f, 9.5f))
                poly(3.5f, 17f, 9f, 12.5f, 13f, 15.5f, 16f, 13f, 20.5f, 16.5f)
            }
            Glyph.Keyboard -> {
                drawRoundRect(tint, o(2.5f, 6f), sz(19f, 12f), CornerRadius(3.5f * k), style = st)
                for (x in listOf(6.5f, 10f, 13.5f, 17f)) drawCircle(tint, 0.9f * k, o(x, 10f))
                line(8f, 14.5f, 16f, 14.5f)
            }
            Glyph.Flash -> { poly(13f, 2.5f, 5.5f, 13.5f, 11.5f, 13.5f, 10.5f, 21.5f, 18.5f, 10f, 12.5f, 10f, close = true) }
            Glyph.Scan -> {
                poly(3.5f, 8f, 3.5f, 3.5f, 8f, 3.5f); poly(16f, 3.5f, 20.5f, 3.5f, 20.5f, 8f)
                poly(3.5f, 16f, 3.5f, 20.5f, 8f, 20.5f); poly(16f, 20.5f, 20.5f, 20.5f, 20.5f, 16f)
                line(6.5f, 12f, 17.5f, 12f)
            }
            Glyph.Copy -> {
                drawRoundRect(tint, o(8f, 8f), sz(12.5f, 12.5f), CornerRadius(3f * k), style = st)
                poly(16f, 5f, 16f, 4.5f, 14.5f, 3.5f, 5f, 3.5f, 3.5f, 5f, 3.5f, 14.5f, 5f, 16f)
            }
            Glyph.Back -> { poly(15f, 4.5f, 7.5f, 12f, 15f, 19.5f) }
            Glyph.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Glyph.More -> { for (x in listOf(5.5f, 12f, 18.5f)) drawCircle(tint, 1.6f * k, o(x, 12f)) }
            Glyph.Search -> { drawCircle(tint, 6.5f * k, o(10.5f, 10.5f), style = st); line(15.5f, 15.5f, 20f, 20f) }
            Glyph.Check -> { poly(5f, 12.5f, 10f, 17.5f, 19f, 7f) }
            Glyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Glyph.Trash -> {
                line(4f, 6.5f, 20f, 6.5f); poly(9f, 6.5f, 9.5f, 3.5f, 14.5f, 3.5f, 15f, 6.5f)
                poly(6f, 6.5f, 7f, 20.5f, 17f, 20.5f, 18f, 6.5f)
                line(10f, 10.5f, 10f, 16.5f); line(14f, 10.5f, 14f, 16.5f)
            }
            Glyph.Edit -> {
                poly(4f, 20f, 4.5f, 16f, 15.5f, 5f, 19f, 8.5f, 8f, 19.5f, close = true)
                line(13f, 7.5f, 16.5f, 11f)
            }
            Glyph.Sun -> {
                drawCircle(tint, 4f * k, o(12f, 12f), style = st)
                val r1 = 7f; val r2 = 9.5f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    line(12f + (r1 * Math.cos(a)).toFloat(), 12f + (r1 * Math.sin(a)).toFloat(),
                        12f + (r2 * Math.cos(a)).toFloat(), 12f + (r2 * Math.sin(a)).toFloat())
                }
            }
        }
    }
}
