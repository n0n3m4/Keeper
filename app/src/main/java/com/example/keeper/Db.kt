package com.example.keeper

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/* ----- data model ----- */

data class Item(var id: Long = 0, var text: String = "", var checked: Boolean = false)

data class Label(val id: Long, val name: String)

data class Note(
    var id: Long = 0,
    var title: String = "",
    var body: String = "",
    var checklist: Boolean = false,
    var color: Int = 0,                 // index into MainActivity.NoteColors
    var pinned: Boolean = false,
    var archived: Boolean = false,
    var created: Long = 0,
    var modified: Long = 0,
    var reminderAt: Long = 0,           // epoch ms; 0 = no reminder
    var reminderRepeat: String = "NONE",// NONE | DAILY | WEEKLY | MONTHLY | YEARLY
    var reminderFired: Boolean = false, // a one-time reminder that already went off
    var items: MutableList<Item> = mutableListOf(),
    var labelIds: MutableSet<Long> = mutableSetOf(),
)

/*
 * Db — the single source of truth. A god object wrapping raw SQLite: no DAOs,
 * no entities, no Room. Every read returns plain Note/Item/Label objects and
 * every write takes them. Because the database is one ordinary file, backup
 * and restore are just a byte copy.
 */
object Db {
    private const val NAME = "keeper.db"
    private lateinit var ctx: Context
    private lateinit var helper: SQLiteOpenHelper

