package com.mycards.wallet.pdf

import android.graphics.Bitmap
import android.graphics.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.roundToInt

/**
 * Открытый PDF для просмотра. Использует встроенный в Android PdfRenderer —
 * работает полностью офлайн и без сторонних библиотек.
 * PdfRenderer не потокобезопасен, поэтому все обращения идут через один Mutex.
 */
class PdfSession private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val pageCount: Int = renderer.pageCount
    private val lock = Mutex()

    /** Высота страницы / ширина страницы (для A4 примерно 1.41). */
    suspend fun aspect(index: Int): Float = lock.withLock {
        renderer.openPage(index).use { it.height.toFloat() / it.width.toFloat() }
    }

    /** Рисует страницу в растр нужной ширины на белом фоне. */
    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        renderer.openPage(index).use { page ->
            val heightPx = (widthPx * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        /** Открывает файл. Бросает исключение, если это не PDF или он защищён паролем. */
        fun open(file: File): PdfSession {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount == 0) {
                    renderer.close()
                    throw IllegalArgumentException("В документе нет страниц")
                }
                PdfSession(pfd, renderer)
            } catch (e: Exception) {
                pfd.close()
                throw e
            }
        }

        fun pageCount(file: File): Int {
            val session = open(file)
            return try {
                session.pageCount
            } finally {
                session.close()
            }
        }
    }
}
