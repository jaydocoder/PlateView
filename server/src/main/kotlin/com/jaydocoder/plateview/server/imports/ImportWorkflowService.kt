package com.jaydocoder.plateview.server.imports

import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class ImportWorkflowService(
    private val dataSource: DataSource,
    private val parser: ExcelImportParser = ExcelImportParser(),
) {
    fun preview(fileName: String, bytes: ByteArray, actorId: Long): ImportBatchView {
        require(bytes.isNotEmpty()) { "导入文件不能为空" }
        require(bytes.size <= MAX_FILE_SIZE_BYTES) { "导入文件不能超过10MB" }
        val parsedRows = parser.parse(bytes)
        if (parsedRows.isEmpty()) throw ImportFileInvalidException("未识别到可导入的工作表或数据行")
        require(parsedRows.size <= MAX_IMPORT_ROWS) { "导入行数不能超过10000条" }

        return dataSource.inTransaction { connection ->
            val classifiedRows = classifyRows(connection, parsedRows)
            val stats = classifiedRows.toStats()
            val batchId = insertBatch(connection, fileName, sha256(bytes), stats, actorId)
            val storedRows = classifiedRows.map { row -> row.toView(insertRow(connection, batchId, row, actorId)) }
            ImportBatchView(
                id = batchId,
                sourceFileName = fileName,
                status = "VALIDATED",
                stats = stats,
                createdAt = null,
                publishedAt = null,
                rollbackAt = null,
                rowTotal = storedRows.count(ImportRowView::isReviewable),
                rows = storedRows.filter(ImportRowView::isReviewable).take(INITIAL_PREVIEW_PAGE_SIZE),
            )
        }
    }

    fun getBatch(batchId: Long, limit: Int, offset: Int, filter: ImportRowFilter = ImportRowFilter.REVIEW): ImportBatchView {
        require(limit in 1..MAX_PAGE_SIZE) { "每页记录数必须在1到500之间" }
        require(offset >= 0) { "分页偏移量不能为负数" }
        return dataSource.connection.use { connection ->
            val batch = findBatch(connection, batchId) ?: throw ImportBatchNotFoundException()
            batch.toView(
                stats = readStats(connection, batchId),
                rows = readRows(connection, batchId, limit, offset, filter),
                rowTotal = countRows(connection, batchId, filter),
            )
        }
    }

    fun getRowDetail(batchId: Long, rowId: Long): ImportRowDetailView = dataSource.connection.use { connection ->
        readRowDetail(connection, batchId, rowId) ?: throw ImportWorkflowConflictException("IMPORT_ROW_NOT_FOUND", "导入行不存在")
    }

    fun updateResolutions(batchId: Long, changes: List<ImportRowResolutionChange>, actorId: Long): ImportBatchView {
        require(changes.isNotEmpty()) { "至少需要提交一条行处置" }
        require(changes.map(ImportRowResolutionChange::rowId).distinct().size == changes.size) { "行处置不能包含重复记录" }
        dataSource.inTransaction { connection ->
            ensureStatus(lockBatch(connection, batchId), "VALIDATED")
            changes.forEach { change ->
                validateResolution(lockRow(connection, batchId, change.rowId), change.resolution)
                connection.prepareStatement("UPDATE import_rows SET resolution = ?, updated_by = ? WHERE id = ?").use { statement ->
                    statement.setString(1, change.resolution.name)
                    statement.setLong(2, actorId)
                    statement.setLong(3, change.rowId)
                    statement.executeUpdate()
                }
            }
        }
        return getBatch(batchId, MAX_PAGE_SIZE, 0)
    }

    fun publish(batchId: Long, actorId: Long): ImportBatchView {
        dataSource.inTransaction { connection ->
            val publishMode = prepareImportPublish(lockBatch(connection, batchId).status)
            ensureImportReadyToPublish(countPendingReviewRows(connection, batchId))
            val rows = publishableRows(connection, batchId)
            if (rows.isEmpty()) throw ImportWorkflowConflictException("IMPORT_NOTHING_TO_PUBLISH", "当前批次没有可发布的数据")
            if (publishMode == ImportPublishMode.REPUBLISH) {
                clearRollbackEffects(connection, batchId)
                restoreBatchForRepublish(connection, batchId, actorId)
            }
            rows.forEach { row ->
                val vehicleId = when (row.plannedAction) {
                    ImportPlannedAction.CREATE -> publishCreate(connection, batchId, row, actorId)
                    ImportPlannedAction.UPDATE -> publishUpdate(connection, batchId, row, actorId)
                    ImportPlannedAction.DEACTIVATE -> publishDeactivate(connection, batchId, row, actorId)
                    ImportPlannedAction.REACTIVATE -> publishReactivate(connection, batchId, row, actorId)
                    ImportPlannedAction.SKIP, ImportPlannedAction.NONE -> throw ImportWorkflowConflictException("IMPORT_ROW_NOT_PUBLISHABLE", "导入行不允许发布")
                }
                connection.prepareStatement(
                    "UPDATE import_rows SET result_status = 'PUBLISHED', published_vehicle_id = ?, vehicle_id = ?, updated_by = ? WHERE id = ?",
                ).use { statement ->
                    statement.setLong(1, vehicleId)
                    statement.setLong(2, vehicleId)
                    statement.setLong(3, actorId)
                    statement.setLong(4, row.id)
                    statement.executeUpdate()
                }
            }
            connection.prepareStatement(
                "UPDATE import_batches SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP, published_by = ?, updated_by = ?, version = version + 1 WHERE id = ?",
            ).use { statement ->
                statement.setLong(1, actorId)
                statement.setLong(2, actorId)
                statement.setLong(3, batchId)
                statement.executeUpdate()
            }
        }
        return getBatch(batchId, MAX_PAGE_SIZE, 0)
    }

    fun rollback(batchId: Long, actorId: Long): ImportBatchView {
        dataSource.inTransaction { connection ->
            ensureStatus(lockBatch(connection, batchId), "PUBLISHED")
            val effects = loadEffectsForRollback(connection, batchId)
            if (effects.isEmpty()) throw ImportWorkflowConflictException("IMPORT_ROLLBACK_UNAVAILABLE", "当前批次没有可回滚的数据")
            effects.forEach { effect -> validateRollbackEffect(connection, batchId, effect) }
            effects.forEach { effect -> rollbackEffect(connection, effect, actorId) }
            connection.prepareStatement(
                "UPDATE import_batches SET status = 'ROLLED_BACK', rollback_at = CURRENT_TIMESTAMP, rollback_by = ?, updated_by = ?, version = version + 1 WHERE id = ?",
            ).use { statement ->
                statement.setLong(1, actorId)
                statement.setLong(2, actorId)
                statement.setLong(3, batchId)
                statement.executeUpdate()
            }
        }
        return getBatch(batchId, MAX_PAGE_SIZE, 0)
    }

    private fun classifyRows(connection: Connection, parsedRows: List<ParsedImportRow>): List<ParsedImportRow> {
        val normalizedPlates = parsedRows.mapNotNull { it.vehicle.normalizedPlate }.distinct()
        val existingVehicles = findExistingVehicles(connection, normalizedPlates)
        val handledPlates = mutableSetOf<String>()
        val classifiedRows = parsedRows.map { row ->
            val normalizedPlate = row.vehicle.normalizedPlate
            if (row.resultStatus == ImportResultStatus.ERROR || normalizedPlate == null) return@map row
            if (!handledPlates.add(normalizedPlate)) {
                return@map row.copy(
                    resultStatus = ImportResultStatus.DUPLICATE,
                    plannedAction = ImportPlannedAction.SKIP,
                    resolution = ImportResolution.SKIP,
                    warningMessage = appendMessage(row.warningMessage, "与同一批次的前序车牌重复，默认跳过"),
                )
            }
            val candidates = existingVehicles[normalizedPlate].orEmpty()
            val activeVehicle = candidates.singleOrNull { it.status == "ACTIVE" }
            if (activeVehicle != null) {
                return@map classifyExistingVehicle(row, activeVehicle, ImportPlannedAction.UPDATE)
            }
            val inactiveVehicles = candidates.filter { it.status == "INACTIVE" }
            when (inactiveVehicles.size) {
                0 -> row
                1 -> classifyExistingVehicle(row, inactiveVehicles.single(), ImportPlannedAction.REACTIVATE)
                else -> row.copy(
                    resultStatus = ImportResultStatus.ERROR,
                    plannedAction = ImportPlannedAction.NONE,
                    resolution = ImportResolution.ERROR,
                    errorMessage = appendMessage(row.errorMessage, "存在多条同车牌失效历史档案，无法自动恢复"),
                )
            }
        }
        val coveredCategories = parsedRows.mapNotNull { it.vehicle.category }.toSet()
        val presentPlates = parsedRows.mapNotNull { it.vehicle.normalizedPlate }.toSet()
        val missingRows = findActiveVehiclesMissingFromImport(connection, coveredCategories, presentPlates).mapIndexed { diffIndex, existing ->
            val source = systemDiffSourceIdentity(diffIndex)
            val vehicle = existing.toParsedVehicle()
            ParsedImportRow(
                sourceSheetName = source.sheetName,
                sourceRowNumber = source.rowNumber,
                sourceItemIndex = source.itemIndex,
                rawValues = JsonObject(emptyMap()),
                vehicle = vehicle,
                resultStatus = ImportResultStatus.VALID,
                plannedAction = ImportPlannedAction.DEACTIVATE,
                resolution = ImportResolution.PENDING,
                warningMessage = "本次导入的${existing.category.displayName}数据中未出现该车牌，请确认是否失效",
                beforeValues = existing.toComparisonValues(),
            )
        }
        return classifiedRows + missingRows
    }

    private fun classifyExistingVehicle(
        row: ParsedImportRow,
        existing: ExistingVehicle,
        action: ImportPlannedAction,
    ): ParsedImportRow {
        val sourceVehicle = row.vehicle.copy(sourceVehicleId = existing.id, sourceVehicleVersion = existing.version)
        return if (existing.hasSameContent(sourceVehicle) && action == ImportPlannedAction.UPDATE) {
            row.copy(
                vehicle = sourceVehicle,
                resultStatus = ImportResultStatus.DUPLICATE,
                plannedAction = ImportPlannedAction.SKIP,
                resolution = ImportResolution.SKIP,
                warningMessage = appendMessage(row.warningMessage, "正式库已存在相同数据，默认跳过"),
                beforeValues = existing.toComparisonValues(),
            )
        } else {
            row.copy(
                vehicle = sourceVehicle,
                resultStatus = ImportResultStatus.DUPLICATE,
                plannedAction = action,
                resolution = ImportResolution.PENDING,
                warningMessage = appendMessage(
                    row.warningMessage,
                    if (action == ImportPlannedAction.REACTIVATE) "正式库存在失效档案，请确认恢复有效" else "正式库存在同车牌数据，请确认是否更新",
                ),
                beforeValues = existing.toComparisonValues(),
            )
        }
    }

    private fun insertBatch(
        connection: Connection,
        fileName: String,
        checksum: String,
        stats: ImportBatchStats,
        actorId: Long,
    ): Long = connection.prepareStatement(
        """
        INSERT INTO import_batches (
            source_file_name, source_checksum, status, total_rows, valid_rows, duplicate_rows, error_rows, created_by, updated_by
        ) VALUES (?, ?, 'VALIDATED', ?, ?, ?, ?, ?, ?)
        RETURNING id
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, fileName.take(255))
        statement.setString(2, checksum)
        statement.setInt(3, stats.totalRows)
        statement.setInt(4, stats.newRows)
        statement.setInt(5, stats.duplicateRows)
        statement.setInt(6, stats.errorRows)
        statement.setLong(7, actorId)
        statement.setLong(8, actorId)
        statement.executeQuery().use { result -> result.next(); result.getLong(1) }
    }

    private fun insertRow(connection: Connection, batchId: Long, row: ParsedImportRow, actorId: Long): Long = connection.prepareStatement(
        """
        INSERT INTO import_rows (
            import_batch_id, source_sheet_name, source_row_number, source_item_index,
            raw_values, parsed_values, result_status, planned_action, resolution,
            error_message, warning_message, before_values, created_by, updated_by
        ) VALUES (?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?)
        RETURNING id
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.setString(2, row.sourceSheetName)
        statement.setInt(3, row.sourceRowNumber)
        statement.setInt(4, row.sourceItemIndex)
        statement.setString(5, Json.encodeToString(row.rawValues))
        statement.setString(6, Json.encodeToString(row.vehicle.toJson()))
        statement.setString(7, row.resultStatus.name)
        statement.setString(8, row.plannedAction.name)
        statement.setString(9, row.resolution.name)
        statement.setString(10, row.errorMessage)
        statement.setString(11, row.warningMessage)
        statement.setString(12, row.beforeValues?.let { Json.encodeToString(it) })
        statement.setLong(13, actorId)
        statement.setLong(14, actorId)
        statement.executeQuery().use { result -> result.next(); result.getLong(1) }
    }

    private fun readRows(
        connection: Connection,
        batchId: Long,
        limit: Int,
        offset: Int,
        filter: ImportRowFilter,
    ): List<ImportRowView> = connection.prepareStatement(
        """
        SELECT id, source_sheet_name, source_row_number, source_item_index, parsed_values::text,
               result_status, planned_action, resolution, error_message, warning_message
        FROM import_rows
        WHERE import_batch_id = ? AND ${filter.sqlCondition}
        ORDER BY CASE WHEN source_sheet_name = '$SYSTEM_DIFF_SOURCE' THEN 1 ELSE 0 END,
                 source_sheet_name, source_row_number, source_item_index, id
        LIMIT ? OFFSET ?
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.setInt(2, limit)
        statement.setInt(3, offset)
        statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toRowView()) } }
    }

    private fun countRows(connection: Connection, batchId: Long, filter: ImportRowFilter): Int = connection.prepareStatement(
        "SELECT COUNT(*) FROM import_rows WHERE import_batch_id = ? AND ${filter.sqlCondition}",
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.executeQuery().use { result -> result.next(); result.getInt(1) }
    }

    private fun countPendingReviewRows(connection: Connection, batchId: Long): Int = connection.prepareStatement(
        "SELECT COUNT(*) FROM import_rows WHERE import_batch_id = ? AND resolution = 'PENDING'",
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.executeQuery().use { result -> result.next(); result.getInt(1) }
    }

    private fun readRowDetail(connection: Connection, batchId: Long, rowId: Long): ImportRowDetailView? = connection.prepareStatement(
        """
        SELECT id, source_sheet_name, source_row_number, source_item_index, raw_values::text, parsed_values::text,
               before_values::text, result_status, planned_action, resolution, error_message, warning_message
        FROM import_rows WHERE import_batch_id = ? AND id = ?
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.setLong(2, rowId)
        statement.executeQuery().use { result ->
            if (!result.next()) return@use null
            val vehicle = ParsedVehicle.fromJson(result.getString("parsed_values"))
            val row = result.toRowView()
            val action = ImportPlannedAction.valueOf(row.plannedAction)
            val beforeValues = result.getString("before_values")
                ?.let { Json.parseToJsonElement(it).jsonObject }
                ?: JsonObject(emptyMap())
            val afterValues = vehicle.toComparisonValues(
                status = if (action == ImportPlannedAction.DEACTIVATE) "INACTIVE" else "ACTIVE",
            )
            ImportRowDetailView(
                row = row,
                sections = buildDiffSections(beforeValues, afterValues),
                sourceValues = Json.parseToJsonElement(result.getString("raw_values")).jsonObject
                    .entries
                    .sortedBy { it.key }
                    .mapNotNull { (label, value) -> value.jsonPrimitive.content.takeIf(String::isNotBlank)?.let { ImportSourceValue(label, it) } },
            )
        }
    }

    private fun buildDiffSections(before: JsonObject, after: JsonObject): List<ImportDiffSection> = buildList {
        addSection("车辆信息", before, after, VEHICLE_FIELDS)
        addSection("状态变化", before, after, STATUS_FIELDS)
        addSection("身份或单位信息", before, after, PROFILE_FIELDS)
        addAttributeSection(before, after)
    }

    private fun MutableList<ImportDiffSection>.addSection(
        title: String,
        before: JsonObject,
        after: JsonObject,
        fields: List<ImportComparisonField>,
    ) {
        val differences = fields.mapNotNull { field ->
            val oldValue = before.stringOrNull(field.key)
            val newValue = after.stringOrNull(field.key)
            if (oldValue == newValue) null else ImportFieldDifference(field.label, oldValue, newValue)
        }
        if (differences.isNotEmpty()) add(ImportDiffSection(title, differences))
    }

    private fun MutableList<ImportDiffSection>.addAttributeSection(before: JsonObject, after: JsonObject) {
        val beforeAttributes = before["attributes"]?.jsonObject ?: JsonObject(emptyMap())
        val afterAttributes = after["attributes"]?.jsonObject ?: JsonObject(emptyMap())
        val fields = (beforeAttributes.keys + afterAttributes.keys)
            .sorted()
            .map { key -> ImportComparisonField(key, ATTRIBUTE_LABELS[key] ?: key) }
        val differences = fields.mapNotNull { field ->
            val oldValue = beforeAttributes.stringOrNull(field.key)
            val newValue = afterAttributes.stringOrNull(field.key)
            if (oldValue == newValue) null else ImportFieldDifference(field.label, oldValue, newValue)
        }
        if (differences.isNotEmpty()) add(ImportDiffSection("扩展字段", differences))
    }

    private fun readStats(connection: Connection, batchId: Long): ImportBatchStats = connection.prepareStatement(
        """
        SELECT COUNT(*) AS total_rows,
               COUNT(*) FILTER (WHERE planned_action = 'CREATE') AS new_rows,
               COUNT(*) FILTER (WHERE planned_action = 'UPDATE') AS update_rows,
               COUNT(*) FILTER (WHERE planned_action = 'REACTIVATE') AS reactivate_rows,
               COUNT(*) FILTER (WHERE planned_action = 'DEACTIVATE') AS deactivate_rows,
               COUNT(*) FILTER (WHERE planned_action = 'SKIP') AS duplicate_rows,
               COUNT(*) FILTER (WHERE result_status = 'ERROR') AS error_rows,
               COUNT(*) FILTER (WHERE warning_message IS NOT NULL) AS warning_rows,
               COUNT(*) FILTER (WHERE resolution = 'PUBLISH' AND result_status IN ('VALID', 'DUPLICATE', 'PUBLISHED')) AS publishable_rows,
               COUNT(*) FILTER (WHERE resolution = 'PENDING') AS pending_review_rows
        FROM import_rows WHERE import_batch_id = ?
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.executeQuery().use { result -> result.next(); result.toStats() }
    }

    private fun publishableRows(connection: Connection, batchId: Long): List<StoredImportRow> = connection.prepareStatement(
        """
        SELECT id, result_status, planned_action, resolution, parsed_values::text
        FROM import_rows WHERE import_batch_id = ? AND resolution = 'PUBLISH'
        ORDER BY id FOR UPDATE
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    val status = ImportResultStatus.valueOf(result.getString("result_status"))
                    if (status !in setOf(ImportResultStatus.VALID, ImportResultStatus.DUPLICATE)) {
                        throw ImportWorkflowConflictException("IMPORT_ROW_NOT_PUBLISHABLE", "导入行状态不允许发布")
                    }
                    add(
                        StoredImportRow(
                            id = result.getLong("id"),
                            resultStatus = status,
                            plannedAction = ImportPlannedAction.valueOf(result.getString("planned_action")),
                            resolution = ImportResolution.valueOf(result.getString("resolution")),
                            vehicle = ParsedVehicle.fromJson(result.getString("parsed_values")),
                        ),
                    )
                }
            }
        }
    }

    private fun publishCreate(connection: Connection, batchId: Long, row: StoredImportRow, actorId: Long): Long {
        val vehicle = ensureVehiclePublishable(row.vehicle)
        if (findVehicleForUpdate(connection, vehicle.normalizedPlate!!) != null) {
            throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "正式数据已变更，请重新上传并预览")
        }
        val vehicleId = insertVehicle(connection, vehicle, batchId, actorId)
        replaceProfile(connection, vehicleId, vehicle, actorId)
        insertEffect(connection, batchId, row.id, vehicleId, "CREATED", 0, null, null, null)
        return vehicleId
    }

    private fun publishUpdate(connection: Connection, batchId: Long, row: StoredImportRow, actorId: Long): Long {
        val vehicle = ensureVehiclePublishable(row.vehicle)
        val previous = vehicle.sourceVehicleId?.let { findVehicleForUpdate(connection, vehicle.normalizedPlate!!, it) }
            ?: throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "正式数据已变更，请重新上传并预览")
        if (previous.version != vehicle.sourceVehicleVersion) {
            throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "正式数据已变更，请重新上传并预览")
        }
        updateVehicle(connection, previous.id, vehicle, actorId)
        replaceProfile(connection, previous.id, vehicle, actorId)
        insertEffect(
            connection,
            batchId,
            row.id,
            previous.id,
            "UPDATED",
            previous.version + 1,
            previous.toVehicleSnapshot(),
            previous.residentProfile?.toJson(),
            previous.longTermProfile?.toJson(),
        )
        return previous.id
    }

    private fun publishDeactivate(connection: Connection, batchId: Long, row: StoredImportRow, actorId: Long): Long {
        val vehicle = row.vehicle
        val normalizedPlate = vehicle.normalizedPlate
            ?: throw ImportWorkflowConflictException("IMPORT_ROW_INVALID", "待失效记录缺少车牌号")
        val previous = vehicle.sourceVehicleId?.let { findVehicleForUpdate(connection, normalizedPlate, it) }
            ?: throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "正式数据已变更，请重新上传并预览")
        if (previous.version != vehicle.sourceVehicleVersion) {
            throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "正式数据已变更，请重新上传并预览")
        }
        connection.prepareStatement(
            "UPDATE vehicles SET status = 'INACTIVE', version = version + 1, updated_by = ? WHERE id = ? AND version = ?",
        ).use { statement ->
            statement.setLong(1, actorId)
            statement.setLong(2, previous.id)
            statement.setInt(3, previous.version)
            if (statement.executeUpdate() != 1) throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "正式数据已变更，请重新上传并预览")
        }
        insertEffect(
            connection,
            batchId,
            row.id,
            previous.id,
            "DEACTIVATED",
            previous.version + 1,
            previous.toVehicleSnapshot(),
            previous.residentProfile?.toJson(),
            previous.longTermProfile?.toJson(),
        )
        return previous.id
    }

    private fun publishReactivate(connection: Connection, batchId: Long, row: StoredImportRow, actorId: Long): Long {
        val vehicle = ensureVehiclePublishable(row.vehicle)
        val previous = vehicle.sourceVehicleId?.let {
            findVehicleForUpdate(connection, vehicle.normalizedPlate!!, it, expectedStatus = "INACTIVE")
        } ?: throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "正式数据已变更，请重新上传并预览")
        if (previous.version != vehicle.sourceVehicleVersion) {
            throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "正式数据已变更，请重新上传并预览")
        }
        if (findVehicleForUpdate(connection, vehicle.normalizedPlate!!, expectedStatus = "ACTIVE") != null) {
            throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "该车牌已有有效档案，请重新上传并预览")
        }
        updateVehicle(connection, previous.id, vehicle, actorId, status = "ACTIVE")
        replaceProfile(connection, previous.id, vehicle, actorId)
        insertEffect(
            connection,
            batchId,
            row.id,
            previous.id,
            "REACTIVATED",
            previous.version + 1,
            previous.toVehicleSnapshot(),
            previous.residentProfile?.toJson(),
            previous.longTermProfile?.toJson(),
        )
        return previous.id
    }

    private fun insertVehicle(connection: Connection, vehicle: ParsedVehicle, batchId: Long, actorId: Long): Long = connection.prepareStatement(
        """
        INSERT INTO vehicles (
            plate_number, normalized_plate, category, vehicle_type, status, import_batch_id, attributes, created_by, updated_by
        ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, CAST(? AS JSONB), ?, ?)
        RETURNING id
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, vehicle.originalPlate)
        statement.setString(2, vehicle.normalizedPlate)
        statement.setString(3, vehicle.category!!.name)
        statement.setString(4, vehicle.vehicleType)
        statement.setLong(5, batchId)
        statement.setString(6, Json.encodeToString(vehicle.attributes))
        statement.setLong(7, actorId)
        statement.setLong(8, actorId)
        statement.executeQuery().use { result -> result.next(); result.getLong(1) }
    }

    private fun updateVehicle(
        connection: Connection,
        vehicleId: Long,
        vehicle: ParsedVehicle,
        actorId: Long,
        status: String? = null,
    ) {
        connection.prepareStatement(
            """
            UPDATE vehicles
            SET plate_number = ?, normalized_plate = ?, category = ?, vehicle_type = ?, attributes = CAST(? AS JSONB),
                status = COALESCE(?, status), updated_by = ?, version = version + 1
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, vehicle.originalPlate)
            statement.setString(2, vehicle.normalizedPlate)
            statement.setString(3, vehicle.category!!.name)
            statement.setString(4, vehicle.vehicleType)
            statement.setString(5, Json.encodeToString(vehicle.attributes))
            statement.setString(6, status)
            statement.setLong(7, actorId)
            statement.setLong(8, vehicleId)
            statement.executeUpdate()
        }
    }

    private fun replaceProfile(connection: Connection, vehicleId: Long, vehicle: ParsedVehicle, actorId: Long) {
        connection.prepareStatement("DELETE FROM resident_profiles WHERE vehicle_id = ?").use { statement ->
            statement.setLong(1, vehicleId)
            statement.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM long_term_profiles WHERE vehicle_id = ?").use { statement ->
            statement.setLong(1, vehicleId)
            statement.executeUpdate()
        }
        if (vehicle.category == ImportCategory.RESIDENT) insertResidentProfile(connection, vehicleId, vehicle, actorId)
        else insertLongTermProfile(connection, vehicleId, vehicle, actorId)
    }

    private fun insertResidentProfile(
        connection: Connection,
        vehicleId: Long,
        vehicle: ParsedVehicle,
        actorId: Long,
        version: Int = 0,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO resident_profiles (
                vehicle_id, owner_name, identity_card_number, contact_phone, remarks, version, created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, vehicleId)
            statement.setString(2, vehicle.ownerName)
            statement.setString(3, vehicle.identityCardNumber)
            statement.setString(4, vehicle.contactPhone)
            statement.setString(5, vehicle.remarks)
            statement.setInt(6, version)
            statement.setLong(7, actorId)
            statement.setLong(8, actorId)
            statement.executeUpdate()
        }
    }

    private fun insertLongTermProfile(
        connection: Connection,
        vehicleId: Long,
        vehicle: ParsedVehicle,
        actorId: Long,
        version: Int = 0,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO long_term_profiles (
                vehicle_id, organization_name, pass_holder, passage_details, remarks, version, created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, vehicleId)
            statement.setString(2, vehicle.organizationName)
            statement.setString(3, vehicle.passHolder)
            statement.setString(4, vehicle.passageDetails)
            statement.setString(5, vehicle.remarks)
            statement.setInt(6, version)
            statement.setLong(7, actorId)
            statement.setLong(8, actorId)
            statement.executeUpdate()
        }
    }

    private fun insertEffect(
        connection: Connection,
        batchId: Long,
        rowId: Long,
        vehicleId: Long,
        action: String,
        appliedVersion: Int,
        previousVehicle: JsonObject?,
        previousResident: JsonObject?,
        previousLongTerm: JsonObject?,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO import_effects (
                import_batch_id, import_row_id, vehicle_id, action, applied_version,
                previous_vehicle, previous_resident_profile, previous_long_term_profile
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB))
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, batchId)
            statement.setLong(2, rowId)
            statement.setLong(3, vehicleId)
            statement.setString(4, action)
            statement.setInt(5, appliedVersion)
            statement.setString(6, previousVehicle?.let { Json.encodeToString(it) })
            statement.setString(7, previousResident?.let { Json.encodeToString(it) })
            statement.setString(8, previousLongTerm?.let { Json.encodeToString(it) })
            statement.executeUpdate()
        }
    }

    private fun findExistingVehicles(connection: Connection, plates: List<String>): Map<String, List<ExistingVehicle>> {
        if (plates.isEmpty()) return emptyMap()
        val placeholders = plates.joinToString(",") { "?" }
        return connection.prepareStatement(
            """
            SELECT v.id, v.plate_number, v.normalized_plate, v.category, v.vehicle_type, v.status, v.import_batch_id,
                   v.attributes::text, v.version,
                   rp.id AS resident_profile_id, rp.owner_name, rp.identity_card_number, rp.contact_phone,
                   rp.remarks AS resident_remarks, rp.version AS resident_version,
                   lp.id AS long_term_profile_id, lp.organization_name, lp.pass_holder, lp.passage_details,
                   lp.remarks AS long_term_remarks, lp.version AS long_term_version
            FROM vehicles v
            LEFT JOIN resident_profiles rp ON rp.vehicle_id = v.id
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.normalized_plate IN ($placeholders)
            ORDER BY v.normalized_plate, CASE v.status WHEN 'ACTIVE' THEN 0 ELSE 1 END, v.id
            """.trimIndent(),
        ).use { statement ->
            plates.forEachIndexed { index, plate -> statement.setString(index + 1, plate) }
            statement.executeQuery().use { result ->
                val vehicles = linkedMapOf<String, MutableList<ExistingVehicle>>()
                while (result.next()) {
                    val vehicle = result.toExistingVehicle()
                    vehicles.getOrPut(vehicle.normalizedPlate) { mutableListOf() } += vehicle
                }
                vehicles
            }
        }
    }

    private fun findActiveVehiclesMissingFromImport(
        connection: Connection,
        categories: Set<ImportCategory>,
        presentPlates: Set<String>,
    ): List<ExistingVehicle> {
        if (categories.isEmpty()) return emptyList()
        val categoryPlaceholders = categories.joinToString(",") { "?" }
        val plateClause = if (presentPlates.isEmpty()) "" else "AND v.normalized_plate NOT IN (${presentPlates.joinToString(",") { "?" }})"
        return connection.prepareStatement(
            """
            SELECT v.id, v.plate_number, v.normalized_plate, v.category, v.vehicle_type, v.status, v.import_batch_id,
                   v.attributes::text, v.version,
                   rp.id AS resident_profile_id, rp.owner_name, rp.identity_card_number, rp.contact_phone,
                   rp.remarks AS resident_remarks, rp.version AS resident_version,
                   lp.id AS long_term_profile_id, lp.organization_name, lp.pass_holder, lp.passage_details,
                   lp.remarks AS long_term_remarks, lp.version AS long_term_version
            FROM vehicles v
            LEFT JOIN resident_profiles rp ON rp.vehicle_id = v.id
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.status = 'ACTIVE' AND v.category IN ($categoryPlaceholders) $plateClause
            ORDER BY v.category, v.normalized_plate, v.id
            """.trimIndent(),
        ).use { statement ->
            var index = 1
            categories.sortedBy(ImportCategory::name).forEach { statement.setString(index++, it.name) }
            presentPlates.sorted().forEach { statement.setString(index++, it) }
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toExistingVehicle()) } }
        }
    }

    private fun findVehicleForUpdate(
        connection: Connection,
        normalizedPlate: String,
        expectedId: Long? = null,
        expectedStatus: String = "ACTIVE",
    ): ExistingVehicle? = connection.prepareStatement(
        """
        SELECT v.id, v.plate_number, v.normalized_plate, v.category, v.vehicle_type, v.status, v.import_batch_id,
               v.attributes::text, v.version,
               rp.id AS resident_profile_id, rp.owner_name, rp.identity_card_number, rp.contact_phone,
               rp.remarks AS resident_remarks, rp.version AS resident_version,
               lp.id AS long_term_profile_id, lp.organization_name, lp.pass_holder, lp.passage_details,
               lp.remarks AS long_term_remarks, lp.version AS long_term_version
        FROM vehicles v
        LEFT JOIN resident_profiles rp ON rp.vehicle_id = v.id
        LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
        WHERE v.status = ? AND v.normalized_plate = ?
        FOR UPDATE OF v
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, expectedStatus)
        statement.setString(2, normalizedPlate)
        statement.executeQuery().use { result ->
            if (!result.next()) null else result.toExistingVehicle().also {
                if (expectedId != null && it.id != expectedId) {
                    throw ImportWorkflowConflictException("IMPORT_SOURCE_CHANGED", "正式数据已变更，请重新上传并预览")
                }
            }
        }
    }

    private fun lockBatch(connection: Connection, batchId: Long): BatchRecord = connection.prepareStatement(
        "SELECT id, source_file_name, status, created_at, published_at, rollback_at FROM import_batches WHERE id = ? FOR UPDATE",
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.executeQuery().use { result -> if (result.next()) result.toBatchRecord() else throw ImportBatchNotFoundException() }
    }

    private fun findBatch(connection: Connection, batchId: Long): BatchRecord? = connection.prepareStatement(
        "SELECT id, source_file_name, status, created_at, published_at, rollback_at FROM import_batches WHERE id = ?",
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.executeQuery().use { result -> if (result.next()) result.toBatchRecord() else null }
    }

    private fun lockRow(connection: Connection, batchId: Long, rowId: Long): StoredImportRow = connection.prepareStatement(
        """
        SELECT id, result_status, planned_action, resolution, parsed_values::text
        FROM import_rows WHERE import_batch_id = ? AND id = ? FOR UPDATE
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.setLong(2, rowId)
        statement.executeQuery().use { result ->
            if (!result.next()) throw ImportWorkflowConflictException("IMPORT_ROW_NOT_FOUND", "导入行不存在")
            StoredImportRow(
                id = result.getLong("id"),
                resultStatus = ImportResultStatus.valueOf(result.getString("result_status")),
                plannedAction = ImportPlannedAction.valueOf(result.getString("planned_action")),
                resolution = ImportResolution.valueOf(result.getString("resolution")),
                vehicle = ParsedVehicle.fromJson(result.getString("parsed_values")),
            )
        }
    }

    private fun validateResolution(row: StoredImportRow, resolution: ImportResolution) {
        if (resolution !in setOf(ImportResolution.PUBLISH, ImportResolution.SKIP)) {
            throw ImportWorkflowConflictException("IMPORT_RESOLUTION_INVALID", "导入行只能设置为发布或跳过")
        }
        if (row.resultStatus == ImportResultStatus.ERROR && resolution == ImportResolution.PUBLISH) {
            throw ImportWorkflowConflictException("IMPORT_RESOLUTION_INVALID", "异常行不能发布")
        }
        if (row.plannedAction in setOf(ImportPlannedAction.SKIP, ImportPlannedAction.NONE) && resolution == ImportResolution.PUBLISH) {
            throw ImportWorkflowConflictException("IMPORT_RESOLUTION_INVALID", "重复或异常行不能发布")
        }
    }

    private fun loadEffectsForRollback(connection: Connection, batchId: Long): List<ImportEffect> = connection.prepareStatement(
        """
        SELECT import_row_id, vehicle_id, action, applied_version,
               previous_vehicle::text, previous_resident_profile::text, previous_long_term_profile::text
        FROM import_effects WHERE import_batch_id = ? ORDER BY id DESC FOR UPDATE
        """.trimIndent(),
    ).use { statement ->
        statement.setLong(1, batchId)
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) add(
                    ImportEffect(
                        importRowId = result.getLong("import_row_id"),
                        vehicleId = result.getLong("vehicle_id"),
                        action = result.getString("action"),
                        appliedVersion = result.getInt("applied_version"),
                        previousVehicle = result.getString("previous_vehicle"),
                        previousResident = result.getString("previous_resident_profile"),
                        previousLongTerm = result.getString("previous_long_term_profile"),
                    ),
                )
            }
        }
    }

    private fun clearRollbackEffects(connection: Connection, batchId: Long) {
        connection.prepareStatement("DELETE FROM import_effects WHERE import_batch_id = ?").use { statement ->
            statement.setLong(1, batchId)
            statement.executeUpdate()
        }
    }

    private fun restoreBatchForRepublish(connection: Connection, batchId: Long, actorId: Long) {
        connection.prepareStatement(
            """
            UPDATE import_batches
            SET status = 'VALIDATED', published_at = NULL, published_by = NULL,
                rollback_at = NULL, rollback_by = NULL, updated_by = ?, version = version + 1
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, actorId)
            statement.setLong(2, batchId)
            statement.executeUpdate()
        }
    }

    private fun validateRollbackEffect(connection: Connection, batchId: Long, effect: ImportEffect) {
        connection.prepareStatement("SELECT version, import_batch_id FROM vehicles WHERE id = ? FOR UPDATE").use { statement ->
            statement.setLong(1, effect.vehicleId)
            statement.executeQuery().use { result ->
                if (!result.next()) throw ImportWorkflowConflictException("IMPORT_ROLLBACK_CONFLICT", "导入后的车辆已不存在，无法安全回滚")
                if (result.getInt("version") != effect.appliedVersion) {
                    throw ImportWorkflowConflictException("IMPORT_ROLLBACK_CONFLICT", "导入后车辆已被修改，无法安全回滚")
                }
                val sourceBatchId = result.getObject("import_batch_id") as? Number
                if (effect.action == "CREATED" && sourceBatchId?.toLong() != batchId) {
                    throw ImportWorkflowConflictException("IMPORT_ROLLBACK_CONFLICT", "车辆已被其他数据批次接管，无法安全回滚")
                }
            }
        }
    }

    private fun rollbackEffect(connection: Connection, effect: ImportEffect, actorId: Long) {
        if (effect.action == "CREATED") {
            resetPublishedRow(connection, effect.importRowId, "VALID", actorId)
            connection.prepareStatement("DELETE FROM vehicles WHERE id = ?").use { statement ->
                statement.setLong(1, effect.vehicleId)
                statement.executeUpdate()
            }
            return
        }
        restoreUpdatedVehicle(connection, effect, actorId)
        resetPublishedRow(connection, effect.importRowId, "DUPLICATE", actorId)
    }

    private fun resetPublishedRow(connection: Connection, rowId: Long, resultStatus: String, actorId: Long) {
        connection.prepareStatement(
            "UPDATE import_rows SET published_vehicle_id = NULL, vehicle_id = NULL, result_status = ?, updated_by = ? WHERE id = ?",
        ).use { statement ->
            statement.setString(1, resultStatus)
            statement.setLong(2, actorId)
            statement.setLong(3, rowId)
            statement.executeUpdate()
        }
    }

    private fun restoreUpdatedVehicle(connection: Connection, effect: ImportEffect, actorId: Long) {
        val vehicle = effect.previousVehicle?.let { Json.parseToJsonElement(it).jsonObject }
            ?: throw ImportWorkflowConflictException("IMPORT_ROLLBACK_CONFLICT", "缺少更新前车辆快照")
        connection.prepareStatement(
            """
            UPDATE vehicles
            SET plate_number = ?, normalized_plate = ?, category = ?, vehicle_type = ?, status = ?, import_batch_id = ?,
                attributes = CAST(? AS JSONB), version = ?, updated_by = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, vehicle.stringOrNull("plateNumber"))
            statement.setString(2, vehicle.stringOrNull("normalizedPlate"))
            statement.setString(3, vehicle.stringOrNull("category"))
            statement.setString(4, vehicle.stringOrNull("vehicleType"))
            statement.setString(5, vehicle.stringOrNull("status"))
            statement.setNullableLong(6, vehicle.longOrNull("importBatchId"))
            statement.setString(7, Json.encodeToString(vehicle["attributes"]!!.jsonObject))
            statement.setInt(8, vehicle.intOrNull("version")!!)
            statement.setLong(9, actorId)
            statement.setLong(10, effect.vehicleId)
            statement.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM resident_profiles WHERE vehicle_id = ?").use { statement ->
            statement.setLong(1, effect.vehicleId)
            statement.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM long_term_profiles WHERE vehicle_id = ?").use { statement ->
            statement.setLong(1, effect.vehicleId)
            statement.executeUpdate()
        }
        effect.previousResident?.let { restoreResidentProfile(connection, effect.vehicleId, Json.parseToJsonElement(it).jsonObject, actorId) }
        effect.previousLongTerm?.let { restoreLongTermProfile(connection, effect.vehicleId, Json.parseToJsonElement(it).jsonObject, actorId) }
    }

    private fun restoreResidentProfile(connection: Connection, vehicleId: Long, profile: JsonObject, actorId: Long) {
        val vehicle = ParsedVehicle(
            originalPlate = null,
            normalizedPlate = null,
            category = ImportCategory.RESIDENT,
            vehicleType = null,
            ownerName = profile.stringOrNull("ownerName"),
            identityCardNumber = profile.stringOrNull("identityCardNumber"),
            contactPhone = profile.stringOrNull("contactPhone"),
            organizationName = null,
            passHolder = null,
            passageDetails = null,
            remarks = profile.stringOrNull("remarks"),
            attributes = JsonObject(emptyMap()),
        )
        insertResidentProfile(connection, vehicleId, vehicle, actorId, profile.intOrNull("version") ?: 0)
    }

    private fun restoreLongTermProfile(connection: Connection, vehicleId: Long, profile: JsonObject, actorId: Long) {
        val vehicle = ParsedVehicle(
            originalPlate = null,
            normalizedPlate = null,
            category = ImportCategory.SCENIC_UNIT,
            vehicleType = null,
            ownerName = null,
            identityCardNumber = null,
            contactPhone = null,
            organizationName = profile.stringOrNull("organizationName"),
            passHolder = profile.stringOrNull("passHolder"),
            passageDetails = profile.stringOrNull("passageDetails"),
            remarks = profile.stringOrNull("remarks"),
            attributes = JsonObject(emptyMap()),
        )
        insertLongTermProfile(connection, vehicleId, vehicle, actorId, profile.intOrNull("version") ?: 0)
    }

    private fun ensureStatus(batch: BatchRecord, expected: String) {
        if (batch.status != expected) {
            throw ImportWorkflowConflictException("IMPORT_BATCH_STATE_CONFLICT", "导入批次当前状态不允许该操作")
        }
    }

    private fun ResultSet.toExistingVehicle(): ExistingVehicle = ExistingVehicle(
        id = getLong("id"),
        plateNumber = getString("plate_number"),
        normalizedPlate = getString("normalized_plate"),
        category = ImportCategory.valueOf(getString("category")),
        vehicleType = getString("vehicle_type"),
        status = getString("status"),
        importBatchId = (getObject("import_batch_id") as? Number)?.toLong(),
        attributes = Json.parseToJsonElement(getString("attributes")).jsonObject,
        version = getInt("version"),
        residentProfile = if (getObject("resident_profile_id") == null) null else ResidentProfileSnapshot(
            ownerName = getString("owner_name"),
            identityCardNumber = getString("identity_card_number"),
            contactPhone = getString("contact_phone"),
            remarks = getString("resident_remarks"),
            version = getInt("resident_version"),
        ),
        longTermProfile = if (getObject("long_term_profile_id") == null) null else LongTermProfileSnapshot(
            organizationName = getString("organization_name"),
            passHolder = getString("pass_holder"),
            passageDetails = getString("passage_details"),
            remarks = getString("long_term_remarks"),
            version = getInt("long_term_version"),
        ),
    )

    private fun ResultSet.toBatchRecord(): BatchRecord = BatchRecord(
        id = getLong("id"),
        sourceFileName = getString("source_file_name"),
        status = getString("status"),
        createdAt = getTimestamp("created_at")?.toInstant()?.toString(),
        publishedAt = getTimestamp("published_at")?.toInstant()?.toString(),
        rollbackAt = getTimestamp("rollback_at")?.toInstant()?.toString(),
    )

    private fun ResultSet.toStats(): ImportBatchStats = ImportBatchStats(
        totalRows = getInt("total_rows"),
        newRows = getInt("new_rows"),
        updateRows = getInt("update_rows"),
        reactivateRows = getInt("reactivate_rows"),
        deactivateRows = getInt("deactivate_rows"),
        duplicateRows = getInt("duplicate_rows"),
        errorRows = getInt("error_rows"),
        warningRows = getInt("warning_rows"),
        publishableRows = getInt("publishable_rows"),
        pendingReviewRows = getInt("pending_review_rows"),
    )

    private fun ResultSet.toRowView(): ImportRowView {
        val vehicle = ParsedVehicle.fromJson(getString("parsed_values"))
        return ImportRowView(
            id = getLong("id"),
            sourceSheetName = getString("source_sheet_name"),
            sourceRowNumber = getInt("source_row_number"),
            sourceItemIndex = getInt("source_item_index"),
            plateNumber = vehicle.originalPlate,
            normalizedPlate = vehicle.normalizedPlate,
            category = vehicle.category?.name,
            primarySubject = if (vehicle.category == ImportCategory.RESIDENT) vehicle.ownerName else vehicle.organizationName,
            resultStatus = getString("result_status"),
            plannedAction = getString("planned_action"),
            resolution = getString("resolution"),
            errorMessage = getString("error_message"),
            warningMessage = getString("warning_message"),
        )
    }

    private fun ParsedImportRow.toView(id: Long): ImportRowView = ImportRowView(
        id = id,
        sourceSheetName = sourceSheetName,
        sourceRowNumber = sourceRowNumber,
        sourceItemIndex = sourceItemIndex,
        plateNumber = vehicle.originalPlate,
        normalizedPlate = vehicle.normalizedPlate,
        category = vehicle.category?.name,
        primarySubject = if (vehicle.category == ImportCategory.RESIDENT) vehicle.ownerName else vehicle.organizationName,
        resultStatus = resultStatus.name,
        plannedAction = plannedAction.name,
        resolution = resolution.name,
        errorMessage = errorMessage,
        warningMessage = warningMessage,
    )

    private fun BatchRecord.toView(
        stats: ImportBatchStats,
        rows: List<ImportRowView>,
        rowTotal: Int,
    ): ImportBatchView = ImportBatchView(
        id = id,
        sourceFileName = sourceFileName,
        status = status,
        stats = stats,
        createdAt = createdAt,
        publishedAt = publishedAt,
        rollbackAt = rollbackAt,
        rowTotal = rowTotal,
        rows = rows,
    )

    private fun List<ParsedImportRow>.toStats(): ImportBatchStats = ImportBatchStats(
        totalRows = size,
        newRows = count { it.plannedAction == ImportPlannedAction.CREATE },
        updateRows = count { it.plannedAction == ImportPlannedAction.UPDATE },
        reactivateRows = count { it.plannedAction == ImportPlannedAction.REACTIVATE },
        deactivateRows = count { it.plannedAction == ImportPlannedAction.DEACTIVATE },
        duplicateRows = count { it.plannedAction == ImportPlannedAction.SKIP },
        errorRows = count { it.resultStatus == ImportResultStatus.ERROR },
        warningRows = count { !it.warningMessage.isNullOrBlank() },
        publishableRows = count { it.resolution == ImportResolution.PUBLISH },
        pendingReviewRows = count { it.resolution == ImportResolution.PENDING },
    )

    private fun ExistingVehicle.hasSameContent(vehicle: ParsedVehicle): Boolean {
        if (plateNumber != vehicle.originalPlate || category != vehicle.category || vehicleType != vehicle.vehicleType || attributes != vehicle.attributes) return false
        return if (category == ImportCategory.RESIDENT) {
            residentProfile?.let {
                it.ownerName == vehicle.ownerName && it.identityCardNumber == vehicle.identityCardNumber &&
                    it.contactPhone == vehicle.contactPhone && it.remarks == vehicle.remarks
            } ?: false
        } else {
            longTermProfile?.let {
                it.organizationName == vehicle.organizationName && it.passHolder == vehicle.passHolder &&
                    it.passageDetails == vehicle.passageDetails && it.remarks == vehicle.remarks
            } ?: false
        }
    }

    private fun ExistingVehicle.toParsedVehicle(): ParsedVehicle = ParsedVehicle(
        originalPlate = plateNumber,
        normalizedPlate = normalizedPlate,
        category = category,
        vehicleType = vehicleType,
        ownerName = residentProfile?.ownerName,
        identityCardNumber = residentProfile?.identityCardNumber,
        contactPhone = residentProfile?.contactPhone,
        organizationName = longTermProfile?.organizationName,
        passHolder = longTermProfile?.passHolder,
        passageDetails = longTermProfile?.passageDetails,
        remarks = residentProfile?.remarks ?: longTermProfile?.remarks,
        attributes = attributes,
        sourceVehicleId = id,
        sourceVehicleVersion = version,
    )

    private fun ExistingVehicle.toComparisonValues(): JsonObject = buildJsonObject {
        put("plateNumber", JsonPrimitive(plateNumber))
        put("category", JsonPrimitive(category.name))
        putNullable("vehicleType", vehicleType)
        put("status", JsonPrimitive(status))
        putNullable("ownerName", residentProfile?.ownerName)
        putNullable("identityCardNumber", residentProfile?.identityCardNumber)
        putNullable("contactPhone", residentProfile?.contactPhone)
        putNullable("organizationName", longTermProfile?.organizationName)
        putNullable("passHolder", longTermProfile?.passHolder)
        putNullable("passageDetails", longTermProfile?.passageDetails)
        putNullable("remarks", residentProfile?.remarks ?: longTermProfile?.remarks)
        put("attributes", attributes)
    }

    private fun ParsedVehicle.toComparisonValues(status: String): JsonObject = buildJsonObject {
        putNullable("plateNumber", originalPlate)
        putNullable("category", category?.name)
        putNullable("vehicleType", vehicleType)
        put("status", JsonPrimitive(status))
        putNullable("ownerName", ownerName)
        putNullable("identityCardNumber", identityCardNumber)
        putNullable("contactPhone", contactPhone)
        putNullable("organizationName", organizationName)
        putNullable("passHolder", passHolder)
        putNullable("passageDetails", passageDetails)
        putNullable("remarks", remarks)
        put("attributes", attributes)
    }

    private fun ExistingVehicle.toVehicleSnapshot(): JsonObject = buildJsonObject {
        put("plateNumber", JsonPrimitive(plateNumber))
        put("normalizedPlate", JsonPrimitive(normalizedPlate))
        put("category", JsonPrimitive(category.name))
        putNullable("vehicleType", vehicleType)
        put("status", JsonPrimitive(status))
        putNullable("importBatchId", importBatchId)
        put("attributes", attributes)
        put("version", JsonPrimitive(version))
    }

    private fun ResidentProfileSnapshot.toJson(): JsonObject = buildJsonObject {
        putNullable("ownerName", ownerName)
        putNullable("identityCardNumber", identityCardNumber)
        putNullable("contactPhone", contactPhone)
        putNullable("remarks", remarks)
        put("version", JsonPrimitive(version))
    }

    private fun LongTermProfileSnapshot.toJson(): JsonObject = buildJsonObject {
        putNullable("organizationName", organizationName)
        putNullable("passHolder", passHolder)
        putNullable("passageDetails", passageDetails)
        putNullable("remarks", remarks)
        put("version", JsonPrimitive(version))
    }

    private fun <T> DataSource.inTransaction(block: (Connection) -> T): T {
        connection.use { connection ->
            val autoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                return block(connection).also { connection.commit() }
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = autoCommit
            }
        }
    }

    private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) setObject(index, null) else setLong(index, value)
    }

    private fun appendMessage(current: String?, addition: String): String = listOfNotNull(current, addition).joinToString("；")

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024
        const val MAX_IMPORT_ROWS = 10_000
        const val MAX_PAGE_SIZE = 500
        const val INITIAL_PREVIEW_PAGE_SIZE = 200
        val VEHICLE_FIELDS = listOf(
            ImportComparisonField("plateNumber", "车牌号"),
            ImportComparisonField("category", "车辆类别"),
            ImportComparisonField("vehicleType", "车辆类型"),
        )
        val STATUS_FIELDS = listOf(ImportComparisonField("status", "档案状态"))
        val PROFILE_FIELDS = listOf(
            ImportComparisonField("ownerName", "所属人姓名"),
            ImportComparisonField("identityCardNumber", "身份证号"),
            ImportComparisonField("contactPhone", "联系方式"),
            ImportComparisonField("organizationName", "单位名称"),
            ImportComparisonField("passHolder", "通行人员"),
            ImportComparisonField("passageDetails", "通行说明"),
            ImportComparisonField("remarks", "备注"),
        )
        val ATTRIBUTE_LABELS = mapOf(
            "vehicleUse" to "车辆用途",
            "passageArea" to "通行区域",
            "position" to "职务",
            "brandModel" to "品牌型号",
            "approvedCapacity" to "核载人数",
            "plateColor" to "号牌颜色",
        )
    }
}

