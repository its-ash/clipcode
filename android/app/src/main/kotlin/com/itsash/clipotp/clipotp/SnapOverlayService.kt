package com.itsash.clipotp.clipotp

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PersistableBundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Hosts the OTP pop-up: a full-width card at the top of the screen, added via
 * WindowManager + TYPE_APPLICATION_OVERLAY.
 *
 * The code is copied only when the user taps Copy; nothing is copied
 * automatically. The card slides in, can be swiped up or closed, and hides
 * itself after the configured timeout.
 *
 * Lifecycle-safe: a single overlay view is tracked at a time; any add is
 * preceded by a safe removal of a prior view, and onDestroy always tears the
 * view down, so no WindowManager leaks occur across repeated OTP events.
 */
class SnapOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var shownCode: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getStringExtra(EXTRA_CODE)
        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: "Unknown"
        if (code.isNullOrBlank()) {
            if (overlayView == null) stopSelf()
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
        val timeoutMs = SnapPrefs.get(this).getInt(SnapPrefs.KEY_OVERLAY_TIMEOUT_SEC, 10) * 1000L

        // Same code already on screen: keep the card, just restart its timer.
        if (overlayView != null && shownCode == code) {
            handler.removeCallbacks(dismissRunnable)
            handler.postDelayed(dismissRunnable, timeoutMs)
            return
        }
        removeOverlay()

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = LayoutInflater.from(this).inflate(R.layout.view_otp_overlay, null)
        view.findViewById<TextView>(R.id.overlaySender).text = sender
        view.findViewById<TextView>(R.id.overlayCode).text = code

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = statusBarHeight()
        }

        try {
            wm.addView(view, params)
        } catch (t: Throwable) {
            stopSelf()
            return
        }
        overlayView = view
        shownCode = code

        view.findViewById<View>(R.id.overlayClose).setOnClickListener { dismiss() }
        val copyButton = view.findViewById<TextView>(R.id.overlayCopyButton)
        copyButton.setOnClickListener {
            copyToClipboard(code)
            copyButton.text = "Copied"
            copyButton.isEnabled = false
            handler.removeCallbacks(dismissRunnable)
            handler.postDelayed(dismissRunnable, 700)
        }
        enableSwipeToDismiss(view.findViewById(R.id.overlayCard))

        // Slide down from above the screen, like a heads-up notification.
        view.alpha = 0f
        view.post {
            view.translationY = -view.height.toFloat()
            view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        handler.postDelayed(dismissRunnable, timeoutMs)
    }

    /** Swipe the card up (or sideways) to dismiss it. */
    @SuppressLint("ClickableViewAccessibility")
    private fun enableSwipeToDismiss(card: View) {
        var downX = 0f
        var downY = 0f
        card.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (e.rawY - downY).coerceAtMost(0f)
                    val dx = e.rawX - downX
                    if (abs(dx) > abs(dy)) {
                        v.translationX = dx
                        v.alpha = 1f - (abs(dx) / v.width).coerceIn(0f, 1f)
                    } else {
                        v.translationY = dy
                        v.alpha = 1f - (abs(dy) / v.height).coerceIn(0f, 1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val farEnough = abs(v.translationX) > v.width / 3f || -v.translationY > v.height / 3f
                    if (farEnough) {
                        dismiss()
                    } else {
                        v.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(150).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun copyToClipboard(code: String) {
        // Clipboard write happens natively here (never routed through the
        // Flutter engine) so copy works even if the Flutter engine is not
        // running.
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SnapOTP code", code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Hides the code in the system clipboard preview.
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        // Android 13+ shows its own "Copied" confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show()
        }
    }

    /** Slides the card out, then removes it and stops the service. */
    private fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val view = overlayView
        if (view == null) {
            stopSelf()
            return
        }
        view.animate()
            .translationY(-view.height.toFloat())
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (overlayView === view) {
                    removeOverlay()
                    stopSelf()
                }
            }
            .start()
    }

    private fun removeOverlay() {
        handler.removeCallbacks(dismissRunnable)
        val view = overlayView ?: return
        overlayView = null
        shownCode = null
        view.animate().cancel()
        try {
            windowManager?.removeViewImmediate(view)
        } catch (_: Throwable) {
            // View was already detached; nothing further to clean up.
        }
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
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
    const val KEY_OVERLAY_TIMEOUT_SEC = "overlay_timeout_sec"
    private const val KEY_RECENT_CODES = "recent_codes"

    /**
     * The same code seen again within this window is a duplicate. OTPs stay
     * valid for minutes, and their notification can be re-posted all that time.
     */
    private const val DUPLICATE_WINDOW_MS = 15 * 60_000L

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * True the first time [code] is seen; false when it was already seen in
     * the last [DUPLICATE_WINDOW_MS]. One OTP usually arrives several times
     * (the SMS broadcast, then the messaging app's notification and each of
     * its updates and re-posts), and each would otherwise show the pop-up and
     * add a history row. Every recent code is remembered, not just the last,
     * so two codes re-posted in turn do not keep popping up.
     */
    @Synchronized
    fun claimNewCode(context: Context, code: String): Boolean {
        val prefs = get(context)
        val now = System.currentTimeMillis()
        // Entries are "<timestamp>:<code>"; keep only those still in the window.
        val recent = prefs.getStringSet(KEY_RECENT_CODES, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val at = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                if (now - at in 0 until DUPLICATE_WINDOW_MS) at to entry.substringAfter(':') else null
            }
        if (recent.any { it.second == code }) return false
        val updated = (recent + (now to code)).map { "${it.first}:${it.second}" }.toSet()
        prefs.edit().putStringSet(KEY_RECENT_CODES, updated).commit()
        return true
    }
}
