package com.example.whispertoinput.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.whispertoinput.MainActivity
import com.example.whispertoinput.R
import com.example.whispertoinput.WhisperTranscriber
import com.example.whispertoinput.recorder.RecorderManager
import com.example.whispertoinput.voice.VoiceInputConfig
import com.example.whispertoinput.voice.VoiceInputSessionController
import com.example.whispertoinput.voice.VoiceInputSessionState
import com.example.whispertoinput.voice.VoiceSessionRecorder
import com.example.whispertoinput.voice.VoiceSessionTranscriber
import com.example.whispertoinput.voice.loadVoiceInputConfig
import com.github.liuyueyi.quick.transfer.ChineseUtils
import com.github.liuyueyi.quick.transfer.constants.TransType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val HUAWEI_VOICE_INPUT_KEY_CODE = 204
private const val ACCESSIBILITY_TAG = "whisper-input-overlay"

class HuaweiDictationAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private lateinit var recorderManager: RecorderManager
    private lateinit var sessionController: VoiceInputSessionController
    private val keyTracker = HoldToDictateKeyTracker(HUAWEI_VOICE_INPUT_KEY_CODE)
    private lateinit var overlayController: DictationOverlayController
    private lateinit var focusedInputEditor: FocusedInputEditor
    private var shouldRestoreKeyboard: Boolean = false
    private val startRecordingRunnable = Runnable {
        if (keyTracker.onLongPress()) {
            beginDictationTakeover()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        recorderManager = RecorderManager(this)
        sessionController = VoiceInputSessionController(
            context = this,
            recorder = RecorderAdapter(recorderManager),
            transcriber = TranscriberAdapter(WhisperTranscriber()),
            minimumRecordingDurationMs = resources.getInteger(R.integer.dictation_min_recording_duration_ms).toLong(),
            nowMs = { SystemClock.elapsedRealtime() },
            callbacks = SessionCallbacks(),
        )
        overlayController = DictationOverlayController(this) { cancelCurrentSession() }
        focusedInputEditor = FocusedInputEditor(this)
        preloadChineseConversionTables()
        configureServiceInfo()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        ) {
            Unit
        }
    }

    override fun onInterrupt() {
        cancelCurrentSession()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != HUAWEI_VOICE_INPUT_KEY_CODE) {
            return false
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!keyTracker.onKeyDown(event.keyCode)) {
                    return keyTracker.isPressed()
                }
                Log.d(ACCESSIBILITY_TAG, "Voice key down detected")
                handler.postDelayed(startRecordingRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }

            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(startRecordingRunnable)
                val shouldStop = keyTracker.onKeyUp(event.keyCode)
                if (shouldStop) {
                    Log.d(ACCESSIBILITY_TAG, "Voice key released, stopping recording")
                    sessionController.stopRecordingAndTranscribe()
                    return true
                }
                return true
            }
        }

        return false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlayController.isInitialized && ::sessionController.isInitialized) {
            cancelCurrentSession()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun configureServiceInfo() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
            } else {
                0
            }
        serviceInfo = info
    }

    private fun beginDictationTakeover() {
        if (!::overlayController.isInitialized || !::sessionController.isInitialized) {
            return
        }
        val focusedNode = focusedInputEditor.findFocusedEditableNode()
        if (!isEditableTarget(focusedNode)) {
            Log.d(ACCESSIBILITY_TAG, "Voice key long press ignored because no editable field is focused")
            showToast(R.string.overlay_focus_required)
            restoreKeyboardIfNeeded()
            return
        }

        serviceScope.launch {
            val config = loadVoiceInputConfig()
            shouldRestoreKeyboard = softKeyboardController.showMode != SHOW_MODE_HIDDEN
            softKeyboardController.setShowMode(SHOW_MODE_HIDDEN)
            Log.d(ACCESSIBILITY_TAG, "Starting dictation overlay for language ${config.languageLabel}")
            overlayController.show(config.languageLabel)
            overlayController.resetWaveform()
            val started = sessionController.startRecording(config)
            if (!started) {
                overlayController.hide()
                restoreKeyboardIfNeeded()
            }
        }
    }

    private fun handleTranscriptionResult(text: String) {
        Log.d(ACCESSIBILITY_TAG, "Received transcript with ${text.length} characters")
        val inserted = focusedInputEditor.insertText(text)
        if (!inserted) {
            Log.w(ACCESSIBILITY_TAG, "Failed to insert transcript into focused field")
            showToast(R.string.overlay_insert_failed)
        }
        overlayController.hide()
        restoreKeyboardIfNeeded()
    }

    private fun cancelCurrentSession() {
        handler.removeCallbacks(startRecordingRunnable)
        keyTracker.cancel()
        sessionController.cancel()
        overlayController.hide()
        restoreKeyboardIfNeeded()
    }

    private fun restoreKeyboardIfNeeded() {
        if (!shouldRestoreKeyboard) {
            return
        }
        softKeyboardController.setShowMode(SHOW_MODE_AUTO)
        shouldRestoreKeyboard = false
    }

    private fun launchMainActivity() {
        val dialogIntent = Intent(this, MainActivity::class.java)
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(dialogIntent)
    }

    private fun preloadChineseConversionTables() {
        ChineseUtils.preLoad(true, TransType.SIMPLE_TO_TAIWAN)
        ChineseUtils.preLoad(true, TransType.TAIWAN_TO_SIMPLE)
    }

    private fun showToast(stringRes: Int) {
        Toast.makeText(this, stringRes, Toast.LENGTH_SHORT).show()
    }

    private inner class SessionCallbacks : VoiceInputSessionController.Callbacks {
        override fun onStateChanged(state: VoiceInputSessionState) {
            when (state) {
                VoiceInputSessionState.Idle -> Unit
                VoiceInputSessionState.Recording -> overlayController.setRecordingState()
                VoiceInputSessionState.Transcribing -> overlayController.setTranscribingState()
            }
        }

        override fun onAmplitudeChanged(amplitude: Int) {
            overlayController.setAmplitude(amplitude)
        }

        override fun onPermissionsMissing() {
            Log.w(ACCESSIBILITY_TAG, "Permissions missing for voice dictation")
            overlayController.hide()
            restoreKeyboardIfNeeded()
            showToast(R.string.mic_permission_required)
            launchMainActivity()
        }

        override fun onTooShort() {
            Log.d(ACCESSIBILITY_TAG, "Recording was too short to submit")
            overlayController.hide()
            restoreKeyboardIfNeeded()
            showToast(R.string.dictation_too_short)
        }

        override fun onRecordingError() {
            Log.e(ACCESSIBILITY_TAG, "Failed to finalize the recorded audio")
            overlayController.hide()
            restoreKeyboardIfNeeded()
            showToast(R.string.dictation_recording_failed)
        }

        override fun onTranscriptionResult(text: String) {
            handleTranscriptionResult(text)
        }

        override fun onTranscriptionError(message: String) {
            Log.e(ACCESSIBILITY_TAG, "Transcription failed: $message")
            overlayController.hide()
            restoreKeyboardIfNeeded()
            Toast.makeText(this@HuaweiDictationAccessibilityService, message, Toast.LENGTH_LONG).show()
        }
    }

    private class RecorderAdapter(
        private val recorderManager: RecorderManager,
    ) : VoiceSessionRecorder {
        override fun hasPermissions(context: android.content.Context): Boolean {
            return recorderManager.allPermissionsGranted(context)
        }

        override fun start(context: android.content.Context, filename: String, useOggFormat: Boolean) {
            recorderManager.start(context, filename, useOggFormat)
        }

        override fun stop(): Boolean {
            return recorderManager.stop()
        }

        override fun setOnAmplitudeUpdate(listener: (Int) -> Unit) {
            recorderManager.setOnUpdateMicrophoneAmplitude(listener)
        }
    }

    private class TranscriberAdapter(
        private val transcriber: WhisperTranscriber,
    ) : VoiceSessionTranscriber {
        override fun startAsync(
            context: android.content.Context,
            filename: String,
            mediaType: String,
            attachToEnd: String,
            callback: (String?) -> Unit,
            exceptionCallback: (String) -> Unit,
        ) {
            transcriber.startAsync(context, filename, mediaType, attachToEnd, callback, exceptionCallback)
        }

        override fun stop() {
            transcriber.stop()
        }
    }
}
