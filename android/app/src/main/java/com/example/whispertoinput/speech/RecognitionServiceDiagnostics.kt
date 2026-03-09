package com.example.whispertoinput.speech

import android.content.Context

private const val DIAGNOSTICS_PREFS = "recognition-service-diagnostics"
private const val START_COUNT_KEY = "start-count"

object RecognitionServiceDiagnostics {
    fun reset(context: Context) {
        context.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(START_COUNT_KEY, 0)
            .apply()
    }

    fun recordStart(context: Context) {
        val preferences = context.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
        val nextValue = preferences.getInt(START_COUNT_KEY, 0) + 1
        preferences.edit().putInt(START_COUNT_KEY, nextValue).apply()
    }

    fun startCount(context: Context): Int {
        return context.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
            .getInt(START_COUNT_KEY, 0)
    }
}
