package com.lilt.feature.home

import android.content.Context

class LocalSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun phoneNumber(): String = preferences.getString(KEY_PHONE_NUMBER, "").orEmpty()

    fun displayName(): String = preferences.getString(KEY_DISPLAY_NAME, "").orEmpty()

    fun saveSession(phoneNumber: String, displayName: String = displayName()) {
        preferences.edit()
            .putString(KEY_PHONE_NUMBER, phoneNumber)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
    }

    fun savePhoneNumber(phoneNumber: String) {
        saveSession(phoneNumber = phoneNumber)
    }

    fun saveDisplayName(displayName: String) {
        saveSession(phoneNumber = phoneNumber(), displayName = displayName)
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val NAME = "lilt_session"
        const val KEY_PHONE_NUMBER = "phone_number"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}
