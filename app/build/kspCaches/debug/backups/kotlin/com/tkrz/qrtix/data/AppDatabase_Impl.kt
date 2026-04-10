package com.tkrz.qrtix.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _ticketDao: Lazy<TicketDao> = lazy {
    TicketDao_Impl(this)
  }

  private val _eventDao: Lazy<EventDao> = lazy {
    EventDao_Impl(this)
  }

  private val _historyLogDao: Lazy<HistoryLogDao> = lazy {
    HistoryLogDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(10,
        "c9c7da8e935f833854386b14a226a0af", "3bd1f6c4fe49ecbab7a3b6049bd38457") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tickets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `qrContent` TEXT NOT NULL, `ticketType` TEXT NOT NULL, `isScanned` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `scannedAt` INTEGER, `isModified` INTEGER NOT NULL, `eventId` INTEGER NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tickets_qrContent_eventId` ON `tickets` (`qrContent`, `eventId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastAccessedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `history_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isUndone` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c9c7da8e935f833854386b14a226a0af')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `tickets`")
        connection.execSQL("DROP TABLE IF EXISTS `events`")
        connection.execSQL("DROP TABLE IF EXISTS `history_logs`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsTickets: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTickets.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTickets.put("qrContent", TableInfo.Column("qrContent", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTickets.put("ticketType", TableInfo.Column("ticketType", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTickets.put("isScanned", TableInfo.Column("isScanned", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTickets.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTickets.put("scannedAt", TableInfo.Column("scannedAt", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTickets.put("isModified", TableInfo.Column("isModified", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTickets.put("eventId", TableInfo.Column("eventId", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTickets: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTickets: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesTickets.add(TableInfo.Index("index_tickets_qrContent_eventId", true,
            listOf("qrContent", "eventId"), listOf("ASC", "ASC")))
        val _infoTickets: TableInfo = TableInfo("tickets", _columnsTickets, _foreignKeysTickets,
            _indicesTickets)
        val _existingTickets: TableInfo = read(connection, "tickets")
        if (!_infoTickets.equals(_existingTickets)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |tickets(com.tkrz.qrtix.data.Ticket).
              | Expected:
              |""".trimMargin() + _infoTickets + """
              |
              | Found:
              |""".trimMargin() + _existingTickets)
        }
        val _columnsEvents: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsEvents.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsEvents.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsEvents.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsEvents.put("lastAccessedAt", TableInfo.Column("lastAccessedAt", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysEvents: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesEvents: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoEvents: TableInfo = TableInfo("events", _columnsEvents, _foreignKeysEvents,
            _indicesEvents)
        val _existingEvents: TableInfo = read(connection, "events")
        if (!_infoEvents.equals(_existingEvents)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |events(com.tkrz.qrtix.data.Event).
              | Expected:
              |""".trimMargin() + _infoEvents + """
              |
              | Found:
              |""".trimMargin() + _existingEvents)
        }
        val _columnsHistoryLogs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsHistoryLogs.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistoryLogs.put("eventId", TableInfo.Column("eventId", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistoryLogs.put("action", TableInfo.Column("action", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistoryLogs.put("description", TableInfo.Column("description", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHistoryLogs.put("details", TableInfo.Column("details", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistoryLogs.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistoryLogs.put("isUndone", TableInfo.Column("isUndone", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysHistoryLogs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesHistoryLogs: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoHistoryLogs: TableInfo = TableInfo("history_logs", _columnsHistoryLogs,
            _foreignKeysHistoryLogs, _indicesHistoryLogs)
        val _existingHistoryLogs: TableInfo = read(connection, "history_logs")
        if (!_infoHistoryLogs.equals(_existingHistoryLogs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |history_logs(com.tkrz.qrtix.data.HistoryLog).
              | Expected:
              |""".trimMargin() + _infoHistoryLogs + """
              |
              | Found:
              |""".trimMargin() + _existingHistoryLogs)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "tickets", "events",
        "history_logs")
  }

  public override fun clearAllTables() {
    super.performClear(false, "tickets", "events", "history_logs")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(TicketDao::class, TicketDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(EventDao::class, EventDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(HistoryLogDao::class, HistoryLogDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun ticketDao(): TicketDao = _ticketDao.value

  public override fun eventDao(): EventDao = _eventDao.value

  public override fun historyLogDao(): HistoryLogDao = _historyLogDao.value
}
