package com.jaydocoder.plateview.server.vehicle

import java.sql.ResultSet
import javax.sql.DataSource
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
                statement.setString(2, normalizedKeyword)
                statement.setString(3, "$normalizedKeyword%")
                statement.setInt(4, MAXIMUM_SEARCH_RESULT_COUNT)
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
    }
}

internal data class VehicleSearchCandidate(
    val id: Long,
    val plateNumber: String,
    val category: VehicleCategory,
)

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

internal data class ResidentVehicleProfile(
    val ownerName: String,
    val identityCardNumber: String,
    val contactPhone: String?,
    val remarks: String?,
)

internal data class LongTermVehicleProfile(
    val organizationName: String?,
    val passHolder: String?,
    val passageDetails: String?,
    val remarks: String?,
)

internal class VehicleSearchKeywordException : RuntimeException("请至少输入3个有效车牌字符")

internal class VehicleNotFoundException : RuntimeException()
