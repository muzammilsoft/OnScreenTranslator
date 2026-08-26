package com.example.domain.managers

import android.util.LruCache
import com.example.data.local.cache.AppDatabase
import com.example.data.local.cache.TranslationEntity
import com.example.data.providers.translation.OfflineMlKitTranslator
import com.example.data.providers.translation.OfflineNllbTranslator
import com.example.data.providers.translation.OnlineBaiduTranslator
import com.example.domain.interfaces.Translator
import com.example.domain.state.TargetLanguage
import com.example.domain.state.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Coordinates Room DB caching, in-memory LRU cache, batching, and resilient offline/online fallback.
 */
class TranslationManager(
    private val database: AppDatabase
) {
    private var currentTranslator: Translator = OfflineMlKitTranslator()
    private val memoryLruCache = LruCache<String, String>(50) // Fast 50-item cache for UI
    private var isOfflineOnly: Boolean = false
    private var targetLangCode: String = "ar"

    fun updateSettings(engine: TranslationEngine, targetLang: TargetLanguage, offlineOnly: Boolean) {
        this.targetLangCode = targetLang.code
        this.isOfflineOnly = offlineOnly

        currentTranslator = if (offlineOnly) {
            when (engine) {
                TranslationEngine.NLLB_TFLITE -> OfflineNllbTranslator()
                else -> OfflineMlKitTranslator()
            }
        } else {
            when (engine) {
                TranslationEngine.MLKIT_TRANSLATE -> OfflineMlKitTranslator()
                TranslationEngine.NLLB_TFLITE -> OfflineNllbTranslator()
                TranslationEngine.BAIDU_ONLINE -> OnlineBaiduTranslator()
            }
        }
    }

    /**
     * Translates a single text string with cache verification, fallback, and retry logic.
     */
    suspend fun translateText(
        sourceText: String,
        sourceLang: String = "zh"
    ): String = withContext(Dispatchers.IO) {
        val trimmed = sourceText.trim()
        if (trimmed.isEmpty()) return@withContext ""

        val cacheKey = "${trimmed}_${sourceLang}_${targetLangCode}"
        
        // 1. In-memory LRU cache check
        memoryLruCache.get(cacheKey)?.let { return@withContext it }

        // 2. Room Database persistence cache check
        val cachedEntity = database.translationDao().getTranslation(trimmed, sourceLang, targetLangCode)
        if (cachedEntity != null) {
            database.translationDao().updateHit(cachedEntity.id)
            memoryLruCache.put(cacheKey, cachedEntity.targetText)
            return@withContext cachedEntity.targetText
        }

        // 3. Translate using current provider with fallback logic
        var resultText: String? = null
        val primary = currentTranslator

        val primaryResult = primary.translate(trimmed, sourceLang, targetLangCode)
        if (primaryResult.isSuccess) {
            resultText = primaryResult.getOrNull()
        } else if (!primary.isOffline) {
            // Online provider failed -> fallback immediately to offline
            val offlineFallback = OfflineMlKitTranslator()
            val fallbackRes = offlineFallback.translate(trimmed, sourceLang, targetLangCode)
            if (fallbackRes.isSuccess) {
                resultText = fallbackRes.getOrNull()
            }
        }

        // If still null, retry up to 2 times with 500ms delay
        if (resultText.isNullOrBlank()) {
            for (retry in 1..2) {
                delay(500L)
                val retryRes = OfflineMlKitTranslator().translate(trimmed, sourceLang, targetLangCode)
                if (retryRes.isSuccess && !retryRes.getOrNull().isNullOrBlank()) {
                    resultText = retryRes.getOrNull()
                    break
                }
            }
        }

        val finalized = resultText ?: trimmed

        // Save into caches
        memoryLruCache.put(cacheKey, finalized)
        database.translationDao().insertTranslation(
            TranslationEntity(
                sourceText = trimmed,
                targetText = finalized,
                sourceLang = sourceLang,
                targetLang = targetLangCode,
                provider = primary.providerName
            )
        )

        // Evict translations older than 7 days
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        database.translationDao().evictStaleTranslations(sevenDaysAgo)

        finalized
    }

    /**
     * Batch translation for up to 5 UI text elements.
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLang: String = "zh"
    ): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<String>()
        val unCachedIndices = mutableListOf<Int>()
        val unCachedTexts = mutableListOf<String>()

        // Check cache first
        for (i in texts.indices) {
            val text = texts[i].trim()
            val cacheKey = "${text}_${sourceLang}_${targetLangCode}"
            val memVal = memoryLruCache.get(cacheKey)
            if (memVal != null) {
                results.add(memVal)
            } else {
                val dbVal = database.translationDao().getTranslation(text, sourceLang, targetLangCode)
                if (dbVal != null) {
                    memoryLruCache.put(cacheKey, dbVal.targetText)
                    results.add(dbVal.targetText)
                } else {
                    results.add("") // Placeholder
                    unCachedIndices.add(i)
                    unCachedTexts.add(text)
                }
            }
        }

        if (unCachedTexts.isNotEmpty()) {
            // Batch translate up to 5 at a time
            val chunks = unCachedTexts.chunked(5)
            var currentChunkOffset = 0

            for (chunk in chunks) {
                val batchRes = currentTranslator.translateBatch(chunk, sourceLang, targetLangCode)
                val translatedChunk = if (batchRes.isSuccess) {
                    batchRes.getOrNull() ?: chunk.map { OfflineMlKitTranslator().translate(it, sourceLang, targetLangCode).getOrDefault(it) }
                } else {
                    chunk.map { OfflineMlKitTranslator().translate(it, sourceLang, targetLangCode).getOrDefault(it) }
                }

                for (cIdx in translatedChunk.indices) {
                    val originalIdx = unCachedIndices[currentChunkOffset + cIdx]
                    val srcText = chunk[cIdx]
                    val transText = translatedChunk[cIdx]
                    results[originalIdx] = transText

                    // Update caches
                    val cacheKey = "${srcText}_${sourceLang}_${targetLangCode}"
                    memoryLruCache.put(cacheKey, transText)
                    database.translationDao().insertTranslation(
                        TranslationEntity(
                            sourceText = srcText,
                            targetText = transText,
                            sourceLang = sourceLang,
                            targetLang = targetLangCode,
                            provider = currentTranslator.providerName
                        )
                    )
                }
                currentChunkOffset += chunk.size
            }
        }

        results
    }
}
