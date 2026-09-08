package com.lockglow.app

/**
 * BiometricPrompt must be hosted by a real Activity (it attaches a hidden
 * Fragment internally) - a bare Service can't host it directly. So the flow is:
 *
 *   LockOverlayService (owns the glow overlay + attempt counter)
 *        -> launches transparent BiometricUnlockActivity
 *              -> shows the real BiometricPrompt
 *              -> reports the outcome back through this bus
 *              -> finishes itself immediately
 *
 * Everything happens in-process, so a simple listener is enough - no need
 * for broadcasts or a bound service.
 */
object BiometricResultBus {

    enum class Result { SUCCESS, FAILED, ERROR, CANCELLED }

    private var listener: ((Result, String?) -> Unit)? = null

    fun setListener(callback: (Result, String?) -> Unit) {
        listener = callback
    }

    fun clearListener() {
        listener = null
    }

    fun report(result: Result, message: String? = null) {
        listener?.invoke(result, message)
    }
}
