package com.tkrz.qrtix.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventPreferences @Inject constructor(
    context: Context,
    private val authPreferences: AuthPreferences
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("EventPrefs", Context.MODE_PRIVATE)

    private fun getKey(): String {
        val email = authPreferences.userEmail ?: "default"
        return "ACTIVE_EVENT_ID_$email"
    }

    private val _activeEventId = MutableStateFlow(getActiveEventId())
    val activeEventId: StateFlow<Long> = _activeEventId

    fun getActiveEventId(): Long {
        val key = getKey()
        if (!prefs.contains(key) && prefs.contains("ACTIVE_EVENT_ID")) {
            // Migrate legacy active event ID
            val legacyId = prefs.getLong("ACTIVE_EVENT_ID", 1L)
            prefs.edit().putLong(key, legacyId).apply()
            return legacyId
        }
        return prefs.getLong(key, 1L)
    }

    fun setActiveEventId(id: Long) {
        prefs.edit().putLong(getKey(), id).apply()
        _activeEventId.value = id
    }

    fun reloadActiveEventId() {
        _activeEventId.value = getActiveEventId()
    }
}
