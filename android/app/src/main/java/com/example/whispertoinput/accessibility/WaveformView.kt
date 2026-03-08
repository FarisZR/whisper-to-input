package com.example.whispertoinput.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.whispertoinput.R
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val BAR_COUNT = 39
private const val MIN_BAR_SCALE = 0.08f
private const val MAX_AMPLITUDE = 32000f
private const val FRAME_TIME_MS = 16L

private enum class WaveformMode {
    Idle,
    Recording,
    Transcribing,
}

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_waveform)
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(4.5f)
    }
    private val spacingPx = dp(7f)
    private var mode: WaveformMode = WaveformMode.Idle
    private var phase: Float = 0f
    private var targetLevel: Float = MIN_BAR_SCALE
    private var displayedLevel: Float = MIN_BAR_SCALE
    private var lastAmplitudeAtMs: Long = 0L
    private var lastFrameAtMs: Long = 0L

    fun showRecordingState() {
        mode = WaveformMode.Recording
        displayedLevel = max(displayedLevel, 0.15f)
        targetLevel = max(targetLevel, 0.18f)
        ensureAnimating()
    }

    fun showTranscribingState() {
        mode = WaveformMode.Transcribing
        targetLevel = 0.34f
        ensureAnimating()
    }

    fun reset() {
        mode = WaveformMode.Idle
        targetLevel = MIN_BAR_SCALE
        displayedLevel = MIN_BAR_SCALE
        lastAmplitudeAtMs = 0L
        lastFrameAtMs = 0L
        phase = 0f
        invalidate()
    }

    fun setAmplitude(amplitude: Int) {
        mode = WaveformMode.Recording
        targetLevel = normalizeAmplitude(amplitude)
        lastAmplitudeAtMs = SystemClock.uptimeMillis()
        ensureAnimating()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) {
            return
        }

        val now = SystemClock.uptimeMillis()
        val deltaMs = if (lastFrameAtMs == 0L) {
            FRAME_TIME_MS
        } else {
            (now - lastFrameAtMs).coerceIn(1L, 48L)
        }
        lastFrameAtMs = now
        updateAnimation(now, deltaMs)

        val centerY = height / 2f
        val totalWidth = (BAR_COUNT - 1) * spacingPx
        val startX = (width - totalWidth) / 2f
        val maxHeight = height * 0.46f
        for (index in 0 until BAR_COUNT) {
            val x = startX + index * spacingPx
            val normalizedIndex = index.toFloat() / (BAR_COUNT - 1)
            val centerDistance = abs((normalizedIndex - 0.5f) * 2f)
            val envelope = 1f - centerDistance.pow(1.65f)
            val wave = 0.45f + (0.55f * abs(sin(phase + (index * 0.43f))))
            val shimmer = 0.78f + (0.22f * abs(sin((phase * 0.53f) - (index * 0.18f))))
            val dynamicScale = 0.12f + (displayedLevel * envelope * wave * shimmer)
            val barHeight = (maxHeight * dynamicScale).coerceAtLeast(dp(3f))
            canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, paint)
        }

        if (mode != WaveformMode.Idle || displayedLevel > (MIN_BAR_SCALE + 0.01f)) {
            postInvalidateOnAnimation()
        }
    }

    private fun updateAnimation(now: Long, deltaMs: Long) {
        when (mode) {
            WaveformMode.Idle -> {
                targetLevel = MIN_BAR_SCALE
            }

            WaveformMode.Recording -> {
                if (lastAmplitudeAtMs == 0L || now - lastAmplitudeAtMs > 80L) {
                    targetLevel = max(MIN_BAR_SCALE, targetLevel * 0.9f)
                }
            }

            WaveformMode.Transcribing -> {
                targetLevel = 0.28f + (0.08f * (0.5f + (0.5f * sin(phase * 0.8f))))
            }
        }

        val smoothing = when (mode) {
            WaveformMode.Recording -> 0.35f
            WaveformMode.Transcribing -> 0.18f
            WaveformMode.Idle -> 0.16f
        }
        displayedLevel += (targetLevel - displayedLevel) * smoothing
        phase += deltaMs * if (mode == WaveformMode.Transcribing) 0.010f else 0.018f
    }

    private fun normalizeAmplitude(amplitude: Int): Float {
        val clampedAmplitude = amplitude.coerceAtLeast(0).toFloat()
        val normalized = ln(1f + clampedAmplitude) / ln(1f + MAX_AMPLITUDE)
        return min(1f, max(0.18f, normalized))
    }

    private fun ensureAnimating() {
        if (!isAttachedToWindow) {
            invalidate()
            return
        }
        postInvalidateOnAnimation()
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics,
        )
    }
}
