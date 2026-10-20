package com.mycards.wallet.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Хранит карты локально в JSON-файле внутри приложения. Никакой сети. */
class CardRepository(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "cards.json"))
    private val _cards = MutableStateFlow(load())
    val cards: StateFlow<List<LoyaltyCard>> = _cards.asStateFlow()

    private fun load(): List<LoyaltyCard> = try {
        CardJson.decode(String(file.readFully(), Charsets.UTF_8))
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun persist(list: List<LoyaltyCard>) {
        _cards.value = list
        var out: FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(CardJson.encode(list).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            out?.let { file.failWrite(it) }
        }
    }

    fun upsert(card: LoyaltyCard) = persist(_cards.value.filterNot { it.id == card.id } + card)

    fun delete(id: String) = persist(_cards.value.filterNot { it.id == id })

    fun markUsed(id: String) = persist(_cards.value.map {
        if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis(), useCount = it.useCount + 1) else it
    })

    fun exportJson(): String = CardJson.encode(_cards.value)

    /** Импорт с объединением: дубликаты (тот же id или номер+формат) пропускаются. */
    fun importJson(json: String): Int {
        val incoming = CardJson.decode(json)
        val current = _cards.value.toMutableList()
        var added = 0
        for (c in incoming) {
            if (current.none { it.id == c.id || (it.number == c.number && it.format == c.format) }) {
                current += c
                added++
            }
        }
        if (added > 0) persist(current)
        return added
    }
}

object CardJson {
    fun encode(list: List<LoyaltyCard>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("brandId", c.brandId ?: JSONObject.NULL)
                put("number", c.number)
                put("format", c.format.name)
                put("colorStart", c.colorStart)
                put("colorEnd", c.colorEnd)
                put("note", c.note)
                put("createdAt", c.createdAt)
                put("lastUsedAt", c.lastUsedAt)
                put("useCount", c.useCount)
                put("logoFile", c.logoFile ?: JSONObject.NULL)
            })
        }
        return JSONObject().put("version", 1).put("cards", arr).toString(2)
    }

    fun decode(json: String): List<LoyaltyCard> {
        val arr = JSONObject(json).getJSONArray("cards")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val number = o.optString("number")
            if (number.isEmpty()) return@mapNotNull null
            LoyaltyCard(
                id = o.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                name = o.optString("name"),
                brandId = if (o.isNull("brandId")) null else o.optString("brandId"),
                number = number,
                format = CardFormat.fromName(o.optString("format")),
                colorStart = o.optInt("colorStart", Palette.presets[0].first),
                colorEnd = o.optInt("colorEnd", Palette.presets[0].second),
                note = o.optString("note"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                lastUsedAt = o.optLong("lastUsedAt", System.currentTimeMillis()),
                useCount = o.optInt("useCount", 0),
                logoFile = if (o.isNull("logoFile")) null else o.optString("logoFile").ifEmpty { null },
            )
        }
    }
}
