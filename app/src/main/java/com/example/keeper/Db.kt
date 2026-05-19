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

/** A cached link preview. `status` ∈ LOADING | OK | FAILED. A preview is
 *  fetched once when the URL first appears and is never refreshed; a FAILED
 *  row is kept so the dead link is not re-fetched on every edit. */
data class Link(
    var id: Long = 0,
    var url: String = "",
    var title: String = "",
    var domain: String = "",
    var favicon: ByteArray? = null,
    var status: String = "LOADING",
)

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
    var reminderSnoozeAt: Long = 0,     // epoch ms; a temporary one-time snooze, 0 = none
    var position: Int = 0,              // manual sort order; lower = higher on screen
    var items: MutableList<Item> = mutableListOf(),
    var labelIds: MutableSet<Long> = mutableSetOf(),
    var links: MutableList<Link> = mutableListOf(),
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
        helper = object : SQLiteOpenHelper(ctx, NAME, null, 5) {
            override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, " +
                        "checklist INTEGER, color INTEGER, pinned INTEGER, archived INTEGER, " +
                        "created INTEGER, modified INTEGER, reminder_at INTEGER, reminder_repeat TEXT, " +
                        "reminder_fired INTEGER DEFAULT 0, position INTEGER DEFAULT 0, " +
                        "reminder_snooze_at INTEGER DEFAULT 0)"
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
                createLinks(db)
            }
            override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
                if (oldV < 2) db.execSQL("ALTER TABLE notes ADD COLUMN reminder_fired INTEGER DEFAULT 0")
                if (oldV < 3) db.execSQL("ALTER TABLE notes ADD COLUMN position INTEGER DEFAULT 0")
                if (oldV < 4) createLinks(db)
                if (oldV < 5) db.execSQL("ALTER TABLE notes ADD COLUMN reminder_snooze_at INTEGER DEFAULT 0")
            }
            private fun createLinks(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE links(id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER, " +
                        "url TEXT, title TEXT, domain TEXT, favicon BLOB, status TEXT, " +
                        "FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE UNIQUE INDEX idx_links ON links(note_id, url)")
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
            "SELECT * FROM notes WHERE $where ORDER BY pinned DESC, position ASC",
            args.toTypedArray()
        ).use { while (it.moveToNext()) out.add(readNote(it)) }
        attachChildren(out)
        return out
    }

    fun note(id: Long): Note? = db.rawQuery("SELECT * FROM notes WHERE id=?", arrayOf("$id")).use {
        if (it.moveToNext()) readNote(it).also(::loadChildren) else null
    }

    /** Notes whose reminder is still pending — used to (re)schedule alarms.
     *  One-time reminders that already fired are excluded so they never re-fire.
     *  Items/labels are not loaded: scheduling only needs the reminder columns. */
    fun withReminders(): List<Note> {
        val out = mutableListOf<Note>()
        db.rawQuery(
            "SELECT * FROM notes WHERE reminder_at>0 AND (reminder_fired=0 OR reminder_repeat<>'NONE')",
            null,
        ).use { while (it.moveToNext()) out.add(readNote(it)) }
        return out
    }

    /** Inserts or updates a note together with all its items and label links. */
    fun save(n: Note): Note {
        val now = System.currentTimeMillis()
        if (n.created == 0L) n.created = now
        n.modified = now
        if (n.id == 0L) {                       // a new note lands on top, like Keep
            n.position = db.rawQuery("SELECT IFNULL(MIN(position),0) FROM notes", null)
                .use { it.moveToFirst(); it.getInt(0) } - 1
        }
        val v = ContentValues().apply {
            put("title", n.title); put("body", n.body)
            put("checklist", if (n.checklist) 1 else 0)
            put("color", n.color)
            put("pinned", if (n.pinned) 1 else 0)
            put("archived", if (n.archived) 1 else 0)
            put("created", n.created); put("modified", n.modified)
            put("reminder_at", n.reminderAt); put("reminder_repeat", n.reminderRepeat)
            put("reminder_fired", if (n.reminderFired) 1 else 0)
            put("reminder_snooze_at", n.reminderSnoozeAt)
            put("position", n.position)
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

    /** Persists a manual ordering — `ids` is the notes top-to-bottom on screen. */
    fun reorder(ids: List<Long>) {
        db.beginTransaction()
        try {
            ids.forEachIndexed { i, id ->
                db.update("notes", ContentValues().apply { put("position", i) },
                    "id=?", arrayOf("$id"))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

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

    /* ----- link previews ----- */

    /** All cached link previews for one note. */
    fun links(noteId: Long): MutableList<Link> {
        val out = mutableListOf<Link>()
        if (noteId == 0L) return out
        db.rawQuery(
            "SELECT id,url,title,domain,favicon,status FROM links WHERE note_id=?",
            arrayOf("$noteId"),
        ).use {
            while (it.moveToNext()) out.add(
                Link(
                    id = it.getLong(0),
                    url = it.getString(1) ?: "",
                    title = it.getString(2) ?: "",
                    domain = it.getString(3) ?: "",
                    favicon = if (it.isNull(4)) null else it.getBlob(4),
                    status = it.getString(5) ?: "FAILED",
                )
            )
        }
        return out
    }

    /** Inserts or replaces the preview for (note_id, url). */
    fun upsertLink(noteId: Long, link: Link) {
        db.insertWithOnConflict(
            "links", null,
            ContentValues().apply {
                put("note_id", noteId)
                put("url", link.url)
                put("title", link.title)
                put("domain", link.domain)
                if (link.favicon != null) put("favicon", link.favicon) else putNull("favicon")
                put("status", link.status)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** Drops cached previews whose URL is no longer present in the note. */
    fun deleteLinksNotIn(noteId: Long, urls: Collection<String>) {
        if (urls.isEmpty()) {
            db.delete("links", "note_id=?", arrayOf("$noteId"))
            return
        }
        val placeholders = urls.joinToString(",") { "?" }
        db.delete(
            "links",
            "note_id=? AND url NOT IN ($placeholders)",
            (listOf("$noteId") + urls).toTypedArray(),
        )
    }

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
        reminderSnoozeAt = c.getLong(c.getColumnIndexOrThrow("reminder_snooze_at")),
        position = c.getInt(c.getColumnIndexOrThrow("position")),
    )

    /** Loads items + label ids for a single note (2 queries). */
    private fun loadChildren(n: Note) = attachChildren(listOf(n))

    /** Attaches items and label ids to a batch of notes with just 2 queries
     *  total, instead of 2 per note. Selecting every items/note_labels row and
     *  grouping in memory avoids a dynamic IN(...) clause; the row counts are
     *  tiny for any realistic note count. */
    private fun attachChildren(notes: List<Note>) {
        if (notes.isEmpty()) return
        val byId = notes.associateBy { it.id }
        db.rawQuery(
            // ordered by pos only — checked items keep their place, never resort
            "SELECT note_id,id,text,checked FROM items ORDER BY note_id, pos",
            null,
        ).use {
            while (it.moveToNext()) {
                byId[it.getLong(0)]?.items?.add(
                    Item(it.getLong(1), it.getString(2) ?: "", it.getInt(3) == 1)
                )
            }
        }
        db.rawQuery("SELECT note_id,label_id FROM note_labels", null).use {
            while (it.moveToNext()) byId[it.getLong(0)]?.labelIds?.add(it.getLong(1))
        }
        db.rawQuery("SELECT note_id,id,url,title,domain,favicon,status FROM links", null).use {
            while (it.moveToNext()) {
                byId[it.getLong(0)]?.links?.add(
                    Link(
                        id = it.getLong(1),
                        url = it.getString(2) ?: "",
                        title = it.getString(3) ?: "",
                        domain = it.getString(4) ?: "",
                        favicon = if (it.isNull(5)) null else it.getBlob(5),
                        status = it.getString(6) ?: "FAILED",
                    )
                )
            }
        }
    }
}
