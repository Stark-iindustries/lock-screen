package com.lockglow.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.app.ActivityCompat

/**
 * First-run setup wizard:
 *  1. Ask for overlay ("draw over other apps") permission
 *  2. Ask for notification permission (needed to keep the foreground service alive on Android 13+)
 *  3. Ask for battery-optimization exemption, so the OS doesn't kill the service
 *  4. Check a fingerprint is enrolled - if not, send the user to Settings to add one
 *  5. Start LockOverlayService and finish setup
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            runSetupStep()
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            runSetupStep()
        }

    private val batteryOptimizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            runSetupStep()
        }

    private val biometricEnrollLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            runSetupStep()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        actionButton = findViewById(R.id.actionButton)

        actionButton.setOnClickListener { runSetupStep() }
        runSetupStep()
    }

    override fun onResume() {
        super.onResume()
        // Re-check in case the user just came back from a Settings screen
        runSetupStep()
    }

    /**
     * Walks through each requirement in order. Each granted step calls this again,
     * so it naturally advances to the next missing permission until everything is ready.
     */
    private fun runSetupStep() {
        when {
            !hasOverlayPermission() -> {
                statusText.text = getString(R.string.setup_need_overlay)
                actionButton.text = getString(R.string.grant_permission)
                actionButton.setOnClickListener { requestOverlayPermission() }
            }
            !hasNotificationPermission() -> {
                statusText.text = getString(R.string.setup_need_notifications)
                actionButton.text = getString(R.string.grant_permission)
                actionButton.setOnClickListener { requestNotificationPermission() }
            }
            !isIgnoringBatteryOptimizations() -> {
                statusText.text = getString(R.string.setup_need_battery)
                actionButton.text = getString(R.string.grant_permission)
                actionButton.setOnClickListener { requestBatteryExemption() }
            }
            !hasEnrolledFingerprint() -> {
                statusText.text = getString(R.string.setup_need_fingerprint)
                actionButton.text = getString(R.string.open_settings)
                actionButton.setOnClickListener { openBiometricEnrollment() }
            }
            else -> {
                statusText.text = getString(R.string.setup_complete)
                actionButton.text = getString(R.string.start_lock)
                actionButton.setOnClickListener { startLockServiceAndFinish() }
            }
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runSetupStep()
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryExemption() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        batteryOptimizationLauncher.launch(intent)
    }

    private fun hasEnrolledFingerprint(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun openBiometricEnrollment() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        biometricEnrollLauncher.launch(intent)
    }

    private fun startLockServiceAndFinish() {
        LockOverlayService.start(this)
        Toast.makeText(this, R.string.lock_active, Toast.LENGTH_SHORT).show()
        finish()
    }
}
