package com.example.whispertoinput.speech

import android.speech.SpeechRecognizer
import com.example.whispertoinput.voice.AUDIO_MEDIA_TYPE_M4A
import com.example.whispertoinput.voice.applyRequestedLanguage
import com.example.whispertoinput.voice.VoiceInputConfig
import com.example.whispertoinput.voice.VoiceInputSessionController
import com.example.whispertoinput.voice.VoiceInputSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhisperRecognitionSessionTest {
    @Test
    fun startListeningStartsRecordingAndSignalsReady() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
        )

        session.startListening()
        runCurrent()

        assertEquals(1, sessionFactory.voiceSession.startRecordingCalls)
        assertEquals(listOf("ready"), callback.events)
    }

    @Test
    fun speechBeginsWhenAmplitudeArrives() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
        )

        session.startListening()
        runCurrent()
        sessionFactory.callbacks.onAmplitudeChanged(1200)

        assertEquals(listOf("ready", "beginning"), callback.events)
    }

    @Test
    fun permissionFailureReportsInsufficientPermissions() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
        )

        session.startListening()
        runCurrent()
        sessionFactory.callbacks.onPermissionsMissing()

        assertEquals(listOf(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS), callback.errors)
        assertEquals(1, callback.finishedCount)
    }

    @Test
    fun amplitudeChangesBecomeRmsUpdates() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
        )

        session.startListening()
        runCurrent()
        sessionFactory.callbacks.onAmplitudeChanged(1200)

        assertEquals(listOf(1200f), callback.rmsValues)
    }

    @Test
    fun stopListeningEndsSpeechAndStartsTranscription() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
        )

        session.startListening()
        runCurrent()
        sessionFactory.voiceSession.state = VoiceInputSessionState.Recording
        sessionFactory.callbacks.onAmplitudeChanged(1200)
        session.stopListening()

        assertTrue(callback.events.contains("end"))
        assertEquals(1, sessionFactory.voiceSession.stopRecordingAndTranscribeCalls)
    }

    @Test
    fun transcriptionResultReturnsSingleRecognitionHypothesis() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
        )

        session.startListening()
        runCurrent()
        sessionFactory.callbacks.onTranscriptionResult("hello world")

        assertEquals(listOf(listOf("hello world")), callback.results)
        assertEquals(1, callback.finishedCount)
    }

    @Test
    fun noMatchReportsNoMatchError() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
        )

        session.startListening()
        runCurrent()
        sessionFactory.callbacks.onNoTranscriptionMatch()

        assertEquals(listOf(SpeechRecognizer.ERROR_NO_MATCH), callback.errors)
        assertEquals(1, callback.finishedCount)
    }

    @Test
    fun cancelStopsActiveSessionWithoutError() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
        )

        session.startListening()
        runCurrent()
        session.cancel()

        assertEquals(1, sessionFactory.voiceSession.cancelCalls)
        assertTrue(callback.errors.isEmpty())
        assertEquals(1, callback.finishedCount)
    }

    @Test
    fun silenceAfterSpeechStopsRecordingAutomatically() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
            speechCompleteSilenceMillis = 1_000L,
        )

        session.startListening()
        runCurrent()
        sessionFactory.callbacks.onAmplitudeChanged(1200)

        advanceTimeBy(999L)
        runCurrent()
        assertEquals(0, sessionFactory.voiceSession.stopRecordingAndTranscribeCalls)

        advanceTimeBy(1L)
        advanceUntilIdle()
        assertEquals(1, sessionFactory.voiceSession.stopRecordingAndTranscribeCalls)
        assertTrue(callback.events.contains("end"))
    }

    @Test
    fun noSpeechTriggersTimeoutError() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
            noSpeechTimeoutMillis = 2_000L,
        )

        session.startListening()
        runCurrent()
        advanceTimeBy(2_000L)
        advanceUntilIdle()

        assertEquals(listOf(SpeechRecognizer.ERROR_SPEECH_TIMEOUT), callback.errors)
        assertEquals(1, sessionFactory.voiceSession.cancelCalls)
    }

    @Test
    fun languageOverrideUsesRecognizerIntentExtra() {
        val baseConfig = testConfig(languageCode = "en")
        val overridden = baseConfig.applyRequestedLanguage("fr-FR")

        assertEquals("fr-FR", overridden.languageCode)
    }

    @Test
    fun silenceDoesNotStartSpeechCallback() = runTest {
        val sessionFactory = FakeVoiceInputSessionFactory()
        val callback = FakeRecognitionCallback()
        val session = createSession(
            scope = this,
            sessionFactory = sessionFactory,
            callback = callback,
        )

        session.startListening()
        runCurrent()
        sessionFactory.callbacks.onAmplitudeChanged(0)

        assertFalse(callback.events.contains("beginning"))
    }

    private fun createSession(
        scope: TestScope,
        sessionFactory: FakeVoiceInputSessionFactory,
        callback: FakeRecognitionCallback,
        noSpeechTimeoutMillis: Long = 5_000L,
        speechCompleteSilenceMillis: Long = 1_500L,
    ): WhisperRecognitionSession {
        return WhisperRecognitionSession(
            coroutineScope = scope,
            callback = callback,
            loadConfig = { testConfig() },
            sessionFactory = sessionFactory,
            dispatcher = StandardTestDispatcher(scope.testScheduler),
            noSpeechTimeoutMillis = noSpeechTimeoutMillis,
            speechCompleteSilenceMillis = speechCompleteSilenceMillis,
            onFinished = callback::onFinished,
        )
    }

    private fun testConfig(languageCode: String = "en"): VoiceInputConfig {
        return VoiceInputConfig(
            endpoint = "http://localhost",
            languageCode = languageCode,
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