private data class ImportComparisonField(val key: String, val label: String)

internal data class ImportBatchView(
    val id: Long,
    val sourceFileName: String,
    val status: String,
    val stats: ImportBatchStats,
    val createdAt: String?,
    val publishedAt: String?,
    val rollbackAt: String?,
    val rowTotal: Int,
    val rows: List<ImportRowView>,
)

internal data class ImportBatchStats(
    val totalRows: Int,
    val newRows: Int,
    val updateRows: Int,
    val reactivateRows: Int,
    val deactivateRows: Int,
    val duplicateRows: Int,
    val errorRows: Int,
    val warningRows: Int,
    val publishableRows: Int,
    val pendingReviewRows: Int,
) {
    companion object {
        fun empty(): ImportBatchStats = ImportBatchStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }
}

internal data class ImportRowView(
    val id: Long,
    val sourceSheetName: String,
    val sourceRowNumber: Int,
    val sourceItemIndex: Int,
    val plateNumber: String?,
    val normalizedPlate: String?,
    val category: String?,
    val primarySubject: String?,
    val resultStatus: String,
    val plannedAction: String,
    val resolution: String,
    val errorMessage: String?,
    val warningMessage: String?,
) {
    fun isReviewable(): Boolean = plannedAction in setOf(
        ImportPlannedAction.CREATE.name,
        ImportPlannedAction.UPDATE.name,
        ImportPlannedAction.DEACTIVATE.name,
        ImportPlannedAction.REACTIVATE.name,
    ) || resultStatus == ImportResultStatus.ERROR.name
}

