package com.itsash.clipotp.clipotp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.text.TextUtils
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * Registers the platform channel contract consumed by
 * lib/services/platform_channel_service.dart:
 *
 * MethodChannel "com.itsash.clipotp/control":
 *   - getMasterEnabled() -> bool
 *   - setMasterEnabled(bool enabled) -> void
 *   - getOverlayTimeoutSec() -> int
 *   - setOverlayTimeoutSec(int seconds) -> void
 *   - isNotificationAccessGranted() -> bool
 *   - isOverlayPermissionGranted() -> bool
 *   - openNotificationAccessSettings() -> void
 *   - openOverlayPermissionSettings() -> void
 *   - showLastOtp() -> String: finds the newest SMS in the inbox with an
 *     OTP and shows the pop-up for it. Returns "shown", "no_sms_permission",
 *     "no_overlay_permission" or "not_found". Does not touch history.
 *
 * EventChannel "com.itsash.clipotp/events": reserved for forwarding live
 * capture events to a running Flutter UI (history screen live refresh);
 * the DB is the source of truth so Flutter also polls it directly.
 */
class MainActivity : FlutterActivity() {

    private val controlChannel = "com.itsash.clipotp/control"
    private val eventChannelName = "com.itsash.clipotp/events"
    private var eventSink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, controlChannel).setMethodCallHandler { call, result ->
            val prefs = SnapPrefs.get(applicationContext)
            when (call.method) {
                "getMasterEnabled" -> result.success(prefs.getBoolean(SnapPrefs.KEY_MASTER_ENABLED, true))
                "setMasterEnabled" -> {
                    prefs.edit().putBoolean(SnapPrefs.KEY_MASTER_ENABLED, call.argument<Boolean>("enabled") ?: true).apply()
                    result.success(null)
                }
                "getOverlayTimeoutSec" -> result.success(prefs.getInt(SnapPrefs.KEY_OVERLAY_TIMEOUT_SEC, 10))
                "setOverlayTimeoutSec" -> {
                    prefs.edit().putInt(SnapPrefs.KEY_OVERLAY_TIMEOUT_SEC, call.argument<Int>("seconds") ?: 10).apply()
                    result.success(null)
                }
                "isNotificationAccessGranted" -> result.success(isNotificationAccessGranted())
                "isOverlayPermissionGranted" -> result.success(Settings.canDrawOverlays(applicationContext))
                "openNotificationAccessSettings" -> {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    result.success(null)
                }
                "openOverlayPermissionSettings" -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    result.success(null)
                }
                "showLastOtp" -> showLastOtp(result)
                else -> result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, eventChannelName).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            },
        )
    }

    /** "Try it": show the pop-up for the newest OTP in the SMS inbox. */
    private fun showLastOtp(result: MethodChannel.Result) {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            result.success("no_sms_permission")
            return
        }
        if (!Settings.canDrawOverlays(applicationContext)) {
            result.success("no_overlay_permission")
            return
        }
        Thread {
            val match = try {
                findLastOtp()
            } catch (_: Throwable) {
                null
            }
            runOnUiThread {
                if (match == null) {
                    result.success("not_found")
                } else {
                    startService(
                        Intent(applicationContext, SnapOverlayService::class.java).apply {
                            putExtra(SnapOverlayService.EXTRA_CODE, match.code)
                            putExtra(SnapOverlayService.EXTRA_SENDER, match.sender)
                        },
                    )
                    result.success("shown")
                }
            }
        }.start()
    }

    /** Newest of the last 100 inbox messages that contains an OTP. */
    private fun findLastOtp(): OtpMatch? {
        contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val addressCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            var seen = 0
            while (c.moveToNext() && seen++ < 100) {
                val body = c.getString(bodyCol) ?: continue
                val sender = c.getString(addressCol) ?: "Unknown"
                OtpExtractor.extract(body, sender)?.let { return it }
            }
        }
        return null
    }

    private fun isNotificationAccessGranted(): Boolean {
        val pkgName = applicationContext.packageName
        val flat = Settings.Secure.getString(applicationContext.contentResolver, "enabled_notification_listeners")
        if (TextUtils.isEmpty(flat)) return false
        return flat.split(":").any { componentString ->
            val cn = ComponentName.unflattenFromString(componentString)
            cn != null && TextUtils.equals(pkgName, cn.packageName)
        }
    }
}
