package com.subbrowser.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HistoryItem(val id: Long, val title: String, val url: String, val timestamp: Long)
data class BookmarkItem(val id: Long, val title: String, val url: String)

class BrowserDatabase(context: Context) : SQLiteOpenHelper(context, "sub_browser.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                url TEXT,
                timestamp INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                url TEXT UNIQUE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_history_url_timestamp " +
                    "ON history(url, timestamp)"
            )
        }
    }

    fun addHistory(title: String, url: String) {
        if (url == "about:blank" || url.isBlank()) return
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.rawQuery(
            "SELECT timestamp FROM history WHERE url = ? ORDER BY timestamp DESC LIMIT 1",
            arrayOf(url)
        ).use { cursor ->
            if (cursor.moveToFirst() && now - cursor.getLong(0) < 2000L) return
        }
        val values = ContentValues().apply {
            put("title", title.ifBlank { url })
            put("url", url)
            put("timestamp", now)
        }
        db.insert("history", null, values)
    }

    fun getHistory(): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, title, url, timestamp FROM history ORDER BY timestamp DESC LIMIT 50", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    HistoryItem(
                        id = it.getLong(0),
                        title = it.getString(1),
                        url = it.getString(2),
                        timestamp = it.getLong(3)
                    )
                )
            }
        }
        return list
    }

    fun clearHistory() {
        writableDatabase.execSQL("DELETE FROM history")
    }

    fun addBookmark(title: String, url: String) {
        if (url == "about:blank" || url.isBlank()) return
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", title.ifBlank { url })
            put("url", url)
        }
        db.insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getBookmarks(): List<BookmarkItem> {
        val list = mutableListOf<BookmarkItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, title, url FROM bookmarks ORDER BY id DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    BookmarkItem(
                        id = it.getLong(0),
                        title = it.getString(1),
                        url = it.getString(2)
                    )
                )
            }
        }
        return list
    }

    fun deleteBookmark(id: Long) {
        writableDatabase.delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }
}
