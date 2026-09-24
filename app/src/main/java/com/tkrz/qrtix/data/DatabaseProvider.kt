package com.tkrz.qrtix.data

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPreferences: AuthPreferences
) {
    private var currentEmail: String? = null
    private var currentDb: AppDatabase? = null

    @Synchronized
    fun getDatabase(): AppDatabase {
        val email = authPreferences.userEmail ?: "default"
        if (currentDb == null || currentEmail != email) {
            currentDb?.close()

            migrateLegacyDatabaseIfNeeded(email)

            val dbName = AppDatabase.buildDatabaseName(email)
            currentDb = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName
            )
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()

            currentEmail = email
        }
        return currentDb!!
    }

    private fun migrateLegacyDatabaseIfNeeded(email: String) {
        try {
            val oldDbFile = context.getDatabasePath("ticketing_database")
            val newDbName = AppDatabase.buildDatabaseName(email)
            val newDbFile = context.getDatabasePath(newDbName)

            if (oldDbFile.exists() && !newDbFile.exists()) {
                val dbDir = oldDbFile.parentFile ?: return
                oldDbFile.renameTo(newDbFile)

                val oldWal = File(dbDir, "ticketing_database-wal")
                val newWal = File(dbDir, "$newDbName-wal")
                if (oldWal.exists()) oldWal.renameTo(newWal)

                val oldShm = File(dbDir, "ticketing_database-shm")
                val newShm = File(dbDir, "$newDbName-shm")
                if (oldShm.exists()) oldShm.renameTo(newShm)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val ticketDao: TicketDao
        get() = getDatabase().ticketDao()

    val eventDao: EventDao
        get() = getDatabase().eventDao()

    val historyLogDao: HistoryLogDao
        get() = getDatabase().historyLogDao()

    val categoryDao: CategoryDao
        get() = getDatabase().categoryDao()

    @Synchronized
    fun closeDatabase() {
        try {
            currentDb?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentDb = null
            currentEmail = null
        }
    }
}
