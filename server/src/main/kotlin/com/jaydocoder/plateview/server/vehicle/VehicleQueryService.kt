package com.jaydocoder.plateview.server.vehicle

import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal class VehicleQueryService(
    private val dataSource: DataSource,
) {
    fun accessScope(userId: Long): VehicleAccessScope = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT username, role, other_long_term_access_enabled, resident_remarks_access_enabled FROM users WHERE id = ?",
        ).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { result ->
                check(result.next()) { "当前账号不存在" }
                if (result.getString("username") == "admin" && result.getString("role") == "ADMIN") {
                    VehicleAccessScope.fullAccess()
                } else {
                    VehicleAccessScope(
                        otherLongTermAccessEnabled = result.getBoolean("other_long_term_access_enabled"),
                        residentRemarksAccessEnabled = result.getBoolean("resident_remarks_access_enabled"),
                    )
                }
            }
        }
    }

    fun search(keyword: String, accessScope: VehicleAccessScope, limit: Int = MAXIMUM_SEARCH_RESULT_COUNT): List<VehicleSearchCandidate> {
        require(limit in 1..MAXIMUM_SEARCH_RESULT_COUNT) { "车辆搜索数量必须在1至50之间" }
        val normalizedKeyword = normalizeSearchKeyword(keyword)
        return dataSource.connection.use { connection ->
            if (isCompletePlateNumber(normalizedKeyword)) {
                connection.queryExactPlate(normalizedKeyword, accessScope, limit).takeIf(List<*>::isNotEmpty)?.let { return@use it }
            }
            connection.prepareStatement(SEARCH_VEHICLES).use { statement ->
                statement.setString(1, VehicleCategory.RESIDENT.name)
                statement.setString(2, normalizedKeyword)
                statement.setString(3, "$normalizedKeyword%")
                statement.setString(4, normalizedKeyword)
                statement.setString(5, "%$normalizedKeyword%")
                statement.setBoolean(6, accessScope.otherLongTermAccessEnabled)
                statement.setInt(7, limit)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toSearchCandidate())
                    }
                }
            }
        }
    }

    private fun Connection.queryExactPlate(
        normalizedPlate: String,
        accessScope: VehicleAccessScope,
        limit: Int,
    ): List<VehicleSearchCandidate> = prepareStatement(SEARCH_EXACT_PLATE).use { statement ->
        statement.setString(1, normalizedPlate)
        statement.setBoolean(2, accessScope.otherLongTermAccessEnabled)
        statement.setString(3, VehicleCategory.RESIDENT.name)
        statement.setInt(4, limit)
        statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toSearchCandidate()) } }
    }

    fun findDetail(vehicleId: Long, accessScope: VehicleAccessScope): VehicleDetail? {
        require(vehicleId > 0) { "车辆标识无效" }
        return dataSource.connection.use { connection -> connection.findDetail(vehicleId, accessScope) }
    }

    private fun Connection.findDetail(vehicleId: Long, accessScope: VehicleAccessScope): VehicleDetail? =
        prepareStatement(SELECT_VEHICLE_DETAIL).use { statement ->
            statement.setLong(1, vehicleId)
            statement.setBoolean(2, accessScope.otherLongTermAccessEnabled)
            statement.executeQuery().use { result ->
                if (result.next()) result.toVehicleDetail().filteredFor(accessScope) else null
            }
        }

    fun recordQueryEvent(actorId: Long, vehicle: VehicleDetail) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO vehicle_query_events (actor_id, vehicle_id, category, vehicle_type) VALUES (?, ?, ?, ?)",
            ).use { statement ->
                statement.setLong(1, actorId)
                statement.setLong(2, vehicle.id)
                statement.setString(3, vehicle.category.name)
                statement.setString(4, vehicle.vehicleType)
                statement.executeUpdate()
            }
        }
    }

    fun catalogVersion(accessScope: VehicleAccessScope): Long = catalogVersion(catalogRevision(), accessScope)

    fun catalog(accessScope: VehicleAccessScope, limit: Int, offset: Int): VehicleCatalogPage {
        require(limit in 1..500) { "目录分页大小必须在1到500之间" }
        val revision = catalogVersion(accessScope)
        return dataSource.connection.use { connection ->
            val items = connection.prepareStatement(CATALOG_VEHICLES).use { statement ->
                statement.setBoolean(1, accessScope.otherLongTermAccessEnabled)
                statement.setInt(2, limit)
                statement.setInt(3, offset.coerceAtLeast(0))
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toSearchCandidate()) } }
            }
            val total = connection.prepareStatement("SELECT COUNT(*) FROM vehicles WHERE status <> 'DELETED' AND (? OR category <> 'OTHER_LONG_TERM')").use { statement ->
                statement.setBoolean(1, accessScope.otherLongTermAccessEnabled)
                statement.executeQuery().use { result -> result.next(); result.getInt(1) }
            }
            VehicleCatalogPage(revision, total, items)
        }
    }

    fun fullCatalog(accessScope: VehicleAccessScope, expectedRevision: Long, limit: Int, offset: Int): VehicleFullCatalogPage {
        require(expectedRevision >= 0) { "目录版本无效" }
        require(limit in 1..500) { "目录分页大小必须在1到500之间" }
        return repeatableRead { connection ->
            val expectedBits = accessScope.versionBits
            require(expectedRevision % 4L == expectedBits) { "目标目录权限范围与当前账号不一致" }
            val targetRawRevision = expectedRevision / 4L
            val currentRevision = catalogVersion(catalogRevision(connection), accessScope)
            require(expectedRevision <= currentRevision) { "目标目录版本不能高于服务器当前版本" }
            if (connection.hasVehicleConflictAfter(targetRawRevision)) throw VehicleCatalogVersionConflictException()
            val safeOffset = offset.coerceAtLeast(0)
            val items = connection.prepareStatement(SELECT_FULL_CATALOG_PAGE).use { statement ->
                statement.setBoolean(1, accessScope.otherLongTermAccessEnabled)
                statement.setLong(2, targetRawRevision)
                statement.setLong(3, targetRawRevision)
                statement.setInt(4, limit)
                statement.setInt(5, safeOffset)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toVehicleDetail().filteredFor(accessScope)) } }
            }
            val total = connection.prepareStatement(
                "SELECT COUNT(*) FROM vehicles WHERE status <> 'DELETED' AND (? OR category <> 'OTHER_LONG_TERM') " +
                    "AND created_catalog_revision <= ? AND catalog_revision <= ?",
            ).use { statement ->
                statement.setBoolean(1, accessScope.otherLongTermAccessEnabled)
                statement.setLong(2, targetRawRevision)
                statement.setLong(3, targetRawRevision)
                statement.executeQuery().use { result -> result.next(); result.getInt(1) }
            }
            VehicleFullCatalogPage(expectedRevision, total, items)
        }
    }

    fun changes(
        accessScope: VehicleAccessScope,
        afterRevision: Long,
        afterId: Long,
        targetRevision: Long,
        limit: Int,
    ): VehicleCatalogChangePage = repeatableRead { connection ->
        require(limit in 1..200) { "目录变更分页大小必须在1到200之间" }
        val currentRevision = catalogVersion(catalogRevision(connection), accessScope)
        require(targetRevision <= currentRevision) { "目标目录版本不能高于服务器当前版本" }
        require(targetRevision >= afterRevision) { "目标目录版本不能早于本地版本" }
        val expectedBits = accessScope.versionBits
        if (afterRevision > 0 && afterRevision % 4L != expectedBits) {
            return@repeatableRead VehicleCatalogChangePage(targetRevision, afterRevision, afterId, false, true, emptyList())
        }
        require(targetRevision % 4L == expectedBits) { "目标目录权限范围与当前账号不一致" }
        val afterRawRevision = afterRevision / 4L
        val targetRawRevision = targetRevision / 4L
        val earliestRevision = connection.prepareStatement("SELECT MIN(revision) FROM vehicle_catalog_changes").use { statement ->
            statement.executeQuery().use { result -> result.next(); result.getLong(1).takeUnless { result.wasNull() } }
        }
        if (afterRevision > 0 && earliestRevision != null && afterRawRevision < earliestRevision - 1) {
            return@repeatableRead VehicleCatalogChangePage(targetRevision, afterRevision, afterId, false, true, emptyList())
        }
        val changes = connection.prepareStatement(
            """
            SELECT revision, entity_id, operation
            FROM vehicle_catalog_changes
            WHERE (revision > ? OR (revision = ? AND entity_id > ?)) AND revision <= ?
            ORDER BY revision, entity_id
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, afterRawRevision)
            statement.setLong(2, afterRawRevision)
            statement.setLong(3, afterId.coerceAtLeast(0))
            statement.setLong(4, targetRawRevision)
            statement.setInt(5, limit + 1)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(Triple(result.getLong(1), result.getLong(2), result.getString(3)))
                }
            }
        }
        val page = changes.take(limit)
        if (connection.hasVehicleConflictAfter(targetRawRevision)) {
            throw VehicleCatalogVersionConflictException()
        }
        val items = page.map { (revision, entityId, operation) ->
            val record = if (operation == "UPSERT") connection.findDetail(entityId, accessScope) else null
            VehicleCatalogChangeItem(
                revision = revision * 4L + expectedBits,
                entityId = entityId,
                operation = if (operation == "UPSERT" && record == null) "REVOKE" else operation,
                record = record,
            )
        }
        VehicleCatalogChangePage(
            catalogVersion = targetRevision,
            nextRevision = items.lastOrNull()?.revision ?: afterRevision,
            nextId = items.lastOrNull()?.entityId ?: afterId,
            hasMore = changes.size > limit,
            fullSyncRequired = false,
            items = items,
        )
    }

    private fun Connection.hasVehicleConflictAfter(targetRevision: Long): Boolean {
        return prepareStatement(
            """
            SELECT 1
            FROM vehicle_catalog_changes c
            WHERE c.revision > ?
              AND c.entity_created_revision <= ?
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, targetRevision)
            statement.setLong(2, targetRevision)
            statement.executeQuery().use(ResultSet::next)
        }
    }

    private fun <T> repeatableRead(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        try {
            block(connection).also { connection.commit() }
        } catch (throwable: Throwable) {
            runCatching { connection.rollback() }
            throw throwable
        }
    }

    private fun catalogRevision(): Long = dataSource.connection.use(::catalogRevision)

    private fun catalogRevision(connection: Connection): Long =
        connection.prepareStatement("SELECT revision FROM vehicle_catalog_state WHERE id = 1").use { statement ->
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }

    private fun catalogVersion(revision: Long, accessScope: VehicleAccessScope): Long = revision * 4L + accessScope.versionBits

    private fun normalizeSearchKeyword(keyword: String): String {
        val normalizedKeyword = normalizePlate(keyword)
        if (normalizedKeyword.length < MINIMUM_SEARCH_KEYWORD_LENGTH) {
            throw VehicleSearchKeywordException()
        }
        return normalizedKeyword
    }

    private fun ResultSet.toSearchCandidate(): VehicleSearchCandidate {
        val category = VehicleCategory.valueOf(getString("category"))
        return VehicleSearchCandidate(
            id = getLong("id"),
            plateNumber = getString("plate_number"),
            category = category,
            organizationName = getString("organization_name"),
            plateColor = getString("plate_color"),
            status = getString("status"),
        )
    }

    private fun ResultSet.toVehicleDetail(): VehicleDetail {
        val category = VehicleCategory.valueOf(getString("category"))
        return VehicleDetail(
            id = getLong("id"),
            plateNumber = getString("plate_number"),
            normalizedPlate = getString("normalized_plate"),
            category = category,
            vehicleType = getString("vehicle_type"),
            status = getString("status"),
            attributes = Json.parseToJsonElement(getString("attributes")).jsonObject,
            residentProfile = if (getObject("resident_profile_id") == null) null else ResidentVehicleProfile(
                ownerName = getString("owner_name"),
                identityCardNumber = getString("identity_card_number"),
                contactPhone = getString("contact_phone"),
                remarks = getString("resident_remarks"),
            ),
            longTermProfile = if (getObject("long_term_profile_id") == null) null else LongTermVehicleProfile(
                organizationName = getString("organization_name"),
                passHolder = getString("pass_holder"),
                passageDetails = getString("passage_details"),
                remarks = getString("long_term_remarks"),
            ),
        )
    }

    private companion object {
        const val SEARCH_VEHICLES = """
            WITH candidates AS (
                SELECT id,
                       CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END AS status_rank,
                       CASE WHEN category = ? THEN 0 ELSE 1 END AS category_rank,
                       CASE
                           WHEN normalized_plate = ? THEN 0
                           WHEN normalized_plate LIKE ? THEN 1
                           ELSE 2
                       END AS match_rank,
                       LENGTH(normalized_plate) AS plate_length,
                       normalized_plate
                FROM vehicles
                WHERE (normalized_plate = ? OR searchable_text LIKE ?)
                  AND status <> 'DELETED'
                  AND (? OR category <> 'OTHER_LONG_TERM')
                ORDER BY
                    status_rank, category_rank, match_rank, plate_length, normalized_plate, id
                LIMIT ?
            )
            SELECT v.id, v.plate_number, v.category, v.status, v.attributes ->> 'plateColor' AS plate_color, lp.organization_name
            FROM candidates c
            JOIN vehicles v ON v.id = c.id
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            ORDER BY
                c.status_rank, c.category_rank, c.match_rank, c.plate_length, c.normalized_plate, v.id
        """

        const val SEARCH_EXACT_PLATE = """
            SELECT v.id, v.plate_number, v.category, v.status, v.attributes ->> 'plateColor' AS plate_color, lp.organization_name
            FROM vehicles v
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.normalized_plate = ?
              AND v.status <> 'DELETED'
              AND (? OR v.category <> 'OTHER_LONG_TERM')
            ORDER BY
                CASE WHEN v.status = 'ACTIVE' THEN 0 ELSE 1 END,
                CASE WHEN v.category = ? THEN 0 ELSE 1 END,
                v.id
            LIMIT ?
        """

        const val CATALOG_VEHICLES = """
            SELECT v.id, v.plate_number, v.category, v.status, v.attributes ->> 'plateColor' AS plate_color, lp.organization_name
            FROM vehicles v
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.status <> 'DELETED' AND (? OR v.category <> 'OTHER_LONG_TERM')
            ORDER BY CASE WHEN v.status = 'ACTIVE' THEN 0 ELSE 1 END, v.normalized_plate, v.id LIMIT ? OFFSET ?
        """
        const val SELECT_VEHICLE_DETAIL = """
            SELECT v.id, v.plate_number, v.normalized_plate, v.category, v.vehicle_type, v.status, v.attributes::text,
                   rp.id AS resident_profile_id, rp.owner_name, rp.identity_card_number, rp.contact_phone,
                   rp.remarks AS resident_remarks,
                   lp.id AS long_term_profile_id, lp.organization_name, lp.pass_holder, lp.passage_details,
                   lp.remarks AS long_term_remarks
            FROM vehicles v
            LEFT JOIN resident_profiles rp ON rp.vehicle_id = v.id
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.id = ? AND v.status <> 'DELETED' AND (? OR v.category <> 'OTHER_LONG_TERM')
        """

        const val SELECT_FULL_CATALOG_PAGE = """
            SELECT v.id, v.plate_number, v.normalized_plate, v.category, v.vehicle_type, v.status, v.attributes::text,
                   rp.id AS resident_profile_id, rp.owner_name, rp.identity_card_number, rp.contact_phone,
                   rp.remarks AS resident_remarks,
                   lp.id AS long_term_profile_id, lp.organization_name, lp.pass_holder, lp.passage_details,
                   lp.remarks AS long_term_remarks
            FROM vehicles v
            LEFT JOIN resident_profiles rp ON rp.vehicle_id = v.id
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.status <> 'DELETED' AND (? OR v.category <> 'OTHER_LONG_TERM')
              AND v.created_catalog_revision <= ? AND v.catalog_revision <= ?
            ORDER BY CASE WHEN v.status = 'ACTIVE' THEN 0 ELSE 1 END, v.normalized_plate, v.id
            LIMIT ? OFFSET ?
        """
    }
}

