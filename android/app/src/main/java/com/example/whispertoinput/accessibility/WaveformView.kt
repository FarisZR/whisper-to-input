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

/** Half the bar count on each side of center. Total bars = 2 * HALF_BARS + 1. */
private const val HALF_BARS = 22

/** Minimum amplitude to bother rendering a bar (below this it's invisible). */
private const val VISIBILITY_THRESHOLD = 0.012f

private const val MAX_AMPLITUDE = 32000f
private const val FRAME_TIME_MS = 16L

/** How often a new amplitude sample is pushed into the ring buffer. */
private const val SAMPLE_INTERVAL_MS = 38L

private enum class WaveformMode { Idle, Recording, Transcribing }

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_waveform)
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(3.5f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_waveform)
        style = Paint.Style.FILL
    }

    private val spacingPx = dp(6.5f)
    private val dotRadiusPx = dp(4f)
    private val minBarHeightPx = dp(2.5f)

    private var mode = WaveformMode.Idle

    /*
     * Ring buffer of amplitude targets.
     * Index 0 = center (most recent sample), higher indices = further out (older).
     * Bars are drawn mirrored: position ±i from center uses targetHeights[i].
     */
    private val totalSlots = HALF_BARS + 1
    private val targetHeights = FloatArray(totalSlots)
    private val displayedHeights = FloatArray(totalSlots)

    /* Accumulate peak amplitude between sample pushes. */
    private var pendingPeak = 0f
    private var lastSamplePushMs = 0L

    /* Animation bookkeeping. */
    private var lastFrameMs = 0L
    private var phase = 0f

    // ----- public API (unchanged) -----

    fun showRecordingState() {
        mode = WaveformMode.Recording
        ensureAnimating()
    }

    fun showTranscribingState() {
        mode = WaveformMode.Transcribing
        ensureAnimating()
    }

    fun reset() {
        mode = WaveformMode.Idle
        targetHeights.fill(0f)
        displayedHeights.fill(0f)
        pendingPeak = 0f
        lastSamplePushMs = 0L
        lastFrameMs = 0L
        phase = 0f
        invalidate()
    }

    fun setAmplitude(amplitude: Int) {
        mode = WaveformMode.Recording
        val normalized = normalizeAmplitude(amplitude)
        pendingPeak = max(pendingPeak, normalized)

        val now = SystemClock.uptimeMillis()
        if (lastSamplePushMs == 0L) lastSamplePushMs = now
        if (now - lastSamplePushMs >= SAMPLE_INTERVAL_MS) {
            pushSample(pendingPeak)
            pendingPeak = 0f
            lastSamplePushMs = now
        }
        ensureAnimating()
    }

    // ----- rendering -----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val now = SystemClock.uptimeMillis()
        val deltaMs = if (lastFrameMs == 0L) FRAME_TIME_MS else (now - lastFrameMs).coerceIn(1, 48)
        lastFrameMs = now
        stepAnimation(deltaMs)

        val cx = width / 2f
        val cy = height / 2f

        if (mode == WaveformMode.Transcribing) {
            drawTranscribingDot(canvas, cx, cy)
        } else if (maxDisplayed() < VISIBILITY_THRESHOLD) {
            drawIdleDot(canvas, cx, cy)
        } else {
            drawBars(canvas, cx, cy)
        }

        if (shouldKeepAnimating()) postInvalidateOnAnimation()
    }

    private fun drawIdleDot(canvas: Canvas, cx: Float, cy: Float) {
        dotPaint.alpha = 255
        canvas.drawCircle(cx, cy, dotRadiusPx, dotPaint)
    }

    private fun drawTranscribingDot(canvas: Canvas, cx: Float, cy: Float) {
        val pulse = (0.5f + 0.5f * sin(phase.toDouble())).toFloat() // 0‥1
        val radius = dotRadiusPx * (1f + pulse * 0.55f)
        dotPaint.alpha = (185 + (70 * pulse)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius, dotPaint)
        dotPaint.alpha = 255
    }

    private fun drawBars(canvas: Canvas, cx: Float, cy: Float) {
        val maxH = height * 0.44f

        // Center bar (index 0).
        val centerH = barHeight(0, maxH)
        if (centerH >= minBarHeightPx * 0.5f) {
            canvas.drawLine(cx, cy - centerH, cx, cy + centerH, barPaint)
        } else {
            // Fall back to dot when center bar is tiny.
            drawIdleDot(canvas, cx, cy)
        }

        // Symmetric bars radiating outward.
        for (i in 1 until totalSlots) {
            val h = barHeight(i, maxH)
            if (h < minBarHeightPx * 0.5f) continue
            val offset = i * spacingPx
            canvas.drawLine(cx - offset, cy - h, cx - offset, cy + h, barPaint)
            canvas.drawLine(cx + offset, cy - h, cx + offset, cy + h, barPaint)
        }
    }

    /** Compute the pixel height for a bar, including a subtle per-bar shimmer. */
    private fun barHeight(index: Int, maxH: Float): Float {
        val level = displayedHeights[index]
        if (level < VISIBILITY_THRESHOLD) return 0f
        // Subtle shimmer keeps bars from looking static.
        val shimmer = 0.88f + 0.12f * abs(sin(phase * 1.1f + index * 1.4f))
        return max(minBarHeightPx, level * shimmer * maxH)
    }

    // ----- animation -----

    private fun stepAnimation(deltaMs: Long) {
        phase += deltaMs * when (mode) {
            WaveformMode.Transcribing -> 0.0045f
            else -> 0.012f
        }

        when (mode) {
            WaveformMode.Recording -> {
                // Gently decay targets so old bars taper off.
                val decay = 0.997f.pow(deltaMs.toFloat())
                for (i in targetHeights.indices) targetHeights[i] *= decay
            }
            WaveformMode.Idle -> {
                val decay = 0.88f.pow(deltaMs / FRAME_TIME_MS.toFloat())
                for (i in targetHeights.indices) targetHeights[i] *= decay
            }
            WaveformMode.Transcribing -> {
                val decay = 0.92f.pow(deltaMs / FRAME_TIME_MS.toFloat())
                for (i in displayedHeights.indices) displayedHeights[i] *= decay
            }
        }

        // Smooth displayed heights toward targets.
        val riseFactor = 0.40f
        val fallFactor = 0.10f
        for (i in displayedHeights.indices) {
            val diff = targetHeights[i] - displayedHeights[i]
            val factor = if (diff > 0) riseFactor else fallFactor
            displayedHeights[i] += diff * factor
            if (displayedHeights[i] < 0.001f) displayedHeights[i] = 0f
        }
    }

    private fun shouldKeepAnimating(): Boolean =
        mode != WaveformMode.Idle || displayedHeights.any { it > 0.002f }

    // ----- helpers -----

    private fun pushSample(level: Float) {
        // Shift everything outward (older samples move to higher indices).
        for (i in targetHeights.size - 1 downTo 1) {
            targetHeights[i] = targetHeights[i - 1]
        }
        targetHeights[0] = level
    }

    private fun maxDisplayed(): Float {
        var m = 0f
        for (v in displayedHeights) if (v > m) m = v
        return m
    }

    private fun normalizeAmplitude(amplitude: Int): Float {
        val clamped = amplitude.coerceAtLeast(0).toFloat()
        val normalized = ln(1f + clamped) / ln(1f + MAX_AMPLITUDE)
        return min(1f, max(0.04f, normalized))
    }

    private fun ensureAnimating() {
        if (!isAttachedToWindow) { invalidate(); return }
        postInvalidateOnAnimation()
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
