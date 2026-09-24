package com.tkrz.qrtix.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)

    private val _isSignedIn = MutableStateFlow(prefs.getBoolean("IS_SIGNED_IN", false))
    val isSignedIn: StateFlow<Boolean> = _isSignedIn
    
    private val _hasSeenOnboarding = MutableStateFlow(prefs.getBoolean("HAS_SEEN_ONBOARDING", false))
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding
    
    val userEmail: String?
        get() = prefs.getString("USER_EMAIL", null)

    val userName: String?
        get() = prefs.getString("USER_NAME", null)

    val userPhotoUrl: String?
        get() = prefs.getString("USER_PHOTO_URL", null)

    fun setSignedIn(signedIn: Boolean, email: String? = null, displayName: String? = null, photoUrl: String? = null) {
        prefs.edit()
            .putBoolean("IS_SIGNED_IN", signedIn)
            .putString("USER_EMAIL", email)
            .putString("USER_NAME", displayName)
            .putString("USER_PHOTO_URL", photoUrl)
            .apply()
        _isSignedIn.value = signedIn
    }

    fun setHasSeenOnboarding(hasSeen: Boolean) {
        prefs.edit().putBoolean("HAS_SEEN_ONBOARDING", hasSeen).apply()
        _hasSeenOnboarding.value = hasSeen
    }
}
