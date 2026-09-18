package com.itsash.clipotp.clipotp

import android.animation.ObjectAnimator
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

/**
 * Hosts the floating OTP overlay via WindowManager + TYPE_APPLICATION_OVERLAY.
 *
 * Lifecycle-safe: a single overlay view is tracked at a time; any add is
 * preceded by a safe removal of a prior view, and onDestroy always tears the
 * view down, so no WindowManager leaks occur across repeated OTP events.
 */
class SnapOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val dismissHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getStringExtra(EXTRA_CODE)
        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: "Unknown"
        if (code.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay(code, sender)
        return START_NOT_STICKY
    }

    private fun showOverlay(code: String, sender: String) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        removeOverlay()

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = LayoutInflater.from(this).inflate(R.layout.view_otp_overlay, null)
        overlayView = view

        view.findViewById<TextView>(R.id.overlaySender).text = sender
        val codeView = view.findViewById<TextView>(R.id.overlayCode)
        codeView.text = code

        val prefs = applicationContext.getSharedPreferences(SnapPrefs.FILE_NAME, Context.MODE_PRIVATE)
        val autoCopy = prefs.getBoolean(SnapPrefs.KEY_AUTO_COPY, true)
        val timeoutSec = prefs.getInt(SnapPrefs.KEY_OVERLAY_TIMEOUT_SEC, 10)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        try {
            wm.addView(view, params)
        } catch (t: Throwable) {
            overlayView = null
            stopSelf()
            return
        }

        view.findViewById<View>(R.id.overlayClose).setOnClickListener { removeOverlay(); stopSelf() }
        view.findViewById<View>(R.id.overlayCopyButton).setOnClickListener {
            copyToClipboard(code)
            animateCopySuccess(it as TextView)
            dismissHandler.postDelayed({ removeOverlay(); stopSelf() }, 350)
        }

        if (autoCopy) {
            copyToClipboard(code)
        }

        dismissRunnable = Runnable { removeOverlay(); stopSelf() }
        dismissHandler.postDelayed(dismissRunnable!!, timeoutSec * 1000L)
    }

    private fun copyToClipboard(code: String) {
        // Clipboard write happens natively here (never routed through the
        // Flutter engine) so copy works even if the Flutter engine is not
        // running, keeping the app privacy-first and functional headless.
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SnapOTP code", code))
        Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show()
    }

    private fun animateCopySuccess(view: TextView) {
        view.text = "Copied ✓"
        ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.08f, 1f).apply { duration = 220 }.start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.08f, 1f).apply { duration = 220 }.start()
    }

    private fun removeOverlay() {
        dismissRunnable?.let { dismissHandler.removeCallbacks(it) }
        dismissRunnable = null
        val view = overlayView ?: return
        overlayView = null
        try {
            windowManager?.removeViewImmediate(view)
        } catch (_: Throwable) {
            // View was already detached; nothing further to clean up.
        }
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "extra_code"
        const val EXTRA_SENDER = "extra_sender"
    }
}

/** Shared prefs key contract, readable/writable from both Kotlin and Flutter (via platform channel). */
object SnapPrefs {
    const val FILE_NAME = "snapotp_prefs"
    const val KEY_MASTER_ENABLED = "master_enabled"
    const val KEY_AUTO_COPY = "auto_copy"
    const val KEY_OVERLAY_TIMEOUT_SEC = "overlay_timeout_sec"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
