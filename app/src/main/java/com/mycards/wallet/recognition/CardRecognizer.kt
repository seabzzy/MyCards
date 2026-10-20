package com.mycards.wallet.recognition

import com.mycards.wallet.data.Brand
import com.mycards.wallet.data.Candidate
import com.mycards.wallet.data.CardFormat
import com.mycards.wallet.data.LoyaltyCard
import com.mycards.wallet.data.Recognition

/**
 * Офлайн-определение магазина по нескольким признакам:
 *  1. содержимое кода (QR часто содержит адрес сайта магазина);
 *  2. надписи на карте/скриншоте (распознанный текст, с поправкой на кириллицу);
 *  3. правила номеров из каталога (numberPatterns в brands.json);
 *  4. самообучение — сходство номера с уже сохранёнными картами
 *     (карты одной сети обычно имеют общий префикс номера).
 * Баллы складываются как независимые вероятности.
 */
class CardRecognizer(private val catalog: List<Brand>) {

    fun recognize(
        raw: String,
        format: CardFormat,
        ocrText: String,
        existing: List<LoyaltyCard>,
    ): Recognition {
        val scores = LinkedHashMap<String, Pair<Float, String>>()
        fun add(brand: Brand, score: Float, reason: String) {
            val cur = scores[brand.id]
            val combined = if (cur == null) score else 1f - (1f - cur.first) * (1f - score)
            val why = if (cur == null || score > cur.first) reason else cur.second
            scores[brand.id] = combined to why
        }

        val rawLower = raw.lowercase()
        val rawSkeleton = skeleton(raw)
        val ocrLower = ocrText.lowercase()
        val ocrSkeleton = skeleton(ocrText)

        for (brand in catalog) {
            // 1. Адрес/ключ в содержимом кода
            if (brand.domains.any { it.isNotEmpty() && rawLower.contains(it) }) {
                add(brand, 0.95f, "по содержимому кода")
            }
            // 2. Название на изображении и в коде
            for (alias in brand.aliases) {
                val a = alias.lowercase()
                val sk = skeleton(alias)
                if (a.length >= 4 && ocrLower.contains(a)) {
                    add(brand, 0.8f, "по надписи на карте"); break
                }
                if (sk.length >= 4 && ocrSkeleton.contains(sk)) {
                    add(brand, 0.7f, "по надписи на карте"); break
                }
                if (sk.length >= 5 && rawSkeleton.contains(sk)) {
                    add(brand, 0.75f, "по содержимому кода"); break
                }
            }
            // 3. Правила номеров
            if (brand.numberPatterns.any { it.matches(raw) }) {
                add(brand, 0.4f, "по формату номера")
            }
        }

        // 4. Самообучение на ваших картах
        var similar: LoyaltyCard? = null
        var similarPrefix = 0
        if (raw.length >= 6) {
            for (card in existing) {
                if (card.number == raw || card.number.length != raw.length || card.format != format) continue
                val p = card.number.commonPrefixWith(raw).length
                if (p >= 4 && p > similarPrefix) {
                    similar = card
                    similarPrefix = p
                }
            }
        }
        val sim = similar
        if (sim != null) {
            val brand = catalog.firstOrNull { it.id == sim.brandId }
            if (brand != null) {
                val s = (0.5f + 0.08f * (similarPrefix - 4)).coerceAtMost(0.9f)
                add(brand, s, "номер похож на вашу карту «${sim.name}»")
            }
        }

        val ranked = scores.mapNotNull { (id, v) ->
            catalog.firstOrNull { it.id == id }?.let { Candidate(it, v.first, v.second) }
        }.sortedByDescending { it.score }

        val best = ranked.firstOrNull()?.takeIf { it.score >= 0.5f }
        return Recognition(
            best = best,
            suggestions = ranked.take(4),
            similarCard = similar,
        )
    }

    companion object {
        /**
         * «Скелет» строки: кириллица и латиница приводятся к общим похожим начертаниям.
         * Встроенный офлайн-распознаватель текста ML Kit читает латиницу, поэтому
         * «МАГНИТ» он может вернуть как «MAFHNT» — скелеты обеих строк совпадут.
         */
        fun skeleton(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s.lowercase()) {
                val m = glyphs[ch] ?: ch
                if (m.isLetterOrDigit()) sb.append(m)
            }
            return sb.toString()
        }

        private val glyphs: Map<Char, Char> = mapOf(
            // кириллица → латинские двойники
            'а' to 'a', 'б' to '6', 'в' to 'b', 'г' to 'r', 'д' to 'a', 'е' to 'e', 'ё' to 'e',
            'ж' to 'x', 'з' to '3', 'и' to 'n', 'й' to 'n', 'к' to 'k', 'л' to 'n', 'м' to 'm',
            'н' to 'h', 'о' to 'o', 'п' to 'n', 'р' to 'p', 'с' to 'c', 'т' to 't', 'у' to 'y',
            'ф' to 'o', 'х' to 'x', 'ц' to 'n', 'ч' to '4', 'ш' to 'w', 'щ' to 'w', 'ъ' to 'b',
            'ы' to 'b', 'ь' to 'b', 'э' to '3', 'ю' to 'o', 'я' to 'r',
            // типичные путаницы OCR
            'f' to 'r', 'l' to 'i', '1' to 'i', '|' to 'i', '0' to 'o', 'u' to 'n',
        )
    }
}
