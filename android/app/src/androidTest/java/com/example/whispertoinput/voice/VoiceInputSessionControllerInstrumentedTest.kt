package com.example.whispertoinput.voice

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceInputSessionControllerInstrumentedTest {
    @Test
    fun startRecordingRequiresPermissions() {
        val callbacks = FakeCallbacks()
        val recorder = FakeRecorder(hasPermissions = false)
        val controller = createController(recorder = recorder, callbacks = callbacks)

        val started = controller.startRecording(testConfig())

        assertFalse(started)
        assertTrue(callbacks.permissionsMissing)
        assertEquals(VoiceInputSessionState.Idle, controller.state())
    }

    @Test
    fun shortRecordingTriggersTooShort() {
        val callbacks = FakeCallbacks()
        val now = FakeTimeProvider(0L)
        val recorder = FakeRecorder(hasPermissions = true)
        val controller = createController(recorder = recorder, callbacks = callbacks, now = now)

        assertTrue(controller.startRecording(testConfig()))
        now.current = 100L
        controller.stopRecordingAndTranscribe()

        assertTrue(callbacks.tooShort)
        assertEquals(VoiceInputSessionState.Idle, controller.state())
    }

    @Test
    fun successfulRecordingTransitionsThroughTranscription() {
        val callbacks = FakeCallbacks()
        val now = FakeTimeProvider(0L)
        val recorder = FakeRecorder(hasPermissions = true)
        val transcriber = FakeTranscriber(result = "Hello there")
        val controller = createController(
            recorder = recorder,
            transcriber = transcriber,
            callbacks = callbacks,
            now = now,
        )

        assertTrue(controller.startRecording(testConfig()))
        now.current = 500L
        controller.stopRecordingAndTranscribe()

        assertTrue(transcriber.started)
        assertEquals("Hello there", callbacks.result)
        assertEquals(VoiceInputSessionState.Idle, controller.state())
    }

    private fun createController(
        recorder: FakeRecorder,
        transcriber: FakeTranscriber = FakeTranscriber(result = "done"),
        callbacks: FakeCallbacks,
        now: FakeTimeProvider = FakeTimeProvider(0L),
    ): VoiceInputSessionController {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return VoiceInputSessionController(
            context = context,
            recorder = recorder,
            transcriber = transcriber,
            minimumRecordingDurationMs = 350L,
            nowMs = { now.current },
            callbacks = callbacks,
        )
    }

    private fun testConfig(): VoiceInputConfig {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return VoiceInputConfig(
            endpoint = "http://localhost",
            languageCode = "en",
            useOggFormat = false,
            recordedAudioFilename = "${context.cacheDir.absolutePath}/test-recording.m4a",
            audioMediaType = AUDIO_MEDIA_TYPE_M4A,
        )
    }

    private class FakeTimeProvider(var current: Long)

    private class FakeRecorder(
        private val hasPermissions: Boolean,
    ) : VoiceSessionRecorder {
        private var amplitudeListener: (Int) -> Unit = { }

        override fun hasPermissions(context: Context): Boolean = hasPermissions

        override fun start(context: Context, filename: String, useOggFormat: Boolean) {
            amplitudeListener(1200)
        }

        override fun stop(): Boolean = true

        override fun setOnAmplitudeUpdate(listener: (Int) -> Unit) {
            amplitudeListener = listener
        }
    }

    private class FakeTranscriber(
        private val result: String,
    ) : VoiceSessionTranscriber {
        var started: Boolean = false

        override fun startAsync(
            context: Context,
            filename: String,
            mediaType: String,
            attachToEnd: String,
            callback: (String?) -> Unit,
            exceptionCallback: (String) -> Unit,
        ) {
            started = true
            callback(result)
        }

        override fun stop() = Unit
    }

    private class FakeCallbacks : VoiceInputSessionController.Callbacks {
        var permissionsMissing: Boolean = false
        var tooShort: Boolean = false
        var noMatch: Boolean = false
        var result: String? = null

        override fun onStateChanged(state: VoiceInputSessionState) = Unit

        override fun onAmplitudeChanged(amplitude: Int) = Unit

        override fun onPermissionsMissing() {
            permissionsMissing = true
        }

        override fun onTooShort() {
            tooShort = true
        }

        override fun onRecordingError() = Unit

        override fun onNoTranscriptionMatch() {
            noMatch = true
        }

        override fun onTranscriptionResult(text: String) {
            result = text
        }

        override fun onTranscriptionError(message: String) = Unit
    }
}
