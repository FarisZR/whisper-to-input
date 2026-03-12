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

package com.example.whispertoinput

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.*
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import com.example.whispertoinput.accessibility.KeyboardShortcut
import com.example.whispertoinput.accessibility.captureKeyboardShortcut
import com.example.whispertoinput.accessibility.defaultKeyboardShortcut
import com.example.whispertoinput.accessibility.formatKeyboardShortcut
import com.example.whispertoinput.accessibility.toKeyboardShortcut

// 200 is an arbitrary value as long as it does not conflict with another request code
private const val MICROPHONE_PERMISSION_REQUEST_CODE = 200

class MainActivity : AppCompatActivity() {
    private data class ShortcutPreset(
        val label: String,
        val shortcut: KeyboardShortcut?,
    )

    private var setupSettingItemsDone: Boolean = false
    private var shortcutCaptureSetting: SettingShortcutCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupSettingItems()
        checkPermissions()
    }

    // The onClick event of the grant permission button.
    // Opens up the app settings panel to manually configure permissions.
    fun onRequestMicrophonePermission(view: View) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        with(intent) {
            data = Uri.fromParts("package", packageName, null)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }

        startActivity(intent)
    }

    fun onOpenAccessibilitySettings(view: View) {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun onOpenKeyboardSettings(view: View) {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun onOpenVoiceInputSettings(view: View) {
        try {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    // Checks whether permissions are granted. If not, automatically make a request.
    private fun checkPermissions() {
        val permission_and_code = arrayOf(
            Pair(Manifest.permission.RECORD_AUDIO, MICROPHONE_PERMISSION_REQUEST_CODE),
        )
        for ((permission, code) in permission_and_code) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_DENIED
            ) {
                // Shows a popup for permission request.
                // If the permission has been previously (hard-)denied, the popup will not show.
                // onRequestPermissionsResult will be called in either case.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    code
                )
            }
        }
    }

    // Handles the results of permission requests.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        var msg: String

        // Only handles requests marked with the unique code.
        if (requestCode == MICROPHONE_PERMISSION_REQUEST_CODE) {
            msg = getString(R.string.mic_permission_required)
        } else {
            return
        }

        // All permissions should be granted.
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    // Below are settings related functions
    abstract inner class SettingItem() {
        protected var isDirty: Boolean = false
        abstract fun setup() : Job
        abstract suspend fun apply()
        protected suspend fun <T> readSetting(key: Preferences.Key<T>): T? {
            // work is moved to `Dispatchers.IO` under the hood
            // Ref: https://developer.android.com/codelabs/android-preferences-datastore#3
            return dataStore.data.map { preferences ->
                preferences[key]
            }.first()
        }
        protected suspend fun <T> writeSetting(key: Preferences.Key<T>, newValue: T) {
            // work is moved to `Dispatchers.IO` under the hood
            // Ref: https://developer.android.com/codelabs/android-preferences-datastore#3
            dataStore.edit { settings ->
                settings[key] = newValue
            }
        }
    }

    inner class SettingText(
        private val viewId: Int,
        private val preferenceKey: Preferences.Key<String>,
        private val defaultValue: String = ""
    ): SettingItem() {
        override fun setup(): Job {
            return CoroutineScope(Dispatchers.Main).launch {
                val btnApply: Button = findViewById(R.id.btn_settings_apply)
                val editText = findViewById<EditText>(viewId)
                editText.isEnabled = false
                editText.doOnTextChanged { _, _, _, _ ->
                    if (!setupSettingItemsDone) return@doOnTextChanged
                    isDirty = true
                    btnApply.isEnabled = true
                }

                // Read data. If none, apply default value.
                val settingValue: String? = readSetting(preferenceKey)
                val value: String = settingValue ?: defaultValue
                if (settingValue == null) {
                    writeSetting(preferenceKey, defaultValue)
                }
                editText.setText(value)
                editText.isEnabled = true
            }
        }
        override suspend fun apply() {
            if (!isDirty) return
            val newValue: String = findViewById<EditText>(viewId).text.toString()
            writeSetting(preferenceKey, newValue)
            isDirty = false
        }
    }

    inner class SettingDropdown(
        private val viewId: Int,
        private val preferenceKey: Preferences.Key<Boolean>,
        private val stringToValue: HashMap<String, Boolean>,
        private val defaultValue: Boolean = true
    ): SettingItem() {
        override fun setup(): Job {
            return CoroutineScope(Dispatchers.Main).launch {
                val btnApply: Button = findViewById(R.id.btn_settings_apply)
                val spinner = findViewById<Spinner>(viewId)
                spinner.isEnabled = false
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                        if (!setupSettingItemsDone) return
                        isDirty = true
                        btnApply.isEnabled = true
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) { }
                }

                val valueToString = stringToValue.map { (k, v) -> v to k }.toMap()
                // Read data. If none, apply default value.
                val settingValue: Boolean? = readSetting(preferenceKey)
                val value: Boolean = settingValue ?: defaultValue
                val string: String = valueToString[value]!!
                if (settingValue == null) {
                    writeSetting(preferenceKey, defaultValue)
                }
                val index: Int? = (0 until spinner.adapter.count).firstOrNull {
                    spinner.adapter.getItem(it) == string
                }
                spinner.setSelection(index!!, false)
                spinner.isEnabled = true
            }
        }
        override suspend fun apply() {
            if (!isDirty) return
            val selectedItem = findViewById<Spinner>(viewId).selectedItem
            val newValue: Boolean = stringToValue[selectedItem]!!
            writeSetting(preferenceKey, newValue)
            isDirty = false
        }
    }

    inner class SettingStringDropdown(
        private val viewId: Int,
        private val preferenceKey: Preferences.Key<String>,
        private val optionValues: List<String>,
        private val defaultValue: String = ""
    ): SettingItem() {
        override fun setup(): Job {
            return CoroutineScope(Dispatchers.Main).launch {
                val btnApply: Button = findViewById(R.id.btn_settings_apply)
                val spinner = findViewById<Spinner>(viewId)
                spinner.isEnabled = false
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                        if (!setupSettingItemsDone) return
                        isDirty = true
                        btnApply.isEnabled = true
                        // Deal with individual spinner
                        if (parent.id == R.id.spinner_speech_to_text_backend) {
                            val selectedItem = parent.getItemAtPosition(pos)
                            if (selectedItem == getString(R.string.settings_option_openai_api)) {
                                val endpointEditText: EditText = findViewById<EditText>(R.id.field_endpoint)
                                endpointEditText.setText(getString(R.string.settings_option_openai_api_default_endpoint))
                                val modelEditText: EditText = findViewById<EditText>(R.id.field_model)
                                modelEditText.setText(getString(R.string.settings_option_openai_api_default_model))
                            } else if (selectedItem == getString(R.string.settings_option_whisper_asr_webservice)) {
                                val endpointEditText: EditText = findViewById<EditText>(R.id.field_endpoint)
                                if (endpointEditText.text.isEmpty() ||
                                    endpointEditText.text.toString() == getString(R.string.settings_option_openai_api_default_endpoint) ||
                                    endpointEditText.text.toString() == getString(R.string.settings_option_nvidia_nim_default_endpoint)
                                ) {
                                    endpointEditText.setText(getString(R.string.settings_option_whisper_asr_webservice_default_endpoint))
                                }
                                val modelEditText: EditText = findViewById<EditText>(R.id.field_model)
                                modelEditText.setText(getString(R.string.settings_option_whisper_asr_webservice_default_model))
                            } else if (selectedItem == getString(R.string.settings_option_nvidia_nim)) {
                                val endpointEditText: EditText = findViewById<EditText>(R.id.field_endpoint)
                                if (endpointEditText.text.isEmpty() ||
                                    endpointEditText.text.toString() == getString(R.string.settings_option_openai_api_default_endpoint) ||
                                    endpointEditText.text.toString() == getString(R.string.settings_option_whisper_asr_webservice_default_endpoint)
                                ) {
                                    endpointEditText.setText(getString(R.string.settings_option_nvidia_nim_default_endpoint))
                                }
                                val modelEditText: EditText = findViewById<EditText>(R.id.field_model)
                                modelEditText.setText(getString(R.string.settings_option_nvidia_nim_default_model))
                                val languageCodeEditText: EditText = findViewById<EditText>(R.id.field_language_code)
                                languageCodeEditText.setText(getString(R.string.settings_option_nvidia_nim_default_language))
                            }
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) { }
                }

                // Read data. If none, apply default value.
                val settingValue: String? = readSetting(preferenceKey)
                val value: String = settingValue ?: defaultValue
                if (settingValue == null) {
                    writeSetting(preferenceKey, defaultValue)
                }
                val index: Int? = (0 until spinner.adapter.count).firstOrNull {
                    spinner.adapter.getItem(it) == value
                }
                spinner.setSelection(index ?: 0, false)
                spinner.isEnabled = true
            }
        }
        override suspend fun apply() {
            if (!isDirty) return
            val selectedItem = findViewById<Spinner>(viewId).selectedItem
            val newValue: String = selectedItem.toString()
            writeSetting(preferenceKey, newValue)
            isDirty = false
        }
    }

    inner class SettingShortcutCapture(
        private val valueViewId: Int,
        private val presetSpinnerViewId: Int,
        private val buttonViewId: Int,
    ) : SettingItem() {
        private var shortcut: KeyboardShortcut = KeyboardShortcut(0, 0)
        private var capturing: Boolean = false
        private var updatingPresetSelection: Boolean = false

        private val presetOptions: List<ShortcutPreset> by lazy {
            listOf(
                ShortcutPreset(
                    formatKeyboardShortcut(defaultKeyboardShortcut()),
                    defaultKeyboardShortcut(),
                ),
                ShortcutPreset(
                    formatKeyboardShortcut(
                        KeyboardShortcut(
                            keyCode = KeyEvent.KEYCODE_SPACE,
                            modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
                        ),
                    ),
                    KeyboardShortcut(
                        keyCode = KeyEvent.KEYCODE_SPACE,
                        modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
                    ),
                ),
                ShortcutPreset(
                    formatKeyboardShortcut(
                        KeyboardShortcut(
                            keyCode = KeyEvent.KEYCODE_D,
                            modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
                        ),
                    ),
                    KeyboardShortcut(
                        keyCode = KeyEvent.KEYCODE_D,
                        modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
                    ),
                ),
                ShortcutPreset(
                    formatKeyboardShortcut(
                        KeyboardShortcut(
                            keyCode = KeyEvent.KEYCODE_D,
                            modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
                        ),
                    ),
                    KeyboardShortcut(
                        keyCode = KeyEvent.KEYCODE_D,
                        modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
                    ),
                ),
                ShortcutPreset(
                    getString(R.string.settings_overlay_shortcut_custom_option),
                    null,
                ),
            )
        }

        override fun setup(): Job {
            return CoroutineScope(Dispatchers.Main).launch {
                val btnApply: Button = findViewById(R.id.btn_settings_apply)
                val presetSpinner = findViewById<Spinner>(presetSpinnerViewId)
                presetSpinner.isEnabled = false
                presetSpinner.adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    presetOptions.map { it.label },
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                        if (!setupSettingItemsDone || updatingPresetSelection) {
                            return
                        }
                        val selectedShortcut = presetOptions[pos].shortcut ?: return
                        if (selectedShortcut == shortcut) {
                            return
                        }
                        shortcut = selectedShortcut
                        isDirty = true
                        updateViews()
                        btnApply.isEnabled = true
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {
                    }
                }
                shortcut = dataStore.data.map { preferences -> preferences.toKeyboardShortcut() }.first()
                updateViews()
                presetSpinner.isEnabled = true

                findViewById<Button>(buttonViewId).setOnClickListener {
                    capturing = !capturing
                    if (capturing) {
                        Toast.makeText(
                            this@MainActivity,
                            R.string.settings_overlay_shortcut_capture_toast,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    updateViews()
                    btnApply.isEnabled = isDirty
                }
            }
        }

        override suspend fun apply() {
            if (!isDirty) return
            writeSetting(SHORTCUT_KEY_CODE, shortcut.keyCode)
            writeSetting(SHORTCUT_MODIFIERS, shortcut.modifiers)
            isDirty = false
            updateViews()
        }

        fun handleKeyEvent(event: KeyEvent): Boolean {
            if (!capturing || event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
                return false
            }
            if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                capturing = false
                updateViews()
                return true
            }

            val capturedShortcut = captureKeyboardShortcut(event.keyCode, event.metaState)
            if (capturedShortcut == null) {
                Toast.makeText(
                    this@MainActivity,
                    R.string.settings_overlay_shortcut_requires_modifier,
                    Toast.LENGTH_SHORT,
                ).show()
                return true
            }

            shortcut = capturedShortcut
            capturing = false
            isDirty = true
            findViewById<Button>(R.id.btn_settings_apply).isEnabled = true
            updateViews()
            return true
        }

        fun cancelCapture() {
            if (!capturing) {
                return
            }
            capturing = false
            updateViews()
        }

        private fun updateViews() {
            findViewById<TextView>(valueViewId).text = formatKeyboardShortcut(shortcut)
            val presetSpinner = findViewById<Spinner>(presetSpinnerViewId)
            val selectedPresetIndex = presetOptions.indexOfFirst { it.shortcut == shortcut }
            updatingPresetSelection = true
            presetSpinner.setSelection(
                if (selectedPresetIndex >= 0) selectedPresetIndex else presetOptions.lastIndex,
                false,
            )
            updatingPresetSelection = false
            findViewById<Button>(buttonViewId).text = if (capturing) {
                getString(R.string.settings_overlay_shortcut_capture_listening)
            } else {
                getString(R.string.settings_overlay_shortcut_capture_button)
            }
        }
    }

    private fun setupSettingItems() {
        setupSettingItemsDone = false
        // Add setting items here to apply functions to them
        CoroutineScope(Dispatchers.Main).launch {
            val settingItems = arrayOf(
                SettingStringDropdown(R.id.spinner_speech_to_text_backend, SPEECH_TO_TEXT_BACKEND, listOf(
                    getString(R.string.settings_option_openai_api),
                    getString(R.string.settings_option_whisper_asr_webservice),
                    getString(R.string.settings_option_nvidia_nim)
                ), getString(R.string.settings_option_openai_api)),
                SettingText(R.id.field_endpoint, ENDPOINT, getString(R.string.settings_option_openai_api_default_endpoint)),
                SettingText(R.id.field_language_code, LANGUAGE_CODE, getString(R.string.settings_option_openai_api_default_language)),
                SettingText(R.id.field_api_key, API_KEY),
                SettingText(R.id.field_model, MODEL, getString(R.string.settings_option_openai_api_default_model)),
                SettingDropdown(R.id.spinner_auto_recording_start, AUTO_RECORDING_START, hashMapOf(
                    getString(R.string.settings_option_yes) to true,
                    getString(R.string.settings_option_no) to false,
                )),
                SettingDropdown(R.id.spinner_auto_switch_back, AUTO_SWITCH_BACK, hashMapOf(
                    getString(R.string.settings_option_yes) to true,
                    getString(R.string.settings_option_no) to false,
                ), false),
                SettingDropdown(R.id.spinner_add_trailing_space, ADD_TRAILING_SPACE, hashMapOf(
                    getString(R.string.settings_option_yes) to true,
                    getString(R.string.settings_option_no) to false,
                ), false),
                SettingStringDropdown(R.id.spinner_postprocessing, POSTPROCESSING, listOf(
                    getString(R.string.settings_option_to_traditional),
                    getString(R.string.settings_option_to_simplified),
                    getString(R.string.settings_option_no_conversion)
                ), getString(R.string.settings_option_to_traditional)),
            )
            shortcutCaptureSetting = SettingShortcutCapture(
                R.id.value_overlay_shortcut,
                R.id.spinner_overlay_shortcut_preset,
                R.id.btn_capture_overlay_shortcut,
            )
            val btnApply: Button = findViewById(R.id.btn_settings_apply)
            btnApply.isEnabled = false
            btnApply.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    btnApply.isEnabled = false
                    for (settingItem in settingItems) {
                        settingItem.apply()
                    }
                    shortcutCaptureSetting?.apply()
                    btnApply.isEnabled = false
                }
                Toast.makeText(this@MainActivity, R.string.successfully_set, Toast.LENGTH_SHORT).show()
            }
            settingItems.map { settingItem -> settingItem.setup() }.joinAll()
            shortcutCaptureSetting?.setup()?.join()
            setupSettingItemsDone = true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (shortcutCaptureSetting?.handleKeyEvent(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        shortcutCaptureSetting?.cancelCapture()
        super.onPause()
    }
}
