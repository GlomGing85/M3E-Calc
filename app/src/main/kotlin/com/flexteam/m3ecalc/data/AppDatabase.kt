package com.flexteam.m3ecalc.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HistoryEntry(
    val id: Long,
    val expression: String,
    val result: String,
    val timestamp: Long
)

data class ConversionEntry(
    val id: Long,
    val category: String,
    val fromUnit: String,
    val toUnit: String,
    val input: String,
    val output: String,
    val timestamp: Long
) {
    val summary: String get() = "$input $fromUnit = $output $toUnit"
}

/**
 * On-device storage. Calculations, conversions and editable currency rates all
 * live in one SQLite database; nothing leaves the device.
 */
class AppDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "m3ecalc.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE calculations (
                   id INTEGER PRIMARY KEY AUTOINCREMENT,
                   expression TEXT NOT NULL,
                   result TEXT NOT NULL,
                   ts INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE conversions (
                   id INTEGER PRIMARY KEY AUTOINCREMENT,
                   category TEXT NOT NULL,
                   from_unit TEXT NOT NULL,
                   to_unit TEXT NOT NULL,
                   input TEXT NOT NULL,
                   output TEXT NOT NULL,
                   ts INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE rates (
                   code TEXT PRIMARY KEY,
                   rate REAL NOT NULL)"""
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---------------------------------------------------------- calculations

    fun addCalculation(expression: String, result: String, limit: Int = 200): Long {
        val values = ContentValues().apply {
            put("expression", expression)
            put("result", result)
            put("ts", System.currentTimeMillis())
        }
        val id = writableDatabase.insert("calculations", null, values)
        trimCalculations(limit)
        return id
    }

    private fun trimCalculations(limit: Int) {
        writableDatabase.execSQL(
            "DELETE FROM calculations WHERE id NOT IN " +
                "(SELECT id FROM calculations ORDER BY ts DESC LIMIT ?)",
            arrayOf(limit.toString())
        )
    }

    fun calculations(query: String = "", limit: Int = 500): List<HistoryEntry> {
        val selection = if (query.isBlank()) null else "expression LIKE ? OR result LIKE ?"
        val args = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")
        readableDatabase.query(
            "calculations", null, selection, args, null, null, "ts DESC", limit.toString()
        ).use { cursor ->
            val out = mutableListOf<HistoryEntry>()
            while (cursor.moveToNext()) {
                out.add(
                    HistoryEntry(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3)
                    )
                )
            }
            return out
        }
    }

    fun deleteCalculation(id: Long) {
        writableDatabase.delete("calculations", "id = ?", arrayOf(id.toString()))
    }

    fun clearCalculations() {
        writableDatabase.delete("calculations", null, null)
    }

    /** Restores a calculation that was just deleted (used by the Undo snack bar). */
    fun restoreCalculation(entry: HistoryEntry) {
        val values = ContentValues().apply {
            put("expression", entry.expression)
            put("result", entry.result)
            put("ts", entry.timestamp)
        }
        writableDatabase.insert("calculations", null, values)
    }

    fun calculationCount(): Int = count("calculations")

    // ----------------------------------------------------------- conversions

    fun addConversion(entry: ConversionEntry, limit: Int = 100): Long {
        val values = ContentValues().apply {
            put("category", entry.category)
            put("from_unit", entry.fromUnit)
            put("to_unit", entry.toUnit)
            put("input", entry.input)
            put("output", entry.output)
            put("ts", System.currentTimeMillis())
        }
        val id = writableDatabase.insert("conversions", null, values)
        writableDatabase.execSQL(
            "DELETE FROM conversions WHERE id NOT IN " +
                "(SELECT id FROM conversions ORDER BY ts DESC LIMIT ?)",
            arrayOf(limit.toString())
        )
        return id
    }

    fun conversions(limit: Int = 100, category: String? = null): List<ConversionEntry> {
        val selection = if (category == null) null else "category = ?"
        val args = if (category == null) null else arrayOf(category)
        readableDatabase.query(
            "conversions", null, selection, args, null, null, "ts DESC", limit.toString()
        ).use { cursor ->
            val out = mutableListOf<ConversionEntry>()
            while (cursor.moveToNext()) {
                out.add(
                    ConversionEntry(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getLong(6)
                    )
                )
            }
            return out
        }
    }

    fun clearConversions() {
        writableDatabase.delete("conversions", null, null)
    }

    // ---------------------------------------------------------- currency rates

    /** Seeds the reference table once; existing rates are never overwritten. */
    fun ensureRates(defaults: List<Rate>) {
        val existing = rates()
        if (existing.isNotEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            defaults.forEach { rate ->
                db.insert(
                    "rates", null,
                    ContentValues().apply {
                        put("code", rate.code)
                        put("rate", rate.rate)
                    }
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun rates(): List<Rate> {
        readableDatabase.rawQuery("SELECT code, rate FROM rates ORDER BY code", null).use { c ->
            val out = mutableListOf<Rate>()
            while (c.moveToNext()) out.add(Rate(c.getString(0), c.getDouble(1)))
            return out
        }
    }

    fun upsertRate(code: String, rate: Double) {
        val values = ContentValues().apply {
            put("code", code.uppercase())
            put("rate", rate)
        }
        writableDatabase.insertWithOnConflict("rates", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteRate(code: String) {
        writableDatabase.delete("rates", "code = ?", arrayOf(code.uppercase()))
    }

    // ------------------------------------------------------------------ meta

    fun meta(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun setMeta(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "meta", null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun count(table: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
}
