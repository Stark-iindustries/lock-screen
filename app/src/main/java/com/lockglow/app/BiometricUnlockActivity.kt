package com.lockglow.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * Invisible activity whose only job is to host the BiometricPrompt dialog
 * (required - BiometricPrompt can't attach to a bare Service) and report the
 * result back to LockOverlayService via BiometricResultBus, then close itself.
 * Uses Theme.Transparent so the glow overlay underneath stays fully visible.
 */
class BiometricUnlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showPrompt()
    }

    private fun showPrompt() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            BiometricResultBus.report(BiometricResultBus.Result.ERROR, getString(R.string.no_biometric_hardware))
            finish()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    BiometricResultBus.report(BiometricResultBus.Result.SUCCESS)
                    finish()
                }

                override fun onAuthenticationFailed() {
                    BiometricResultBus.report(BiometricResultBus.Result.FAILED)
                    // Don't finish - let the same prompt keep listening for another attempt,
                    // the OS dialog itself allows retries. We finish on error/cancel/success only.
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    BiometricResultBus.report(BiometricResultBus.Result.ERROR, errString.toString())
                    finish()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setSubtitle(getString(R.string.unlock_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(getString(R.string.use_device_lock))
            .build()

        prompt.authenticate(promptInfo)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
