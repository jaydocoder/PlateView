package com.jaydocoder.plateview.data.vehicle

import com.jaydocoder.plateview.domain.vehicle.LongTermProfile
import com.jaydocoder.plateview.domain.vehicle.ResidentProfile
import com.jaydocoder.plateview.domain.vehicle.VehicleAttribute
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.VehicleCatalogPage
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.domain.vehicle.VehicleFullCatalogPage
import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkVehicleRepository @Inject constructor(
    private val api: VehicleApi,
) : VehicleRepository {
    override suspend fun search(accessToken: String, keyword: String): List<VehicleCandidate> = api
        .search(authorization = bearer(accessToken), keyword = keyword)
        .candidates
        .map(VehicleCandidateDto::toDomain)

    override suspend fun getVehicle(accessToken: String, vehicleId: Long): VehicleDetail = api
        .getVehicle(authorization = bearer(accessToken), vehicleId = vehicleId)
        .toDomain()

    override suspend fun getCatalogVersion(accessToken: String): Long = api
        .getCatalogVersion(authorization = bearer(accessToken))
        .catalogVersion

    override suspend fun getCatalog(accessToken: String, limit: Int, offset: Int): VehicleCatalogPage = api
        .getCatalog(authorization = bearer(accessToken), limit = limit, offset = offset)
        .let { response ->
            VehicleCatalogPage(
                catalogVersion = response.catalogVersion,
                total = response.total,
                candidates = response.items.map(VehicleCandidateDto::toDomain),
            )
        }

    override suspend fun getFullCatalog(
        accessToken: String,
        version: Long,
        limit: Int,
        offset: Int,
    ): VehicleFullCatalogPage = api
        .getFullCatalog(
            authorization = bearer(accessToken),
            version = version,
            limit = limit,
            offset = offset,
        ).let { response ->
            VehicleFullCatalogPage(
                catalogVersion = response.catalogVersion,
                total = response.total,
                vehicles = response.items.map(VehicleDetailDto::toDomain),
            )
        }

    private fun bearer(accessToken: String): String = "Bearer $accessToken"
}

private fun VehicleCandidateDto.toDomain(): VehicleCandidate = VehicleCandidate(
    id = id,
    plateNumber = plateNumber,
    category = category,
    categoryLabel = categoryLabel,
)

private fun VehicleDetailDto.toDomain(): VehicleDetail = VehicleDetail(
    id = id,
    plateNumber = plateNumber,
    normalizedPlate = normalizedPlate,
    category = category,
    categoryLabel = categoryLabel,
    vehicleType = vehicleType,
    attributes = attributes.entrySet()
        .mapNotNull { (key, value) ->
            value.takeIf { it.isJsonPrimitive && !it.isJsonNull }
                ?.asString
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { VehicleAttribute(label = key.toVehicleAttributeLabel(), value = it) }
        },
    residentProfile = residentProfile?.let {
        ResidentProfile(
            ownerName = it.ownerName,
            identityCardNumber = it.identityCardNumber,
            contactPhone = it.contactPhone,
            remarks = it.remarks,
        )
    },
    longTermProfile = longTermProfile?.let {
        LongTermProfile(
            organizationName = it.organizationName,
            passHolder = it.passHolder,
            passageDetails = it.passageDetails,
            remarks = it.remarks,
        )
    },
)

private fun String.toVehicleAttributeLabel(): String = when (this) {
    "vehicleUse" -> "车辆用途"
    "passageArea" -> "通行区域"
    "position" -> "职务"
    "brandModel" -> "品牌型号"
    "approvedCapacity" -> "核载人数"
    "plateColor" -> "号牌颜色"
    else -> this
}
