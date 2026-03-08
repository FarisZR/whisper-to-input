package com.example.whispertoinput.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.whispertoinput.R
import kotlin.math.max
import kotlin.math.min

private const val BAR_COUNT = 39
private const val MIN_BAR_SCALE = 0.12f

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val amplitudes = MutableList(BAR_COUNT) { MIN_BAR_SCALE }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_waveform)
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(3f)
    }
    private val spacingPx = dp(8f)

    fun reset() {
        for (index in amplitudes.indices) {
            amplitudes[index] = MIN_BAR_SCALE
        }
        invalidate()
    }

    fun setAmplitude(amplitude: Int) {
        val normalized = min(1f, max(MIN_BAR_SCALE, amplitude / 18000f))
        amplitudes.removeAt(0)
        amplitudes.add(normalized)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) {
            return
        }

        val centerY = height / 2f
        val totalWidth = (BAR_COUNT - 1) * spacingPx
        val startX = (width - totalWidth) / 2f
        val maxHeight = height * 0.44f
        for (index in amplitudes.indices) {
            val amplitude = amplitudes[index]
            val x = startX + index * spacingPx
            val barHeight = maxHeight * amplitude
            canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, paint)
        }
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics,
        )
    }
}
