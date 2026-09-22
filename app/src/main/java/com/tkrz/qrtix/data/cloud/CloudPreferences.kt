package com.tkrz.qrtix.data.cloud

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CloudPrefs", Context.MODE_PRIVATE)

    var folderId: String?
        get() = prefs.getString("FOLDER_ID", null)
        set(value) = prefs.edit().putString("FOLDER_ID", value).apply()

    var mediaFolderId: String?
        get() = prefs.getString("MEDIA_FOLDER_ID", null)
        set(value) = prefs.edit().putString("MEDIA_FOLDER_ID", value).apply()

    var spreadsheetId: String?
        get() = prefs.getString("SPREADSHEET_ID", null)
        set(value) = prefs.edit().putString("SPREADSHEET_ID", value).apply()

    var systemFolderId: String?
        get() = prefs.getString("SYSTEM_FOLDER_ID", null)
        set(value) = prefs.edit().putString("SYSTEM_FOLDER_ID", value).apply()

    var profilesFolderId: String?
        get() = prefs.getString("PROFILES_FOLDER_ID", null)
        set(value) = prefs.edit().putString("PROFILES_FOLDER_ID", value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
