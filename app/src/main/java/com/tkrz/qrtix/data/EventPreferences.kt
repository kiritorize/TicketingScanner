package com.tkrz.qrtix.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EventPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("EventPrefs", Context.MODE_PRIVATE)

    private val _activeEventId = MutableStateFlow(getActiveEventId())
    val activeEventId: StateFlow<Long> = _activeEventId

    fun getActiveEventId(): Long {
        return prefs.getLong("ACTIVE_EVENT_ID", 1L)
    }

    fun setActiveEventId(id: Long) {
        prefs.edit().putLong("ACTIVE_EVENT_ID", id).apply()
        _activeEventId.value = id
    }
}
