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

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(6,
        "f361e913c1aa42dab425f61a0422da30", "b55ea60e21e5ea1960df67b607a6d59c") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tickets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `qrContent` TEXT NOT NULL, `ticketType` TEXT NOT NULL, `isScanned` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `scannedAt` INTEGER, `isModified` INTEGER NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tickets_qrContent` ON `tickets` (`qrContent`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f361e913c1aa42dab425f61a0422da30')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `tickets`")
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
        val _foreignKeysTickets: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTickets: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesTickets.add(TableInfo.Index("index_tickets_qrContent", true, listOf("qrContent"),
            listOf("ASC")))
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
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "tickets")
  }

  public override fun clearAllTables() {
    super.performClear(false, "tickets")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(TicketDao::class, TicketDao_Impl.getRequiredConverters())
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
}
