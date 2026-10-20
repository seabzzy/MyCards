package com.mycards.wallet.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Логотипы магазинов: файлы в памяти приложения + кэш картинок для интерфейса. */
object Logos {
    private var dir: File? = null
    private val cache = LruCache<String, ImageBitmap>(64)

    fun init(context: Context) {
        if (dir == null) dir = File(context.applicationContext.filesDir, "logos").apply { mkdirs() }
    }

    fun file(name: String): File? = dir?.let { File(it, name) }

    fun save(bitmap: Bitmap): String? {
        val name = "logo_${UUID.randomUUID()}.png"
        val f = file(name) ?: return null
        return try {
            FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            name
        } catch (e: IOException) {
            null
        }
    }

    fun saveBytes(name: String, bytes: ByteArray) {
        if (!name.matches(Regex("^[A-Za-z0-9_.-]+$"))) return
        val f = file(name) ?: return
        if (!f.exists()) runCatching { f.writeBytes(bytes) }
    }

    fun bytes(name: String): ByteArray? = file(name)?.takeIf { it.exists() }?.readBytes()

    fun image(name: String?): ImageBitmap? {
        if (name == null) return null
        cache.get(name)?.let { return it }
        val f = file(name)?.takeIf { it.exists() } ?: return null
        val bmp = BitmapFactory.decodeFile(f.path)?.asImageBitmap() ?: return null
        cache.put(name, bmp)
        return bmp
    }
}

/**
 * Сохранённые оформления магазинов. Ищутся один раз в интернете,
 * затем применяются к новым картам того же магазина без сети.
 */
class DesignStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "designs.json"))
    private val designs = MutableStateFlow(load())

    fun find(name: String?): StoreDesign? {
        if (name.isNullOrBlank()) return null
        return designs.value[key(name)]
    }

    fun findForBrand(brand: Brand): StoreDesign? =
        find(brand.name) ?: brand.aliases.firstNotNullOfOrNull { find(it) }

    @Synchronized
    fun save(design: StoreDesign, vararg extraNames: String) {
        val map = designs.value.toMutableMap()
        map[design.key] = design
        extraNames.map(::key).filter { it.isNotEmpty() }.forEach { map[it] = design.copy(key = it) }
        designs.value = map
        persist(map)
    }

    private fun load(): Map<String, StoreDesign> = try {
        val o = JSONObject(String(file.readFully(), Charsets.UTF_8))
        o.keys().asSequence().associateWith { k ->
            val d = o.getJSONObject(k)
            StoreDesign(
                key = k,
                name = d.optString("name"),
                colorStart = d.optInt("colorStart"),
                colorEnd = d.optInt("colorEnd"),
                logoFile = if (d.isNull("logoFile")) null else d.optString("logoFile").ifEmpty { null },
                website = if (d.isNull("website")) null else d.optString("website").ifEmpty { null },
                savedAt = d.optLong("savedAt"),
            )
        }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun persist(map: Map<String, StoreDesign>) {
        val o = JSONObject()
        map.forEach { (k, d) ->
            o.put(k, JSONObject().apply {
                put("name", d.name)
                put("colorStart", d.colorStart)
                put("colorEnd", d.colorEnd)
                put("logoFile", d.logoFile ?: JSONObject.NULL)
                put("website", d.website ?: JSONObject.NULL)
                put("savedAt", d.savedAt)
            })
        }
        var out: FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(o.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            out?.let { file.failWrite(it) }
        }
    }

    companion object {
        /** «Пятёрочка», «ПЯТЕРОЧКА », «пятерочка!» → один и тот же ключ. */
        fun key(name: String): String =
            name.lowercase().replace('ё', 'е').filter { it.isLetterOrDigit() }
    }
}
