package com.lockglow.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Runs the whole time. Owns:
 *  - the persistent foreground notification (required by Android to stay alive)
 *  - the ScreenStateReceiver that tells it when to show/hide the overlay
 *  - the overlay window itself with the glow view and biometric prompt wiring
 *
 * After MAX_ATTEMPTS wrong fingerprints, the overlay is torn down for that
 * screen-on cycle so the phone's own keyguard (already underneath) takes over.
 */
class LockOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "lockglow_service"
        private const val NOTIFICATION_ID = 1
        private const val MAX_ATTEMPTS = 3

        fun start(context: Context) {
            val intent = Intent(context, LockOverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockOverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var glowView: FingerprintGlowView? = null
    private var failedAttempts = 0
    private var currentHintView: TextView? = null

    private lateinit var screenStateReceiver: ScreenStateReceiver

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        screenStateReceiver = ScreenStateReceiver(
            onScreenOn = { showOverlay() },
            onScreenOff = { removeOverlay() },
            onUserPresent = { removeOverlay() }
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Android 13+ requires an explicit exported flag for context-registered
        // receivers. These are system broadcasts only we need to hear, so NOT_EXPORTED.
        ContextCompat.registerReceiver(
            this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky: if the OS kills us, restart and resume protecting the device
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        unregisterReceiver(screenStateReceiver)
    }

    // ---------- Overlay lifecycle ----------

    private fun showOverlay() {
        if (overlayView != null) return // already showing
        failedAttempts = 0

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_lock, null)
        glowView = view.findViewById(R.id.glowTouchTarget)
        val hintText = view.findViewById<TextView>(R.id.hintText)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(view, params)
        overlayView = view

        glowView?.setOnClickListener {
            authenticate(hintText)
        }

        // Auto-prompt as soon as the overlay appears
        authenticate(hintText)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // Already removed - safe to ignore
            }
        }
        overlayView = null
        glowView = null
        currentHintView = null
        BiometricResultBus.clearListener()
    }

    // ---------- Biometric flow ----------
    // The actual BiometricPrompt dialog is hosted by the transparent
    // BiometricUnlockActivity (a bare Service can't host it). This just
    // launches that activity and listens for its result on the bus.

    private fun authenticate(hintText: TextView) {
        currentHintView = hintText

        BiometricResultBus.setListener { result, message ->
            when (result) {
                BiometricResultBus.Result.SUCCESS -> {
                    glowView?.playSuccessFlash()
                    removeOverlay()
                }
                BiometricResultBus.Result.FAILED -> {
                    failedAttempts++
                    glowView?.playErrorFlash()
                    if (failedAttempts >= MAX_ATTEMPTS) {
                        currentHintView?.text = getString(R.string.too_many_attempts)
                        // Drop our overlay - the phone's own lock screen is already
                        // underneath and takes over from here.
                        removeOverlay()
                    } else {
                        currentHintView?.text = getString(
                            R.string.attempts_remaining, MAX_ATTEMPTS - failedAttempts
                        )
                    }
                }
                BiometricResultBus.Result.ERROR, BiometricResultBus.Result.CANCELLED -> {
                    // Negative button ("use device lock"), hardware error, or user
                    // backed out - honor it and drop our overlay too.
                    currentHintView?.text = message ?: getString(R.string.too_many_attempts)
                    removeOverlay()
                }
            }
        }

        val launchIntent = Intent(this, BiometricUnlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(launchIntent)
    }

    // ---------- Notification plumbing ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
