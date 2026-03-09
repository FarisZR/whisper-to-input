package com.example.whispertoinput.speech

import android.speech.SpeechRecognizer
import android.os.SystemClock
import com.example.whispertoinput.WhisperTranscriber
import com.example.whispertoinput.recorder.RecorderManager
import com.example.whispertoinput.voice.VoiceInputConfig
import com.example.whispertoinput.voice.VoiceInputSessionController
import com.example.whispertoinput.voice.VoiceInputSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface RecognitionCallback {
    fun readyForSpeech()
    fun beginningOfSpeech()
    fun rmsChanged(rmsDb: Float)
    fun endOfSpeech()
    fun results(hypotheses: List<String>)
    fun error(errorCode: Int)
}

interface VoiceInputSession {
    fun state(): VoiceInputSessionState
    fun startRecording(config: VoiceInputConfig): Boolean
    fun stopRecordingAndTranscribe()
    fun cancel()
}

fun interface VoiceInputSessionFactory {
    fun create(callbacks: VoiceInputSessionController.Callbacks): VoiceInputSession
}

class WhisperRecognitionSession(
    private val coroutineScope: CoroutineScope,
    private val callback: RecognitionCallback,
    private val loadConfig: suspend () -> VoiceInputConfig,
    private val sessionFactory: VoiceInputSessionFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val noSpeechTimeoutMillis: Long = 5_000L,
    private val speechCompleteSilenceMillis: Long = 1_500L,
    private val onFinished: () -> Unit,
) : VoiceInputSessionController.Callbacks {
    private val session: VoiceInputSession = sessionFactory.create(this)
    private var completed: Boolean = false
    private var recordingStarted: Boolean = false
    private var speechDetected: Boolean = false
    private var startJob: Job? = null
    private var noSpeechTimeoutJob: Job? = null
    private var speechCompleteJob: Job? = null

    fun startListening() {
        startJob = coroutineScope.launch {
            val config = try {
                loadConfig()
            } catch (_: Exception) {
                emitError(SpeechRecognizer.ERROR_CLIENT)
                return@launch
            }
            if (completed) {
                return@launch
            }

            val started = session.startRecording(config)
            if (!started) {
                if (!completed) {
                    emitError(SpeechRecognizer.ERROR_CLIENT)
                }
                return@launch
            }

            recordingStarted = true
            callback.readyForSpeech()
            scheduleNoSpeechTimeout()
        }
    }

    fun stopListening() {
        if (completed) {
            return
        }
        if (session.state() != VoiceInputSessionState.Recording) {
            emitError(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        recordingStarted = false
        cancelTimeoutJobs()
        callback.endOfSpeech()
        session.stopRecordingAndTranscribe()
    }

    fun cancel() {
        if (completed) {
            return
        }
        startJob?.cancel()
        cancelTimeoutJobs()
        session.cancel()
        finish()
    }

    override fun onStateChanged(state: VoiceInputSessionState) {
        if (state == VoiceInputSessionState.Idle) {
            recordingStarted = false
            speechDetected = false
            cancelTimeoutJobs()
        }
    }

    override fun onAmplitudeChanged(amplitude: Int) {
        if (recordingStarted && !completed) {
            if (amplitude > 0) {
                if (!speechDetected) {
                    speechDetected = true
                    cancelNoSpeechTimeout()
                    callback.beginningOfSpeech()
                }
                scheduleSpeechCompleteTimeout()
            }
            callback.rmsChanged(amplitude.toFloat())
        }
    }

    override fun onPermissionsMissing() {
        emitError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
    }

    override fun onTooShort() {
        emitError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
    }

    override fun onRecordingError() {
        emitError(SpeechRecognizer.ERROR_AUDIO)
    }

    override fun onNoTranscriptionMatch() {
        emitError(SpeechRecognizer.ERROR_NO_MATCH)
    }

    override fun onTranscriptionResult(text: String) {
        if (completed) {
            return
        }
        callback.results(listOf(text))
        finish()
    }

    override fun onTranscriptionError(message: String) {
        emitError(SpeechRecognizer.ERROR_SERVER)
    }

    private fun emitError(errorCode: Int) {
        if (completed) {
            return
        }
        callback.error(errorCode)
        finish()
    }

    private fun finish() {
        if (completed) {
            return
        }
        completed = true
        recordingStarted = false
        speechDetected = false
        cancelTimeoutJobs()
        onFinished()
    }

    private fun scheduleNoSpeechTimeout() {
        cancelNoSpeechTimeout()
        noSpeechTimeoutJob = coroutineScope.launch(dispatcher) {
            delay(noSpeechTimeoutMillis)
            if (!speechDetected && !completed && session.state() == VoiceInputSessionState.Recording) {
                session.cancel()
                emitError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            }
        }
    }

    private fun scheduleSpeechCompleteTimeout() {
        speechCompleteJob?.cancel()
        speechCompleteJob = coroutineScope.launch(dispatcher) {
            delay(speechCompleteSilenceMillis)
            if (speechDetected && !completed && session.state() == VoiceInputSessionState.Recording) {
                stopListening()
            }
        }
    }

    private fun cancelNoSpeechTimeout() {
        noSpeechTimeoutJob?.cancel()
        noSpeechTimeoutJob = null
    }

    private fun cancelTimeoutJobs() {
        cancelNoSpeechTimeout()
        speechCompleteJob?.cancel()
        speechCompleteJob = null
    }
}

class DefaultVoiceInputSessionFactory(
    private val context: android.content.Context,
    private val minimumRecordingDurationMs: Long,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) : VoiceInputSessionFactory {
    override fun create(callbacks: VoiceInputSessionController.Callbacks): VoiceInputSession {
        return VoiceInputSessionControllerAdapter(
            VoiceInputSessionController(
                context = context,
                recorder = RecorderManager(context),
                transcriber = WhisperTranscriber(),
                minimumRecordingDurationMs = minimumRecordingDurationMs,
                nowMs = nowMs,
                callbacks = callbacks,
            ),
        )
    }
}

private class VoiceInputSessionControllerAdapter(
    private val controller: VoiceInputSessionController,
) : VoiceInputSession {
    override fun state(): VoiceInputSessionState = controller.state()

    override fun startRecording(config: VoiceInputConfig): Boolean {
        return controller.startRecording(config)
    }

    override fun stopRecordingAndTranscribe() {
        controller.stopRecordingAndTranscribe()
    }

    override fun cancel() {
        controller.cancel()
    }
}
