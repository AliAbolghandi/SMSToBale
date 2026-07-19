package com.example.smstobale

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SendMessageWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val KEY_WEBAPP_URL = "webAppUrl"
        const val KEY_CHAT_ID = "chatId"
        const val KEY_SECRET_KEY = "secretKey"
        const val KEY_MESSAGE = "message"
        const val KEY_SENDER = "sender"
        private const val MAX_ATTEMPTS = 6
    }

    override fun doWork(): Result {
        val webAppUrl = inputData.getString(KEY_WEBAPP_URL) ?: return Result.failure()
        val chatIdsRaw = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
        val secretKey = inputData.getString(KEY_SECRET_KEY) ?: ""
        val message = inputData.getString(KEY_MESSAGE) ?: ""
        val sender = inputData.getString(KEY_SENDER) ?: ""

        val chatIds = chatIdsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (chatIds.isEmpty()) return Result.failure()

        val dbHelper = LogDbHelper(applicationContext)
        val attemptResults = mutableListOf<String>()
        var allSuccess = true

        for (chatId in chatIds) {
            try {
                val code = sendToBale(webAppUrl, chatId, message, secretKey)
                attemptResults.add("$chatId: $code")
                if (code !in 200..299) allSuccess = false
            } catch (e: Exception) {
                attemptResults.add("$chatId: خطا")
                allSuccess = false
            }
        }

        val detail = attemptResults.joinToString(" | ")

        return when {
            allSuccess -> {
                dbHelper.insertLog(sender, message, LogDbHelper.STATUS_SUCCESS, detail)
                Result.success()
            }
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> {
                dbHelper.insertLog(sender, message, LogDbHelper.STATUS_FAILED, "$detail (بعد از $MAX_ATTEMPTS تلاش)")
                Result.failure()
            }
        }
    }

    private fun sendToBale(webAppUrl: String, chatId: String, message: String, secretKey: String): Int {
        val url = URL(webAppUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val json = JSONObject()
        json.put("chatId", chatId)
        json.put("message", message)
        json.put("secret", secretKey)

        conn.outputStream.use { os -> os.write(json.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        conn.disconnect()
        return code
    }
}
