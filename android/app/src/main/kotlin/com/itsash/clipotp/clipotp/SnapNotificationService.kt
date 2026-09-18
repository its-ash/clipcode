package com.itsash.clipotp.clipotp

import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Monitors incoming notifications, extracts OTP-looking text via
 * [OtpExtractor], stores a history row, and launches [SnapOverlayService].
 * Idle CPU/memory usage when no notifications arrive: the OS only invokes
 * these callbacks on actual notification posts.
 */
class SnapNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val prefs = SnapPrefs.get(applicationContext)
        if (!prefs.getBoolean(SnapPrefs.KEY_MASTER_ENABLED, true)) return
        if (sbn.packageName == applicationContext.packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val body = listOf(text, bigText).firstOrNull { it.isNotBlank() } ?: text
        val combined = "$title $body".trim()
        if (combined.isBlank()) return

        val appLabel = try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            sbn.packageName
        }

        val match = OtpExtractor.extract(combined, appLabel) ?: return

        SnapDbHelper(applicationContext).insert(
            sender = appLabel,
            code = match.code,
            source = "notification",
            appLabel = appLabel,
            timestamp = System.currentTimeMillis(),
        )

        if (Settings.canDrawOverlays(applicationContext)) {
            val overlayIntent = Intent(applicationContext, SnapOverlayService::class.java).apply {
                putExtra(SnapOverlayService.EXTRA_CODE, match.code)
                putExtra(SnapOverlayService.EXTRA_SENDER, appLabel)
            }
            applicationContext.startService(overlayIntent)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // No-op: history retention is handled by SnapDbHelper.pruneOld().
    }
}