@Serializable
internal data class VehicleSearchCandidate(
    val id: Long,
    val plateNumber: String,
    val category: VehicleCategory,
    val organizationName: String?,
    val plateColor: String?,
    val status: String,
)

@Serializable
internal data class VehicleDetail(
    val id: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: VehicleCategory,
    val vehicleType: String?,
    val status: String,
    val attributes: JsonObject,
    val residentProfile: ResidentVehicleProfile?,
    val longTermProfile: LongTermVehicleProfile?,
)

@Serializable
internal data class ResidentVehicleProfile(
    val ownerName: String?,
    val identityCardNumber: String?,
    val contactPhone: String?,
    val remarks: String?,
)

@Serializable
internal data class LongTermVehicleProfile(
    val organizationName: String?,
    val passHolder: String?,
    val passageDetails: String?,
    val remarks: String?,
)

@Serializable
internal data class VehicleCatalogPage(val revision: Long, val total: Int, val items: List<VehicleSearchCandidate>)

@Serializable

internal data class VehicleFullCatalogPage(val revision: Long, val total: Int, val items: List<VehicleDetail>)
internal data class VehicleCatalogChangeItem(
    val revision: Long,
    val entityId: Long,
    val operation: String,
    val record: VehicleDetail?,
)
internal data class VehicleCatalogChangePage(
    val catalogVersion: Long,
    val nextRevision: Long,
    val nextId: Long,
    val hasMore: Boolean,
    val fullSyncRequired: Boolean,
    val items: List<VehicleCatalogChangeItem>,
)

internal data class VehicleAccessScope(
    val otherLongTermAccessEnabled: Boolean,
    val residentRemarksAccessEnabled: Boolean,
) {
    val versionBits: Long
        get() = (if (otherLongTermAccessEnabled) 2L else 0L) + (if (residentRemarksAccessEnabled) 1L else 0L)

    companion object {
        fun fullAccess() = VehicleAccessScope(otherLongTermAccessEnabled = true, residentRemarksAccessEnabled = true)
    }
}

internal fun VehicleDetail.filteredFor(accessScope: VehicleAccessScope): VehicleDetail =
    if (accessScope.residentRemarksAccessEnabled) this else copy(residentProfile = residentProfile?.copy(remarks = null))

internal class VehicleSearchKeywordException : RuntimeException("请输入至少一个车牌、姓名或单位字符")

internal class VehicleCatalogVersionConflictException : RuntimeException("车辆目录已更新，请重新同步")

internal class VehicleNotFoundException : RuntimeException()
