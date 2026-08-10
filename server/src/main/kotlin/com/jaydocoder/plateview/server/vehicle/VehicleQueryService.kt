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
    fun search(keyword: String): List<VehicleSearchCandidate> {
        val normalizedKeyword = normalizeSearchKeyword(keyword)
        return dataSource.connection.use { connection ->
            connection.prepareStatement(SEARCH_VEHICLES).use { statement ->
                statement.setString(1, "%$normalizedKeyword%")
                statement.setString(2, VehicleCategory.RESIDENT.name)
                statement.setString(3, normalizedKeyword)
                statement.setString(4, "$normalizedKeyword%")
                statement.setInt(5, MAXIMUM_SEARCH_RESULT_COUNT)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toSearchCandidate())
                    }
                }
            }
        }
    }

    fun findDetail(vehicleId: Long): VehicleDetail? {
        require(vehicleId > 0) { "车辆标识无效" }
        return dataSource.connection.use { connection ->
            connection.prepareStatement(SELECT_VEHICLE_DETAIL).use { statement ->
                statement.setLong(1, vehicleId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.toVehicleDetail() else null
                }
            }
        }
    }

    fun catalogVersion(): Long = catalogRevision()

    fun catalog(limit: Int, offset: Int): VehicleCatalogPage {
        require(limit in 1..500) { "目录分页大小必须在1到500之间" }
        val revision = catalogRevision()
        return dataSource.connection.use { connection ->
            val items = connection.prepareStatement(CATALOG_VEHICLES).use { statement ->
                statement.setInt(1, limit)
                statement.setInt(2, offset.coerceAtLeast(0))
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toSearchCandidate()) } }
            }
            val total = connection.prepareStatement("SELECT COUNT(*) FROM vehicles WHERE status = 'ACTIVE'").use { statement ->
                statement.executeQuery().use { result -> result.next(); result.getInt(1) }
            }
            VehicleCatalogPage(revision, total, items)
        }
    }

    fun fullCatalog(expectedRevision: Long, limit: Int, offset: Int): VehicleFullCatalogPage {
        require(expectedRevision >= 0) { "目录版本无效" }
        require(limit in 1..500) { "目录分页大小必须在1到500之间" }
        val snapshot = loadFullCatalogSnapshot(expectedRevision)
        val safeOffset = offset.coerceAtLeast(0)
        return VehicleFullCatalogPage(
            revision = snapshot.revision,
            total = snapshot.items.size,
            items = snapshot.items.drop(safeOffset).take(limit),
        )
    }

    private fun loadFullCatalogSnapshot(expectedRevision: Long): VehicleFullCatalogSnapshot = dataSource.connection.use { connection ->
        connection.autoCommit = false
        connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        try {
            val revision = catalogRevision(connection)
            if (revision != expectedRevision) throw VehicleCatalogVersionConflictException()
            val items = connection.prepareStatement(SELECT_FULL_CATALOG).use { statement ->
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toVehicleDetail()) } }
            }
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
            SELECT id, plate_number, category
            FROM vehicles
            WHERE status = 'ACTIVE' AND normalized_plate LIKE ?
            ORDER BY
                CASE WHEN category = ? THEN 0 ELSE 1 END,
                CASE
                    WHEN normalized_plate = ? THEN 0
                    WHEN normalized_plate LIKE ? THEN 1
                    ELSE 2
                END,
                LENGTH(normalized_plate),
                normalized_plate,
                id
            LIMIT ?
        """

        const val CATALOG_VEHICLES = """
            SELECT id, plate_number, category FROM vehicles
            WHERE status = 'ACTIVE' ORDER BY normalized_plate, id LIMIT ? OFFSET ?
        """
        const val SELECT_VEHICLE_DETAIL = """
            SELECT v.id, v.plate_number, v.normalized_plate, v.category, v.vehicle_type, v.attributes::text,
                   rp.id AS resident_profile_id, rp.owner_name, rp.identity_card_number, rp.contact_phone,
                   rp.remarks AS resident_remarks,
                   lp.id AS long_term_profile_id, lp.organization_name, lp.pass_holder, lp.passage_details,
                   lp.remarks AS long_term_remarks
            FROM vehicles v
            LEFT JOIN resident_profiles rp ON rp.vehicle_id = v.id
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.id = ? AND v.status = 'ACTIVE'
        """

        const val SELECT_FULL_CATALOG = """
            SELECT v.id, v.plate_number, v.normalized_plate, v.category, v.vehicle_type, v.attributes::text,
                   rp.id AS resident_profile_id, rp.owner_name, rp.identity_card_number, rp.contact_phone,
                   rp.remarks AS resident_remarks,
                   lp.id AS long_term_profile_id, lp.organization_name, lp.pass_holder, lp.passage_details,
                   lp.remarks AS long_term_remarks
            FROM vehicles v
            LEFT JOIN resident_profiles rp ON rp.vehicle_id = v.id
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.status = 'ACTIVE'
            ORDER BY v.normalized_plate, v.id
        """
    }
}

@Serializable
internal data class VehicleSearchCandidate(
    val id: Long,
    val plateNumber: String,
    val category: VehicleCategory,
)

@Serializable
internal data class VehicleDetail(
    val id: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: VehicleCategory,
    val vehicleType: String?,
    val attributes: JsonObject,
    val residentProfile: ResidentVehicleProfile?,
    val longTermProfile: LongTermVehicleProfile?,
)

@Serializable
internal data class ResidentVehicleProfile(
    val ownerName: String,
    val identityCardNumber: String,
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

internal class VehicleSearchKeywordException : RuntimeException("请输入有效车牌字符")

internal class VehicleCatalogVersionConflictException : RuntimeException("车辆目录已更新，请重新同步")

internal class VehicleNotFoundException : RuntimeException()
