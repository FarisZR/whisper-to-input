package com.example.whispertoinput.voice

import android.content.Context
import com.example.whispertoinput.R
import java.io.File

enum class VoiceInputSessionState {
    Idle,
    Recording,
    Transcribing,
}

interface VoiceSessionRecorder {
    fun hasPermissions(context: Context): Boolean
    fun start(context: Context, filename: String, useOggFormat: Boolean)
    fun stop(): Boolean
    fun setOnAmplitudeUpdate(listener: (Int) -> Unit)
}

interface VoiceSessionTranscriber {
    fun startAsync(
        context: Context,
        filename: String,
        mediaType: String,
        attachToEnd: String,
        callback: (String?) -> Unit,
        exceptionCallback: (String) -> Unit,
    )

    fun stop()
}

class VoiceInputSessionController(
    private val context: Context,
    private val recorder: VoiceSessionRecorder,
    private val transcriber: VoiceSessionTranscriber,
    private val minimumRecordingDurationMs: Long,
    private val nowMs: () -> Long,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onStateChanged(state: VoiceInputSessionState)
        fun onAmplitudeChanged(amplitude: Int)
        fun onPermissionsMissing()
        fun onTooShort()
        fun onRecordingError()
        fun onNoTranscriptionMatch()
        fun onTranscriptionResult(text: String)
        fun onTranscriptionError(message: String)
    }

    private var state: VoiceInputSessionState = VoiceInputSessionState.Idle
    private var currentConfig: VoiceInputConfig? = null
    private var recordingStartedAtMs: Long = 0L
    private var activeSessionToken: Long = 0L

    init {
        recorder.setOnAmplitudeUpdate { amplitude ->
            if (state == VoiceInputSessionState.Recording) {
                callbacks.onAmplitudeChanged(amplitude)
            }
        }
    }

    fun state(): VoiceInputSessionState = state

    fun startRecording(config: VoiceInputConfig): Boolean {
        if (state != VoiceInputSessionState.Idle) {
            return false
        }
        if (!recorder.hasPermissions(context)) {
            callbacks.onPermissionsMissing()
            return false
        }

        currentConfig = config
        activeSessionToken += 1
        recordingStartedAtMs = nowMs()
        recorder.start(context, config.recordedAudioFilename, config.useOggFormat)
        updateState(VoiceInputSessionState.Recording)
        return true
    }

    fun stopRecordingAndTranscribe() {
        if (state != VoiceInputSessionState.Recording) {
            return
        }

        val config = currentConfig ?: run {
            updateState(VoiceInputSessionState.Idle)
            return
        }

        val recordingDurationMs = nowMs() - recordingStartedAtMs
        val recorderStoppedCleanly = recorder.stop()
        if (!recorderStoppedCleanly) {
            cleanupRecordedAudio(config.recordedAudioFilename)
            currentConfig = null
            updateState(VoiceInputSessionState.Idle)
            callbacks.onRecordingError()
            return
        }

        if (recordingDurationMs < minimumRecordingDurationMs) {
            cleanupRecordedAudio(config.recordedAudioFilename)
            currentConfig = null
            updateState(VoiceInputSessionState.Idle)
            callbacks.onTooShort()
            return
        }

        val sessionToken = activeSessionToken
        updateState(VoiceInputSessionState.Transcribing)
        transcriber.startAsync(
            context = context,
            filename = config.recordedAudioFilename,
            mediaType = config.audioMediaType,
            attachToEnd = "",
            callback = { text ->
                if (sessionToken != activeSessionToken) {
                    return@startAsync
                }
                if (!text.isNullOrEmpty()) {
                    currentConfig = null
                    updateState(VoiceInputSessionState.Idle)
                    callbacks.onTranscriptionResult(text)
                } else {
                    currentConfig = null
                    updateState(VoiceInputSessionState.Idle)
                    callbacks.onNoTranscriptionMatch()
                }
            },
            exceptionCallback = { message ->
                if (sessionToken != activeSessionToken) {
                    return@startAsync
                }
                currentConfig = null
                updateState(VoiceInputSessionState.Idle)
                callbacks.onTranscriptionError(message)
            },
        )
    }

    fun cancel() {
        val config = currentConfig
        activeSessionToken += 1
        when (state) {
            VoiceInputSessionState.Recording -> {
                recorder.stop()
                if (config != null) {
                    cleanupRecordedAudio(config.recordedAudioFilename)
                }
            }

            VoiceInputSessionState.Transcribing -> {
                transcriber.stop()
                if (config != null) {
                    cleanupRecordedAudio(config.recordedAudioFilename)
                }
            }

            VoiceInputSessionState.Idle -> Unit
        }
        currentConfig = null
        updateState(VoiceInputSessionState.Idle)
    }

    private fun cleanupRecordedAudio(filename: String) {
        File(filename).delete()
    }

    private fun updateState(newState: VoiceInputSessionState) {
        state = newState
        callbacks.onStateChanged(newState)
    }
}
