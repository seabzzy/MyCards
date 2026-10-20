package com.mycards.wallet.data

import com.google.zxing.BarcodeFormat

/** Поддерживаемые форматы кода (совпадают с тем, что умеют ML Kit и ZXing). */
enum class CardFormat(val label: String, val zxing: BarcodeFormat, val is2D: Boolean) {
    QR_CODE("QR-код", BarcodeFormat.QR_CODE, true),
    EAN_13("EAN-13", BarcodeFormat.EAN_13, false),
    EAN_8("EAN-8", BarcodeFormat.EAN_8, false),
    UPC_A("UPC-A", BarcodeFormat.UPC_A, false),
    UPC_E("UPC-E", BarcodeFormat.UPC_E, false),
    CODE_128("Code 128", BarcodeFormat.CODE_128, false),
    CODE_39("Code 39", BarcodeFormat.CODE_39, false),
    CODE_93("Code 93", BarcodeFormat.CODE_93, false),
    CODABAR("Codabar", BarcodeFormat.CODABAR, false),
    ITF("ITF", BarcodeFormat.ITF, false),
    PDF_417("PDF417", BarcodeFormat.PDF_417, true),
    AZTEC("Aztec", BarcodeFormat.AZTEC, true),
    DATA_MATRIX("Data Matrix", BarcodeFormat.DATA_MATRIX, true);

    companion object {
        fun fromName(name: String?): CardFormat = entries.firstOrNull { it.name == name } ?: CODE_128
    }
}

/** Сохранённая карта. Цвета — ARGB Int. */
data class LoyaltyCard(
    val id: String,
    val name: String,
    val brandId: String?,
    val number: String,
    val format: CardFormat,
    val colorStart: Int,
    val colorEnd: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 0,
    /** Логотип магазина — имя файла в папке logos (оформление хранится локально). */
    val logoFile: String? = null,
)

/** Магазин из встроенного офлайн-каталога. */
data class Brand(
    val id: String,
    val name: String,
    val category: String,
    val aliases: List<String>,
    val domains: List<String>,
    val colorStart: Int,
    val colorEnd: Int,
    val numberPatterns: List<Regex>,
)

/** Результат сканирования: содержимое кода, формат и текст, найденный на изображении. */
data class ScanResult(val raw: String, val format: CardFormat, val ocrText: String)

/**
 * Оформление магазина, найденное в интернете один раз и сохранённое на телефоне:
 * цвета градиента и логотип (файл в папке logos).
 */
data class StoreDesign(
    val key: String,
    val name: String,
    val colorStart: Int,
    val colorEnd: Int,
    val logoFile: String?,
    val website: String?,
    val savedAt: Long = System.currentTimeMillis(),
)

/** Вариант магазина из поиска в интернете (Wikidata). */
data class StoreCandidate(val id: String, val label: String, val description: String)

/** Состояние онлайн-уточнения магазина для конкретного черновика. */
sealed interface OnlineStatus {
    data object Loading : OnlineStatus
    data class Found(val name: String, val brandId: String?, val colorStart: Int?, val colorEnd: Int?, val confidence: Float, val source: String) : OnlineStatus
    data class NotFound(val hint: String) : OnlineStatus
    data class Failed(val message: String) : OnlineStatus
}

data class OnlineLookup(val id: Long, val status: OnlineStatus)

data class Candidate(val brand: Brand, val score: Float, val reason: String)

data class Recognition(
    val best: Candidate?,
    val suggestions: List<Candidate>,
    /** Уже сохранённая карта с похожим номером (тот же выпуск карт). */
    val similarCard: LoyaltyCard?,
)

/** Черновик карты — экран проверки/редактирования. */
data class CardDraft(
    val id: String? = null,
    val name: String = "",
    val brandId: String? = null,
    val number: String = "",
    val format: CardFormat = CardFormat.CODE_128,
    val colorStart: Int = Palette.presets.first().first,
    val colorEnd: Int = Palette.presets.first().second,
    val note: String = "",
    val logoFile: String? = null,
    val recognition: Recognition? = null,
    /** Идентификатор онлайн-запроса, запущенного для этого черновика. */
    val lookupId: Long? = null,
)

object Palette {
    /** Градиенты для «своих» карт — мягкие, в духе OriginOS. */
    val presets: List<Pair<Int, Int>> = listOf(
        0xFF5B7CFF.toInt() to 0xFF3448D6.toInt(),
        0xFF8E6BFF.toInt() to 0xFF5B3BD9.toInt(),
        0xFFFF7A9A.toInt() to 0xFFE0436B.toInt(),
        0xFFFF9F5A.toInt() to 0xFFF0612E.toInt(),
        0xFF35C98A.toInt() to 0xFF16936A.toInt(),
        0xFF33C3E8.toInt() to 0xFF1786C9.toInt(),
        0xFF3B3F4A.toInt() to 0xFF16181D.toInt(),
        0xFFE9D8B4.toInt() to 0xFFB9975B.toInt(),
    )
}

object CardText {
    fun pretty(number: String): String =
        if (number.length in 6..24 && number.all { it.isDigit() }) number.chunked(4).joinToString(" ")
        else number

    fun masked(number: String): String =
        if (number.length > 6) "•••• " + number.takeLast(4) else number

    fun cardsCount(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        val word = when {
            m10 == 1 && m100 != 11 -> "карта"
            m10 in 2..4 && m100 !in 12..14 -> "карты"
            else -> "карт"
        }
        return "$n $word"
    }
}
