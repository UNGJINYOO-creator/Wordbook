package com.wordbook.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One headword read by the AI: spelling, part of speech ("명", "동", …) and the Korean meaning. */
data class AiWord(val word: String, val pos: String, val meaning: String)

/**
 * The Gemini API key, kept only on this phone: encrypted with a key from the Android Keystore, in its own
 * preference file that family sync doesn't send. A copied backup can't be decrypted on another phone.
 */
object AiKey {
    private const val ALIAS = "wordbook_ai_key"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ai", Context.MODE_PRIVATE)

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun get(context: Context): String {
        val stored = prefs(context).getString("key", null) ?: return ""
        return runCatching {
            val (iv, data) = stored.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv))
            String(c.doFinal(data), Charsets.UTF_8)
        }.getOrDefault("")
    }

    /** Saves (or with "" removes) the key; false if this phone couldn't encrypt it. */
    fun set(context: Context, key: String): Boolean {
        val clean = key.trim()
        if (clean.isEmpty()) { prefs(context).edit().remove("key").apply(); return true }
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, secret())
            val enc = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(c.doFinal(clean.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            prefs(context).edit().putString("key", enc).commit()
        }.getOrDefault(false)
    }

    /** The AI reading can be switched off without deleting the key. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("enabled", on).apply()

    fun ready(context: Context): Boolean = enabled(context) && get(context).isNotBlank()
}

/**
 * Reads a vocabulary page with Google's Gemini (free tier): the photo goes to the AI, which answers with
 * the big headwords, their part of speech and the Korean meaning printed in the book.
 */
object GeminiVision {
    /**
     * Tried in order. The free tier allows only a few Flash photos a day; when they run out (HTTP 429) the
     * Flash-Lite models, which allow many more, take over.
     */
    val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-flash-lite-latest")
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    sealed class Outcome {
        data class Words(val words: List<AiWord>, val model: String) : Outcome()
        /** Couldn't use the AI; [reason] is shown on the scan screen and the old reader is used instead. */
        data class Failed(val reason: String) : Outcome()
    }

    val PROMPT = """
        This is a photo of a page from an English vocabulary book for Korean students.
        List ONLY the headwords: the main entry words printed in the biggest English type, in reading order
        (column by column, top to bottom). Do NOT include derivatives, related words, phrases or idioms in
        smaller print, example sentences, pronunciation, page titles, or handwritten notes.

        For each headword return:
        - "word": the exact spelling of the headword (lowercase unless it is a proper noun).
        - "pos": its main part of speech in Korean, exactly one of 명, 동, 형, 부, 전, 접, 대, 감.
        - "meaning": the Korean meaning printed next to that headword in the book, copied as printed
          (several senses separated by ", "). It must fit the part of speech: a verb meaning ends like
          "~하다/~다" (connect → 연결하다), an adjective like "~한/~ㄴ/~운" (helpful → 도움이 되는), an
          adverb like "~하게/~히/~이", a noun is a noun (computer → 컴퓨터). If the book gives meanings for
          another part of speech too, put them after " / " (for example "연결하다 / 연결").
          If a printed letter is unclear, write the correct Korean word. If no meaning is printed, write
          the usual Korean meaning for a middle/high school student.
        - "from_book": true if the meaning was printed in the book, false if you had to supply it.

        Answer with a JSON array only, for example:
        [{"word":"connect","pos":"동","meaning":"연결하다, 잇다","from_book":true}]
        If there are no English headwords, answer [].
    """.trimIndent()

    private class HttpError(val code: Int, val body: String) : IOException("HTTP $code")

