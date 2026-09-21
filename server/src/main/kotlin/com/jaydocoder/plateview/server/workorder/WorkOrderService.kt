package com.jaydocoder.plateview.server.workorder

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

internal class WorkOrderService(private val dataSource: DataSource) {
    fun hasAccess(userId: Long): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT 1 FROM users WHERE id = ? AND status = 'ACTIVE' AND ((username = 'admin' AND role = 'ADMIN') OR wechat_work_order_access_enabled = TRUE)",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use(ResultSet::next)
        }
    }

    fun ingest(batch: WorkOrderMessageBatch): WorkOrderIngestResult = transaction { connection ->
        requireKnownSource(batch.sourceKey, batch.sourceName)
        val sourceId = connection.upsertSource(batch.sourceKey, batch.sourceName)
        var inserted = 0
        var duplicate = 0
        batch.messages.forEach { message ->
            message.validate()
            val parsed = WorkOrderParser.parse(message.rawContent)
            val passageSenderEnabled = connection.isPassageSenderEnabled(message.senderUsername)
            val businessType = WorkOrderParser.classify(parsed, message.rawContent, passageSenderEnabled)
            val messageId = connection.insertMessage(sourceId, message, businessType, parsed.searchableText)
            if (messageId == null) {
                connection.enrichMessageSender(sourceId, message)
                duplicate += 1
            } else {
                inserted += 1
                val revision = connection.nextCatalogRevision()
                connection.prepareStatement("UPDATE wechat_messages SET catalog_revision = ? WHERE id = ?").use { statement ->
                    statement.setLong(1, revision)
                    statement.setLong(2, messageId)
                    statement.executeUpdate()
                }
                if (businessType == "STRUCTURED_WORK_ORDER" || businessType == "ATTACHMENT_WORK_ORDER") {
                    connection.insertWorkOrder(messageId, parsed, revision)
                }
            }
        }
        connection.prepareStatement(
            "UPDATE wechat_sources SET latest_message_at = GREATEST(COALESCE(latest_message_at, ?), ?), last_uploaded_at = CURRENT_TIMESTAMP, collector_status = 'HEALTHY', error_code = NULL WHERE id = ?",
        ).use { statement ->
            val latest = batch.messages.maxOf { Timestamp.from(it.sentAt) }
            statement.setTimestamp(1, latest)
            statement.setTimestamp(2, latest)
            statement.setLong(3, sourceId)
            statement.executeUpdate()
        }
        WorkOrderIngestResult(inserted, duplicate, connection.catalogRevision())
    }

    fun updateHeartbeat(heartbeat: WorkOrderHeartbeat) {
        requireKnownSource(heartbeat.sourceKey, heartbeat.sourceName)
        transaction { connection ->
            val sourceId = connection.upsertSource(heartbeat.sourceKey, heartbeat.sourceName)
            connection.prepareStatement(
                "UPDATE wechat_sources SET collector_status = ?, latest_message_at = COALESCE(?, latest_message_at), last_heartbeat_at = CURRENT_TIMESTAMP, backlog_count = ?, error_code = ? WHERE id = ?",
            ).use { statement ->
                statement.setString(1, heartbeat.status)
                statement.setTimestamp(2, heartbeat.latestMessageAt?.let(Timestamp::from))
                statement.setInt(3, heartbeat.backlogCount.coerceAtLeast(0))
                statement.setString(4, heartbeat.errorCode)
                statement.setLong(5, sourceId)
                statement.executeUpdate()
            }
        }
    }

    fun search(keyword: String, limit: Int = DEFAULT_SEARCH_RESULT_LIMIT): List<WorkOrderRecord> {
        val normalized = WorkOrderParser.normalizeSearchText(keyword)
        if (normalized.isBlank()) return emptyList()
        return dataSource.connection.use { connection ->
            val safeLimit = limit.coerceIn(1, 50)
            val exactPlate = WorkOrderParser.extractPlateNumbers(keyword).singleOrNull()
                ?.takeIf { WorkOrderParser.normalizeSearchText(it) == normalized }
            val exactRecords = when {
                normalized.matches(WORK_ORDER_NUMBER_PATTERN) -> connection.queryRecords(SEARCH_EXACT_ORDER, normalized, safeLimit)
                exactPlate != null -> connection.queryRecords(SEARCH_EXACT_PLATE, exactPlate, safeLimit)
                else -> emptyList()
            }
            connection.hydrateRecords(
                exactRecords.ifEmpty { connection.queryRecords(SEARCH, "%$normalized%", safeLimit, keywordParameterCount = 2) },
            )
        }
    }

    fun searchMessages(keyword: String, offset: Int = 0, limit: Int = DEFAULT_SEARCH_RESULT_LIMIT): WechatMessagePage {
        val normalized = WorkOrderParser.normalizeSearchText(keyword)
        if (normalized.isBlank()) return WechatMessagePage(emptyList(), null)
        val safeLimit = limit.coerceIn(1, 50)
        return dataSource.connection.use { connection ->
            val query = if (normalized.length >= MINIMUM_TRIGRAM_QUERY_LENGTH) MESSAGE_INDEXED_SEARCH else MESSAGE_SHORT_SEARCH
            connection.prepareStatement(query).use { statement ->
                val contains = "%$normalized%"
                statement.setString(1, normalized)
                statement.setString(2, contains)
                statement.setString(3, "$normalized%")
                statement.setInt(4, safeLimit + 1)
                statement.setInt(5, offset.coerceAtLeast(0))
                val records = statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(connection.readMessage(result, normalized, includeAttachments = false)) }
                }
                val pageRecords = connection.hydrateMessages(records.take(safeLimit))
                WechatMessagePage(pageRecords, (offset + safeLimit).takeIf { records.size > safeLimit })
            }
        }
    }

    fun messageDetail(messageId: Long): WechatMessageRecord = dataSource.connection.use { connection ->
        connection.prepareStatement("$MESSAGE_BASE_SELECT WHERE m.id = ? AND m.business_type IN ('PASSAGE_MESSAGE', 'GENERAL_MESSAGE')").use { statement ->
            statement.setLong(1, messageId)
            statement.executeQuery().use { result ->
                if (!result.next()) throw WorkOrderNotFoundException()
                connection.readMessage(result, "")
            }
        }
    }

    fun passageSenders(): List<WechatPassageSender> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT sender_username, original_display_name, display_alias, enabled FROM wechat_passage_senders ORDER BY display_alias, sender_username",
        ).use { statement ->
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(WechatPassageSender(result.getString(1), result.getString(2), result.getString(3), result.getBoolean(4)))
                }
            }
        }
    }

    fun savePassageSender(sender: WechatPassageSender) {
        require(sender.senderUsername.isNotBlank() && sender.senderUsername.length <= 255) { "微信发送者标识无效" }
        require(sender.displayAlias.isNotBlank() && sender.displayAlias.length <= 255) { "应用显示称呼无效" }
        transaction { connection ->
            connection.prepareStatement(
                """
                INSERT INTO wechat_passage_senders(sender_username, original_display_name, display_alias, enabled, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(sender_username) DO UPDATE SET original_display_name = EXCLUDED.original_display_name,
                    display_alias = EXCLUDED.display_alias, enabled = EXCLUDED.enabled, updated_at = CURRENT_TIMESTAMP
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, sender.senderUsername.trim())
                statement.setString(2, sender.originalDisplayName?.trim())
                statement.setString(3, sender.displayAlias.trim())
                statement.setBoolean(4, sender.enabled)
                statement.executeUpdate()
            }
            connection.nextCatalogRevision()
        }
    }

    fun detail(recordId: Long): WorkOrderRecord = dataSource.connection.use { connection ->
        connection.prepareStatement(DETAIL).use { statement ->
            statement.setLong(1, recordId)
            statement.executeQuery().use { result ->
                if (!result.next()) throw WorkOrderNotFoundException()
                connection.readRecord(result)
            }
        }
    }

    fun history(recordId: Long): List<WorkOrderRecord> = dataSource.connection.use { connection ->
        val current = detail(recordId)
        val orderNumber = current.orderNumber ?: return@use listOf(current)
        connection.prepareStatement("$BASE_SELECT WHERE r.order_number = ? ORDER BY m.sent_at DESC, m.local_message_id DESC, r.id DESC").use { statement ->
            statement.setString(1, orderNumber)
            statement.executeQuery().use { result -> buildList { while (result.next()) add(connection.readRecord(result)) } }
        }
    }

    fun catalogVersion(): Long = dataSource.connection.use { connection -> connection.catalogRevision() }

    fun changes(afterVersion: Long, limit: Int): WorkOrderChangePage = dataSource.connection.use { connection ->
        val safeLimit = limit.coerceIn(1, 200)
        val records = connection.prepareStatement(
            "$BASE_SELECT WHERE r.catalog_revision > ? ORDER BY r.catalog_revision, r.id LIMIT ?",
        ).use { statement ->
            statement.setLong(1, afterVersion.coerceAtLeast(0))
            statement.setInt(2, safeLimit + 1)
            statement.executeQuery().use { result -> buildList { while (result.next()) add(connection.readRecord(result)) } }
        }
        val pageItems = records.take(safeLimit)
        WorkOrderChangePage(
            catalogVersion = connection.catalogRevision(),
            nextVersion = pageItems.maxOfOrNull(WorkOrderRecord::catalogRevision) ?: afterVersion,
            hasMore = records.size > safeLimit,
            records = pageItems,
        )
    }

    fun syncStatus(): List<WechatSourceStatus> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT source_key, display_name,
                   CASE WHEN last_heartbeat_at IS NULL OR last_heartbeat_at < CURRENT_TIMESTAMP - INTERVAL '90 seconds'
                        THEN 'OFFLINE' ELSE collector_status END AS collector_status,
                   latest_message_at, last_heartbeat_at, last_uploaded_at, backlog_count, error_code
            FROM wechat_sources
            ORDER BY id
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            WechatSourceStatus(
                                sourceKey = result.getString("source_key"),
                                displayName = result.getString("display_name"),
                                status = result.getString("collector_status"),
                                latestMessageAt = result.getTimestamp("latest_message_at")?.toInstant(),
                                lastHeartbeatAt = result.getTimestamp("last_heartbeat_at")?.toInstant(),
                                lastUploadedAt = result.getTimestamp("last_uploaded_at")?.toInstant(),
                                backlogCount = result.getInt("backlog_count"),
                                errorCode = result.getString("error_code"),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun syncIssues(): List<WechatSyncIssue> = dataSource.connection.use { connection ->
        val messageIssues = connection.prepareStatement(
            """
            SELECT 'UNSEARCHABLE_MESSAGE' AS issue_type, r.id AS record_id, NULL::BIGINT AS image_id,
                   s.display_name, m.sent_at, LEFT(m.raw_content, 240) AS summary,
                   NULL::VARCHAR AS attachment_kind, NULL::TEXT AS file_name, NULL::INTEGER AS page_count
            FROM work_order_records r
            JOIN wechat_messages m ON m.id = r.message_id
            JOIN wechat_sources s ON s.id = m.source_id
            WHERE r.parse_quality = 'RAW_FALLBACK'
            ORDER BY m.sent_at DESC, r.id DESC
            LIMIT 100
            """.trimIndent(),
        ).use { statement -> statement.executeQuery().use(::readIssues) }
        val imageIssues = connection.prepareStatement(
            """
            SELECT CASE WHEN candidate_count > 1 THEN 'ATTACHMENT_CONFLICT' ELSE 'ATTACHMENT_UNAVAILABLE' END AS issue_type,
                   NULL::BIGINT AS record_id, image_id, display_name, sent_at, summary, attachment_kind, file_name, page_count
            FROM (
                SELECT i.id AS image_id, s.display_name, i.sent_at, i.attachment_kind, i.file_name, i.page_count,
                       COALESCE(i.sender_display, i.sender_username, '未知发送者') AS summary,
                       i.availability,
                       (SELECT COUNT(*)
                        FROM work_order_records r
                        JOIN wechat_messages m ON m.id = r.message_id
                        WHERE m.source_id = i.source_id
                          AND m.sender_username = i.sender_username
                          AND m.sent_at BETWEEN i.sent_at - INTERVAL '2 minutes' AND i.sent_at + INTERVAL '2 minutes') AS candidate_count
                FROM work_order_images i
                JOIN wechat_sources s ON s.id = i.source_id
                WHERE i.linked_record_id IS NULL AND i.ignored_at IS NULL AND i.sender_username IS NOT NULL
            ) candidates
            WHERE candidate_count > 1 OR (candidate_count = 1 AND availability <> 'AVAILABLE')
            ORDER BY sent_at DESC, image_id DESC
            LIMIT 100
            """.trimIndent(),
        ).use { statement -> statement.executeQuery().use(::readIssues) }
        (messageIssues + imageIssues).sortedByDescending(WechatSyncIssue::sentAt).map { issue ->
            if (issue.imageId == null) issue else issue.copy(candidates = connection.attachmentCandidates(issue.imageId))
        }
    }

    fun correctRecord(recordId: Long, correction: WorkOrderCorrection): WorkOrderRecord = transaction { connection ->
        require(correction.orderNumber?.isNotBlank() == true || correction.rawPlate?.isNotBlank() == true) {
            "单号和车牌至少填写一项"
        }
        require(correction.status in setOf("ACTIVE", "VOID")) { "车单状态无效" }
        val rawContent = connection.prepareStatement(
            "SELECT m.raw_content FROM work_order_records r JOIN wechat_messages m ON m.id = r.message_id WHERE r.id = ? FOR UPDATE",
        ).use { statement ->
            statement.setLong(1, recordId)
            statement.executeQuery().use { result -> if (result.next()) result.getString(1) else throw WorkOrderNotFoundException() }
        }
        val revision = connection.nextCatalogRevision()
        val searchable = WorkOrderParser.normalizeSearchText(
            listOfNotNull(rawContent, correction.orderNumber, correction.rawPlate, correction.vehicleType, correction.location, correction.reason, correction.remarks)
                .joinToString(" "),
        )
        connection.prepareStatement(
            """
            UPDATE work_order_records
            SET order_number = ?, raw_plate = ?, normalized_plate = ?, vehicle_type = ?, declared_people = ?,
                raw_valid_time = ?, location = ?, verification_method = ?, reason = ?, remarks = ?, status = ?,
                parse_quality = 'PARTIAL', searchable_text = ?, catalog_revision = ?, parsed_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, correction.orderNumber?.trim())
            statement.setString(2, correction.rawPlate?.trim())
            statement.setString(3, correction.rawPlate?.let(WorkOrderParser::normalizeSearchText))
            statement.setString(4, correction.vehicleType?.trim())
            statement.setObject(5, correction.declaredPeople)
            statement.setString(6, correction.rawValidTime?.trim())
            statement.setString(7, correction.location?.trim())
            statement.setString(8, correction.verificationMethod?.trim())
            statement.setString(9, correction.reason?.trim())
            statement.setString(10, correction.remarks?.trim())
            statement.setString(11, correction.status)
            statement.setString(12, searchable)
            statement.setLong(13, revision)
            statement.setLong(14, recordId)
            if (statement.executeUpdate() == 0) throw WorkOrderNotFoundException()
        }
        connection.prepareStatement(DETAIL).use { statement ->
            statement.setLong(1, recordId)
            statement.executeQuery().use { result -> result.next(); connection.readRecord(result) }
        }
    }

    fun upsertImage(upload: WorkOrderImageUpload): Long = transaction { connection ->
        requireKnownSource(upload.sourceKey, upload.sourceName)
        val sourceId = connection.upsertSource(upload.sourceKey, upload.sourceName)
        val existingId = connection.prepareStatement(
            "SELECT id FROM work_order_images WHERE source_id = ? AND local_attachment_id = ?",
        ).use { statement ->
            statement.setLong(1, sourceId)
            statement.setString(2, upload.localAttachmentId)
            statement.executeQuery().use { result -> if (result.next()) result.getLong(1) else null }
        }
        val imageId = if (existingId == null) {
            connection.prepareStatement(
                """
                INSERT INTO work_order_images(source_id, local_attachment_id, sender_username, sender_display, sent_at, sha256,
                    original_content_type, original_size, original_path, preview_path, preview_size, thumbnail_path, thumbnail_size,
                    availability, attachment_kind, file_name, page_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, sourceId)
                statement.setString(2, upload.localAttachmentId)
                statement.setString(3, upload.senderUsername)
                statement.setString(4, upload.senderDisplay)
                statement.setTimestamp(5, Timestamp.from(upload.sentAt))
                statement.setString(6, upload.sha256)
                statement.setString(7, upload.originalContentType)
                statement.setObject(8, upload.originalSize)
                statement.setString(9, upload.originalPath)
                statement.setString(10, upload.previewPath)
                statement.setObject(11, upload.previewSize)
                statement.setString(12, upload.thumbnailPath)
                statement.setObject(13, upload.thumbnailSize)
                statement.setString(14, upload.availability)
                statement.setString(15, upload.attachmentKind)
                statement.setString(16, upload.fileName)
                statement.setObject(17, upload.pageCount)
                statement.executeQuery().use { result -> result.next(); result.getLong(1) }
            }
        } else {
            connection.prepareStatement(
                """
                UPDATE work_order_images SET sender_username = ?, sender_display = ?, sent_at = ?, sha256 = ?,
                    original_content_type = ?, original_size = ?, original_path = ?, preview_path = ?, preview_size = ?,
                    thumbnail_path = ?, thumbnail_size = ?, availability = ?, attachment_kind = ?, file_name = ?, page_count = ? WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, upload.senderUsername)
                statement.setString(2, upload.senderDisplay)
                statement.setTimestamp(3, Timestamp.from(upload.sentAt))
                statement.setString(4, upload.sha256)
                statement.setString(5, upload.originalContentType)
                statement.setObject(6, upload.originalSize)
                statement.setString(7, upload.originalPath)
                statement.setString(8, upload.previewPath)
                statement.setObject(9, upload.previewSize)
                statement.setString(10, upload.thumbnailPath)
                statement.setObject(11, upload.thumbnailSize)
                statement.setString(12, upload.availability)
                statement.setString(13, upload.attachmentKind)
                statement.setString(14, upload.fileName)
                statement.setObject(15, upload.pageCount)
                statement.setLong(16, existingId)
                statement.executeUpdate()
            }
            existingId
        }
        connection.autoAssociateImage(imageId, sourceId, upload.localMessageId, upload.senderUsername, upload.sentAt)
        connection.autoAssociateMessageAttachment(imageId, sourceId, upload.senderUsername, upload.sentAt)
        imageId
    }

    fun messageAttachmentVariant(messageId: Long, attachmentId: Long, variant: String): StoredImageVariant = dataSource.connection.use { connection ->
        val columns = when (variant) {
            "thumbnail" -> "thumbnail_path, 'image/webp', thumbnail_size"
            "preview" -> "preview_path, 'image/webp', preview_size"
            "original" -> "original_path, original_content_type, original_size"
            else -> throw IllegalArgumentException("附件规格无效")
        }
        connection.prepareStatement("SELECT $columns, sha256 FROM work_order_images WHERE id = ? AND linked_message_id = ? AND availability = 'AVAILABLE'").use { statement ->
            statement.setLong(1, attachmentId)
            statement.setLong(2, messageId)
            statement.executeQuery().use { result ->
                if (!result.next()) throw WorkOrderNotFoundException()
                StoredImageVariant(result.getString(1) ?: throw WorkOrderNotFoundException(), result.getString(2), result.getLong(3), result.getString(4))
            }
        }
    }

    fun imageVariant(recordId: Long, imageId: Long, variant: String): StoredImageVariant = dataSource.connection.use { connection ->
        val columns = when (variant) {
            "thumbnail" -> "thumbnail_path, 'image/webp', thumbnail_size"
            "preview" -> "preview_path, 'image/webp', preview_size"
            "original" -> "original_path, original_content_type, original_size"
            else -> throw IllegalArgumentException("图片规格无效")
        }
        connection.prepareStatement("SELECT $columns, sha256 FROM work_order_images WHERE id = ? AND linked_record_id = ? AND availability = 'AVAILABLE'").use { statement ->
            statement.setLong(1, imageId)
            statement.setLong(2, recordId)
            statement.executeQuery().use { result ->
                if (!result.next()) throw WorkOrderNotFoundException()
                val path = result.getString(1) ?: throw WorkOrderNotFoundException()
                StoredImageVariant(path, result.getString(2) ?: "application/octet-stream", result.getLong(3), result.getString(4))
            }
        }
    }

    fun adminImageVariant(imageId: Long, variant: String): StoredImageVariant = dataSource.connection.use { connection ->
        val columns = when (variant) {
            "thumbnail" -> "thumbnail_path, 'image/webp', thumbnail_size"
            "preview" -> "preview_path, 'image/webp', preview_size"
            "original" -> "original_path, original_content_type, original_size"
            else -> throw IllegalArgumentException("附件规格无效")
        }
        connection.prepareStatement("SELECT $columns, sha256 FROM work_order_images WHERE id = ? AND availability = 'AVAILABLE'").use { statement ->
            statement.setLong(1, imageId)
            statement.executeQuery().use { result ->
                if (!result.next()) throw WorkOrderNotFoundException()
                StoredImageVariant(result.getString(1) ?: throw WorkOrderNotFoundException(), result.getString(2) ?: "application/octet-stream", result.getLong(3), result.getString(4))
            }
        }
    }

    fun associateImage(imageId: Long, recordId: Long?) {
        dataSource.connection.use { connection ->
            if (recordId != null) {
                connection.prepareStatement("SELECT 1 FROM work_order_records WHERE id = ?").use { statement ->
                    statement.setLong(1, recordId)
                    statement.executeQuery().use { if (!it.next()) throw WorkOrderNotFoundException() }
                }
            }
            connection.prepareStatement("UPDATE work_order_images SET linked_record_id = ?, association_method = ? WHERE id = ?").use { statement ->
                statement.setObject(1, recordId)
                statement.setString(2, recordId?.let { "MANUAL" })
                statement.setLong(3, imageId)
                if (statement.executeUpdate() == 0) throw WorkOrderNotFoundException()
            }
        }
    }

    fun ignoreImage(imageId: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE work_order_images SET ignored_at = CURRENT_TIMESTAMP WHERE id = ?").use { statement ->
                statement.setLong(1, imageId)
                if (statement.executeUpdate() == 0) throw WorkOrderNotFoundException()
            }
        }
    }

    private fun Connection.autoAssociateImage(imageId: Long, sourceId: Long, localMessageId: String?, senderUsername: String?, sentAt: Instant) {
        if (!localMessageId.isNullOrBlank()) {
            prepareStatement(
                """
                UPDATE work_order_images i SET linked_message_id = m.id
                FROM wechat_messages m
                WHERE i.id = ? AND m.source_id = ? AND m.local_message_id = ?
                  AND i.linked_message_id IS NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, imageId)
                statement.setLong(2, sourceId)
                statement.setString(3, localMessageId)
                if (statement.executeUpdate() > 0) return
            }
        }
        if (senderUsername.isNullOrBlank()) return
        val candidates = prepareStatement(
            """
            SELECT r.id
            FROM work_order_records r
            JOIN wechat_messages m ON m.id = r.message_id
            WHERE m.source_id = ? AND m.sender_username = ?
              AND m.sent_at BETWEEN ? AND ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM work_order_records newer_record
                  JOIN wechat_messages newer_message ON newer_message.id = newer_record.message_id
                  WHERE newer_message.source_id = m.source_id
                    AND newer_message.sender_username = m.sender_username
                    AND newer_message.sent_at > m.sent_at
                    AND newer_message.sent_at <= ?
              )
            ORDER BY ABS(EXTRACT(EPOCH FROM (m.sent_at - ?))), m.sent_at DESC, r.id DESC
            LIMIT 2
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, sourceId)
            statement.setString(2, senderUsername)
            statement.setTimestamp(3, Timestamp.from(sentAt.minusSeconds(120)))
            statement.setTimestamp(4, Timestamp.from(sentAt.plusSeconds(120)))
            statement.setTimestamp(5, Timestamp.from(sentAt))
            statement.setTimestamp(6, Timestamp.from(sentAt))
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.getLong(1)) } }
        }
        if (candidates.size == 1) {
            prepareStatement("UPDATE work_order_images SET linked_record_id = ?, association_method = 'AUTO' WHERE id = ? AND association_method IS NULL").use { statement ->
                statement.setLong(1, candidates.single())
                statement.setLong(2, imageId)
                statement.executeUpdate()
            }
        }
    }

    private fun Connection.autoAssociateMessageAttachment(attachmentId: Long, sourceId: Long, senderUsername: String?, sentAt: Instant) {
        if (senderUsername.isNullOrBlank()) return
        val candidates = prepareStatement(
            """
            SELECT m.id FROM wechat_messages m
            WHERE m.source_id = ? AND m.sender_username = ?
              AND m.business_type = 'ATTACHMENT_WORK_ORDER'
              AND m.sent_at BETWEEN ? AND ?
            ORDER BY ABS(EXTRACT(EPOCH FROM (m.sent_at - ?))), m.id DESC
            LIMIT 2
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, sourceId)
            statement.setString(2, senderUsername)
            statement.setTimestamp(3, Timestamp.from(sentAt.minusSeconds(120)))
            statement.setTimestamp(4, Timestamp.from(sentAt.plusSeconds(120)))
            statement.setTimestamp(5, Timestamp.from(sentAt))
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.getLong(1)) } }
        }
        if (candidates.size == 1) {
            prepareStatement("UPDATE work_order_images SET linked_message_id = ? WHERE id = ? AND linked_message_id IS NULL").use { statement ->
                statement.setLong(1, candidates.single())
                statement.setLong(2, attachmentId)
                statement.executeUpdate()
            }
        }
    }

    private fun Connection.insertWorkOrder(messageId: Long, parsed: ParsedWorkOrder, revision: Long) {
        val recordId = prepareStatement(
            """
            INSERT INTO work_order_records(message_id, order_number, raw_plate, normalized_plate, vehicle_type, declared_people, raw_valid_time, location, verification_method, reason, remarks, status, parse_quality, searchable_text, catalog_revision)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, messageId)
            statement.setString(2, parsed.orderNumber)
            statement.setString(3, parsed.rawPlate)
            statement.setString(4, parsed.normalizedPlate)
            statement.setString(5, parsed.vehicleType)
            statement.setObject(6, parsed.declaredPeople)
            statement.setString(7, parsed.rawValidTime)
            statement.setString(8, parsed.location)
            statement.setString(9, parsed.verificationMethod)
            statement.setString(10, parsed.reason)
            statement.setString(11, parsed.remarks)
            statement.setString(12, parsed.status)
            statement.setString(13, parsed.parseQuality)
            statement.setString(14, parsed.searchableText)
            statement.setLong(15, revision)
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }
        parsed.people.forEachIndexed { index, person ->
            prepareStatement(
                "INSERT INTO work_order_people(record_id, sequence_number, raw_line, person_name, identity_number) VALUES (?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setLong(1, recordId)
                statement.setInt(2, index)
                statement.setString(3, person.rawLine)
                statement.setString(4, person.name)
                statement.setString(5, person.identityNumber)
                statement.executeUpdate()
            }
        }
        parsed.vehicles.forEachIndexed { index, vehicle ->
            prepareStatement(
                """
                INSERT INTO work_order_vehicles(record_id, sequence_number, raw_description, raw_plate, normalized_plate, vehicle_type)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, recordId)
                statement.setInt(2, index)
                statement.setString(3, vehicle.rawDescription)
                statement.setString(4, vehicle.rawPlate)
                statement.setString(5, vehicle.normalizedPlate)
                statement.setString(6, vehicle.vehicleType)
                statement.executeUpdate()
            }
        }
    }

    private fun Connection.hydrateRecords(records: List<WorkOrderRecord>): List<WorkOrderRecord> {
        if (records.isEmpty()) return records
        val recordIds = createArrayOf("bigint", records.map(WorkOrderRecord::id).toTypedArray())
        val people = prepareStatement(
            "SELECT record_id, raw_line, person_name, identity_number FROM work_order_people WHERE record_id = ANY (?) ORDER BY record_id, sequence_number",
        ).use { statement ->
            statement.setArray(1, recordIds)
            statement.executeQuery().use { result ->
                buildMap<Long, MutableList<WorkOrderPerson>> {
                    while (result.next()) getOrPut(result.getLong("record_id"), ::mutableListOf)
                        .add(WorkOrderPerson(result.getString("raw_line"), result.getString("person_name"), result.getString("identity_number")))
                }
            }
        }
        val vehicles = prepareStatement(
            "SELECT record_id, raw_description, raw_plate, normalized_plate, vehicle_type FROM work_order_vehicles WHERE record_id = ANY (?) ORDER BY record_id, sequence_number",
        ).use { statement ->
            statement.setArray(1, recordIds)
            statement.executeQuery().use { result ->
                buildMap<Long, MutableList<WorkOrderVehicle>> {
                    while (result.next()) getOrPut(result.getLong("record_id"), ::mutableListOf).add(
                        WorkOrderVehicle(result.getString("raw_description"), result.getString("raw_plate"), result.getString("normalized_plate"), result.getString("vehicle_type")),
                    )
                }
            }
        }
        val images = prepareStatement(
            """
            SELECT linked_record_id, id, sha256, original_content_type, original_size, preview_size,
                   thumbnail_size, availability, attachment_kind, file_name, page_count
            FROM work_order_images WHERE linked_record_id = ANY (?) ORDER BY linked_record_id, sent_at, id
            """.trimIndent(),
        ).use { statement ->
            statement.setArray(1, recordIds)
            statement.executeQuery().use { result ->
                buildMap<Long, MutableList<WorkOrderImage>> {
                    while (result.next()) getOrPut(result.getLong("linked_record_id"), ::mutableListOf).add(
                        WorkOrderImage(
                            result.getLong("id"), result.getString("sha256"), result.getString("original_content_type"),
                            result.getLongOrNull("original_size"), result.getLongOrNull("preview_size") != null,
                            result.getLongOrNull("thumbnail_size") != null, result.getString("availability"),
                            result.getString("attachment_kind"), result.getString("file_name"), result.getIntOrNull("page_count"),
                        ),
                    )
                }
            }
        }
        recordIds.free()
        return records.map { record ->
            record.copy(
                people = people[record.id].orEmpty(),
                vehicles = vehicles[record.id].orEmpty(),
                images = images[record.id].orEmpty(),
            )
        }
    }

    private fun Connection.hydrateMessages(records: List<WechatMessageRecord>): List<WechatMessageRecord> {
        if (records.isEmpty()) return records
        val messageIds = createArrayOf("bigint", records.map(WechatMessageRecord::id).toTypedArray())
        val attachments = prepareStatement(
            """
            SELECT linked_message_id, id, attachment_kind, file_name, sha256, original_content_type, original_size,
                   preview_size, thumbnail_size, availability, page_count
            FROM work_order_images WHERE linked_message_id = ANY (?) ORDER BY linked_message_id, sent_at, id
            """.trimIndent(),
        ).use { statement ->
            statement.setArray(1, messageIds)
            statement.executeQuery().use { result ->
                buildMap<Long, MutableList<WorkOrderAttachment>> {
                    while (result.next()) getOrPut(result.getLong("linked_message_id"), ::mutableListOf).add(
                        WorkOrderAttachment(
                            result.getLong("id"), result.getString("attachment_kind"), result.getString("file_name"),
                            result.getString("sha256"), result.getString("original_content_type"), result.getLongOrNull("original_size"),
                            result.getLongOrNull("preview_size") != null, result.getLongOrNull("thumbnail_size") != null,
                            result.getString("availability"), result.getIntOrNull("page_count"),
                        ),
                    )
                }
            }
        }
        messageIds.free()
        return records.map { it.copy(attachments = attachments[it.id].orEmpty()) }
    }

    private fun Connection.queryRecords(
        sql: String,
        keyword: String,
        limit: Int,
        keywordParameterCount: Int = 1,
    ): List<WorkOrderRecord> =
        prepareStatement(sql).use { statement ->
            repeat(keywordParameterCount) { statement.setString(it + 1, keyword) }
            statement.setInt(keywordParameterCount + 1, limit)
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(readRecord(result, includeRelations = false)) }
            }
        }

    private fun Connection.readRecord(result: ResultSet, includeRelations: Boolean = true): WorkOrderRecord {
        val recordId = result.getLong("record_id")
        val people = if (includeRelations) prepareStatement(
            "SELECT raw_line, person_name, identity_number FROM work_order_people WHERE record_id = ? ORDER BY sequence_number",
        ).use { statement ->
            statement.setLong(1, recordId)
            statement.executeQuery().use { peopleResult ->
                buildList {
                    while (peopleResult.next()) add(WorkOrderPerson(peopleResult.getString(1), peopleResult.getString(2), peopleResult.getString(3)))
                }
            }
        } else emptyList()
        val images = if (includeRelations) prepareStatement(
            "SELECT id, sha256, original_content_type, original_size, preview_size, thumbnail_size, availability, attachment_kind, file_name, page_count FROM work_order_images WHERE linked_record_id = ? ORDER BY sent_at, id",
        ).use { statement ->
            statement.setLong(1, recordId)
            statement.executeQuery().use { imageResult ->
                buildList {
                    while (imageResult.next()) {
                        add(
                            WorkOrderImage(
                                id = imageResult.getLong("id"),
                                sha256 = imageResult.getString("sha256"),
                                contentType = imageResult.getString("original_content_type"),
                                originalSize = imageResult.getLongOrNull("original_size"),
                                previewAvailable = imageResult.getLongOrNull("preview_size") != null,
                                thumbnailAvailable = imageResult.getLongOrNull("thumbnail_size") != null,
                                availability = imageResult.getString("availability"),
                                kind = imageResult.getString("attachment_kind"),
                                fileName = imageResult.getString("file_name"),
                                pageCount = imageResult.getIntOrNull("page_count"),
                            ),
                        )
                    }
                }
            }
        } else emptyList()
        val vehicles = if (includeRelations) prepareStatement(
            "SELECT raw_description, raw_plate, normalized_plate, vehicle_type FROM work_order_vehicles WHERE record_id = ? ORDER BY sequence_number",
        ).use { statement ->
            statement.setLong(1, recordId)
            statement.executeQuery().use { vehicleResult ->
                buildList {
                    while (vehicleResult.next()) {
                        add(WorkOrderVehicle(vehicleResult.getString(1), vehicleResult.getString(2), vehicleResult.getString(3), vehicleResult.getString(4)))
                    }
                }
            }
        } else emptyList()
        return WorkOrderRecord(
            id = recordId,
            orderNumber = result.getString("order_number"),
            rawPlate = result.getString("raw_plate"),
            normalizedPlate = result.getString("normalized_plate"),
            vehicleType = result.getString("vehicle_type"),
            declaredPeople = result.getIntOrNull("declared_people"),
            rawValidTime = result.getString("raw_valid_time"),
            location = result.getString("location"),
            verificationMethod = result.getString("verification_method"),
            reason = result.getString("reason"),
            remarks = result.getString("remarks"),
            status = result.getString("status"),
            parseQuality = result.getString("parse_quality"),
            catalogRevision = result.getLong("catalog_revision"),
            rawContent = result.getString("raw_content"),
            sentAt = result.getTimestamp("sent_at").toInstant(),
            sourceKey = result.getString("source_key"),
            sourceName = result.getString("source_name"),
            senderUsername = result.getString("sender_username"),
            senderDisplay = result.getString("sender_display"),
            senderGroupNickname = result.getString("sender_group_nickname"),
            people = people,
            images = images,
            vehicles = vehicles,
        )
    }

    private fun Connection.readMessage(
        result: ResultSet,
        normalizedKeyword: String,
        includeAttachments: Boolean = true,
    ): WechatMessageRecord {
        val messageId = result.getLong("message_id")
        val rawContent = result.getString("raw_content")
        val plates = WorkOrderParser.extractPlateNumbers(rawContent)
        val displayName = resolveWechatSenderDisplayName(
            result.getString("display_alias"),
            result.getString("sender_group_nickname"),
            result.getString("sender_display"),
            result.getString("sender_username"),
        )
        val attachments = if (includeAttachments) prepareStatement(
            """
            SELECT id, attachment_kind, file_name, sha256, original_content_type, original_size,
                   preview_size, thumbnail_size, availability, page_count
            FROM work_order_images WHERE linked_message_id = ? ORDER BY sent_at, id
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, messageId)
            statement.executeQuery().use { attachmentResult ->
                buildList {
                    while (attachmentResult.next()) {
                        add(
                            WorkOrderAttachment(
                                id = attachmentResult.getLong("id"),
                                kind = attachmentResult.getString("attachment_kind"),
                                fileName = attachmentResult.getString("file_name"),
                                sha256 = attachmentResult.getString("sha256"),
                                contentType = attachmentResult.getString("original_content_type"),
                                originalSize = attachmentResult.getLongOrNull("original_size"),
                                previewAvailable = attachmentResult.getLongOrNull("preview_size") != null,
                                thumbnailAvailable = attachmentResult.getLongOrNull("thumbnail_size") != null,
                                availability = attachmentResult.getString("availability"),
                                pageCount = attachmentResult.getIntOrNull("page_count"),
                            ),
                        )
                    }
                }
            }
        } else emptyList()
        return WechatMessageRecord(
            id = messageId,
            businessType = result.getString("business_type"),
            rawContent = rawContent,
            matchedSnippet = matchingSnippet(rawContent, normalizedKeyword),
            sentAt = result.getTimestamp("sent_at").toInstant(),
            sourceKey = result.getString("source_key"),
            sourceName = result.getString("source_name"),
            senderUsername = result.getString("sender_username"),
            senderDisplay = result.getString("sender_display"),
            senderGroupNickname = result.getString("sender_group_nickname"),
            displayName = displayName,
            plateNumbers = plates,
            attachments = attachments,
        )
    }

    private fun Connection.isPassageSenderEnabled(senderUsername: String?): Boolean {
        if (senderUsername.isNullOrBlank()) return false
        return prepareStatement("SELECT enabled FROM wechat_passage_senders WHERE sender_username = ?").use { statement ->
            statement.setString(1, senderUsername)
            statement.executeQuery().use { result -> result.next() && result.getBoolean(1) }
        }
    }

    private fun Connection.upsertSource(sourceKey: String, sourceName: String): Long = prepareStatement(
        "INSERT INTO wechat_sources(source_key, display_name) VALUES (?, ?) ON CONFLICT(source_key) DO UPDATE SET display_name = EXCLUDED.display_name RETURNING id",
    ).use { statement ->
        statement.setString(1, sourceKey)
        statement.setString(2, sourceName)
        statement.executeQuery().use { result -> result.next(); result.getLong(1) }
    }

    private fun Connection.insertMessage(
        sourceId: Long,
        message: WorkOrderIncomingMessage,
        businessType: String,
        normalizedContent: String,
    ): Long? = prepareStatement(
        """
        INSERT INTO wechat_messages(source_id, local_message_id, sender_username, sender_display, sender_group_nickname,
            message_type, raw_content, sent_at, content_fingerprint, normalized_content, business_type, catalog_revision)
        VALUES (?, ?, ?, ?, ?, 'TEXT', ?, ?, ?, ?, ?, 0)
        ON CONFLICT(source_id, local_message_id) DO NOTHING
        RETURNING id
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, sourceId)
        statement.setString(2, message.localMessageId)
        statement.setString(3, message.senderUsername)
        statement.setString(4, message.senderDisplay)
        statement.setString(5, message.senderGroupNickname)
        statement.setString(6, message.rawContent)
        statement.setTimestamp(7, Timestamp.from(message.sentAt))
        statement.setString(8, message.contentFingerprint)
        statement.setString(9, normalizedContent)
        statement.setString(10, businessType)
        statement.executeQuery().use { result -> if (result.next()) result.getLong(1) else null }
    }

    private fun Connection.enrichMessageSender(sourceId: Long, message: WorkOrderIncomingMessage) {
        prepareStatement(
            """
            UPDATE wechat_messages SET
                sender_username = COALESCE(NULLIF(?, ''), sender_username),
                sender_display = COALESCE(NULLIF(?, ''), sender_display),
                sender_group_nickname = COALESCE(NULLIF(?, ''), sender_group_nickname)
            WHERE source_id = ? AND local_message_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, message.senderUsername)
            statement.setString(2, message.senderDisplay)
            statement.setString(3, message.senderGroupNickname)
            statement.setLong(4, sourceId)
            statement.setString(5, message.localMessageId)
            statement.executeUpdate()
        }
    }

    private fun Connection.nextCatalogRevision(): Long = prepareStatement(
        "UPDATE work_order_catalog_state SET revision = revision + 1 WHERE id = 1 RETURNING revision",
    ).use { statement -> statement.executeQuery().use { result -> result.next(); result.getLong(1) } }

    private fun Connection.catalogRevision(): Long = prepareStatement("SELECT revision FROM work_order_catalog_state WHERE id = 1").use {
        it.executeQuery().use { result -> result.next(); result.getLong(1) }
    }

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        }
    }

    private fun requireKnownSource(sourceKey: String, sourceName: String) {
        require(KNOWN_SOURCES[sourceKey] == sourceName) { "微信采集来源不在允许范围内" }
    }

    private fun readIssues(result: ResultSet): List<WechatSyncIssue> = buildList {
        while (result.next()) {
            add(
                WechatSyncIssue(
                    type = result.getString("issue_type"),
                    recordId = result.getLongOrNull("record_id"),
                    imageId = result.getLongOrNull("image_id"),
                    sourceName = result.getString("display_name"),
                    sentAt = result.getTimestamp("sent_at").toInstant(),
                    summary = result.getString("summary"),
                    attachmentKind = result.getString("attachment_kind"),
                    fileName = result.getString("file_name"),
                    pageCount = result.getIntOrNull("page_count"),
                ),
            )
        }
    }

    private fun Connection.attachmentCandidates(attachmentId: Long): List<WechatAttachmentCandidate> = prepareStatement(
        """
        SELECT r.id, r.order_number, m.sent_at, LEFT(m.raw_content, 160) AS summary
        FROM work_order_images i
        JOIN wechat_messages m ON m.source_id = i.source_id
            AND m.sender_username = i.sender_username
            AND m.sent_at BETWEEN i.sent_at - INTERVAL '2 minutes' AND i.sent_at + INTERVAL '2 minutes'
        JOIN work_order_records r ON r.message_id = m.id
        WHERE i.id = ?
        ORDER BY ABS(EXTRACT(EPOCH FROM (m.sent_at - i.sent_at))), m.sent_at DESC, r.id DESC
        LIMIT 10
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, attachmentId)
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) add(WechatAttachmentCandidate(result.getLong(1), result.getString(2), result.getTimestamp(3).toInstant(), result.getString(4)))
            }
        }
    }

    private companion object {
        const val DEFAULT_SEARCH_RESULT_LIMIT = 8
        val KNOWN_SOURCES = mapOf(
            "44367002464@chatroom" to "票务中心工作群",
            "31463879194@chatroom" to "贾登峪车道口",
            "20546602068@chatroom" to "2026车单子接收群",
        )
        const val BASE_SELECT = """
            SELECT r.id AS record_id, r.order_number, r.raw_plate, r.normalized_plate, r.vehicle_type, r.declared_people,
                   r.raw_valid_time, r.location, r.verification_method, r.reason, r.remarks, r.status, r.parse_quality,
                   r.catalog_revision, m.raw_content, m.sent_at, m.sender_username, m.sender_display,
                   m.sender_group_nickname, s.source_key, s.display_name AS source_name
            FROM work_order_records r
            JOIN wechat_messages m ON m.id = r.message_id
            JOIN wechat_sources s ON s.id = m.source_id
        """
        const val DETAIL = "$BASE_SELECT WHERE r.id = ?"
        const val SEARCH_EXACT_ORDER = """
            WITH latest AS (
                SELECT r.id
                FROM work_order_records r
                JOIN wechat_messages m ON m.id = r.message_id
                WHERE r.order_number = ?
                ORDER BY m.sent_at DESC, m.local_message_id DESC, r.id DESC
                LIMIT 1
            )
            $BASE_SELECT
            JOIN latest ON latest.id = r.id
            LIMIT ?
        """
        const val SEARCH_EXACT_PLATE = """
            WITH latest AS (
                SELECT DISTINCT ON (COALESCE(r.order_number, '#' || r.id::text)) r.id, m.sent_at
                FROM work_order_records r
                JOIN wechat_messages m ON m.id = r.message_id
                JOIN work_order_vehicles v ON v.record_id = r.id
                WHERE v.normalized_plate = ?
                ORDER BY COALESCE(r.order_number, '#' || r.id::text), m.sent_at DESC, m.local_message_id DESC, r.id DESC
            )
            $BASE_SELECT
            JOIN latest ON latest.id = r.id
            ORDER BY
                CASE WHEN r.order_number ~ '^(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{3}$' THEN 0 ELSE 1 END,
                CASE WHEN r.order_number ~ '^(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{3}$'
                     THEN r.order_number::INTEGER END DESC,
                latest.sent_at DESC,
                r.id DESC
            LIMIT ?
        """
        const val SEARCH = """
            WITH candidates AS MATERIALIZED (
                SELECT id AS record_id
                FROM work_order_records
                WHERE searchable_text LIKE ?
                UNION
                SELECT record_id
                FROM work_order_vehicles
                WHERE normalized_plate LIKE ?
            ), latest AS MATERIALIZED (
                SELECT DISTINCT ON (COALESCE(r.order_number, '#' || r.id::text)) r.id
                FROM work_order_records r
                JOIN wechat_messages m ON m.id = r.message_id
                JOIN candidates c ON c.record_id = r.id
                ORDER BY COALESCE(r.order_number, '#' || r.id::text), m.sent_at DESC, m.local_message_id DESC, r.id DESC
            )
            $BASE_SELECT
            JOIN latest ON latest.id = r.id
            ORDER BY
                CASE WHEN r.order_number ~ '^(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{3}$' THEN 0 ELSE 1 END,
                CASE WHEN r.order_number ~ '^(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{3}$'
                     THEN r.order_number::INTEGER END DESC,
                m.sent_at DESC,
                r.id DESC
            LIMIT ?
        """
        const val MESSAGE_BASE_SELECT = """
            SELECT m.id AS message_id, m.business_type, m.raw_content, m.sent_at, m.sender_username,
                   m.sender_display, m.sender_group_nickname, s.source_key, s.display_name AS source_name,
                   ps.display_alias
            FROM wechat_messages m
            JOIN wechat_sources s ON s.id = m.source_id
            LEFT JOIN wechat_passage_senders ps ON ps.sender_username = m.sender_username AND ps.enabled = TRUE
        """
        const val MESSAGE_SHORT_SEARCH = """
            WITH input AS (
                SELECT ?::TEXT AS exact, ?::TEXT AS contains, ?::TEXT AS prefix
            ), attachment_matches AS MATERIALIZED (
                SELECT linked_message_id,
                       MIN(CASE WHEN UPPER(COALESCE(file_name, '')) LIKE input.prefix THEN 2 ELSE 3 END) AS match_rank
                FROM work_order_images, input
                WHERE linked_message_id IS NOT NULL AND UPPER(COALESCE(file_name, '')) LIKE input.contains
                GROUP BY linked_message_id
            )
            $MESSAGE_BASE_SELECT
            LEFT JOIN attachment_matches am ON am.linked_message_id = m.id
            CROSS JOIN input
            WHERE m.business_type IN ('PASSAGE_MESSAGE', 'GENERAL_MESSAGE')
              AND (
                m.normalized_content LIKE input.contains OR
                UPPER(COALESCE(m.sender_display, '')) LIKE input.contains OR
                UPPER(COALESCE(m.sender_group_nickname, '')) LIKE input.contains OR
                UPPER(COALESCE(ps.display_alias, '')) LIKE input.contains OR
                UPPER(s.display_name) LIKE input.contains OR
                am.linked_message_id IS NOT NULL
              )
            ORDER BY
                CASE WHEN m.normalized_content = input.exact THEN 0
                     WHEN UPPER(COALESCE(ps.display_alias, m.sender_group_nickname, m.sender_display, '')) = input.exact
                       OR UPPER(s.display_name) = input.exact THEN 1
                     WHEN m.normalized_content LIKE input.prefix
                       OR UPPER(COALESCE(m.sender_display, '')) LIKE input.prefix
                       OR UPPER(COALESCE(m.sender_group_nickname, '')) LIKE input.prefix
                       OR UPPER(COALESCE(ps.display_alias, '')) LIKE input.prefix
                       OR UPPER(s.display_name) LIKE input.prefix
                       OR am.match_rank = 2 THEN 2 ELSE 3 END,
                m.sent_at DESC, m.id DESC
            LIMIT ? OFFSET ?
        """
        const val MESSAGE_INDEXED_SEARCH = """
            WITH input AS (
                SELECT ?::TEXT AS exact, ?::TEXT AS contains, ?::TEXT AS prefix
            ), matches AS MATERIALIZED (
                SELECT message_id, MIN(match_rank) AS match_rank
                FROM (
                    SELECT m.id AS message_id,
                           CASE WHEN m.normalized_content = input.exact THEN 0
                                WHEN m.normalized_content LIKE input.prefix THEN 2 ELSE 3 END AS match_rank
                    FROM wechat_messages m, input
                    WHERE m.business_type IN ('PASSAGE_MESSAGE', 'GENERAL_MESSAGE')
                      AND m.normalized_content LIKE input.contains
                    UNION ALL
                    SELECT m.id, CASE WHEN UPPER(COALESCE(m.sender_display, '')) = input.exact THEN 1
                                      WHEN UPPER(COALESCE(m.sender_display, '')) LIKE input.prefix THEN 2 ELSE 3 END
                    FROM wechat_messages m, input
                    WHERE m.business_type IN ('PASSAGE_MESSAGE', 'GENERAL_MESSAGE')
                      AND UPPER(COALESCE(m.sender_display, '')) LIKE input.contains
                    UNION ALL
                    SELECT m.id, CASE WHEN UPPER(COALESCE(m.sender_group_nickname, '')) = input.exact THEN 1
                                      WHEN UPPER(COALESCE(m.sender_group_nickname, '')) LIKE input.prefix THEN 2 ELSE 3 END
                    FROM wechat_messages m, input
                    WHERE m.business_type IN ('PASSAGE_MESSAGE', 'GENERAL_MESSAGE')
                      AND UPPER(COALESCE(m.sender_group_nickname, '')) LIKE input.contains
                    UNION ALL
                    SELECT m.id, CASE WHEN UPPER(ps.display_alias) = input.exact THEN 1
                                      WHEN UPPER(ps.display_alias) LIKE input.prefix THEN 2 ELSE 3 END
                    FROM wechat_passage_senders ps
                    JOIN wechat_messages m ON m.sender_username = ps.sender_username
                    CROSS JOIN input
                    WHERE ps.enabled AND m.business_type IN ('PASSAGE_MESSAGE', 'GENERAL_MESSAGE')
                      AND UPPER(ps.display_alias) LIKE input.contains
                    UNION ALL
                    SELECT m.id, CASE WHEN UPPER(s.display_name) = input.exact THEN 1
                                      WHEN UPPER(s.display_name) LIKE input.prefix THEN 2 ELSE 3 END
                    FROM wechat_sources s
                    JOIN wechat_messages m ON m.source_id = s.id
                    CROSS JOIN input
                    WHERE m.business_type IN ('PASSAGE_MESSAGE', 'GENERAL_MESSAGE')
                      AND UPPER(s.display_name) LIKE input.contains
                    UNION ALL
                    SELECT a.linked_message_id,
                           CASE WHEN UPPER(COALESCE(a.file_name, '')) LIKE input.prefix THEN 2 ELSE 3 END
                    FROM work_order_images a, input
                    WHERE a.linked_message_id IS NOT NULL
                      AND UPPER(COALESCE(a.file_name, '')) LIKE input.contains
                ) candidates
                GROUP BY message_id
            )
            $MESSAGE_BASE_SELECT
            JOIN matches ON matches.message_id = m.id
            ORDER BY matches.match_rank, m.sent_at DESC, m.id DESC
            LIMIT ? OFFSET ?
        """
        val WORK_ORDER_NUMBER_PATTERN = Regex("^(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{3}$")
        const val MINIMUM_TRIGRAM_QUERY_LENGTH = 3
    }
}

private fun matchingSnippet(rawContent: String, normalizedKeyword: String): String {
    if (rawContent.length <= 240) return rawContent
    if (normalizedKeyword.isBlank()) return rawContent.take(240)
    val directIndex = rawContent.indexOf(normalizedKeyword, ignoreCase = true)
    if (directIndex < 0) return rawContent.take(240)
    val start = (directIndex - 80).coerceAtLeast(0)
    val end = (directIndex + normalizedKeyword.length + 160).coerceAtMost(rawContent.length)
    return rawContent.substring(start, end)
}

internal data class WorkOrderMessageBatch(val sourceKey: String, val sourceName: String, val messages: List<WorkOrderIncomingMessage>)
internal data class WorkOrderIncomingMessage(
    val localMessageId: String,
    val senderUsername: String?,
    val senderDisplay: String?,
    val senderGroupNickname: String?,
    val rawContent: String,
    val sentAt: Instant,
    val contentFingerprint: String,
) {
    fun validate() {
        require(localMessageId.isNotBlank() && localMessageId.length <= 128) { "微信消息标识无效" }
        require(rawContent.isNotBlank() && rawContent.length <= 100_000) { "微信消息正文无效" }
        require(contentFingerprint.matches(Regex("[0-9a-fA-F]{64}"))) { "微信消息摘要无效" }
    }
}
internal data class WorkOrderHeartbeat(val sourceKey: String, val sourceName: String, val status: String, val latestMessageAt: Instant?, val backlogCount: Int, val errorCode: String?)
internal data class WorkOrderIngestResult(val inserted: Int, val duplicate: Int, val catalogVersion: Long)
internal data class WorkOrderChangePage(val catalogVersion: Long, val nextVersion: Long, val hasMore: Boolean, val records: List<WorkOrderRecord>)
internal data class WorkOrderRecord(
    val id: Long, val orderNumber: String?, val rawPlate: String?, val normalizedPlate: String?, val vehicleType: String?,
    val declaredPeople: Int?, val rawValidTime: String?, val location: String?, val verificationMethod: String?, val reason: String?,
    val remarks: String?, val status: String, val parseQuality: String, val catalogRevision: Long, val rawContent: String,
    val sentAt: Instant, val sourceKey: String, val sourceName: String, val senderUsername: String?, val senderDisplay: String?,
    val senderGroupNickname: String?, val people: List<WorkOrderPerson>, val images: List<WorkOrderImage>,
    val vehicles: List<WorkOrderVehicle>,
)
internal data class WorkOrderPerson(val rawLine: String, val name: String?, val identityNumber: String?)
internal data class WorkOrderVehicle(val rawDescription: String, val rawPlate: String, val normalizedPlate: String, val vehicleType: String?)
internal data class WorkOrderImage(
    val id: Long, val sha256: String?, val contentType: String?, val originalSize: Long?, val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean, val availability: String, val kind: String = "IMAGE", val fileName: String? = null,
    val pageCount: Int? = null,
)
internal data class WorkOrderAttachment(
    val id: Long,
    val kind: String,
    val fileName: String?,
    val sha256: String?,
    val contentType: String?,
    val originalSize: Long?,
    val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean,
    val availability: String,
    val pageCount: Int?,
)
internal data class WechatMessageRecord(
    val id: Long,
    val businessType: String,
    val rawContent: String,
    val matchedSnippet: String,
    val sentAt: Instant,
    val sourceKey: String,
    val sourceName: String,
    val senderUsername: String?,
    val senderDisplay: String?,
    val senderGroupNickname: String?,
    val displayName: String,
    val plateNumbers: List<String>,
    val attachments: List<WorkOrderAttachment>,
)
internal data class WechatMessagePage(val records: List<WechatMessageRecord>, val nextOffset: Int?)
internal data class WechatPassageSender(val senderUsername: String, val originalDisplayName: String?, val displayAlias: String, val enabled: Boolean)
internal data class WechatSyncIssue(
    val type: String, val recordId: Long?, val imageId: Long?, val sourceName: String, val sentAt: Instant, val summary: String,
    val attachmentKind: String? = null, val fileName: String? = null, val pageCount: Int? = null,
    val candidates: List<WechatAttachmentCandidate> = emptyList(),
)
internal data class WechatAttachmentCandidate(val recordId: Long, val orderNumber: String?, val sentAt: Instant, val summary: String)
internal data class WorkOrderCorrection(
    val orderNumber: String?, val rawPlate: String?, val vehicleType: String?, val declaredPeople: Int?, val rawValidTime: String?,
    val location: String?, val verificationMethod: String?, val reason: String?, val remarks: String?, val status: String,
)
internal data class WechatSourceStatus(val sourceKey: String, val displayName: String, val status: String, val latestMessageAt: Instant?, val lastHeartbeatAt: Instant?, val lastUploadedAt: Instant?, val backlogCount: Int, val errorCode: String?)
internal data class WorkOrderImageUpload(
    val sourceKey: String, val sourceName: String, val localAttachmentId: String, val localMessageId: String?, val senderUsername: String?, val senderDisplay: String?,
    val sentAt: Instant, val sha256: String?, val originalContentType: String?, val originalSize: Long?, val originalPath: String?,
    val previewPath: String?, val previewSize: Long?, val thumbnailPath: String?, val thumbnailSize: Long?, val availability: String,
    val attachmentKind: String = "IMAGE", val fileName: String? = null, val pageCount: Int? = null,
)
internal data class StoredImageVariant(val relativePath: String, val contentType: String, val size: Long, val sha256: String?)
internal class WorkOrderPermissionException : RuntimeException("当前账号没有微信车单访问权限")
internal class WorkOrderNotFoundException : RuntimeException("微信车单不存在")

private fun ResultSet.getIntOrNull(column: String): Int? = getInt(column).takeUnless { wasNull() }
private fun ResultSet.getLongOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }

internal fun resolveWechatSenderDisplayName(vararg candidates: String?): String =
    candidates.firstOrNull { !it.isNullOrBlank() }?.trim() ?: "未知发送者"
