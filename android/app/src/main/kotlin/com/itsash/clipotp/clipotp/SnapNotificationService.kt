package com.itsash.clipotp.clipotp

import android.app.Notification
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
        val flags = sbn.notification.flags
        // A group summary repeats a message that is also posted on its own.
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // Ongoing / foreground-service notifications (music, navigation,
        // downloads, step counters) update constantly and never carry an OTP.
        if (flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) return
        // Only a new message: apps re-post old notifications (e.g. when the
        // app is opened or syncs), and those keep the message's original time.
        val sentAt = sbn.notification.`when`.takeIf { it > 0 } ?: sbn.postTime
        if (System.currentTimeMillis() - sentAt > FRESH_WINDOW_MS) return

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

        val match = OtpExtractor.extract(combined, appLabel, requireKeyword = true) ?: return
        if (!SnapPrefs.claimNewCode(applicationContext, match.code)) return

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

    private companion object {
        /** A notification about a message older than this is not a new OTP. */
        const val FRESH_WINDOW_MS = 2 * 60_000L
    }
}
