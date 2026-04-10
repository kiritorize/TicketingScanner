package com.tkrz.qrtix.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Ticket::class, Event::class, HistoryLog::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ticketDao(): TicketDao
    abstract fun eventDao(): EventDao
    abstract fun historyLogDao(): HistoryLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tickets ADD COLUMN isModified INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO events (id, name, createdAt) VALUES (1, 'Event Default', ${System.currentTimeMillis()})")
                database.execSQL("CREATE TABLE IF NOT EXISTS `tickets_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `qrContent` TEXT NOT NULL, `ticketType` TEXT NOT NULL, `isScanned` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `scannedAt` INTEGER, `isModified` INTEGER NOT NULL, `eventId` INTEGER NOT NULL DEFAULT 1)")
                database.execSQL("INSERT INTO tickets_new (id, qrContent, ticketType, isScanned, createdAt, scannedAt, isModified) SELECT id, qrContent, ticketType, isScanned, createdAt, scannedAt, isModified FROM tickets")
                database.execSQL("DROP TABLE tickets")
                database.execSQL("ALTER TABLE tickets_new RENAME TO tickets")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tickets_qrContent_eventId` ON `tickets` (`qrContent`, `eventId`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val currentTime = System.currentTimeMillis()
                database.execSQL("ALTER TABLE events ADD COLUMN lastAccessedAt INTEGER NOT NULL DEFAULT $currentTime")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `history_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE history_logs ADD COLUMN details TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE history_logs ADD COLUMN isUndone INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ticketing_database"
                )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
