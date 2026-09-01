package com.whispermmepub.wowcolornote.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.whispermmepub.wowcolornote.model.Note

class NoteDb(context: Context) : SQLiteOpenHelper(context, "wow_note.db", null, 2) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE notes(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                color INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0,
                note_type TEXT NOT NULL DEFAULT 'text',
                archived INTEGER NOT NULL DEFAULT 0,
                locked INTEGER NOT NULL DEFAULT 0,
                calendar_date TEXT NOT NULL DEFAULT '',
                reminder_at INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        createIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN note_type TEXT NOT NULL DEFAULT 'text'")
            db.execSQL("ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN calendar_date TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE notes ADD COLUMN reminder_at INTEGER NOT NULL DEFAULT 0")
            createIndexes(db)
        }
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_updated ON notes(pinned DESC, updated_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_calendar ON notes(calendar_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_archive ON notes(archived, updated_at DESC)")
    }

    fun list(query: String = "", sort: String = "modified", archived: Boolean = false): List<Note> {
        val q = query.trim()
        val order = when (sort) {
            "created" -> "pinned DESC, created_at DESC"
            "title" -> "pinned DESC, title COLLATE NOCASE ASC, updated_at DESC"
            else -> "pinned DESC, updated_at DESC"
        }
        val archiveWhere = "archived=${if (archived) 1 else 0}"
        val sql = if (q.isEmpty()) {
            "SELECT id,title,substr(body,1,650) body,color,pinned,note_type,archived,locked,calendar_date,reminder_at,created_at,updated_at FROM notes WHERE $archiveWhere ORDER BY $order"
        } else {
            "SELECT id,title,substr(body,1,650) body,color,pinned,note_type,archived,locked,calendar_date,reminder_at,created_at,updated_at FROM notes WHERE $archiveWhere AND (title LIKE ? OR body LIKE ?) ORDER BY $order"
        }
        val args = if (q.isEmpty()) emptyArray() else arrayOf("%$q%", "%$q%")
        readableDatabase.rawQuery(sql, args).use { c ->
            val out = ArrayList<Note>(c.count)
            while (c.moveToNext()) out += fromCursor(c)
            return out
        }
    }

    fun listForDate(date: String): List<Note> {
        readableDatabase.query("notes", null, "calendar_date=? AND archived=0", arrayOf(date), null, null, "updated_at DESC").use { c ->
            val out = ArrayList<Note>(c.count)
            while (c.moveToNext()) out += fromCursor(c)
            return out
        }
    }

    fun get(id: Long): Note? {
        readableDatabase.query("notes", null, "id=?", arrayOf(id.toString()), null, null, null, "1").use { c ->
            return if (c.moveToFirst()) fromCursor(c) else null
        }
    }

    fun save(note: Note): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", note.title)
            put("body", note.body)
            put("color", note.color)
            put("pinned", if (note.pinned) 1 else 0)
            put("note_type", note.noteType)
            put("archived", if (note.archived) 1 else 0)
            put("locked", if (note.locked) 1 else 0)
            put("calendar_date", note.calendarDate)
            put("reminder_at", note.reminderAt)
            put("created_at", if (note.id == 0L) now else note.createdAt)
            put("updated_at", now)
        }
        return if (note.id == 0L) writableDatabase.insertOrThrow("notes", null, values)
        else {
            writableDatabase.update("notes", values, "id=?", arrayOf(note.id.toString()))
            note.id
        }
    }

    fun delete(id: Long) { writableDatabase.delete("notes", "id=?", arrayOf(id.toString())) }

    private fun fromCursor(c: android.database.Cursor) = Note(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        body = c.getString(c.getColumnIndexOrThrow("body")),
        color = c.getInt(c.getColumnIndexOrThrow("color")),
        pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) == 1,
        noteType = c.getString(c.getColumnIndexOrThrow("note_type")),
        archived = c.getInt(c.getColumnIndexOrThrow("archived")) == 1,
        locked = c.getInt(c.getColumnIndexOrThrow("locked")) == 1,
        calendarDate = c.getString(c.getColumnIndexOrThrow("calendar_date")),
        reminderAt = c.getLong(c.getColumnIndexOrThrow("reminder_at")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
    )
}