    fun init(context: Context) {
        if (::helper.isInitialized) return
        ctx = context.applicationContext
        helper = object : SQLiteOpenHelper(ctx, NAME, null, 2) {
            override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, " +
                        "checklist INTEGER, color INTEGER, pinned INTEGER, archived INTEGER, " +
                        "created INTEGER, modified INTEGER, reminder_at INTEGER, reminder_repeat TEXT, " +
                        "reminder_fired INTEGER DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER, " +
                        "text TEXT, checked INTEGER, pos INTEGER, " +
                        "FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE TABLE labels(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)")
                db.execSQL(
                    "CREATE TABLE note_labels(note_id INTEGER, label_id INTEGER, " +
                        "PRIMARY KEY(note_id, label_id), " +
                        "FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(label_id) REFERENCES labels(id) ON DELETE CASCADE)"
                )
            }
            override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
                if (oldV < 2) db.execSQL("ALTER TABLE notes ADD COLUMN reminder_fired INTEGER DEFAULT 0")
            }
        }
        helper.setWriteAheadLoggingEnabled(false) // keep one consistent file for backup
    }

    private val db get() = helper.writableDatabase

    /* ----- notes ----- */

    /** Loads notes for one drawer view. archived/onlyReminders/labelId pick the
     *  view; search filters by title, body or checklist item text. */
    fun notes(archived: Boolean, onlyReminders: Boolean, labelId: Long?, search: String): List<Note> {
        val where = StringBuilder("archived=?")
        val args = mutableListOf(if (archived) "1" else "0")
        if (onlyReminders) where.append(" AND reminder_at>0")
        if (labelId != null) {
            where.append(" AND id IN (SELECT note_id FROM note_labels WHERE label_id=?)")
            args.add(labelId.toString())
        }
        if (search.isNotBlank()) {
            where.append(" AND (title LIKE ? OR body LIKE ? OR id IN (SELECT note_id FROM items WHERE text LIKE ?))")
            val s = "%${search.trim()}%"
            args.add(s); args.add(s); args.add(s)
        }
        val out = mutableListOf<Note>()
        db.rawQuery(
            "SELECT * FROM notes WHERE $where ORDER BY pinned DESC, modified DESC",
            args.toTypedArray()
        ).use { while (it.moveToNext()) out.add(readNote(it)) }
        out.forEach { loadChildren(it) }
        return out
    }

    fun note(id: Long): Note? = db.rawQuery("SELECT * FROM notes WHERE id=?", arrayOf("$id")).use {
        if (it.moveToNext()) readNote(it).also(::loadChildren) else null
    }

    /** Notes whose reminder is still pending — used to (re)schedule alarms.
     *  One-time reminders that already fired are excluded so they never re-fire. */
    fun withReminders(): List<Note> {
        val out = mutableListOf<Note>()
        db.rawQuery(
            "SELECT * FROM notes WHERE reminder_at>0 AND (reminder_fired=0 OR reminder_repeat<>'NONE')",
            null,
        ).use { while (it.moveToNext()) out.add(readNote(it)) }
        out.forEach { loadChildren(it) }
        return out
    }

    /** Inserts or updates a note together with all its items and label links. */
    fun save(n: Note): Note {
        val now = System.currentTimeMillis()
        if (n.created == 0L) n.created = now
        n.modified = now
        val v = ContentValues().apply {
            put("title", n.title); put("body", n.body)
            put("checklist", if (n.checklist) 1 else 0)
            put("color", n.color)
            put("pinned", if (n.pinned) 1 else 0)
            put("archived", if (n.archived) 1 else 0)
            put("created", n.created); put("modified", n.modified)
            put("reminder_at", n.reminderAt); put("reminder_repeat", n.reminderRepeat)
            put("reminder_fired", if (n.reminderFired) 1 else 0)
        }
        if (n.id == 0L) n.id = db.insert("notes", null, v)
        else db.update("notes", v, "id=?", arrayOf("${n.id}"))

        db.delete("items", "note_id=?", arrayOf("${n.id}"))
        n.items.forEachIndexed { i, it ->
            it.id = db.insert("items", null, ContentValues().apply {
                put("note_id", n.id); put("text", it.text)
                put("checked", if (it.checked) 1 else 0); put("pos", i)
            })
        }
        db.delete("note_labels", "note_id=?", arrayOf("${n.id}"))
        n.labelIds.forEach { lid ->
            db.insert("note_labels", null, ContentValues().apply {
                put("note_id", n.id); put("label_id", lid)
            })
        }
        return n
    }

    fun delete(id: Long) = run { db.delete("notes", "id=?", arrayOf("$id")) }

    /** True when the note holds no text/items — used to discard empty drafts. */
    fun isBlank(n: Note) =
        n.title.isBlank() && n.body.isBlank() && n.items.all { it.text.isBlank() }

    /* ----- labels ----- */

    fun labels(): List<Label> {
        val out = mutableListOf<Label>()
        db.rawQuery("SELECT * FROM labels ORDER BY name COLLATE NOCASE", null).use {
            while (it.moveToNext()) out.add(Label(it.getLong(0), it.getString(1)))
        }
        return out
    }

    fun addLabel(name: String): Long =
        db.insert("labels", null, ContentValues().apply { put("name", name.trim()) })

    fun renameLabel(id: Long, name: String) {
        db.update("labels", ContentValues().apply { put("name", name.trim()) }, "id=?", arrayOf("$id"))
    }

    fun deleteLabel(id: Long) = run { db.delete("labels", "id=?", arrayOf("$id")) }

    /* ----- backup / restore (raw file copy) ----- */

    fun dbFile(): File = ctx.getDatabasePath(NAME)

    fun backup(out: OutputStream) {
        db                       // make sure the file exists
        helper.close()           // flush & release so the file is consistent
        dbFile().inputStream().use { it.copyTo(out) }
    }

    fun restore(input: InputStream) {
        helper.close()
        File(dbFile().path + "-wal").delete()
        File(dbFile().path + "-shm").delete()
        dbFile().outputStream().use { input.copyTo(it) }
        // next access to `db` transparently reopens the replaced file
    }

    /* ----- cursor helpers ----- */

    private fun readNote(c: Cursor) = Note(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
        body = c.getString(c.getColumnIndexOrThrow("body")) ?: "",
        checklist = c.getInt(c.getColumnIndexOrThrow("checklist")) == 1,
        color = c.getInt(c.getColumnIndexOrThrow("color")),
        pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) == 1,
        archived = c.getInt(c.getColumnIndexOrThrow("archived")) == 1,
        created = c.getLong(c.getColumnIndexOrThrow("created")),
        modified = c.getLong(c.getColumnIndexOrThrow("modified")),
        reminderAt = c.getLong(c.getColumnIndexOrThrow("reminder_at")),
        reminderRepeat = c.getString(c.getColumnIndexOrThrow("reminder_repeat")) ?: "NONE",
        reminderFired = c.getInt(c.getColumnIndexOrThrow("reminder_fired")) == 1,
    )

    private fun loadChildren(n: Note) {
        db.rawQuery(
            "SELECT id,text,checked FROM items WHERE note_id=? ORDER BY checked, pos",
            arrayOf("${n.id}")
        ).use {
            while (it.moveToNext())
                n.items.add(Item(it.getLong(0), it.getString(1) ?: "", it.getInt(2) == 1))
        }
        db.rawQuery("SELECT label_id FROM note_labels WHERE note_id=?", arrayOf("${n.id}")).use {
            while (it.moveToNext()) n.labelIds.add(it.getLong(0))
        }
    }
}
