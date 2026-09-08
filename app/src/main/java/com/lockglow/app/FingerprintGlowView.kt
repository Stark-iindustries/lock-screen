package com.lockglow.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * A pulsing ring + fingerprint glyph, like Samsung's in-display scanner prompt.
 * Idle: slow, dim breathing glow.
 * Pressed: glow snaps bright immediately, ring pulses faster while "scanning".
 */
class FingerprintGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val dimColor = Color.parseColor("#334A9EFF")   // dim idle blue
    private val brightColor = Color.parseColor("#FF4AD1FF") // bright scanning cyan
    private var currentGlowColor = dimColor
    private var glowRadius = 0.55f  // fraction of view size
    private var isPressed = false

    private val breathingAnimator = ValueAnimator.ofFloat(0.4f, 0.7f).apply {
        duration = 1600
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            if (!isPressed) {
                glowRadius = it.animatedValue as Float
                invalidate()
            }
        }
    }

    private val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), dimColor, brightColor).apply {
        duration = 180
    }

    init {
        breathingAnimator.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> setPressedState(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setPressedState(false)
        }
        return true
    }

    private fun setPressedState(pressed: Boolean) {
        isPressed = pressed
        colorAnimator.cancel()
        colorAnimator.setObjectValues(currentGlowColor, if (pressed) brightColor else dimColor)
        colorAnimator.addUpdateListener {
            currentGlowColor = it.animatedValue as Int
            invalidate()
        }
        colorAnimator.start()
        if (pressed) glowRadius = 0.85f
        invalidate()
    }

    /** Call when fingerprint is not recognized - a quick red flash so the user knows to retry. */
    fun playErrorFlash() {
        val flash = ValueAnimator.ofObject(ArgbEvaluator(), Color.parseColor("#FFFF5252"), dimColor)
        flash.duration = 400
        flash.addUpdateListener {
            currentGlowColor = it.animatedValue as Int
            invalidate()
        }
        flash.start()
    }

    /** Call when fingerprint succeeds - a bright green confirmation pulse. */
    fun playSuccessFlash() {
        val flash = ValueAnimator.ofObject(ArgbEvaluator(), Color.parseColor("#FF4CE07A"), Color.parseColor("#FF4CE07A"))
        flash.duration = 250
        flash.addUpdateListener {
            currentGlowColor = it.animatedValue as Int
            glowRadius = 0.9f
            invalidate()
        }
        flash.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = minOf(width, height) / 2f

        // Radial glow
        glowPaint.shader = RadialGradient(
            cx, cy, maxRadius * glowRadius,
            currentGlowColor, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, maxRadius * glowRadius, glowPaint)

        // Ring outline
        ringPaint.color = currentGlowColor
        canvas.drawCircle(cx, cy, maxRadius * 0.62f, ringPaint)

        // Simple fingerprint glyph: concentric arcs
        iconPaint.color = currentGlowColor
        val iconRadius = maxRadius * 0.4f
        for (i in 0..2) {
            val r = iconRadius - (i * 14f)
            canvas.drawArc(
                cx - r, cy - r, cx + r, cy + r,
                200f, 140f, false, iconPaint
            )
        }
    }
}
