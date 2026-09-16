package com.tkrz.qrtix.data.cloud

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.sheets.v4.SheetsScopes
import com.tkrz.qrtix.data.AuthPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCredentialManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPreferences: AuthPreferences
) {
    fun getCredential(): GoogleAccountCredential {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE_FILE, GmailScopes.GMAIL_SEND)
        )
        val email = authPreferences.userEmail
        if (email.isNullOrEmpty()) {
            throw IllegalStateException("User email is missing or empty! Cannot authenticate.")
        }
        credential.setSelectedAccount(android.accounts.Account(email, "com.google"))
        return credential
    }
}
