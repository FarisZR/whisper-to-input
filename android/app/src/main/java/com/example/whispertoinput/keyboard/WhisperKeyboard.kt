/*
 * This file is part of Whisper To Input, see <https://github.com/j3soon/whisper-to-input>.
 *
 * Copyright (c) 2023-2025 Yan-Bin Diau, Johnson Sun
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.whispertoinput.keyboard

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.whispertoinput.R
import com.example.whispertoinput.accessibility.WaveformView

class WhisperKeyboard {
    private enum class KeyboardStatus {
        Idle,             // Ready to start recording
        Recording,       // Currently recording
        Transcribing,    // Waiting for transcription results
    }

    // Keyboard event listeners. Assignable custom behaviors upon certain UI events (user-operated).
    private var onStartRecording: () -> Unit = { }
    private var onCancelRecording: () -> Unit = { }
    private var onStartTranscribing: (attachToEnd: String) -> Unit = { }
    private var onCancelTranscribing: () -> Unit = { }
    private var onButtonBackspace: () -> Unit = { }
    private var onSwitchIme: () -> Unit = { }
    private var onOpenSettings: () -> Unit = { }
    private var onEnter: () -> Unit = { }
    private var onSpaceBar: () -> Unit = { }
    private var shouldShowRetry: () -> Boolean = { false }

    // Keyboard Status
    private var keyboardStatus: KeyboardStatus = KeyboardStatus.Idle

    // Views & Keyboard Layout
    private var keyboardView: ConstraintLayout? = null
    private var waveformView: WaveformView? = null
    private var waveformContainer: FrameLayout? = null
    private var micIcon: ImageView? = null
    private var buttonEnter: ImageButton? = null
    private var buttonCancel: ImageButton? = null
    private var buttonRetry: ImageButton? = null
    private var labelStatus: TextView? = null
    private var buttonSpaceBar: ImageButton? = null
    private var waitingIcon: ProgressBar? = null
    private var buttonBackspace: BackspaceButton? = null
    private var buttonPreviousIme: ImageButton? = null
    private var buttonSettings: ImageButton? = null

    fun setup(
        layoutInflater: LayoutInflater,
        shouldOfferImeSwitch: Boolean,
        onStartRecording: () -> Unit,
        onCancelRecording: () -> Unit,
        onStartTranscribing: (attachToEnd: String) -> Unit,
        onCancelTranscribing: () -> Unit,
        onButtonBackspace: () -> Unit,
        onEnter: () -> Unit,
        onSpaceBar: () -> Unit,
        onSwitchIme: () -> Unit,
        onOpenSettings: () -> Unit,
        shouldShowRetry: () -> Boolean,
    ): View {
        // Inflate the keyboard layout & assign views
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as ConstraintLayout
        waveformView = keyboardView!!.findViewById(R.id.keyboard_waveform_view)
        waveformContainer = keyboardView!!.findViewById(R.id.waveform_container)
        micIcon = keyboardView!!.findViewById(R.id.keyboard_mic_icon)
        buttonEnter = keyboardView!!.findViewById(R.id.btn_enter)
        buttonCancel = keyboardView!!.findViewById(R.id.btn_cancel)
        buttonRetry = keyboardView!!.findViewById(R.id.btn_retry)
        labelStatus = keyboardView!!.findViewById(R.id.label_status)
        buttonSpaceBar = keyboardView!!.findViewById(R.id.btn_space_bar)
        waitingIcon = keyboardView!!.findViewById(R.id.pb_waiting_icon)
        buttonBackspace = keyboardView!!.findViewById(R.id.btn_backspace)
        buttonPreviousIme = keyboardView!!.findViewById(R.id.btn_previous_ime)
        buttonSettings = keyboardView!!.findViewById(R.id.btn_settings)

        // Hide buttonPreviousIme if necessary
        if (!shouldOfferImeSwitch) {
            buttonPreviousIme!!.visibility = View.GONE
        }

        // Set onClick listeners
        waveformView!!.setOnClickListener { onWaveformClick() }
        buttonEnter!!.setOnClickListener { onButtonEnterClick() }
        buttonCancel!!.setOnClickListener { onButtonCancelClick() }
        buttonRetry!!.setOnClickListener { onButtonRetryClick() }
        buttonSettings!!.setOnClickListener { onButtonSettingsClick() }
        buttonBackspace!!.setBackspaceCallback { onButtonBackspaceClick() }
        buttonSpaceBar!!.setOnClickListener { onButtonSpaceBarClick() }

        if (shouldOfferImeSwitch) {
            buttonPreviousIme!!.setOnClickListener { onButtonPreviousImeClick() }
        }

        // Set event listeners
        this.onStartRecording = onStartRecording
        this.onCancelRecording = onCancelRecording
        this.onStartTranscribing = onStartTranscribing
        this.onCancelTranscribing = onCancelTranscribing
        this.onButtonBackspace = onButtonBackspace
        this.onSwitchIme = onSwitchIme
        this.onOpenSettings = onOpenSettings
        this.onEnter = onEnter
        this.onSpaceBar = onSpaceBar
        this.shouldShowRetry = shouldShowRetry

        // Resets keyboard upon setup
        reset()

        // Returns the keyboard view (non-nullable)
        return keyboardView!!
    }

    fun reset() {
        setKeyboardStatus(KeyboardStatus.Idle)
    }

    fun updateMicrophoneAmplitude(amplitude: Int) {
        if (keyboardStatus != KeyboardStatus.Recording) {
            return
        }
        waveformView?.setAmplitude(amplitude)
    }

    fun tryStartRecording() {
        if (keyboardStatus == KeyboardStatus.Idle) {
            setKeyboardStatus(KeyboardStatus.Recording)
            onStartRecording()
        }
    }

    fun tryCancelRecording() {
        if (keyboardStatus == KeyboardStatus.Recording) {
            setKeyboardStatus(KeyboardStatus.Idle)
            onCancelRecording()
        }
    }

    fun tryStartTranscribing(attachToEnd: String) {
        if (keyboardStatus == KeyboardStatus.Recording) {
            setKeyboardStatus(KeyboardStatus.Transcribing)
            onStartTranscribing(attachToEnd)
        }
    }

    private fun onButtonSpaceBarClick() {
        // Upon button space bar click.
        // Recording -> Start transcribing (with a whitespace included)
        // else -> invokes onSpaceBar
        if (keyboardStatus == KeyboardStatus.Recording) {
            setKeyboardStatus(KeyboardStatus.Transcribing)
            onStartTranscribing(" ")
        } else {
            onSpaceBar()
        }
    }

    private fun onButtonBackspaceClick() {
        // Currently, this onClick only makes a call to onButtonBackspace()
        this.onButtonBackspace()
    }

    private fun onButtonPreviousImeClick() {
        // Currently, this onClick only makes a call to onSwitchIme()
        this.onSwitchIme()
    }

    private fun onButtonSettingsClick() {
        // Currently, this onClick only makes a call to onOpenSettings()
        this.onOpenSettings()
    }

    private fun onWaveformClick() {
        // Upon waveform click...
        // Idle -> Start Recording
        // Recording -> Finish Recording (without a newline)
        // Transcribing -> Nothing (to avoid double-clicking by mistake)
        when (keyboardStatus) {
            KeyboardStatus.Idle -> {
                setKeyboardStatus(KeyboardStatus.Recording)
                onStartRecording()
            }

            KeyboardStatus.Recording -> {
                setKeyboardStatus(KeyboardStatus.Transcribing)
                onStartTranscribing("")
            }

            KeyboardStatus.Transcribing -> {
                return
            }
        }
    }

    private fun onButtonEnterClick() {
        // Upon button enter click.
        // Recording -> Start transcribing (with a newline included)
        // else -> invokes onEnter
        if (keyboardStatus == KeyboardStatus.Recording) {
            setKeyboardStatus(KeyboardStatus.Transcribing)
            onStartTranscribing("\r\n")
        } else {
            onEnter()
        }
    }

    private fun onButtonCancelClick() {
        // Upon button cancel click.
        // Recording -> Cancel
        // Transcribing -> Cancel
        // else -> nothing
        if (keyboardStatus == KeyboardStatus.Recording) {
            setKeyboardStatus(KeyboardStatus.Idle)
            onCancelRecording()
        } else if (keyboardStatus == KeyboardStatus.Transcribing) {
            setKeyboardStatus(KeyboardStatus.Idle)
            onCancelTranscribing()
        }
    }

    private fun onButtonRetryClick() {
        // Upon button retry click.
        // Idle -> Retry
        // else -> nothing
        if (keyboardStatus == KeyboardStatus.Idle) {
            setKeyboardStatus(KeyboardStatus.Transcribing)
            onStartTranscribing("")
        }
    }

    private fun setKeyboardStatus(newStatus: KeyboardStatus) {
        when (newStatus) {
            KeyboardStatus.Idle -> {
                labelStatus!!.setText(R.string.whisper_to_input)
                micIcon!!.visibility = View.VISIBLE
                waitingIcon!!.visibility = View.INVISIBLE
                buttonCancel!!.visibility = View.INVISIBLE
                buttonRetry!!.visibility = if (shouldShowRetry()) View.VISIBLE else View.INVISIBLE
                waveformView!!.reset()
                keyboardView!!.keepScreenOn = false
            }

            KeyboardStatus.Recording -> {
                labelStatus!!.setText(R.string.recording)
                micIcon!!.visibility = View.INVISIBLE
                waitingIcon!!.visibility = View.INVISIBLE
                buttonCancel!!.visibility = View.VISIBLE
                buttonRetry!!.visibility = View.INVISIBLE
                waveformView!!.showRecordingState()
                keyboardView!!.keepScreenOn = true
            }

            KeyboardStatus.Transcribing -> {
                labelStatus!!.setText(R.string.transcribing)
                micIcon!!.visibility = View.INVISIBLE
                waitingIcon!!.visibility = View.VISIBLE
                buttonCancel!!.visibility = View.VISIBLE
                buttonRetry!!.visibility = View.INVISIBLE
                waveformView!!.showTranscribingState()
                keyboardView!!.keepScreenOn = true
            }
        }

        updateWaveformAccessibility(newStatus)
        keyboardStatus = newStatus
    }

    private fun updateWaveformAccessibility(status: KeyboardStatus) {
        val view = waveformView ?: return
        val context = view.context

        when (status) {
            KeyboardStatus.Idle -> {
                view.isEnabled = true
                view.isClickable = true
                view.isFocusable = true
                view.contentDescription = context.getString(R.string.start_speech_to_text)
            }

            KeyboardStatus.Recording -> {
                view.isEnabled = true
                view.isClickable = true
                view.isFocusable = true
                view.contentDescription = context.getString(R.string.stop_speech_to_text)
            }

            KeyboardStatus.Transcribing -> {
                view.isEnabled = false
                view.isClickable = false
                view.isFocusable = false
                view.contentDescription = context.getString(R.string.transcribing)
            }
        }
    }
}
