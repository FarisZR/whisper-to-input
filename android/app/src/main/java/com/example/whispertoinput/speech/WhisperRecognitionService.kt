package com.example.whispertoinput.speech

import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.whispertoinput.R
import com.example.whispertoinput.voice.applyRequestedLanguage
import com.example.whispertoinput.voice.loadVoiceInputConfig
import com.example.whispertoinput.voice.requestedLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

private const val RECOGNITION_TAG = "whisper-recognition"

class WhisperRecognitionService : RecognitionService() {
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var activeFrameworkCallback: Callback? = null
    private var activeSession: WhisperRecognitionSession? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        if (activeSession != null) {
            FrameworkRecognitionCallback(listener).error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            return
        }

        val listenerContext = listenerContext(listener)
        RecognitionServiceDiagnostics.recordStart(this)
        activeFrameworkCallback = listener
        activeSession = WhisperRecognitionSession(
            coroutineScope = serviceScope,
            callback = FrameworkRecognitionCallback(listener),
            loadConfig = {
                listenerContext.loadVoiceInputConfig()
                    .applyRequestedLanguage(recognizerIntent.requestedLanguage())
            },
            sessionFactory = DefaultVoiceInputSessionFactory(
                context = listenerContext,
                minimumRecordingDurationMs = resources.getInteger(R.integer.dictation_min_recording_duration_ms).toLong(),
            ),
            onFinished = {
                if (activeFrameworkCallback === listener) {
                    activeFrameworkCallback = null
                    activeSession = null
                }
            },
        )
        activeSession?.startListening()
    }

    override fun onStopListening(listener: Callback) {
        val session = activeSession
        if (session == null || activeFrameworkCallback !== listener) {
            FrameworkRecognitionCallback(listener).error(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        session.stopListening()
    }

    override fun onCancel(listener: Callback) {
        if (activeFrameworkCallback !== listener) {
            return
        }
        activeSession?.cancel()
    }

    override fun onDestroy() {
        activeSession?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun listenerContext(listener: Callback): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return this
        }

        return try {
            createContext(
                ContextParams.Builder()
                    .setNextAttributionSource(listener.callingAttributionSource)
                    .build(),
            )
        } catch (exception: Exception) {
            Log.w(RECOGNITION_TAG, "Falling back to service context for recognition", exception)
            this
        }
    }
}

private class FrameworkRecognitionCallback(
    private val callback: RecognitionService.Callback,
) : RecognitionCallback {
    override fun readyForSpeech() {
        callSafely { callback.readyForSpeech(Bundle()) }
    }

    override fun beginningOfSpeech() {
        callSafely { callback.beginningOfSpeech() }
    }

    override fun rmsChanged(rmsDb: Float) {
        callSafely { callback.rmsChanged(rmsDb) }
    }

    override fun endOfSpeech() {
        callSafely { callback.endOfSpeech() }
    }

    override fun results(hypotheses: List<String>) {
        val results = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, ArrayList(hypotheses))
        }
        callSafely { callback.results(results) }
    }

    override fun error(errorCode: Int) {
        callSafely { callback.error(errorCode) }
    }

    private fun callSafely(action: () -> Unit) {
        try {
            action()
        } catch (exception: RemoteException) {
            Log.w(RECOGNITION_TAG, "Recognition callback delivery failed", exception)
        }
    }
}
