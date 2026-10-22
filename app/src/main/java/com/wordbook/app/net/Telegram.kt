package com.wordbook.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal Telegram Bot API client: send a message, and find the chat id of whoever messaged the bot. */
object Telegram {
    private fun call(token: String, method: String, params: Map<String, String>): JSONObject {
        val body = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val conn = URL("https://api.telegram.org/bot$token/$method").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            return JSONObject(stream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Returns null on success, or an error message to show. */
    suspend fun send(token: String, chatId: String, text: String): String? = withContext(Dispatchers.IO) {
        if (token.isBlank() || chatId.isBlank()) return@withContext "봇 토큰과 채팅 ID를 먼저 입력하세요"
        runCatching {
            val r = call(token, "sendMessage", mapOf("chat_id" to chatId, "text" to text))
            if (r.optBoolean("ok")) null else r.optString("description", "보내기 실패")
        }.getOrElse { "연결 실패: ${it.message}" }
    }

    /** Chat id of the most recent message sent to the bot (the user presses Start first). */
    suspend fun findChatId(token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val r = call(token, "getUpdates", emptyMap())
            if (!r.optBoolean("ok")) error(r.optString("description", "토큰을 확인하세요"))
            val updates = r.getJSONArray("result")
            for (i in updates.length() - 1 downTo 0) {
                val msg = updates.getJSONObject(i).optJSONObject("message") ?: continue
                return@runCatching msg.getJSONObject("chat").getLong("id").toString()
            }
            error("봇에게 온 메시지가 없어요. 텔레그램에서 봇에게 /start를 보낸 뒤 다시 눌러 주세요")
        }
    }
}