internal data class ImportRowDetailView(
    val row: ImportRowView,
    val sections: List<ImportDiffSection>,
    val sourceValues: List<ImportSourceValue>,
)

internal data class ImportDiffSection(val title: String, val fields: List<ImportFieldDifference>)

internal data class ImportFieldDifference(val label: String, val before: String?, val after: String?)

internal data class ImportSourceValue(val label: String, val value: String)

internal enum class ImportRowFilter(val sqlCondition: String) {
    REVIEW("(planned_action IN ('CREATE', 'UPDATE', 'DEACTIVATE', 'REACTIVATE') OR result_status = 'ERROR')"),
    CREATE("planned_action = 'CREATE'"),
    UPDATE("planned_action = 'UPDATE'"),
    DEACTIVATE("planned_action = 'DEACTIVATE'"),
    REACTIVATE("planned_action = 'REACTIVATE'"),
    ERROR("result_status = 'ERROR'"),
}

internal data class ImportRowResolutionChange(val rowId: Long, val resolution: ImportResolution)

internal class ImportBatchNotFoundException : RuntimeException()

internal class ImportFileInvalidException(message: String) : RuntimeException(message)

internal class ImportWorkflowConflictException(val errorCode: String, message: String) : RuntimeException(message)

private data class BatchRecord(
    val id: Long,
    val sourceFileName: String,
    val status: String,
    val createdAt: String?,
    val publishedAt: String?,
    val rollbackAt: String?,
)

private data class StoredImportRow(
    val id: Long,
    val resultStatus: ImportResultStatus,
    val plannedAction: ImportPlannedAction,
    val resolution: ImportResolution,
    val vehicle: ParsedVehicle,
)

private data class ImportEffect(
    val importRowId: Long,
    val vehicleId: Long,
    val action: String,
    val appliedVersion: Int,
    val previousVehicle: String?,
    val previousResident: String?,
    val previousLongTerm: String?,
)
