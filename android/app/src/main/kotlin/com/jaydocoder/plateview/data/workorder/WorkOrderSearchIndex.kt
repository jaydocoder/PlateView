package com.jaydocoder.plateview.data.workorder

import android.database.Cursor
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jaydocoder.plateview.domain.workorder.WechatMessage
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import javax.inject.Inject
import javax.inject.Singleton

/** 本地全文索引。FTS5 不可用时由仓储回退到 Room 查询。 */
@Singleton
class WorkOrderSearchIndex @Inject constructor(private val database: WorkOrderCacheDatabase) {
    @Volatile
    private var initialized = false
    @Volatile
    var ftsAvailable: Boolean = false
        private set

    @Synchronized
    fun ensureReady() {
        if (initialized) return
        val db = database.openHelper.writableDatabase
        installLatestTriggers(db)
        ftsAvailable = runCatching {
            val compileOptionEnabled = db.query(SimpleSQLiteQuery("SELECT sqlite_compileoption_used('ENABLE_FTS5')")).use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) == 1
            }
            createFtsTables(db, "trigram")
            db.query(SimpleSQLiteQuery("SELECT 1 FROM work_order_search_fts LIMIT 1")).use { }
            if (!compileOptionEnabled) Log.i(TAG, "编译选项未声明 FTS5，但虚拟表探测成功")
            true
        }.getOrElse { error ->
            Log.w(TAG, "当前数据库不支持 FTS5，使用兼容查询", error)
            false
        }
        initialized = true
    }

    fun searchWorkOrders(userId: Long, keyword: String, limit: Int): List<WorkOrder> {
        ensureReady()
        val db = database.openHelper.readableDatabase
        val normalized = keyword.trim()
        val rows = if (ftsAvailable && normalized.length >= MIN_FTS_QUERY_LENGTH) {
            queryWorkOrdersFts(db, userId, normalized, limit)
        } else {
            queryWorkOrdersFields(db, userId, normalized, limit)
        }
        return rows.use { c -> generateSequence { if (c.moveToNext()) workOrderFromCursor(c) else null }.toList() }
    }

    fun searchMessages(userId: Long, keyword: String, limit: Int): List<WechatMessage> {
        ensureReady()
        val db = database.openHelper.readableDatabase
        val normalized = keyword.trim()
        val cursor = if (ftsAvailable && normalized.length >= MIN_FTS_QUERY_LENGTH) {
            db.query(SimpleSQLiteQuery(
                "SELECT c.messageId, c.businessType, c.rawContent, c.matchedSnippet, c.sentAt, c.sourceKey, c.sourceName, c.senderUsername, c.senderDisplay, c.senderGroupNickname, c.displayName, c.plateNumbers FROM wechat_message_search_fts f JOIN wechat_message_cache c ON c.rowid = f.rowid WHERE c.userId = ? AND wechat_message_search_fts MATCH ? ORDER BY c.sentAt DESC, c.messageId DESC LIMIT ?",
                arrayOf<Any>(userId, ftsExpression(normalized), limit),
            ))
        } else {
            db.query(SimpleSQLiteQuery(
                "SELECT messageId, businessType, rawContent, matchedSnippet, sentAt, sourceKey, sourceName, senderUsername, senderDisplay, senderGroupNickname, displayName, plateNumbers FROM wechat_message_cache WHERE userId = ? AND searchableText LIKE '%' || ? || '%' ORDER BY sentAt DESC, messageId DESC LIMIT ?",
                arrayOf<Any>(userId, normalized, limit),
            ))
        }
        return cursor.use { c -> generateSequence { if (c.moveToNext()) messageFromCursor(c) else null }.toList() }
    }

    private fun queryWorkOrdersFts(db: SupportSQLiteDatabase, userId: Long, keyword: String, limit: Int): Cursor =
        db.query(SimpleSQLiteQuery(
            "SELECT c.recordId, c.orderNumber, c.rawPlate, c.normalizedPlate, c.status, c.rawValidTime, c.location, c.remarks, c.rawContent, c.sentAt, c.sourceKey, c.sourceName, c.senderUsername, c.senderDisplay, c.senderGroupNickname, c.displayName, c.catalogRevision, c.orderYear FROM work_order_search_fts f JOIN work_order_cache c ON c.rowid = f.rowid WHERE c.userId = ? AND c.isLatest = 1 AND work_order_search_fts MATCH ? ORDER BY c.orderYear DESC, c.sentAt DESC, c.recordId DESC LIMIT ?",
            arrayOf<Any>(userId, ftsExpression(keyword), limit),
        ))

    private fun queryWorkOrdersFields(db: SupportSQLiteDatabase, userId: Long, keyword: String, limit: Int): Cursor =
        db.query(SimpleSQLiteQuery(
            "SELECT recordId, orderNumber, rawPlate, normalizedPlate, status, rawValidTime, location, remarks, rawContent, sentAt, sourceKey, sourceName, senderUsername, senderDisplay, senderGroupNickname, displayName, catalogRevision, orderYear FROM work_order_cache WHERE userId = ? AND isLatest = 1 AND (normalizedPlate = ? OR normalizedPlate LIKE ? || '%' OR orderNumber = ? OR orderNumber LIKE ? || '%' OR searchableText LIKE '%' || ? || '%') ORDER BY orderYear DESC, sentAt DESC, recordId DESC LIMIT ?",
            arrayOf<Any>(userId, keyword, keyword, keyword, keyword, keyword, limit),
        ))

    private fun workOrderFromCursor(c: Cursor) = WorkOrder(
        id = c.getLong(0), orderNumber = c.string(1), rawPlate = c.string(2), normalizedPlate = c.string(3),
        vehicleType = null, declaredPeople = null, rawValidTime = c.string(5), location = c.string(6),
        verificationMethod = null, reason = null, remarks = c.string(7), status = c.getString(4),
        parseQuality = "UNKNOWN", catalogRevision = c.getLong(16), rawContent = c.getString(8),
        sentAt = c.getString(9), sourceKey = c.getString(10), sourceName = c.getString(11),
        senderUsername = c.string(12), senderDisplay = c.string(13), senderGroupNickname = c.string(14),
        people = emptyList(), images = emptyList(), vehicles = emptyList(), orderYear = c.getInt(17), displayName = c.string(15),
    )

    private fun messageFromCursor(c: Cursor) = WechatMessage(
        id = c.getLong(0), businessType = c.getString(1), rawContent = c.getString(2), matchedSnippet = c.getString(3),
        sentAt = c.getString(4), sourceKey = c.getString(5), sourceName = c.getString(6), senderUsername = c.string(7),
        senderDisplay = c.string(8), senderGroupNickname = c.string(9), displayName = c.getString(10),
        plateNumbers = c.getString(11).split('|').filter(String::isNotBlank), attachments = emptyList(),
    )

    private fun createFtsTables(db: SupportSQLiteDatabase, tokenizer: String) {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS work_order_search_fts USING fts5(searchableText, content='work_order_cache', content_rowid='rowid', tokenize='$tokenizer')")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS wechat_message_search_fts USING fts5(searchableText, content='wechat_message_cache', content_rowid='rowid', tokenize='$tokenizer')")
        rebuildIfEmpty(db, "work_order_search_fts", "work_order_cache")
        rebuildIfEmpty(db, "wechat_message_search_fts", "wechat_message_cache")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS work_order_search_fts_ai AFTER INSERT ON work_order_cache BEGIN INSERT INTO work_order_search_fts(rowid, searchableText) VALUES (new.rowid, new.searchableText); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS work_order_search_fts_ad AFTER DELETE ON work_order_cache BEGIN INSERT INTO work_order_search_fts(work_order_search_fts, rowid, searchableText) VALUES('delete', old.rowid, old.searchableText); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS work_order_search_fts_au AFTER UPDATE OF searchableText ON work_order_cache BEGIN INSERT INTO work_order_search_fts(work_order_search_fts, rowid, searchableText) VALUES('delete', old.rowid, old.searchableText); INSERT INTO work_order_search_fts(rowid, searchableText) VALUES(new.rowid, new.searchableText); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS wechat_message_search_fts_ai AFTER INSERT ON wechat_message_cache BEGIN INSERT INTO wechat_message_search_fts(rowid, searchableText) VALUES (new.rowid, new.searchableText); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS wechat_message_search_fts_ad AFTER DELETE ON wechat_message_cache BEGIN INSERT INTO wechat_message_search_fts(wechat_message_search_fts, rowid, searchableText) VALUES('delete', old.rowid, old.searchableText); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS wechat_message_search_fts_au AFTER UPDATE OF searchableText ON wechat_message_cache BEGIN INSERT INTO wechat_message_search_fts(wechat_message_search_fts, rowid, searchableText) VALUES('delete', old.rowid, old.searchableText); INSERT INTO wechat_message_search_fts(rowid, searchableText) VALUES(new.rowid, new.searchableText); END")
    }

    private fun rebuildIfEmpty(db: SupportSQLiteDatabase, indexTable: String, contentTable: String) {
        val indexCount = db.query(SimpleSQLiteQuery("SELECT count(*) FROM $indexTable")).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
        if (indexCount > 0) return
        val contentCount = db.query(SimpleSQLiteQuery("SELECT count(*) FROM $contentTable")).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
        if (contentCount == 0L) return
        db.execSQL("INSERT INTO $indexTable($indexTable) VALUES('rebuild')")
    }

    private fun installLatestTriggers(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS work_order_latest_ai AFTER INSERT ON work_order_cache WHEN new.latestGroupKey <> '' BEGIN UPDATE work_order_cache SET isLatest = 0 WHERE userId = new.userId AND latestGroupKey = new.latestGroupKey AND rowid <> new.rowid; UPDATE work_order_cache SET isLatest = CASE WHEN EXISTS (SELECT 1 FROM work_order_cache n WHERE n.userId = new.userId AND n.latestGroupKey = new.latestGroupKey AND (n.sentAt > new.sentAt OR (n.sentAt = new.sentAt AND n.recordId > new.recordId))) THEN 0 ELSE 1 END WHERE rowid = new.rowid; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS work_order_latest_ad AFTER DELETE ON work_order_cache WHEN old.latestGroupKey <> '' BEGIN UPDATE work_order_cache SET isLatest = 0 WHERE userId = old.userId AND latestGroupKey = old.latestGroupKey; UPDATE work_order_cache SET isLatest = 1 WHERE rowid = (SELECT rowid FROM work_order_cache WHERE userId = old.userId AND latestGroupKey = old.latestGroupKey ORDER BY sentAt DESC, recordId DESC LIMIT 1); END")
    }

    private fun ftsExpression(value: String) = "\"${value.replace("\"", "\"\"")}\""
    private fun Cursor.string(index: Int): String? = if (isNull(index)) null else getString(index)

    private companion object {
        const val TAG = "WorkOrderSearchIndex"
        const val MIN_FTS_QUERY_LENGTH = 3
    }
}
