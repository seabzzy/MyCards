package com.mycards.wallet.ui

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mycards.wallet.data.Brand
import com.mycards.wallet.data.BrandCatalog
import com.mycards.wallet.data.CardDraft
import com.mycards.wallet.data.CardRepository
import com.mycards.wallet.data.DesignStore
import com.mycards.wallet.data.DocumentRepository
import com.mycards.wallet.data.PdfDocument
import com.mycards.wallet.data.Logos
import com.mycards.wallet.data.LoyaltyCard
import com.mycards.wallet.data.OnlineLookup
import com.mycards.wallet.data.OnlineStatus
import com.mycards.wallet.data.Palette
import com.mycards.wallet.data.ScanResult
import com.mycards.wallet.data.Settings
import com.mycards.wallet.data.StoreCandidate
import com.mycards.wallet.data.StoreDesign
import com.mycards.wallet.online.FinderException
import com.mycards.wallet.online.StoreFinder
import com.mycards.wallet.recognition.CardRecognizer
import com.mycards.wallet.scan.ImageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    init { Logos.init(app) }

    private val repo = CardRepository(app)
    private val designs = DesignStore(app)
    val brands: List<Brand> = BrandCatalog.load(app)
    private val recognizer = CardRecognizer(brands)
    val settings = Settings(app)

    val cards: StateFlow<List<LoyaltyCard>> = repo.cards

    private val documentRepo = DocumentRepository(app)
    val documents: StateFlow<List<PdfDocument>> = documentRepo.documents

    fun documentFile(id: String) = documentRepo.fileOf(id)

    fun renameDocument(id: String, title: String) {
        if (title.isNotBlank()) documentRepo.rename(id, title.trim())
    }

    fun deleteDocument(id: String) = documentRepo.delete(id)

    /** Добавляет PDF в приложение. Возвращает false, если файл не удалось открыть. */
    suspend fun addDocument(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            documentRepo.add(getApplication(), uri)
            true
        } catch (e: IllegalArgumentException) {
            _messages.tryEmit(e.message ?: "Не удалось добавить файл")
            false
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось добавить файл")
            false
        }
    }

    private val _online = MutableStateFlow<OnlineLookup?>(null)
    /** Результат проверки ссылки из QR-кода для текущего черновика. */
    val online: StateFlow<OnlineLookup?> = _online
    private var onlineJob: Job? = null

    var busy by mutableStateOf(false)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun brand(id: String?): Brand? = brands.firstOrNull { it.id == id }

    // ---------- Оформления магазинов ----------

    /** Сохранённое оформление по названию (без сети). */
    fun savedDesign(name: String?): StoreDesign? = designs.find(name)

    fun savedDesign(brand: Brand): StoreDesign? = designs.findForBrand(brand)

    suspend fun searchStores(query: String): List<StoreCandidate> = withContext(Dispatchers.IO) {
        try {
            StoreFinder.search(query)
        } catch (e: IOException) {
            _messages.tryEmit("Нет подключения к интернету"); emptyList()
        } catch (e: Exception) {
            _messages.tryEmit("Поиск сейчас недоступен"); emptyList()
        }
    }

    /**
     * Находит в интернете логотип и цвета магазина, сохраняет их на телефоне
     * и запоминает под названием карты — следующие карты этого магазина оформятся без сети.
     */
    suspend fun fetchDesign(cardName: String, candidate: StoreCandidate?, manualSite: String?): StoreDesign? =
        withContext(Dispatchers.IO) {
            try {
                val found = StoreFinder.buildDesign(candidate, manualSite)
                val logoFile = found.logo?.let { Logos.save(it) }
                val name = cardName.trim().ifEmpty { candidate?.label.orEmpty() }
                val design = StoreDesign(
                    key = DesignStore.key(name),
                    name = name,
                    colorStart = found.colorStart,
                    colorEnd = found.colorEnd,
                    logoFile = logoFile,
                    website = found.website,
                )
                designs.save(design, *listOfNotNull(candidate?.label).toTypedArray())
                design
            } catch (e: FinderException) {
                _messages.tryEmit(e.message ?: "Не удалось загрузить оформление"); null
            } catch (e: IOException) {
                _messages.tryEmit("Нет подключения к интернету"); null
            } catch (e: Exception) {
                _messages.tryEmit("Не удалось загрузить оформление"); null
            }
        }

    // ---------- Черновики ----------

    /** Из результата сканирования — черновик с распознанным магазином. */
    fun draftFromScan(scan: ScanResult): CardDraft {
        val duplicate = cards.value.firstOrNull { it.number == scan.raw }
        if (duplicate != null) {
            _messages.tryEmit("Эта карта уже сохранена — «${duplicate.name}»")
            return draftFromCard(duplicate)
        }
        val r = recognizer.recognize(scan.raw, scan.format, scan.ocrText, cards.value)
        val brand = r.best?.brand
        val similar = r.similarCard?.takeIf { brand == null || it.brandId == brand.id }
        val saved = brand?.let { savedDesign(it) } ?: similar?.let { savedDesign(it.name) }

        val colors = when {
            saved != null -> saved.colorStart to saved.colorEnd
            brand != null -> brand.colorStart to brand.colorEnd
            similar != null -> similar.colorStart to similar.colorEnd
            else -> Palette.presets[(scan.raw.hashCode() and 0x7fffffff) % Palette.presets.size]
        }
        val draft = CardDraft(
            name = brand?.name ?: similar?.name ?: "",
            brandId = brand?.id ?: similar?.brandId,
            number = scan.raw,
            format = scan.format,
            colorStart = colors.first,
            colorEnd = colors.second,
            logoFile = saved?.logoFile ?: similar?.logoFile,
            recognition = r,
        )
        val confident = (r.best?.score ?: 0f) >= 0.9f
        return if (!confident && settings.onlineEnabled && StoreFinder.looksLikeUrl(scan.raw)) {
            draft.copy(lookupId = resolveQrLink(scan))
        } else draft
    }

    /** Ссылка из QR-кода → настоящий сайт магазина → сверка с каталогом. */
    private fun resolveQrLink(scan: ScanResult): Long {
        val id = System.nanoTime()
        onlineJob?.cancel()
        _online.value = OnlineLookup(id, OnlineStatus.Loading)
        onlineJob = viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                val finalUrl = StoreFinder.resolveUrl(scan.raw)
                when {
                    finalUrl == null -> OnlineStatus.Failed("ссылка из QR-кода не открылась")
                    else -> {
                        val best = recognizer.recognize(finalUrl, scan.format, "", emptyList()).best
                        if (best == null) OnlineStatus.NotFound("Ссылка из QR-кода ведёт на неизвестный сайт")
                        else OnlineStatus.Found(
                            best.brand.name, best.brand.id, best.brand.colorStart, best.brand.colorEnd,
                            best.score, "по ссылке из QR-кода",
                        )
                    }
                }
            }
            if (_online.value?.id == id) _online.value = OnlineLookup(id, status)
        }
        return id
    }

    fun draftFromCard(c: LoyaltyCard) = CardDraft(
        id = c.id, name = c.name, brandId = c.brandId, number = c.number, format = c.format,
        colorStart = c.colorStart, colorEnd = c.colorEnd, note = c.note, logoFile = c.logoFile,
    )

    suspend fun draftFromImage(uri: Uri): CardDraft? {
        busy = true
        return try {
            val scan = withContext(Dispatchers.Default) { ImageScanner.scanUri(getApplication(), uri) }
            if (scan == null) {
                _messages.tryEmit("На изображении не найден штрихкод или QR-код")
                null
            } else draftFromScan(scan)
        } catch (e: Exception) {
            _messages.tryEmit("Не удалось открыть изображение")
            null
        } finally {
            busy = false
        }
    }

    fun saveSettings(enabled: Boolean) {
        settings.onlineEnabled = enabled
        _messages.tryEmit("Настройки сохранены")
    }

    // ---------- Карты ----------

    fun save(d: CardDraft): String {
        val existing = d.id?.let { id -> cards.value.firstOrNull { it.id == id } }
        val name = d.name.trim().ifEmpty { "Карта" }
        val card = LoyaltyCard(
            id = d.id ?: UUID.randomUUID().toString(),
            name = name,
            brandId = d.brandId,
            number = d.number.trim(),
            format = d.format,
            colorStart = d.colorStart,
            colorEnd = d.colorEnd,
            note = d.note.trim(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            useCount = existing?.useCount ?: 0,
            logoFile = d.logoFile,
        )
        repo.upsert(card)
        // Запоминаем оформление под названием карты — для следующих карт этого магазина.
        if (d.logoFile != null) {
            val prev = designs.find(name)
            designs.save(
                StoreDesign(DesignStore.key(name), name, d.colorStart, d.colorEnd, d.logoFile, prev?.website),
                *listOfNotNull(brand(d.brandId)?.name).toTypedArray(),
            )
        }
        return card.id
    }

    fun delete(id: String) = repo.delete(id)

    fun markUsed(id: String) = repo.markUsed(id)

    // ---------- Резервная копия (вместе с логотипами) ----------

    fun exportTo(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val root = JSONObject(repo.exportJson())
                val logos = JSONObject()
                cards.value.mapNotNull { it.logoFile }.distinct().forEach { f ->
                    Logos.bytes(f)?.let { logos.put(f, Base64.encodeToString(it, Base64.NO_WRAP)) }
                }
                root.put("logos", logos)
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(root.toString().toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
        }
        _messages.tryEmit(if (ok) "Карты сохранены в файл" else "Не удалось сохранить файл")
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val added = withContext(Dispatchers.IO) {
            runCatching {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                JSONObject(text).optJSONObject("logos")?.let { logos ->
                    logos.keys().forEach { name ->
                        runCatching { Logos.saveBytes(name, Base64.decode(logos.getString(name), Base64.DEFAULT)) }
                    }
                }
                repo.importJson(text)
            }.getOrNull()
        }
        _messages.tryEmit(
            when (added) {
                null -> "Файл не похож на резервную копию карт"
                0 -> "Новых карт в файле нет"
                else -> "Добавлено карт: $added"
            }
        )
    }
}
