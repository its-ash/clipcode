package com.itsash.clipotp.clipotp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony

/**
 * Listens for android.provider.Telephony.SMS_RECEIVED, extracts the SMS
 * body, and routes matches through the same [OtpExtractor] + history +
 * overlay pipeline used by [SnapNotificationService].
 */
class SnapSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = SnapPrefs.get(context.applicationContext)
        if (!prefs.getBoolean(SnapPrefs.KEY_MASTER_ENABLED, true)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress ?: "Unknown"
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val match = OtpExtractor.extract(body, sender) ?: return
        val appContext = context.applicationContext

        SnapDbHelper(appContext).insert(
            sender = sender,
            code = match.code,
            source = "sms",
            appLabel = "Messages",
            timestamp = System.currentTimeMillis(),
        )

        if (Settings.canDrawOverlays(appContext)) {
            val overlayIntent = Intent(appContext, SnapOverlayService::class.java).apply {
                putExtra(SnapOverlayService.EXTRA_CODE, match.code)
                putExtra(SnapOverlayService.EXTRA_SENDER, sender)
            }
            appContext.startService(overlayIntent)
        }
    }
}
