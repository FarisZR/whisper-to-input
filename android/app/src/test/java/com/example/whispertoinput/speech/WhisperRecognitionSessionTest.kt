package com.example.whispertoinput.speech

import android.speech.SpeechRecognizer
import com.example.whispertoinput.voice.AUDIO_MEDIA_TYPE_M4A
import com.example.whispertoinput.voice.VoiceInputConfig
import com.example.whispertoinput.voice.VoiceInputSessionController
import com.example.whispertoinput.voice.VoiceInputSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperRecognitionSessionTest {
    @Test
    fun startListeningStartsRecordingAndSignalsReady() {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(sessionFactory = sessionFactory, callback = callback)

        session.startListening()

        assertEquals(1, sessionFactory.voiceSession.startRecordingCalls)
        assertEquals(listOf("ready", "beginning"), callback.events)
    }

    @Test
    fun permissionFailureReportsInsufficientPermissions() {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(sessionFactory = sessionFactory, callback = callback)

        session.startListening()
        sessionFactory.callbacks.onPermissionsMissing()

        assertEquals(listOf(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS), callback.errors)
        assertEquals(1, callback.finishedCount)
    }

    @Test
    fun amplitudeChangesBecomeRmsUpdates() {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(sessionFactory = sessionFactory, callback = callback)

        session.startListening()
        sessionFactory.callbacks.onAmplitudeChanged(1200)

        assertEquals(listOf(1200f), callback.rmsValues)
    }

    @Test
    fun stopListeningEndsSpeechAndStartsTranscription() {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(sessionFactory = sessionFactory, callback = callback)

        session.startListening()
        sessionFactory.voiceSession.state = VoiceInputSessionState.Recording
        session.stopListening()

        assertTrue(callback.events.contains("end"))
        assertEquals(1, sessionFactory.voiceSession.stopRecordingAndTranscribeCalls)
    }

    @Test
    fun transcriptionResultReturnsSingleRecognitionHypothesis() {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(sessionFactory = sessionFactory, callback = callback)

        session.startListening()
        sessionFactory.callbacks.onTranscriptionResult("hello world")

        assertEquals(listOf(listOf("hello world")), callback.results)
        assertEquals(1, callback.finishedCount)
    }

    @Test
    fun noMatchReportsNoMatchError() {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(sessionFactory = sessionFactory, callback = callback)

        session.startListening()
        sessionFactory.callbacks.onNoTranscriptionMatch()

        assertEquals(listOf(SpeechRecognizer.ERROR_NO_MATCH), callback.errors)
        assertEquals(1, callback.finishedCount)
    }

    @Test
    fun cancelStopsActiveSessionWithoutError() {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(sessionFactory = sessionFactory, callback = callback)

        session.startListening()
        session.cancel()

        assertEquals(1, sessionFactory.voiceSession.cancelCalls)
        assertTrue(callback.errors.isEmpty())
        assertEquals(1, callback.finishedCount)
    }

    private fun createSession(
        sessionFactory: FakeVoiceInputSessionFactory,
        callback: FakeRecognitionCallback,
    ): WhisperRecognitionSession {
        return WhisperRecognitionSession(
            coroutineScope = CoroutineScope(Job() + Dispatchers.Unconfined),
            callback = callback,
            loadConfig = { testConfig() },
            sessionFactory = sessionFactory,
            onFinished = callback::onFinished,
        )
    }

    private fun testConfig(): VoiceInputConfig {
        return VoiceInputConfig(
            endpoint = "http://localhost",
            languageCode = "en",
            useOggFormat = false,
            recordedAudioFilename = "/tmp/test-recording.m4a",
            audioMediaType = AUDIO_MEDIA_TYPE_M4A,
        )
    }

    private class FakeVoiceInputSessionFactory : VoiceInputSessionFactory {
        lateinit var callbacks: VoiceInputSessionController.Callbacks
        val voiceSession = FakeVoiceInputSession()

        override fun create(callbacks: VoiceInputSessionController.Callbacks): VoiceInputSession {
            this.callbacks = callbacks
            return voiceSession
        }
    }

    private class FakeVoiceInputSession : VoiceInputSession {
        var state: VoiceInputSessionState = VoiceInputSessionState.Idle
        var startRecordingCalls: Int = 0
        var stopRecordingAndTranscribeCalls: Int = 0
        var cancelCalls: Int = 0

        override fun state(): VoiceInputSessionState = state

        override fun startRecording(config: VoiceInputConfig): Boolean {
            startRecordingCalls += 1
            state = VoiceInputSessionState.Recording
            return true
        }

        override fun stopRecordingAndTranscribe() {
            stopRecordingAndTranscribeCalls += 1
            state = VoiceInputSessionState.Transcribing
        }

        override fun cancel() {
            cancelCalls += 1
            state = VoiceInputSessionState.Idle
        }
    }

    private class FakeRecognitionCallback : RecognitionCallback {
        val events = mutableListOf<String>()
        val rmsValues = mutableListOf<Float>()
        val results = mutableListOf<List<String>>()
        val errors = mutableListOf<Int>()
        var finishedCount: Int = 0

        override fun readyForSpeech() {
            events += "ready"
        }

        override fun beginningOfSpeech() {
            events += "beginning"
        }

        override fun rmsChanged(rmsDb: Float) {
            rmsValues += rmsDb
        }

        override fun endOfSpeech() {
            events += "end"
        }

        override fun results(hypotheses: List<String>) {
            results += hypotheses
        }

        override fun error(errorCode: Int) {
            errors += errorCode
        }

        fun onFinished() {
            finishedCount += 1
        }
    }
}
