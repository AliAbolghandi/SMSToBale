package com.example.smstobale

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LogDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "sms_log.db"
        private const val DB_VERSION = 1
        const val TABLE_NAME = "logs"

        const val COL_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_SENDER = "sender"
        const val COL_MESSAGE = "message"
        const val COL_STATUS = "status"
        const val COL_DETAIL = "detail"

        const val STATUS_SUCCESS = "موفق"
        const val STATUS_FAILED = "ناموفق"
        const val STATUS_ERROR = "خطا"
        const val STATUS_IGNORED = "نادیده گرفته شد"

        private const val MAX_ROWS = 300
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_SENDER TEXT,
                $COL_MESSAGE TEXT,
                $COL_STATUS TEXT,
                $COL_DETAIL TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    @Synchronized
    fun insertLog(sender: String, message: String, status: String, detail: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, System.currentTimeMillis())
            put(COL_SENDER, sender)
            put(COL_MESSAGE, message)
            put(COL_STATUS, status)
            put(COL_DETAIL, detail)
        }
        db.insert(TABLE_NAME, null, values)
        trimOldRows(db)
    }

    private fun trimOldRows(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM $TABLE_NAME WHERE $COL_ID NOT IN (
                SELECT $COL_ID FROM $TABLE_NAME ORDER BY $COL_TIMESTAMP DESC LIMIT $MAX_ROWS
            )
            """.trimIndent()
        )
    }

    @Synchronized
    fun getAllLogs(): List<LogEntry> {
        val list = mutableListOf<LogEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_NAME, null, null, null, null, null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LogEntry(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                        sender = it.getString(it.getColumnIndexOrThrow(COL_SENDER)) ?: "",
                        message = it.getString(it.getColumnIndexOrThrow(COL_MESSAGE)) ?: "",
                        status = it.getString(it.getColumnIndexOrThrow(COL_STATUS)) ?: "",
                        detail = it.getString(it.getColumnIndexOrThrow(COL_DETAIL)) ?: ""
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun clearLogs() {
        writableDatabase.delete(TABLE_NAME, null, null)
    }
}
