package com.tkrz.qrtix.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tkrz.qrtix.R
import com.tkrz.qrtix.data.AuthPreferences
import com.tkrz.qrtix.data.cloud.CloudPreferences
import com.tkrz.qrtix.data.cloud.SpreadsheetManager
import com.tkrz.qrtix.data.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val email: String, val displayName: String?) : AuthState()
    data class Error(val message: String) : AuthState()
    data class NeedsConsent(val intent: android.content.Intent) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val spreadsheetManager: SpreadsheetManager,
    private val eventRepository: EventRepository,
    private val historyLogRepository: com.tkrz.qrtix.data.repository.HistoryLogRepository,
    private val cloudPreferences: CloudPreferences
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isUserAuthenticated: StateFlow<Boolean> = authPreferences.isSignedIn
    val hasSeenOnboarding: StateFlow<Boolean> = authPreferences.hasSeenOnboarding

    fun completeOnboarding() {
        authPreferences.setHasSeenOnboarding(true)
    }

    fun checkAuthStatus() {
        if (authPreferences.isSignedIn.value) {
            _authState.value = AuthState.Authenticated("User", null)
        } else {
            _authState.value = AuthState.Idle
        }
    }

    fun signIn(context: Context) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credentialManager = CredentialManager.create(context)
                val webClientId = context.getString(R.string.web_client_id)
                
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context as Activity // Context must be Activity
                )

                handleSignInResult(result)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign in failed", e)
                _authState.value = AuthState.Error(e.localizedMessage ?: "Gagal masuk")
            }
        }
    }

    private suspend fun handleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                
                // Decode JWT to guarantee email extraction
                var email = googleIdTokenCredential.id
                try {
                    val split = googleIdTokenCredential.idToken.split(".")
                    if (split.size == 3) {
                        val payload = String(android.util.Base64.decode(split[1], android.util.Base64.URL_SAFE))
                        val jsonObject = org.json.JSONObject(payload)
                        email = jsonObject.optString("email", email)
                    }
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "Failed to decode JWT email", e)
                }

                val displayName = googleIdTokenCredential.displayName
                
                authPreferences.setSignedIn(true, email)
                _authState.value = AuthState.Authenticated(email, displayName)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to decode JWT or complete sign in", e)
                _authState.value = AuthState.Error("Terjadi kesalahan saat memproses login.")
                authPreferences.setSignedIn(false, null)
            }
        } else {
            _authState.value = AuthState.Error("Tipe credential tidak didukung")
        }
    }

    fun signOut() {
        authPreferences.setSignedIn(false, null)
        authPreferences.setHasSeenOnboarding(false) // Reset onboarding
        cloudPreferences.clear()
        _authState.value = AuthState.Idle
    }

    fun retryInitialization() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val initResult = spreadsheetManager.initializeSpreadsheet()
            if (initResult.isSuccess) {
                try {
                    eventRepository.syncEventsFromCloud()
                    historyLogRepository.syncLogsFromCloud()
                    val email = authPreferences.userEmail ?: "User"
                    _authState.value = AuthState.Authenticated(email, null)
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "Sync failed on retry", e)
                    _authState.value = AuthState.Error("Gagal sinkronisasi data: ${e.message}")
                    authPreferences.setSignedIn(false, null)
                }
            } else {
                val ex = initResult.exceptionOrNull()
                if (ex is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                    _authState.value = AuthState.NeedsConsent(ex.intent)
                } else {
                    Log.e("AuthViewModel", "Retry init failed", ex)
                    _authState.value = AuthState.Error("Gagal menyiapkan database cloud: ${ex?.message}")
                    authPreferences.setSignedIn(false, null)
                }
            }
        }
    }

    fun setAuthError(message: String) {
        _authState.value = AuthState.Error(message)
        authPreferences.setSignedIn(false, null)
    }
}
