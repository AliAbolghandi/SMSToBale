package com.example.smstobale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val sourceNumber = prefs.getString(Prefs.SOURCE_NUMBER, "")?.trim() ?: ""
        val webAppUrl = prefs.getString(Prefs.WEBAPP_URL, "")?.trim() ?: ""
        val chatId = prefs.getString(Prefs.CHAT_ID, "")?.trim() ?: ""
        val secretKey = prefs.getString(Prefs.SECRET_KEY, "")?.trim() ?: ""

        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")

        val messageBuilder = StringBuilder()
        var sender = ""

        for (pdu in pdus) {
            val sms = if (format != null) {
                SmsMessage.createFromPdu(pdu as ByteArray, format)
            } else {
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(pdu as ByteArray)
            }
            sender = sms.originatingAddress ?: ""
            messageBuilder.append(sms.messageBody)
        }

        val messageBody = messageBuilder.toString()
        val dbHelper = LogDbHelper(context)

        if (!prefs.getBoolean(Prefs.AUTO_FORWARD_ENABLED, true)) {
            return
        }

        if (sourceNumber.isEmpty() || webAppUrl.isEmpty() || chatId.isEmpty()) {
            dbHelper.insertLog(sender, messageBody, LogDbHelper.STATUS_ERROR, "تنظیمات اپ کامل نیست")
            return
        }

        if (!matchesAnySource(sender, sourceNumber)) {
            dbHelper.insertLog(sender, messageBody, LogDbHelper.STATUS_IGNORED, "فرستنده با منابع تنظیم‌شده مطابقت نداشت")
            return
        }

        enqueueSendWork(context, sender, messageBody, webAppUrl, chatId, secretKey)
    }

    // ارسال از طریق WorkManager انجام می‌شود تا در صورت قطعی موقت اینترنت
    // یا فعال بودن Doze mode، کار به‌صورت خودکار و با تاخیر تصاعدی دوباره تلاش شود
    private fun enqueueSendWork(
        context: Context,
        sender: String,
        message: String,
        webAppUrl: String,
        chatId: String,
        secretKey: String
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = Data.Builder()
            .putString(SendMessageWorker.KEY_SENDER, sender)
            .putString(SendMessageWorker.KEY_MESSAGE, message)
            .putString(SendMessageWorker.KEY_WEBAPP_URL, webAppUrl)
            .putString(SendMessageWorker.KEY_CHAT_ID, chatId)
            .putString(SendMessageWorker.KEY_SECRET_KEY, secretKey)
            .build()

        val request = OneTimeWorkRequestBuilder<SendMessageWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    // منبع می‌تواند شماره تلفن یا Sender ID متنی بانک باشد؛ چند منبع با کاما جدا می‌شوند
    private fun matchesAnySource(sender: String, sourcesRaw: String): Boolean {
        val sources = sourcesRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (source in sources) {
            if (isNumeric(source)) {
                if (numbersMatch(sender, source)) return true
            } else {
                if (textSenderMatches(sender, source)) return true
            }
        }
        return false
    }

    private fun isNumeric(value: String): Boolean {
        val stripped = value.removePrefix("+")
        return stripped.isNotEmpty() && stripped.all { it.isDigit() }
    }

    private fun numbersMatch(a: String, b: String): Boolean {
        val cleanA = a.filter { it.isDigit() }.takeLast(10)
        val cleanB = b.filter { it.isDigit() }.takeLast(10)
        return cleanA.isNotEmpty() && cleanA == cleanB
    }

    private fun textSenderMatches(sender: String, expected: String): Boolean {
        val normalizedSender = sender.trim().lowercase()
        val normalizedExpected = expected.trim().lowercase()
        return normalizedSender == normalizedExpected || normalizedSender.contains(normalizedExpected)
    }
}
