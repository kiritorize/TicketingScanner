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

    // ─── Active Email (set on login, cleared on logout) ───

    var activeEmail: String?
        get() = prefs.getString("ACTIVE_EMAIL", null)
        set(value) = prefs.edit().putString("ACTIVE_EMAIL", value).apply()

    // ─── Per-Email Keyed Accessors ───
    // These read/write using "{KEY}_{email}" pattern.
    // When activeEmail is null, they return null (safe default).

    /** QRTix/ root folder ID (shared across accounts in same Drive, but stored per-email) */
    var folderId: String?
        get() = getKeyed("FOLDER_ID")
        set(value) = setKeyed("FOLDER_ID", value)

    /** QRTix/{email}/ account root folder ID */
    var accountRootFolderId: String?
        get() = getKeyed("ACCOUNT_ROOT_FOLDER_ID")
        set(value) = setKeyed("ACCOUNT_ROOT_FOLDER_ID", value)

    /** QRTix/{email}/System/ folder ID */
    var systemFolderId: String?
        get() = getKeyed("SYSTEM_FOLDER_ID")
        set(value) = setKeyed("SYSTEM_FOLDER_ID", value)

    /** QRTix/{email}/Profiles/ folder ID */
    var profilesFolderId: String?
        get() = getKeyed("PROFILES_FOLDER_ID")
        set(value) = setKeyed("PROFILES_FOLDER_ID", value)

    /** QRTix_Data spreadsheet ID */
    var spreadsheetId: String?
        get() = getKeyed("SPREADSHEET_ID")
        set(value) = setKeyed("SPREADSHEET_ID", value)

    /** Legacy media folder ID */
    var mediaFolderId: String?
        get() = getKeyed("MEDIA_FOLDER_ID")
        set(value) = setKeyed("MEDIA_FOLDER_ID", value)

    // ─── Direct per-email helpers ───

    fun getSpreadsheetId(email: String): String? {
        return prefs.getString("SPREADSHEET_ID_$email", null)
    }

    fun setSpreadsheetId(email: String, id: String?) {
        if (id == null) {
            prefs.edit().remove("SPREADSHEET_ID_$email").apply()
        } else {
            prefs.edit().putString("SPREADSHEET_ID_$email", id).apply()
        }
    }

    // ─── Internal Helpers ───

    private fun getKeyed(key: String): String? {
        val email = activeEmail ?: return null
        return prefs.getString("${key}_$email", null)
    }

    private fun setKeyed(key: String, value: String?) {
        val email = activeEmail ?: return
        if (value == null) {
            prefs.edit().remove("${key}_$email").apply()
        } else {
            prefs.edit().putString("${key}_$email", value).apply()
        }
    }

    // ─── Migration Helpers (detect pre-upgrade un-keyed values) ───

    /** Read old un-keyed FOLDER_ID (pre-migration) */
    fun getOldUnkeyedFolderId(): String? = prefs.getString("FOLDER_ID", null)

    /** Read old un-keyed SYSTEM_FOLDER_ID (pre-migration) */
    fun getOldUnkeyedSystemFolderId(): String? = prefs.getString("SYSTEM_FOLDER_ID", null)

    /** Read old un-keyed PROFILES_FOLDER_ID (pre-migration) */
    fun getOldUnkeyedProfilesFolderId(): String? = prefs.getString("PROFILES_FOLDER_ID", null)

    /** Read old un-keyed SPREADSHEET_ID (pre-migration) */
    fun getOldUnkeyedSpreadsheetId(): String? = prefs.getString("SPREADSHEET_ID", null)

    /** Read old un-keyed MEDIA_FOLDER_ID (pre-migration) */
    fun getOldUnkeyedMediaFolderId(): String? = prefs.getString("MEDIA_FOLDER_ID", null)

    /** Check if migration has been completed for this email */
    fun hasMigratedForEmail(email: String): Boolean =
        prefs.getBoolean("MIGRATED_$email", false)

    /** Mark migration as completed for this email */
    fun setMigratedForEmail(email: String) =
        prefs.edit().putBoolean("MIGRATED_$email", true).apply()

    /** Remove old un-keyed values after successful migration */
    fun clearOldUnkeyedValues() {
        prefs.edit()
            .remove("FOLDER_ID")
            .remove("SYSTEM_FOLDER_ID")
            .remove("PROFILES_FOLDER_ID")
            .remove("SPREADSHEET_ID")
            .remove("MEDIA_FOLDER_ID")
            .apply()
    }

    // ─── Session Management ───

    /**
     * Called on sign-out. Only resets activeEmail.
     * Per-email keyed data (folder IDs, spreadsheet IDs) is preserved
     * so re-login to the same account is instant.
     */
    fun clearSession() {
        activeEmail = null
    }

    /**
     * Nuclear option — wipes ALL SharedPreferences data.
     * Only for manual resets or debugging.
     */
    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}
