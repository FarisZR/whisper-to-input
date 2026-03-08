package com.example.whispertoinput.voice

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.example.whispertoinput.ENDPOINT
import com.example.whispertoinput.LANGUAGE_CODE
import com.example.whispertoinput.R
import com.example.whispertoinput.SPEECH_TO_TEXT_BACKEND
import com.example.whispertoinput.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

const val RECORDED_AUDIO_FILENAME_M4A: String = "recorded.m4a"
const val RECORDED_AUDIO_FILENAME_OGG: String = "recorded.ogg"
const val AUDIO_MEDIA_TYPE_M4A: String = "audio/mp4"
const val AUDIO_MEDIA_TYPE_OGG: String = "audio/ogg"

data class VoiceInputConfig(
    val endpoint: String,
    val languageCode: String,
    val useOggFormat: Boolean,
    val recordedAudioFilename: String,
    val audioMediaType: String,
)

suspend fun Context.loadVoiceInputConfig(): VoiceInputConfig {
    val preferences = dataStore.data.map { settings: Preferences -> settings }.first()
    val backend = preferences[SPEECH_TO_TEXT_BACKEND] ?: getString(R.string.settings_option_openai_api)
    val languageCode = preferences[LANGUAGE_CODE] ?: getString(R.string.settings_option_openai_api_default_language)
    val useOggFormat = backend == getString(R.string.settings_option_nvidia_nim)
    val recordedAudioFilename = listOfNotNull(externalCacheDir?.absolutePath, if (useOggFormat) {
        RECORDED_AUDIO_FILENAME_OGG
    } else {
        RECORDED_AUDIO_FILENAME_M4A
    }).joinToString("/")

    return VoiceInputConfig(
        endpoint = preferences[ENDPOINT] ?: "",
        languageCode = languageCode,
        useOggFormat = useOggFormat,
        recordedAudioFilename = recordedAudioFilename,
        audioMediaType = if (useOggFormat) AUDIO_MEDIA_TYPE_OGG else AUDIO_MEDIA_TYPE_M4A,
    )
}
