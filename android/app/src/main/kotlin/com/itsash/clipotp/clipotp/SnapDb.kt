package com.itsash.clipotp.clipotp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Shared SQLite history store.
 *
 * IMPORTANT: this file path MUST match the path sqflite opens from Dart
 * (see lib/services/history_repository.dart, which uses
 * `context.getDatabasePath("snapotp_history.db")`-equivalent via
 * path_provider's application support / getDatabasesPath so both the Kotlin
 * service (which can insert rows while the Flutter engine is not running,
 * e.g. from SnapNotificationService/SnapSmsReceiver) and the Flutter/sqflite
 * side (used to read/search history in the UI) read and write the exact
 * same on-disk database file. Do not rename without updating both sides.
 */
object SnapDbContract {
    const val DB_NAME = "snapotp_history.db"
    const val DB_VERSION = 1
    const val TABLE_HISTORY = "otp_history"

    const val COL_ID = "id"
    const val COL_SENDER = "sender"
    const val COL_CODE = "code"
    const val COL_SOURCE = "source" // "sms" | "notification"
    const val COL_APP = "app_label"
    const val COL_TIMESTAMP = "timestamp" // epoch millis

    const val RETENTION_MILLIS = 24L * 60L * 60L * 1000L
}

class SnapDbHelper(context: Context) :
    SQLiteOpenHelper(
        context,
        context.getDatabasePath(SnapDbContract.DB_NAME).absolutePath,
        null,
        SnapDbContract.DB_VERSION,
    ) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${SnapDbContract.TABLE_HISTORY} (
                ${SnapDbContract.COL_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${SnapDbContract.COL_SENDER} TEXT NOT NULL,
                ${SnapDbContract.COL_CODE} TEXT NOT NULL,
                ${SnapDbContract.COL_SOURCE} TEXT NOT NULL,
                ${SnapDbContract.COL_APP} TEXT,
                ${SnapDbContract.COL_TIMESTAMP} INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_otp_history_ts ON ${SnapDbContract.TABLE_HISTORY}(${SnapDbContract.COL_TIMESTAMP})",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No schema migrations yet; version bumps will branch here.
    }

    fun insert(sender: String, code: String, source: String, appLabel: String?, timestamp: Long) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put(SnapDbContract.COL_SENDER, sender)
                put(SnapDbContract.COL_CODE, code)
                put(SnapDbContract.COL_SOURCE, source)
                put(SnapDbContract.COL_APP, appLabel)
                put(SnapDbContract.COL_TIMESTAMP, timestamp)
            }
            db.insert(SnapDbContract.TABLE_HISTORY, null, values)
        }
        pruneOld()
    }

    /** Deletes rows older than the 24h retention window. */
    fun pruneOld() {
        val cutoff = System.currentTimeMillis() - SnapDbContract.RETENTION_MILLIS
        writableDatabase.use { db ->
            db.delete(SnapDbContract.TABLE_HISTORY, "${SnapDbContract.COL_TIMESTAMP} < ?", arrayOf(cutoff.toString()))
        }
    }
}

private inline fun SQLiteDatabase.use(block: (SQLiteDatabase) -> Unit) {
    try {
        block(this)
    } finally {
        // SQLiteOpenHelper manages the underlying connection lifecycle; we
        // intentionally do not close() here since writableDatabase is cached
        // and reused by the helper across calls.
    }
}
