package com.mycards.wallet.online

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.mycards.wallet.data.StoreCandidate
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max

/** Оформление, найденное в интернете (ещё не сохранённое). */
class FoundDesign(
    val website: String?,
    val logo: Bitmap?,
    val colorStart: Int,
    val colorEnd: Int,
)

class FinderException(message: String) : Exception(message)

/**
 * Поиск магазина в интернете без ключей и регистраций.
 *  • Wikidata (открытая база Википедии): поиск по названию → официальный сайт и логотип.
 *  • Сайт магазина: фирменный цвет (meta theme-color) и иконка, если логотипа в Wikidata нет.
 *  • Цвета, если сайт их не указал, вычисляются по логотипу.
 * Все методы блокирующие — вызывать из Dispatchers.IO.
 */
object StoreFinder {

    private const val UA = "MyCards/1.0 (Android; personal loyalty-card wallet)"
    private const val TIMEOUT = 10_000

    // ---------- 1. Поиск ----------

    fun search(query: String): List<StoreCandidate> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json" +
            "&type=item&limit=8&language=ru&uselang=ru&search=$q"
        val json = JSONObject(String(get(url, 512 * 1024).second, Charsets.UTF_8))
        val arr = json.optJSONArray("search") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StoreCandidate(
                id = o.getString("id"),
                label = o.optString("label", query),
                description = o.optString("description"),
            )
        }
    }

    // ---------- 2. Оформление ----------

    /**
     * @param candidate выбранный результат поиска (может быть null, если сайт указан вручную)
     * @param manualSite адрес сайта, введённый пользователем
     */
    fun buildDesign(candidate: StoreCandidate?, manualSite: String?): FoundDesign {
        var website = manualSite?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeUrl)
        var logo: Bitmap? = null

        if (candidate != null) {
            val entity = runCatching { loadEntity(candidate.id) }.getOrNull()
            if (website == null) website = entity?.first
            entity?.second?.let { file -> logo = runCatching { downloadCommonsImage(file) }.getOrNull() }
        }

        var themeColor: Int? = null
        if (website != null) {
            val page = runCatching { loadSite(website) }.getOrNull()
            if (page != null) {
                themeColor = page.themeColor
                if (logo == null) {
                    logo = page.icons.firstNotNullOfOrNull { runCatching { downloadBitmap(it) }.getOrNull() }
                }
            }
        }

        if (logo == null && themeColor == null) {
            throw FinderException(
                if (website == null) "Не удалось найти сайт магазина — укажите его вручную"
                else "Сайт магазина не отдал ни логотипа, ни фирменного цвета"
            )
        }

        val base = themeColor?.takeIf(::isUsableBrandColor)
            ?: logo?.let(::colorFromLogo)
            ?: themeColor
            ?: 0xFF5B7CFF.toInt()
        return FoundDesign(website, logo?.let(::fitLogo), base, darken(base))
    }

    // ---------- Wikidata ----------

    /** @return (официальный сайт, имя файла логотипа на Wikimedia Commons) */
    private fun loadEntity(id: String): Pair<String?, String?> {
        val url = "https://www.wikidata.org/wiki/Special:EntityData/$id.json"
        val root = JSONObject(String(get(url, 4 * 1024 * 1024).second, Charsets.UTF_8))
        val claims = root.getJSONObject("entities").getJSONObject(id).optJSONObject("claims")
            ?: return null to null
        fun firstString(prop: String): String? {
            val arr = claims.optJSONArray(prop) ?: return null
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optString("rank") != "deprecated" }
                .sortedBy { if (it.optString("rank") == "preferred") 0 else 1 }
            return items.firstNotNullOfOrNull {
                it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")?.takeIf { v -> v.isNotBlank() }
            }
        }
        return firstString("P856") to firstString("P154") // P856 — сайт, P154 — логотип
    }

    private fun downloadCommonsImage(fileName: String): Bitmap? {
        val name = URLEncoder.encode(fileName.replace(' ', '_'), "UTF-8").replace("+", "%20")
        // width — Commons сам растрирует SVG в PNG нужного размера
        return downloadBitmap("https://commons.wikimedia.org/wiki/Special:FilePath/$name?width=320")
    }

    // ---------- Сайт магазина ----------

    private class Page(val themeColor: Int?, val icons: List<String>)

    private fun loadSite(site: String): Page {
        val (finalUrl, bytes) = get(site, 600 * 1024, accept = "text/html")
        val html = String(bytes, Charsets.UTF_8)
        val tags = Regex("<(meta|link)\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }.toList()

        val theme = tags.firstOrNull { attr(it, "name").equals("theme-color", true) }
            ?.let { attr(it, "content") }
            ?: tags.firstOrNull { attr(it, "name").equals("msapplication-TileColor", true) }?.let { attr(it, "content") }

        val base = URL(finalUrl)
        data class Icon(val url: String, val score: Int)
        val icons = tags.mapNotNull { tag ->
            val rel = attr(tag, "rel")?.lowercase() ?: return@mapNotNull null
            if (!rel.contains("icon")) return@mapNotNull null
            val href = attr(tag, "href") ?: return@mapNotNull null
            if (href.endsWith(".svg", true) || attr(tag, "type")?.contains("svg") == true) return@mapNotNull null
            val size = attr(tag, "sizes")?.substringBefore('x')?.toIntOrNull() ?: 32
            val bonus = if (rel.contains("apple-touch-icon")) 1000 else 0
            Icon(runCatching { URL(base, href).toString() }.getOrNull() ?: return@mapNotNull null, size + bonus)
        }.sortedByDescending { it.score }.map { it.url } +
            listOf(URL(base, "/apple-touch-icon.png").toString(), URL(base, "/favicon.ico").toString())

        return Page(parseColor(theme), icons.distinct())
    }

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(tag)?.let {
            it.groupValues[2].ifEmpty { it.groupValues[3] }.ifEmpty { it.groupValues[4] }
        }?.trim()

    // ---------- Цвета ----------

    private fun parseColor(s: String?): Int? {
        val v = s?.trim() ?: return null
        val hex = if (Regex("^#[0-9a-fA-F]{3}$").matches(v)) "#" + v.drop(1).map { "$it$it" }.joinToString("") else v
        return runCatching { Color.parseColor(hex) }.getOrNull()?.let { it or 0xFF000000.toInt() }
    }

    /** Белый, чёрный и серые theme-color не годятся как цвет карты. */
    private fun isUsableBrandColor(c: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        return hsv[1] > 0.18f && hsv[2] > 0.15f
    }

    private fun colorFromLogo(bmp: Bitmap): Int? {
        val p = Palette.from(bmp).maximumColorCount(16).generate()
        return (p.vibrantSwatch ?: p.darkVibrantSwatch ?: p.dominantSwatch)?.rgb
    }

    private fun darken(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] *= 0.68f
        hsv[1] = (hsv[1] * 1.08f).coerceAtMost(1f)
        hsv[0] = (hsv[0] + 8f) % 360f // лёгкий сдвиг оттенка — градиент выглядит живее
        return Color.HSVToColor(hsv)
    }

    // ---------- Сеть ----------

    private fun normalizeUrl(s: String): String = if (s.startsWith("http", true)) s else "https://$s"

    private fun downloadBitmap(url: String): Bitmap? {
        val bytes = get(url, 3 * 1024 * 1024, accept = "image/*").second
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return bmp.takeIf { it.width >= 32 && it.height >= 32 }
    }

    /** Логотип хранится не больше 256 px — достаточно для карты, файл маленький. */
    private fun fitLogo(bmp: Bitmap): Bitmap {
        val longest = max(bmp.width, bmp.height)
        if (longest <= 256) return bmp
        val k = 256f / longest
        return Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
    }

    /** GET с ручным проходом по редиректам (включая http → https). */
    private fun get(url: String, maxBytes: Int, accept: String = "application/json"): Pair<String, ByteArray> {
        var current = url
        repeat(7) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Language", "ru,en;q=0.8")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw FinderException("Ошибка перенаправления")
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) throw FinderException("Сервер ответил ошибкой ($code)")
                val out = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (out.size() < maxBytes) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
                return current to out.toByteArray()
            } finally {
                conn.disconnect()
            }
        }
        throw FinderException("Слишком много перенаправлений")
    }

    // ---------- Ссылки из QR-кодов ----------

    fun looksLikeUrl(raw: String): Boolean {
        val s = raw.trim().lowercase()
        return s.startsWith("http://") || s.startsWith("https://") ||
            Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+(/\\S*)?$").matches(s)
    }

    /** Итоговый адрес короткой ссылки из QR-кода (проход по редиректам). */
    fun resolveUrl(raw: String): String? =
        runCatching { get(normalizeUrl(raw.trim()), 64 * 1024, accept = "text/html").first }.getOrNull()
}
