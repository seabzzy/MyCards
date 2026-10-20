package com.mycards.wallet.scan

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.datamatrix.encoder.SymbolShapeHint
import com.mycards.wallet.data.CardFormat

/** Рисует штрихкод/QR для цифровой копии карты. */
object BarcodeRenderer {

    data class Rendered(val bitmap: Bitmap, val format: CardFormat, val fallback: Boolean)

    fun canEncode(content: String, format: CardFormat): Boolean =
        encode(content, format, 200, 100) != null

    /** Если номер не подходит под формат (например, неверная контрольная цифра EAN),
     *  показываем его в Code 128 — его читают все кассовые сканеры. */
    fun render(content: String, format: CardFormat, width: Int, height: Int): Rendered? {
        encode(content, format, width, height)?.let { return Rendered(it, format, false) }
        if (format != CardFormat.CODE_128) {
            encode(content, CardFormat.CODE_128, width, height)?.let {
                return Rendered(it, CardFormat.CODE_128, true)
            }
        }
        return null
    }

    private fun encode(content: String, format: CardFormat, width: Int, height: Int): Bitmap? = try {
        if (content.isEmpty() || width <= 0 || height <= 0) null else {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.MARGIN] = if (format.is2D) 1 else 0
            if (format == CardFormat.QR_CODE || format == CardFormat.AZTEC ||
                format == CardFormat.PDF_417 || format == CardFormat.DATA_MATRIX
            ) hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            if (format == CardFormat.DATA_MATRIX) hints[EncodeHintType.DATA_MATRIX_SHAPE] = SymbolShapeHint.FORCE_SQUARE
            toBitmap(MultiFormatWriter().encode(content, format.zxing, width, height, hints))
        }
    } catch (e: Exception) {
        null
    }

    private fun toBitmap(m: BitMatrix): Bitmap {
        val w = m.width
        val h = m.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val off = y * w
            for (x in 0 until w) pixels[off + x] = if (m[x, y]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
