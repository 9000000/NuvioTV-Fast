package com.nuvio.tv.features.livetv

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal class ProgrammeIdentityPool {
    private val pool = HashMap<String, String>()
    fun share(s: String?): String? = if (s == null) null else pool.getOrPut(s) { s }
}

/**
 * The TV guide on the device, TiviMate style: every listing is stored here after a guide
 * update, and the app reads only the hours it needs (what's on screen, plus a margin), so Live
 * TV opens straight away however big the guide is, and uses far less memory.
 */
@Singleton
class EpgDatabase @Inject constructor(@ApplicationContext context: Context) :
    SQLiteOpenHelper(context, "livetv_guide2.db", null, 2) {

    @Volatile var changes: Long = 1L
        private set

    init {
        setWriteAheadLoggingEnabled(true)
        runCatching { context.deleteDatabase("livetv_guide.db") }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE listings (
                gid INTEGER NOT NULL, start INTEGER NOT NULL, stop INTEGER NOT NULL, title TEXT NOT NULL,
                descr TEXT, cat TEXT, ep TEXT, icon TEXT, year INTEGER, people TEXT, provider_id TEXT, provider_source TEXT)"""
        )
        db.execSQL("CREATE INDEX listings_gid_start ON listings(gid, start)")
        db.execSQL(
            """CREATE TABLE guide_ch (
                gid INTEGER PRIMARY KEY AUTOINCREMENT, src TEXT NOT NULL, xid TEXT NOT NULL,
                cnt INTEGER NOT NULL DEFAULT 0, last_stop INTEGER NOT NULL DEFAULT 0,
                placeholders INTEGER NOT NULL DEFAULT 0, state INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX guide_ch_src_xid ON guide_ch(src, xid)")
        db.execSQL("CREATE INDEX guide_ch_state ON guide_ch(state)")
        db.execSQL("CREATE TABLE guide_src (src TEXT PRIMARY KEY, stamp TEXT NOT NULL, made INTEGER NOT NULL, listed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE guide_src_channels (src TEXT NOT NULL, xid TEXT NOT NULL, names TEXT, icon TEXT, extra INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX guide_src_channels_src ON guide_src_channels(src)")
        db.execSQL("CREATE TABLE chan (ch TEXT PRIMARY KEY, gid INTEGER NOT NULL, sh INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX chan_gid ON chan(gid)")
        db.execSQL("CREATE TABLE auto (ch TEXT PRIMARY KEY, source TEXT NOT NULL, xmltv TEXT NOT NULL)")
        db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE listings ADD COLUMN provider_id TEXT")
            db.execSQL("ALTER TABLE listings ADD COLUMN provider_source TEXT")
        }
    }

    class GuideLink(val sourceId: String, val xmltvId: String, val shiftMs: Long = 0L)

    private fun putFingerprint(db: SQLiteDatabase, fingerprint: String, parts: String?) {
        putRevision(db)
        db.insertWithOnConflict("meta", null, ContentValues().apply { put("k", "fingerprint"); put("v", fingerprint) },
            SQLiteDatabase.CONFLICT_REPLACE)
        if (parts != null) {
            db.insertWithOnConflict("meta", null, ContentValues().apply { put("k", "parts"); put("v", parts) },
                SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db.delete("meta", "k = ?", arrayOf("parts"))
        }
    }

    fun parts(): String? =
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k = 'parts'", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun revision(): Long {
        val at = changes
        revCache?.let { (c, r) -> if (c == at) return r }
        fun read(): Long? = readableDatabase.rawQuery("SELECT v FROM meta WHERE k = 'rev'", null).use { c ->
            if (c.moveToFirst()) c.getString(0)?.toLongOrNull() else null
        }
        val r = read() ?: run {
            writableDatabase.insertWithOnConflict("meta", null, ContentValues().apply {
                put("k", "rev"); put("v", newRevision().toString())
            }, SQLiteDatabase.CONFLICT_IGNORE)
            read() ?: 0L
        }
        if (changes == at) revCache = at to r
        return r
    }

    @Volatile private var revCache: Pair<Long, Long>? = null

    private fun putRevision(db: SQLiteDatabase) {
        db.insertWithOnConflict("meta", null, ContentValues().apply { put("k", "rev"); put("v", newRevision().toString()) },
            SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun newRevision(): Long = kotlin.random.Random.nextLong().let { if (it == 0L) 1L else it }

    fun fingerprint(): String? =
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k = 'fingerprint'", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private class Link(val ch: String, val gid: Long, val sh: Long)

    private fun links(keys: Collection<String>?): List<Link> {
        val out = ArrayList<Link>()
        fun read(where: String, args: Array<String>?) {
            readableDatabase.rawQuery("SELECT ch, gid, sh FROM chan$where", args).use { c ->
                while (c.moveToNext()) out += Link(c.getString(0), c.getLong(1), c.getLong(2))
            }
        }
        if (keys == null) read("", null)
        else keys.distinct().chunked(400).forEach { part ->
            read(" WHERE ch IN (" + part.joinToString(",") { "?" } + ")", part.toTypedArray())
        }
        return out
    }

    private class Group(val shiftMs: Long, val byGid: Map<Long, List<String>>)

    private fun groups(links: List<Link>): List<Group> =
        links.groupBy { it.sh }.map { (sh, list) ->
            val byGid = HashMap<Long, MutableList<String>>()
            list.forEach { byGid.getOrPut(it.gid) { ArrayList(1) } += it.ch }
            Group(sh, byGid)
        }

    private fun allGroups(): List<Group> {
        val at = changes
        allGroupsCache?.let { (stamp, ref) -> if (stamp == at) ref.get()?.let { return it } }
        val built = groups(links(null))
        if (changes == at) allGroupsCache = at to java.lang.ref.SoftReference(built)
        return built
    }

    @Volatile private var allGroupsCache: Pair<Long, java.lang.ref.SoftReference<List<Group>>>? = null

    fun range(
        fromMs: Long,
        toMs: Long,
        onlyChannel: String? = null,
        light: Boolean = false,
        channels: Collection<String>? = null
    ): Map<String, List<EpgProgram>> {
        repeat(2) {
            val at = changes
            val got = rangeOnce(fromMs, toMs, onlyChannel, light, channels)
            if (changes == at) return got
        }
        return rangeOnce(fromMs, toMs, onlyChannel, light, channels)
    }

    private fun rangeOnce(
        fromMs: Long,
        toMs: Long,
        onlyChannel: String?,
        light: Boolean,
        channels: Collection<String>?
    ): Map<String, List<EpgProgram>> {
        val out = HashMap<String, List<EpgProgram>>()
        val providerIdentities = ProgrammeIdentityPool()
        val columns = if (light) "gid, start, stop, title, ep, icon, provider_id, provider_source" else "gid, start, stop, title, descr, cat, ep, icon, year, people, provider_id, provider_source"
        val wanted = when {
            onlyChannel != null -> groups(links(listOf(onlyChannel)))
            channels != null -> groups(links(channels))
            else -> allGroups()
        }
        for (group in wanted) {
            val sh = group.shiftMs
            val lists = HashMap<Long, ArrayList<EpgProgram>>()
            val timeArgs = arrayOf((fromMs - sh - MAX_SHOW_MS).toString(), (toMs - sh).toString(), (fromMs - sh).toString())
            group.byGid.keys.chunked(GID_CHUNK).forEach { part ->
                val sql = "SELECT $columns FROM listings WHERE gid IN (${part.joinToString(",")}) AND start >= ? AND start < ? AND stop > ?"
                readableDatabase.rawQuery(sql, timeArgs).use { c ->
                    while (c.moveToNext()) {
                        val p = if (light) EpgProgram(
                            startMs = c.getLong(1) + sh,
                            stopMs = c.getLong(2) + sh,
                            title = c.getString(3),
                            episode = if (c.isNull(4)) null else XmltvParser.cleanEpisode(c.getString(4)),
                            icon = if (c.isNull(5)) null else c.getString(5),
                            providerProgramId = if (c.isNull(6)) null else c.getString(6),
                            providerSourceIdentity = if (c.isNull(7)) null else providerIdentities.share(c.getString(7))
                        ) else EpgProgram(
                            startMs = c.getLong(1) + sh,
                            stopMs = c.getLong(2) + sh,
                            title = c.getString(3),
                            description = if (c.isNull(4)) null else c.getString(4),
                            category = if (c.isNull(5)) null else c.getString(5),
                            episode = if (c.isNull(6)) null else XmltvParser.cleanEpisode(c.getString(6)),
                            icon = if (c.isNull(7)) null else c.getString(7),
                            year = if (c.isNull(8)) null else c.getInt(8),
                            people = if (c.isNull(9)) emptyList() else c.getString(9).split('\u001F'),
                            providerProgramId = if (c.isNull(10)) null else c.getString(10),
                            providerSourceIdentity = if (c.isNull(11)) null else providerIdentities.share(c.getString(11))
                        )
                        lists.getOrPut(c.getLong(0)) { ArrayList() } += p
                    }
                }
            }
            lists.forEach { (gid, list) ->
                list.sortBy { it.startMs }
                list.trimToSize()
                group.byGid[gid]?.forEach { ch -> out[ch] = list }
            }
        }
        return out
    }

    fun program(ch: String, startMs: Long): EpgProgram? =
        range(startMs, startMs + 1, onlyChannel = ch)[ch]?.firstOrNull { it.startMs == startMs }

    fun channelRange(ch: String, fromMs: Long, toMs: Long): List<EpgProgram> =
        range(fromMs, toMs, ch)[ch].orEmpty()

    fun autoMatches(): Map<String, EpgAssignment> {
        val out = HashMap<String, EpgAssignment>()
        readableDatabase.rawQuery("SELECT ch, source, xmltv FROM auto", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = EpgAssignment(c.getString(1), c.getString(2))
        }
        return out
    }

    class StagedMeta(val count: Int, val lastStop: Long, val placeholders: Int, val hasRows: Boolean = true)
    class SourceState(val stamp: String, val made: Long, val meta: Map<String, StagedMeta>, val listed: Int)

    inner class SourceWriter internal constructor(private val src: String) {
        private val db = writableDatabase
        private val stmt: android.database.sqlite.SQLiteStatement
        private var rows = 0
        private val gids = HashMap<String, Long>()
        private val meta = HashMap<String, LongArray>()
        private val countedOnly = HashSet<String>()
        private var open = true

        init {
            markUnfinishedDead(db, src)
            purgeDead()
            db.beginTransaction()
            stmt = db.compileStatement(
                "INSERT INTO listings (gid, start, stop, title, descr, cat, ep, icon, year, people, provider_id, provider_source) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
            )
        }

        private fun gidFor(xid: String): Long = gids.getOrPut(xid) {
            db.insertOrThrow("guide_ch", null, ContentValues().apply { put("src", src); put("xid", xid); put("state", PENDING) })
        }

        fun add(xid: String, programs: List<EpgProgram>, isPlaceholder: (String) -> Boolean, keepRows: Boolean = true) {
            if (programs.isEmpty()) return
            val gid = gidFor(xid)
            val m = meta.getOrPut(xid) { LongArray(3) }
            if (!keepRows) countedOnly += xid
            programs.forEach { p ->
                if (keepRows) {
                    bindListing(stmt, gid, p, 0L)
                    stmt.executeInsert()
                }
                m[0]++
                if (p.stopMs > m[1]) m[1] = p.stopMs
                if (isPlaceholder(p.title)) m[2]++
            }
            if (!keepRows) return
            rows += programs.size
            if (rows >= 50_000) {
                rows = 0
                db.setTransactionSuccessful(); db.endTransaction(); db.beginTransaction()
            }
        }

        fun finish(
            stamp: String,
            channels: List<Triple<String, List<String>, String?>>,
            kept: Collection<String>,
            listed: Int = channels.size,
            extraIds: Collection<String> = emptyList()
        ): Map<String, StagedMeta> {
            kept.forEach { gidFor(it) }
            val upd = db.compileStatement("UPDATE guide_ch SET cnt = ?, last_stop = ?, placeholders = ? WHERE gid = ?")
            meta.forEach { (xid, m) ->
                upd.clearBindings()
                upd.bindLong(1, m[0]); upd.bindLong(2, m[1]); upd.bindLong(3, m[2]); upd.bindLong(4, gids.getValue(xid))
                upd.executeUpdateDelete()
            }
            upd.close()
            changes++
            try {
                val old = ArrayList<Pair<Long, String>>()
                db.rawQuery("SELECT gid, xid FROM guide_ch WHERE src = ? AND state IN ($LIVE, $EMPTIED)", arrayOf(src)).use { c ->
                    while (c.moveToNext()) old += c.getLong(0) to c.getString(1)
                }
                val move = db.compileStatement("UPDATE chan SET gid = ? WHERE gid = ?")
                val drop = db.compileStatement("DELETE FROM chan WHERE gid = ?")
                old.forEach { (oldGid, xid) ->
                    val now = gids[xid]
                    if (now != null) { move.clearBindings(); move.bindLong(1, now); move.bindLong(2, oldGid); move.executeUpdateDelete() }
                    else { drop.clearBindings(); drop.bindLong(1, oldGid); drop.executeUpdateDelete() }
                }
                move.close(); drop.close()
                db.execSQL("UPDATE guide_ch SET state = $DEAD WHERE src = ? AND state IN ($LIVE, $EMPTIED)", arrayOf<Any>(src))
                db.execSQL("UPDATE guide_ch SET state = $LIVE WHERE src = ? AND state = $PENDING", arrayOf<Any>(src))
                countedOnly.forEach { xid ->
                    db.execSQL("UPDATE guide_ch SET state = $EMPTIED WHERE gid = ?", arrayOf<Any>(gids.getValue(xid)))
                }
                db.delete("guide_src_channels", "src = ?", arrayOf(src))
                val ch = db.compileStatement("INSERT INTO guide_src_channels (src, xid, names, icon) VALUES (?,?,?,?)")
                channels.forEach { (id, names, icon) ->
                    ch.clearBindings()
                    ch.bindString(1, src); ch.bindString(2, id)
                    if (names.isEmpty()) ch.bindNull(3) else ch.bindString(3, names.joinToString("\u001F"))
                    icon?.let { ch.bindString(4, it) } ?: ch.bindNull(4)
                    ch.executeInsert()
                }
                ch.close()
                if (extraIds.isNotEmpty()) {
                    val ex = db.compileStatement("INSERT INTO guide_src_channels (src, xid, extra) VALUES (?,?,1)")
                    extraIds.forEach { id -> ex.clearBindings(); ex.bindString(1, src); ex.bindString(2, id); ex.executeInsert() }
                    ex.close()
                }
                db.insertWithOnConflict("guide_src", null, ContentValues().apply {
                    put("src", src); put("stamp", stamp); put("made", System.currentTimeMillis()); put("listed", listed)
                }, SQLiteDatabase.CONFLICT_REPLACE)
                putRevision(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                open = false
                changes++
            }
            runCatching { stmt.close() }
            runCatching { purgeDead() }
            return gids.keys.associateWith { xid ->
                val m = meta[xid]
                StagedMeta(m?.get(0)?.toInt() ?: 0, m?.get(1) ?: 0L, m?.get(2)?.toInt() ?: 0, hasRows = xid !in countedOnly)
            }
        }

        fun abort() {
            if (!open) return
            open = false
            runCatching { db.endTransaction() }
            runCatching { stmt.close() }
            runCatching { markUnfinishedDead(db, src); purgeDead() }
        }
    }

    fun startSource(src: String): SourceWriter = SourceWriter(src)

    private fun markUnfinishedDead(db: SQLiteDatabase, src: String) {
        db.execSQL("UPDATE guide_ch SET state = $DEAD WHERE src = ? AND state = $PENDING", arrayOf<Any>(src))
    }

    fun sourceState(src: String): SourceState? {
        val info = readableDatabase.rawQuery("SELECT stamp, made, listed FROM guide_src WHERE src = ?", arrayOf(src)).use { c ->
            if (c.moveToFirst()) Triple(c.getString(0), c.getLong(1), c.getInt(2)) else null
        } ?: return null
        val meta = HashMap<String, StagedMeta>()
        readableDatabase.rawQuery(
            "SELECT xid, cnt, last_stop, placeholders, state FROM guide_ch WHERE src = ? AND state IN ($LIVE, $EMPTIED)", arrayOf(src)
        ).use { c ->
            while (c.moveToNext()) meta[c.getString(0)] = StagedMeta(c.getInt(1), c.getLong(2), c.getInt(3), hasRows = c.getInt(4) == LIVE)
        }
        return SourceState(info.first, info.second, meta, info.third)
    }

    fun sourceChannels(src: String): List<Triple<String, List<String>, String?>> {
        val out = ArrayList<Triple<String, List<String>, String?>>()
        readableDatabase.rawQuery("SELECT xid, names, icon FROM guide_src_channels WHERE src = ? AND extra = 0 ORDER BY rowid", arrayOf(src)).use { c ->
            while (c.moveToNext()) out += Triple(
                c.getString(0),
                if (c.isNull(1)) emptyList() else c.getString(1).split('\u001F'),
                if (c.isNull(2)) null else c.getString(2)
            )
        }
        return out
    }

    private fun guideChannels(db: SQLiteDatabase): HashMap<String, Pair<Long, Boolean>> {
        val out = HashMap<String, Pair<Long, Boolean>>()
        db.rawQuery("SELECT gid, src, xid, state FROM guide_ch WHERE state IN ($LIVE, $EMPTIED)", null).use { c ->
            while (c.moveToNext()) out[c.getString(1) + "\u0000" + c.getString(2)] = c.getLong(0) to (c.getInt(3) == LIVE)
        }
        return out
    }

    private fun releaseUnused(db: SQLiteDatabase) {
        db.execSQL("UPDATE guide_ch SET state = $DEAD WHERE state = $LIVE AND src = ? AND gid NOT IN (SELECT gid FROM chan)", arrayOf<Any>(OWN_SOURCE))
        db.execSQL("UPDATE guide_ch SET state = $EMPTIED WHERE state = $LIVE AND gid NOT IN (SELECT gid FROM chan)")
    }

    fun applyGuide(
        fingerprint: String,
        links: Map<String, GuideLink>,
        auto: Map<String, EpgAssignment>,
        parts: String?,
        sources: Collection<String>
    ) {
        changes++
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val gone = HashSet<String>()
                db.rawQuery("SELECT src FROM guide_src", null).use { c -> while (c.moveToNext()) gone += c.getString(0) }
                db.rawQuery("SELECT DISTINCT src FROM guide_ch WHERE state IN ($LIVE, $EMPTIED)", null).use { c -> while (c.moveToNext()) gone += c.getString(0) }
                gone -= sources.toSet()
                gone -= OWN_SOURCE
                gone.forEach { src -> forgetSource(db, src) }
                val known = guideChannels(db)
                db.delete("chan", null, null)
                db.delete("auto", null, null)
                val ins = db.compileStatement("INSERT OR REPLACE INTO chan (ch, gid, sh) VALUES (?,?,?)")
                links.forEach { (ch, link) ->
                    val gid = known[link.sourceId + "\u0000" + link.xmltvId]?.first ?: return@forEach
                    ins.clearBindings(); ins.bindString(1, ch); ins.bindLong(2, gid); ins.bindLong(3, link.shiftMs)
                    ins.executeInsert()
                }
                ins.close()
                putAuto(db, auto)
                releaseUnused(db)
                putFingerprint(db, fingerprint, parts)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            changes++
        }
        runCatching { purgeDead(); emptyOut() }
    }

    private fun putAuto(db: SQLiteDatabase, auto: Map<String, EpgAssignment>) {
        if (auto.isEmpty()) return
        val stmt = db.compileStatement("INSERT OR REPLACE INTO auto (ch, source, xmltv) VALUES (?,?,?)")
        auto.forEach { (ch, a) ->
            stmt.clearBindings(); stmt.bindString(1, ch); stmt.bindString(2, a.sourceId); stmt.bindString(3, a.xmltvId)
            stmt.executeInsert()
        }
        stmt.close()
    }

    private fun forgetSource(db: SQLiteDatabase, src: String) {
        db.execSQL("DELETE FROM chan WHERE gid IN (SELECT gid FROM guide_ch WHERE src = ?)", arrayOf<Any>(src))
        db.execSQL("UPDATE guide_ch SET state = $DEAD WHERE src = ?", arrayOf<Any>(src))
        db.delete("guide_src", "src = ?", arrayOf(src))
        db.delete("guide_src_channels", "src = ?", arrayOf(src))
    }

    fun putChannelRange(ch: String, fromMs: Long, toMs: Long, programs: List<EpgProgram>) {
        changes++
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                var gid: Long? = null
                var sh = 0L
                db.rawQuery("SELECT gid, sh FROM chan WHERE ch = ?", arrayOf(ch)).use { c ->
                    if (c.moveToFirst()) { gid = c.getLong(0); sh = c.getLong(1) }
                }
                val id = gid ?: run {
                    var own: Long? = null
                    db.rawQuery("SELECT gid FROM guide_ch WHERE src = ? AND xid = ? AND state = $LIVE LIMIT 1", arrayOf(OWN_SOURCE, ch)).use { c ->
                        if (c.moveToFirst()) own = c.getLong(0)
                    }
                    val made = own ?: db.insertOrThrow("guide_ch", null, ContentValues().apply {
                        put("src", OWN_SOURCE); put("xid", ch); put("state", LIVE)
                    })
                    db.insertWithOnConflict("chan", null, ContentValues().apply { put("ch", ch); put("gid", made); put("sh", 0L) },
                        SQLiteDatabase.CONFLICT_REPLACE)
                    made
                }
                db.delete("listings", "gid = ? AND start >= ? AND start < ?", arrayOf(id.toString(), (fromMs - sh).toString(), (toMs - sh).toString()))
                val stmt = db.compileStatement(
                    "INSERT INTO listings (gid, start, stop, title, descr, cat, ep, icon, year, people, provider_id, provider_source) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
                )
                programs.forEach { p ->
                    bindListing(stmt, id, p, sh)
                    stmt.executeInsert()
                }
                stmt.close()
                putRevision(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            changes++
        }
    }

    private fun purgeDead() {
        val db = writableDatabase
        while (true) {
            val gids = ArrayList<Long>()
            db.rawQuery("SELECT gid FROM guide_ch WHERE state = $DEAD LIMIT $PURGE_BATCH", null).use { c -> while (c.moveToNext()) gids += c.getLong(0) }
            if (gids.isEmpty()) return
            val list = gids.joinToString(",")
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM listings WHERE gid IN ($list)")
                db.execSQL("DELETE FROM guide_ch WHERE gid IN ($list)")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun emptyOut() {
        val db = writableDatabase
        val gids = ArrayList<Long>()
        db.rawQuery(
            "SELECT gid FROM guide_ch WHERE state = $EMPTIED AND EXISTS (SELECT 1 FROM listings WHERE listings.gid = guide_ch.gid)", null
        ).use { c -> while (c.moveToNext()) gids += c.getLong(0) }
        gids.chunked(PURGE_BATCH).forEach { part ->
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM listings WHERE gid IN (${part.joinToString(",")})")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun bindListing(stmt: android.database.sqlite.SQLiteStatement, gid: Long, p: EpgProgram, shiftMs: Long) {
        stmt.clearBindings()
        stmt.bindLong(1, gid)
        stmt.bindLong(2, p.startMs - shiftMs); stmt.bindLong(3, p.stopMs - shiftMs); stmt.bindString(4, p.title)
        p.description?.let { stmt.bindString(5, it) } ?: stmt.bindNull(5)
        p.category?.let { stmt.bindString(6, it) } ?: stmt.bindNull(6)
        p.episode?.let { stmt.bindString(7, it) } ?: stmt.bindNull(7)
        p.icon?.let { stmt.bindString(8, it) } ?: stmt.bindNull(8)
        p.year?.let { stmt.bindLong(9, it.toLong()) } ?: stmt.bindNull(9)
        if (p.people.isEmpty()) stmt.bindNull(10) else stmt.bindString(10, p.people.joinToString("\u001F"))
        p.providerProgramId?.let { stmt.bindString(11, it) } ?: stmt.bindNull(11)
        p.providerSourceIdentity?.let { stmt.bindString(12, it) } ?: stmt.bindNull(12)
    }

    fun clear() {
        changes++
        try {
            val db = writableDatabase
            db.delete("listings", null, null)
            db.delete("guide_ch", null, null)
            db.delete("guide_src", null, null)
            db.delete("guide_src_channels", null, null)
            db.delete("chan", null, null)
            db.delete("auto", null, null)
            db.delete("meta", null, null)
        } finally {
            changes++
        }
    }

    companion object {
        const val OWN_SOURCE = "__own__"
        private const val PENDING = 0
        private const val LIVE = 1
        private const val EMPTIED = 2
        private const val DEAD = 3
    }
}

private const val MAX_SHOW_MS = 6L * 60 * 60 * 1000
private const val GID_CHUNK = 400
private const val PURGE_BATCH = 200
