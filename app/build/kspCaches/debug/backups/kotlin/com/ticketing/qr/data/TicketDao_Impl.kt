package com.ticketing.qr.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

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
          "INSERT OR IGNORE INTO `tickets` (`id`,`qrContent`,`ticketType`,`isScanned`) VALUES (nullif(?, 0),?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Ticket) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindText(2, entity.qrContent)
        statement.bindText(3, entity.ticketType)
        val _tmp: Int = if (entity.isScanned) 1 else 0
        statement.bindLong(4, _tmp.toLong())
      }
    }
  }

  public override suspend fun insertTickets(tickets: List<Ticket>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfTicket.insert(_connection, tickets)
  }

  public override suspend fun getAllTickets(): List<Ticket> {
    val _sql: String = "SELECT * FROM tickets ORDER BY id ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfQrContent: Int = getColumnIndexOrThrow(_stmt, "qrContent")
        val _columnIndexOfTicketType: Int = getColumnIndexOrThrow(_stmt, "ticketType")
        val _columnIndexOfIsScanned: Int = getColumnIndexOrThrow(_stmt, "isScanned")
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
          _item = Ticket(_tmpId,_tmpQrContent,_tmpTicketType,_tmpIsScanned)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTicketByQr(qrContent: String): Ticket? {
    val _sql: String = "SELECT * FROM tickets WHERE qrContent = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, qrContent)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfQrContent: Int = getColumnIndexOrThrow(_stmt, "qrContent")
        val _columnIndexOfTicketType: Int = getColumnIndexOrThrow(_stmt, "ticketType")
        val _columnIndexOfIsScanned: Int = getColumnIndexOrThrow(_stmt, "isScanned")
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
          _result = Ticket(_tmpId,_tmpQrContent,_tmpTicketType,_tmpIsScanned)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTicketCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM tickets"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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

  public override suspend fun markAsScanned(qrContent: String) {
    val _sql: String = "UPDATE tickets SET isScanned = 1 WHERE qrContent = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, qrContent)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllTickets() {
    val _sql: String = "DELETE FROM tickets"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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
