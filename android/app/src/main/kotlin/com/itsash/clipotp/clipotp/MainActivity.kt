package com.itsash.clipotp.clipotp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
 *   - getAutoCopy() -> bool
 *   - setAutoCopy(bool enabled) -> void
 *   - getOverlayTimeoutSec() -> int
 *   - setOverlayTimeoutSec(int seconds) -> void
 *   - isNotificationAccessGranted() -> bool
 *   - isOverlayPermissionGranted() -> bool
 *   - openNotificationAccessSettings() -> void
 *   - openOverlayPermissionSettings() -> void
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
                "getAutoCopy" -> result.success(prefs.getBoolean(SnapPrefs.KEY_AUTO_COPY, true))
                "setAutoCopy" -> {
                    prefs.edit().putBoolean(SnapPrefs.KEY_AUTO_COPY, call.argument<Boolean>("enabled") ?: true).apply()
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
