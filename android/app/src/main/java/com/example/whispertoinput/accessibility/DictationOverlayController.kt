package com.example.whispertoinput.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.whispertoinput.R
import kotlin.math.roundToInt

private const val DEFAULT_WIDTH_DP = 440
private const val DEFAULT_MARGIN_DP = 24

class DictationOverlayController(
    context: Context,
    private val onCloseTapped: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rootView: View = LayoutInflater.from(context).inflate(R.layout.overlay_voice_input, null)
    private val waveformView: WaveformView = rootView.findViewById(R.id.waveform_view)
    private val labelStatus: TextView = rootView.findViewById(R.id.label_overlay_status)
    private val labelSubtitle: TextView = rootView.findViewById(R.id.label_overlay_subtitle)
    private val transcribingIndicator: View = rootView.findViewById(R.id.view_transcribing_indicator)
    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }
    private var isAttached: Boolean = false
    private var xPositionPx: Int? = null
    private var yPositionPx: Int? = null

    init {
        rootView.findViewById<ImageButton>(R.id.btn_overlay_close).setOnClickListener {
            onCloseTapped()
        }
        rootView.findViewById<View>(R.id.view_drag_handle_touch).setOnTouchListener(DragTouchListener())
        rootView.elevation = dp(context, 12f)
    }

    fun show() {
        if (!isAttached) {
            updateDefaultPosition(rootView.context)
            windowManager.addView(rootView, layoutParams)
            isAttached = true
        }
        setRecordingState()
    }

    fun setRecordingState() {
        labelStatus.setText(R.string.overlay_recording)
        labelSubtitle.setText(R.string.overlay_recording_hint)
        transcribingIndicator.isVisible = false
        waveformView.isVisible = true
        waveformView.showRecordingState()
    }

    fun setTranscribingState() {
        labelStatus.setText(R.string.overlay_transcribing)
        labelSubtitle.setText(R.string.overlay_transcribing_hint)
        transcribingIndicator.isVisible = true
        waveformView.isVisible = true
        waveformView.showTranscribingState()
    }

    fun setAmplitude(amplitude: Int) {
        waveformView.setAmplitude(amplitude)
    }

    fun resetWaveform() {
        waveformView.reset()
    }

    fun hide() {
        if (isAttached) {
            try {
                windowManager.removeView(rootView)
            } catch (_: IllegalArgumentException) {
            }
            isAttached = false
            waveformView.reset()
        }
    }

    private fun updateDefaultPosition(context: Context) {
        if (xPositionPx != null && yPositionPx != null) {
            layoutParams.x = xPositionPx!!
            layoutParams.y = yPositionPx!!
            return
        }

        val displayMetrics = context.resources.displayMetrics
        val widthPx = dp(context, DEFAULT_WIDTH_DP.toFloat()).roundToInt()
        val marginPx = dp(context, DEFAULT_MARGIN_DP.toFloat()).roundToInt()
        layoutParams.x = (displayMetrics.widthPixels - widthPx - marginPx).coerceAtLeast(0)
        layoutParams.y = (displayMetrics.heightPixels - dp(context, 174f) - marginPx).roundToInt().coerceAtLeast(0)
        xPositionPx = layoutParams.x
        yPositionPx = layoutParams.y
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var initialX: Int = 0
        private var initialY: Int = 0
        private var initialTouchX: Float = 0f
        private var initialTouchY: Float = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).roundToInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).roundToInt()
                    xPositionPx = layoutParams.x
                    yPositionPx = layoutParams.y
                    if (isAttached) {
                        windowManager.updateViewLayout(rootView, layoutParams)
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun dp(context: Context, value: Float): Float {
        return value * context.resources.displayMetrics.density
    }
}
