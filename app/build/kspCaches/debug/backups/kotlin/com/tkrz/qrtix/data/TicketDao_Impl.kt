package com.tkrz.qrtix.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TicketDao_Impl(
  __db: RoomDatabase,
) : TicketDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfTicket: EntityInsertAdapter<Ticket>
  init {
    this.__db = __db
    this.__insertAdapterOfTicket = object : EntityInsertAdapter<Ticket>() {
      protected override fun createQuery(): String =
          "INSERT OR IGNORE INTO `tickets` (`id`,`qrContent`,`ticketType`,`isScanned`,`createdAt`,`scannedAt`,`isModified`,`eventId`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Ticket) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.qrContent)
        statement.bindText(3, entity.ticketType)
        val _tmp: Int = if (entity.isScanned) 1 else 0
        statement.bindLong(4, _tmp.toLong())
        statement.bindLong(5, entity.createdAt)
        val _tmpScannedAt: Long? = entity.scannedAt
        if (_tmpScannedAt == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpScannedAt)
        }
        val _tmp_1: Int = if (entity.isModified) 1 else 0
        statement.bindLong(7, _tmp_1.toLong())
        statement.bindLong(8, entity.eventId)
      }
    }
  }

  public override suspend fun insertTickets(tickets: List<Ticket>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfTicket.insert(_connection, tickets)
  }

  public override suspend fun getAllTickets(eventId: Long): List<Ticket> {
    val _sql: String = "SELECT * FROM tickets WHERE eventId = ? ORDER BY id ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, eventId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfQrContent: Int = getColumnIndexOrThrow(_stmt, "qrContent")
        val _columnIndexOfTicketType: Int = getColumnIndexOrThrow(_stmt, "ticketType")
        val _columnIndexOfIsScanned: Int = getColumnIndexOrThrow(_stmt, "isScanned")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfScannedAt: Int = getColumnIndexOrThrow(_stmt, "scannedAt")
        val _columnIndexOfIsModified: Int = getColumnIndexOrThrow(_stmt, "isModified")
        val _columnIndexOfEventId: Int = getColumnIndexOrThrow(_stmt, "eventId")
        val _result: MutableList<Ticket> = mutableListOf()
        while (_stmt.step()) {
          val _item: Ticket
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpQrContent: String
          _tmpQrContent = _stmt.getText(_columnIndexOfQrContent)
          val _tmpTicketType: String
          _tmpTicketType = _stmt.getText(_columnIndexOfTicketType)
          val _tmpIsScanned: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsScanned).toInt()
          _tmpIsScanned = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpScannedAt: Long?
          if (_stmt.isNull(_columnIndexOfScannedAt)) {
            _tmpScannedAt = null
          } else {
            _tmpScannedAt = _stmt.getLong(_columnIndexOfScannedAt)
          }
          val _tmpIsModified: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsModified).toInt()
          _tmpIsModified = _tmp_1 != 0
          val _tmpEventId: Long
          _tmpEventId = _stmt.getLong(_columnIndexOfEventId)
          _item =
              Ticket(_tmpId,_tmpQrContent,_tmpTicketType,_tmpIsScanned,_tmpCreatedAt,_tmpScannedAt,_tmpIsModified,_tmpEventId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTicketByQr(qrContent: String, eventId: Long): Ticket? {
    val _sql: String = "SELECT * FROM tickets WHERE qrContent = ? AND eventId = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, qrContent)
        _argIndex = 2
        _stmt.bindLong(_argIndex, eventId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfQrContent: Int = getColumnIndexOrThrow(_stmt, "qrContent")
        val _columnIndexOfTicketType: Int = getColumnIndexOrThrow(_stmt, "ticketType")
        val _columnIndexOfIsScanned: Int = getColumnIndexOrThrow(_stmt, "isScanned")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfScannedAt: Int = getColumnIndexOrThrow(_stmt, "scannedAt")
        val _columnIndexOfIsModified: Int = getColumnIndexOrThrow(_stmt, "isModified")
        val _columnIndexOfEventId: Int = getColumnIndexOrThrow(_stmt, "eventId")
        val _result: Ticket?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpQrContent: String
          _tmpQrContent = _stmt.getText(_columnIndexOfQrContent)
          val _tmpTicketType: String
          _tmpTicketType = _stmt.getText(_columnIndexOfTicketType)
          val _tmpIsScanned: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsScanned).toInt()
          _tmpIsScanned = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpScannedAt: Long?
          if (_stmt.isNull(_columnIndexOfScannedAt)) {
            _tmpScannedAt = null
          } else {
            _tmpScannedAt = _stmt.getLong(_columnIndexOfScannedAt)
          }
          val _tmpIsModified: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsModified).toInt()
          _tmpIsModified = _tmp_1 != 0
          val _tmpEventId: Long
          _tmpEventId = _stmt.getLong(_columnIndexOfEventId)
          _result =
              Ticket(_tmpId,_tmpQrContent,_tmpTicketType,_tmpIsScanned,_tmpCreatedAt,_tmpScannedAt,_tmpIsModified,_tmpEventId)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTicketCount(eventId: Long): Int {
    val _sql: String = "SELECT COUNT(*) FROM tickets WHERE eventId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, eventId)
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getExistingCodes(codes: List<String>, eventId: Long): List<String> {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT qrContent FROM tickets WHERE qrContent IN (")
    val _inputSize: Int = codes.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(") AND eventId = ")
    _stringBuilder.append("?")
    val _sql: String = _stringBuilder.toString()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        for (_item: String in codes) {
          _stmt.bindText(_argIndex, _item)
          _argIndex++
        }
        _argIndex = 1 + _inputSize
        _stmt.bindLong(_argIndex, eventId)
        val _result: MutableList<String> = mutableListOf()
        while (_stmt.step()) {
          val _item_1: String
          _item_1 = _stmt.getText(0)
          _result.add(_item_1)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markAsScanned(
    qrContent: String,
    eventId: Long,
    scannedAt: Long,
  ) {
    val _sql: String =
        "UPDATE tickets SET isScanned = 1, scannedAt = ? WHERE qrContent = ? AND eventId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, scannedAt)
        _argIndex = 2
        _stmt.bindText(_argIndex, qrContent)
        _argIndex = 3
        _stmt.bindLong(_argIndex, eventId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllTickets(eventId: Long) {
    val _sql: String = "DELETE FROM tickets WHERE eventId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, eventId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun resetSequence() {
    val _sql: String = "DELETE FROM sqlite_sequence WHERE name = 'tickets'"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteTickets(ids: List<Int>) {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("DELETE FROM tickets WHERE id IN (")
    val _inputSize: Int = ids.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        for (_item: Int in ids) {
          _stmt.bindLong(_argIndex, _item.toLong())
          _argIndex++
        }
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateTicket(
    id: Int,
    newQr: String,
    newType: String,
    updatedAt: Long,
  ) {
    val _sql: String =
        "UPDATE tickets SET qrContent = ?, ticketType = ?, createdAt = ?, isModified = 1 WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, newQr)
        _argIndex = 2
        _stmt.bindText(_argIndex, newType)
        _argIndex = 3
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 4
        _stmt.bindLong(_argIndex, id.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
