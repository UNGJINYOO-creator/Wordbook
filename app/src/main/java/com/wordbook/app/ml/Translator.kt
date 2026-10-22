package com.wordbook.app.ml

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** English -> Korean on-device translation. The model (~30MB) is downloaded once on first use. */
object EnKoTranslator {
    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.KOREAN)
                .build()
        )
    }

    suspend fun ensureModel() {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
    }

    suspend fun translate(text: String): String = translator.translate(text).await()
}
