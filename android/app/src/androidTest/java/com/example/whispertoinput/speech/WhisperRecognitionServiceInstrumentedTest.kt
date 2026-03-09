package com.example.whispertoinput.speech

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperRecognitionServiceInstrumentedTest {
    @Test
    fun defaultSpeechRecognizerRoutesToWhisperRecognitionService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val componentName = ComponentName(context, WhisperRecognitionService::class.java)
        val originalService = shell("settings get secure voice_recognition_service").trim()

        val services = context.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            PackageManager.GET_META_DATA,
        )
        assertTrue(services.any { it.serviceInfo.name == WhisperRecognitionService::class.java.name })

        shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        RecognitionServiceDiagnostics.reset(context)
        shell("settings put secure voice_recognition_service ${componentName.flattenToShortString()}")

        var recognizer: SpeechRecognizer? = null
        try {
            instrumentation.runOnMainSync {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer!!.setRecognitionListener(EmptyRecognitionListener())
                recognizer!!.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    },
                )
            }

            waitForCondition(timeoutMs = 8_000L) {
                RecognitionServiceDiagnostics.startCount(context) > 0
            }

            assertEquals(1, RecognitionServiceDiagnostics.startCount(context))
        } finally {
            instrumentation.runOnMainSync {
                recognizer?.cancel()
                recognizer?.destroy()
            }

            if (originalService.isBlank() || originalService == "null") {
                shell("settings delete secure voice_recognition_service")
            } else {
                shell("settings put secure voice_recognition_service $originalService")
            }
        }
    }

    private fun shell(command: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { reader ->
                return reader.readText()
            }
        }
    }

    private fun waitForCondition(timeoutMs: Long, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) {
                return
            }
            Thread.sleep(200L)
        }
        throw AssertionError("Condition was not met within ${timeoutMs}ms")
    }

    private class EmptyRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) = Unit

        override fun onResults(results: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
