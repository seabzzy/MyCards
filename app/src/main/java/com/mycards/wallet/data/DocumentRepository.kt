package com.mycards.wallet.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import com.mycards.wallet.pdf.PdfSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Документ PDF, сохранённый внутри приложения. */
data class PdfDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val addedAt: Long,
    val pinned: Boolean = false,
    val pinOrder: Long = 0L,
)

/** Закреплённые сверху в заданном порядке, остальные — новые выше. */
fun List<PdfDocument>.inListOrder(): List<PdfDocument> = sortedWith(
    compareByDescending<PdfDocument> { it.pinned }
        .thenBy { if (it.pinned) it.pinOrder else 0L }
        .thenByDescending { it.addedAt }
)

/**
 * Хранилище PDF-документов. Файлы лежат в filesDir/documents, список — в documents.json.
 * Всё внутри приложения: ни одна страница никуда не отправляется.
 */
class DocumentRepository(context: Context) {

    private val dir = File(context.filesDir, "documents").apply { mkdirs() }
    private val index = AtomicFile(File(context.filesDir, "documents.json"))
    private val _documents = MutableStateFlow(load())
    val documents: StateFlow<List<PdfDocument>> = _documents.asStateFlow()

    fun fileOf(id: String): File? =
        _documents.value.firstOrNull { it.id == id }
            ?.let { File(dir, it.fileName) }
            ?.takeIf { it.exists() }

    /** Копирует выбранный PDF в приложение. Бросает IllegalArgumentException, если файл — не PDF. */
    @Synchronized
    fun add(context: Context, uri: Uri): PdfDocument {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

        val fileName = "${UUID.randomUUID()}.pdf"
        val target = File(dir, fileName)
        val input = resolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
        input.use { source -> FileOutputStream(target).use { source.copyTo(it) } }

        val pages = try {
            PdfSession.pageCount(target)
        } catch (e: Exception) {
            target.delete()
            throw IllegalArgumentException("Файл не открывается как PDF или защищён паролем")
        }

        val title = (displayName ?: "Документ").replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "")
        val doc = PdfDocument(
            id = fileName.removeSuffix(".pdf"),
            title = title.ifBlank { "Документ" },
            fileName = fileName,
            pageCount = pages,
            sizeBytes = target.length(),
            addedAt = System.currentTimeMillis(),
        )
        persist(_documents.value + doc)
        return doc
    }

    /** Переименование: меняется только отображаемое название, файл остаётся. */
    @Synchronized
    fun setPinned(id: String, pinned: Boolean) {
        val next = (_documents.value.filter { it.pinned }.maxOfOrNull { it.pinOrder } ?: 0L) + 1
        persist(_documents.value.map {
            if (it.id == id) it.copy(pinned = pinned, pinOrder = if (pinned) next else 0L) else it
        })
    }

    @Synchronized
    fun reorderPinned(ids: List<String>) = persist(_documents.value.map { d ->
        val i = ids.indexOf(d.id)
        if (i >= 0) d.copy(pinOrder = i.toLong()) else d
    })

    @Synchronized
    fun rename(id: String, title: String) {
        persist(_documents.value.map { if (it.id == id) it.copy(title = title) else it })
    }

    /** Удаление: убирает запись и сам файл PDF. */
    @Synchronized
    fun delete(id: String) {
        val doc = _documents.value.firstOrNull { it.id == id } ?: return
        File(dir, doc.fileName).delete()
        persist(_documents.value.filterNot { it.id == id })
    }

    private fun load(): List<PdfDocument> {
        if (!index.baseFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(String(index.readFully(), Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PdfDocument(
                    id = o.getString("id"),
                    title = o.optString("title", "Документ"),
                    fileName = o.getString("fileName"),
                    pageCount = o.optInt("pageCount"),
                    sizeBytes = o.optLong("sizeBytes"),
                    addedAt = o.optLong("addedAt"),
                    pinned = o.optBoolean("pinned", false),
                    pinOrder = o.optLong("pinOrder", 0L),
                )
            }
        } catch (e: Exception) {
            // Не перезаписываем повреждённый список: сохраняем копию файла.
            runCatching {
                index.baseFile.copyTo(File(index.baseFile.parentFile, "documents-broken-${System.currentTimeMillis()}.json"))
            }
            emptyList()
        }
    }

    @Synchronized
    private fun persist(list: List<PdfDocument>) {
        _documents.value = list
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("title", d.title)
                put("fileName", d.fileName)
                put("pageCount", d.pageCount)
                put("sizeBytes", d.sizeBytes)
                put("addedAt", d.addedAt)
                put("pinned", d.pinned)
                put("pinOrder", d.pinOrder)
            })
        }
        var out: FileOutputStream? = null
        try {
            out = index.startWrite()
            out.write(arr.toString().toByteArray(Charsets.UTF_8))
            index.finishWrite(out)
        } catch (e: IOException) {
            out?.let { index.failWrite(it) }
        }
    }
}