    private fun request(url: String, key: String, body: JSONObject?, timeoutMs: Int): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("x-goog-api-key", key)
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpError(code, text)
            return text
        } finally {
            conn.disconnect()
        }
    }

    /** What a failed key or request means, in words for a parent. */
    private fun explain(e: HttpError): String {
        val msg = runCatching { JSONObject(e.body).getJSONObject("error").optString("message") }.getOrDefault("")
        return when {
            e.code == 400 && msg.contains("API key", ignoreCase = true) -> "API 키가 맞지 않아요. 설정에서 키를 다시 확인해 주세요."
            e.code == 400 && msg.contains("location", ignoreCase = true) -> "이 지역에서는 제미나이 무료 API를 쓸 수 없대요."
            e.code == 401 || e.code == 403 -> "API 키를 쓸 수 없어요 (권한 없음). 설정에서 키를 다시 확인해 주세요."
            e.code == 429 -> "오늘 무료 사용량을 다 썼어요. 내일 다시 AI로 읽을 수 있어요."
            e.code >= 500 -> "제미나이 서버가 바빠요. 잠시 뒤 다시 해 보세요."
            else -> "AI가 응답하지 않았어요 (${e.code})."
        }
    }

    /** Checks a key without spending any of the day's photo quota (lists the models). */
    suspend fun testKey(key: String): String? = withContext(Dispatchers.IO) {
        try {
            request("$BASE/models?pageSize=50", key.trim(), null, 20_000)
            null
        } catch (e: HttpError) {
            explain(e)
        } catch (e: IOException) {
            "인터넷에 연결할 수 없어요."
        }
    }

    /** JPEG of the photo, at most 2048 pixels on its longest side (enough for the print, small to send). */
    fun encode(photo: Bitmap, maxSide: Int = 2048): String {
        val longest = maxOf(photo.width, photo.height)
        val bmp = if (longest > maxSide) {
            val k = maxSide / longest.toFloat()
            Bitmap.createScaledBitmap(photo, (photo.width * k).toInt().coerceAtLeast(1), (photo.height * k).toInt().coerceAtLeast(1), true)
        } else photo
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (bmp !== photo) bmp.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun read(key: String, jpegBase64: String): Outcome = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", jpegBase64)))
                .put(JSONObject().put("text", PROMPT)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        var last: Outcome.Failed = Outcome.Failed("AI가 응답하지 않았어요.")
        for (model in MODELS) {
            try {
                val text = request("$BASE/models/$model:generateContent", key, body, 120_000)
                val words = parse(answerText(text)) ?: run {
                    last = Outcome.Failed("AI의 답을 읽지 못했어요.")
                    null
                } ?: continue
                return@withContext Outcome.Words(words, model)
            } catch (e: HttpError) {
                last = Outcome.Failed(explain(e))
                // Out of today's quota, or this model isn't offered (any more): try the next one.
                if (e.code == 429 || e.code == 404 || e.code >= 500) continue
                return@withContext last
            } catch (e: IOException) {
                return@withContext Outcome.Failed("인터넷에 연결할 수 없어서 AI 대신 기존 방식으로 읽었어요.")
            } catch (e: Exception) {
                // An answer in an unexpected shape: try the next model.
                last = Outcome.Failed("AI의 답을 읽지 못했어요.")
            }
        }
        last
    }

    /** The model's answer text (thinking parts left out). */
    fun answerText(response: String): String {
        val parts = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        return (0 until parts.length()).map { parts.getJSONObject(it) }
            .filter { !it.optBoolean("thought", false) }
            .joinToString("") { it.optString("text") }
    }

    private val posNames = mapOf(
        "명사" to "명", "동사" to "동", "형용사" to "형", "부사" to "부", "전치사" to "전", "접속사" to "접", "대명사" to "대", "감탄사" to "감",
        "n" to "명", "v" to "동", "adj" to "형", "adv" to "부", "prep" to "전", "conj" to "접", "pron" to "대",
    )

    /** The words in the AI's JSON answer, or null when it isn't the JSON asked for. Pure, for tests. */
    fun parse(answer: String): List<AiWord>? {
        val start = answer.indexOf('[')
        val end = answer.lastIndexOf(']')
        if (start < 0 || end < start) return null
        val arr = runCatching { JSONArray(answer.substring(start, end + 1)) }.getOrNull() ?: return null
        val wordRegex = Regex("^[A-Za-z]+(?:['\\- ][A-Za-z]+)*$")
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { o ->
            val word = o.optString("word").trim()
            if (!wordRegex.matches(word)) return@mapNotNull null
            val rawPos = o.optString("pos").trim().trimEnd('.')
            val pos = posNames[rawPos.lowercase()] ?: rawPos.takeIf { it in posNames.values } ?: ""
            AiWord(word.lowercase().replace(Regex("\\s+"), " "), pos, o.optString("meaning").trim())
        }.distinctBy { it.word }
    }
}
