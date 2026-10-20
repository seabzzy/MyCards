package com.mycards.wallet.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mycards.wallet.data.CardFormat
import com.mycards.wallet.data.ScanResult
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.min

/** Общие офлайн-распознаватели ML Kit (модели встроены в APK). */
object MlKit {
    val barcodes: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )
    }
    val text: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun mapFormat(f: Int): CardFormat? = when (f) {
        Barcode.FORMAT_QR_CODE -> CardFormat.QR_CODE
        Barcode.FORMAT_EAN_13 -> CardFormat.EAN_13
        Barcode.FORMAT_EAN_8 -> CardFormat.EAN_8
        Barcode.FORMAT_UPC_A -> CardFormat.UPC_A
        Barcode.FORMAT_UPC_E -> CardFormat.UPC_E
        Barcode.FORMAT_CODE_128 -> CardFormat.CODE_128
        Barcode.FORMAT_CODE_39 -> CardFormat.CODE_39
        Barcode.FORMAT_CODE_93 -> CardFormat.CODE_93
        Barcode.FORMAT_CODABAR -> CardFormat.CODABAR
        Barcode.FORMAT_ITF -> CardFormat.ITF
        Barcode.FORMAT_PDF417 -> CardFormat.PDF_417
        Barcode.FORMAT_AZTEC -> CardFormat.AZTEC
        Barcode.FORMAT_DATA_MATRIX -> CardFormat.DATA_MATRIX
        else -> null
    }

    /** Выбирает самый крупный код на изображении — обычно это и есть карта. */
    fun pickBest(codes: List<Barcode>): Pair<String, CardFormat>? =
        codes.mapNotNull { b ->
            val raw = b.rawValue
            val f = mapFormat(b.format)
            if (raw.isNullOrEmpty() || f == null) null
            else Triple(raw, f, b.boundingBox?.let { it.width() * it.height() } ?: 0)
        }.maxByOrNull { it.third }?.let { it.first to it.second }
}

/** Распознавание кода со скриншота/фото из галереи. */
object ImageScanner {

    suspend fun scanUri(context: Context, uri: Uri): ScanResult? {
        val bitmap = decode(context, uri) ?: return null
        scanBitmap(bitmap, withOcr = true)?.let { return it }

        // На длинных скриншотах код может быть мелким — пробуем по фрагментам.
        val w = bitmap.width
        val h = bitmap.height
        val tileH = (h * 0.42f).toInt()
        val step = max(1, (h * 0.25f).toInt())
        var y = 0
        while (y < h) {
            val th = min(tileH, h - y)
            if (th < 64) break
            val tile = Bitmap.createBitmap(bitmap, 0, y, w, th)
            val found = scanBitmap(tile, withOcr = false)
            if (found != null) {
                return found.copy(ocrText = ocr(InputImage.fromBitmap(bitmap, 0)))
            }
            if (y + th >= h) break
            y += step
        }
        return null
    }

    private suspend fun scanBitmap(bmp: Bitmap, withOcr: Boolean): ScanResult? {
        val image = InputImage.fromBitmap(bmp, 0)
        val codes = MlKit.barcodes.process(image).await()
        val best = MlKit.pickBest(codes) ?: return null
        val text = if (withOcr) ocr(image) else ""
        return ScanResult(best.first, best.second, text)
    }

    private suspend fun ocr(image: InputImage): String =
        runCatching { MlKit.text.process(image).await().text }.getOrDefault("")

    private fun decode(context: Context, uri: Uri): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            val limit = 3000
            if (longest > limit) {
                val k = limit.toFloat() / longest
                decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
            }
        }
    } catch (e: Exception) {
        null
    }
}
