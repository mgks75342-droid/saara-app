package com.saara

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    private const val MODEL = "gemini-2.0-flash"

    // Calls Gemini and returns the reply text. Must be called from a background thread.
    fun ask(apiKey: String, prompt: String): String {
        if (apiKey.isBlank()) return "Gemini key সেট করা নেই। Settings-এ key বসান।"
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
        val body = JSONObject().put(
            "contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put(
                    "text", "তুমি Saara, একটি বন্ধুত্বপূর্ণ ভয়েস সহকারী। সংক্ষেপে বাংলায় উত্তর দাও। প্রশ্ন: $prompt"
                )))
            )
        ).toString()
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
            if (code !in 200..299) return "উত্তর আনতে সমস্যা হলো, কোড $code"
            JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                .replace("*", "").trim()
        } catch (e: Exception) {
            "ইন্টারনেট সমস্যা: ${e.message}"
        } finally {
            conn.disconnect()
        }
    }
}
