package com.whispermmepub.wowcolornote.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.whispermmepub.wowcolornote.model.Note

class NoteDb(context: Context) : SQLiteOpenHelper(context, "wow_note.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE notes(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                color INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_notes_updated ON notes(pinned DESC, updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun list(query: String = ""): List<Note> {
        val q = query.trim()
        val selection = if (q.isEmpty()) null else "title LIKE ? OR body LIKE ?"
        val args = if (q.isEmpty()) null else arrayOf("%$q%", "%$q%")
        readableDatabase.query("notes", null, selection, args, null, null, "pinned DESC, updated_at DESC").use { c ->
            val out = ArrayList<Note>(c.count)
            while (c.moveToNext()) out += Note(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                title = c.getString(c.getColumnIndexOrThrow("title")),
                body = c.getString(c.getColumnIndexOrThrow("body")),
                color = c.getInt(c.getColumnIndexOrThrow("color")),
                pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) == 1,
                createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
            )
            return out
        }
    }

    fun get(id: Long): Note? {
        readableDatabase.query("notes", null, "id=?", arrayOf(id.toString()), null, null, null, "1").use { c ->
            if (!c.moveToFirst()) return null
            return Note(
                id = c.getLong(c.getColumnIndexOrThrow("id")),
                title = c.getString(c.getColumnIndexOrThrow("title")),
                body = c.getString(c.getColumnIndexOrThrow("body")),
                color = c.getInt(c.getColumnIndexOrThrow("color")),
                pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) == 1,
                createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
            )
        }
    }

    fun save(note: Note): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", note.title)
            put("body", note.body)
            put("color", note.color)
            put("pinned", if (note.pinned) 1 else 0)
            put("created_at", if (note.id == 0L) now else note.createdAt)
            put("updated_at", now)
        }
        return if (note.id == 0L) writableDatabase.insertOrThrow("notes", null, values)
        else { writableDatabase.update("notes", values, "id=?", arrayOf(note.id.toString())); note.id }
    }

    fun delete(id: Long) { writableDatabase.delete("notes", "id=?", arrayOf(id.toString())) }
}
