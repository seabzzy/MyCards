package com.mycards.wallet.data

import android.content.Context
import android.graphics.Color
import org.json.JSONArray

/** Офлайн-каталог магазинов из assets/brands.json. Его можно свободно дополнять. */
object BrandCatalog {
    fun load(context: Context): List<Brand> = try {
        val text = context.assets.open("brands.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val colors = o.getJSONArray("colors")
            Brand(
                id = o.getString("id"),
                name = o.getString("name"),
                category = o.optString("category", "Другое"),
                aliases = o.optJSONArray("aliases").strings(),
                domains = o.optJSONArray("domains").strings().map { it.lowercase() },
                colorStart = Color.parseColor(colors.getString(0)),
                colorEnd = Color.parseColor(colors.getString(1)),
                numberPatterns = o.optJSONArray("numberPatterns").strings().mapNotNull {
                    runCatching { Regex(it) }.getOrNull()
                },
            )
        }.sortedBy { it.name.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
