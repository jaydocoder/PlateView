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

    fun search(keyword: String, accessScope: VehicleAccessScope): List<VehicleSearchCandidate> {
        val normalizedKeyword = normalizeSearchKeyword(keyword)
        return dataSource.connection.use { connection ->
            if (isCompletePlateNumber(normalizedKeyword)) {
                connection.queryExactPlate(normalizedKeyword, accessScope).takeIf(List<*>::isNotEmpty)?.let { return@use it }
            }
            connection.prepareStatement(SEARCH_VEHICLES).use { statement ->
                statement.setString(1, VehicleCategory.RESIDENT.name)
                statement.setString(2, normalizedKeyword)
                statement.setString(3, "$normalizedKeyword%")
                statement.setString(4, normalizedKeyword)
                statement.setString(5, "%$normalizedKeyword%")
                statement.setBoolean(6, accessScope.otherLongTermAccessEnabled)
                statement.setInt(7, MAXIMUM_SEARCH_RESULT_COUNT)
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
    ): List<VehicleSearchCandidate> = prepareStatement(SEARCH_EXACT_PLATE).use { statement ->
        statement.setString(1, normalizedPlate)
        statement.setBoolean(2, accessScope.otherLongTermAccessEnabled)
        statement.setString(3, VehicleCategory.RESIDENT.name)
        statement.setInt(4, MAXIMUM_SEARCH_RESULT_COUNT)
        statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toSearchCandidate()) } }
    }

    fun findDetail(vehicleId: Long, accessScope: VehicleAccessScope): VehicleDetail? {
        require(vehicleId > 0) { "车辆标识无效" }
        return dataSource.connection.use { connection ->
            connection.prepareStatement(SELECT_VEHICLE_DETAIL).use { statement ->
                statement.setLong(1, vehicleId)
                statement.setBoolean(2, accessScope.otherLongTermAccessEnabled)
                statement.executeQuery().use { result ->
                    if (result.next()) result.toVehicleDetail().filteredFor(accessScope) else null
                }
            }
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
        val snapshot = loadFullCatalogSnapshot(accessScope, expectedRevision)
        val safeOffset = offset.coerceAtLeast(0)
        return VehicleFullCatalogPage(
            revision = snapshot.revision,
            total = snapshot.items.size,
            items = snapshot.items.drop(safeOffset).take(limit),
        )
    }

    private fun loadFullCatalogSnapshot(accessScope: VehicleAccessScope, expectedRevision: Long): VehicleFullCatalogSnapshot = dataSource.connection.use { connection ->
        connection.autoCommit = false
        connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        try {
            val revision = catalogVersion(catalogRevision(connection), accessScope)
            if (revision != expectedRevision) throw VehicleCatalogVersionConflictException()
            val items = connection.prepareStatement(SELECT_FULL_CATALOG).use { statement ->
                statement.setBoolean(1, accessScope.otherLongTermAccessEnabled)
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toVehicleDetail()) } }
            }.map { it.filteredFor(accessScope) }
            connection.commit()
            VehicleFullCatalogSnapshot(revision, items)
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

        const val SELECT_FULL_CATALOG = """
            SELECT v.id, v.plate_number, v.normalized_plate, v.category, v.vehicle_type, v.status, v.attributes::text,
                   rp.id AS resident_profile_id, rp.owner_name, rp.identity_card_number, rp.contact_phone,
                   rp.remarks AS resident_remarks,
                   lp.id AS long_term_profile_id, lp.organization_name, lp.pass_holder, lp.passage_details,
                   lp.remarks AS long_term_remarks
            FROM vehicles v
            LEFT JOIN resident_profiles rp ON rp.vehicle_id = v.id
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.status <> 'DELETED' AND (? OR v.category <> 'OTHER_LONG_TERM')
            ORDER BY CASE WHEN v.status = 'ACTIVE' THEN 0 ELSE 1 END, v.normalized_plate, v.id
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
internal data class VehicleFullCatalogSnapshot(val revision: Long, val items: List<VehicleDetail>)

internal data class VehicleFullCatalogPage(val revision: Long, val total: Int, val items: List<VehicleDetail>)

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
