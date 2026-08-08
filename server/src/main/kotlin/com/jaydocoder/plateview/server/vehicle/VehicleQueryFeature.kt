package com.jaydocoder.plateview.server.vehicle

import com.jaydocoder.plateview.server.infrastructure.database.AuditEvent
import com.jaydocoder.plateview.server.infrastructure.database.AuditLogWriterKey
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun Application.configureVehicleQueryFeature() {
    val dataSource = attributes.getOrNull(DataSourceKey) ?: return
    val service = VehicleQueryService(dataSource)
    routing {
        authenticate("access-token") {
            route("/vehicles") {
                get("/search") {
                    val candidates = service.search(call.request.queryParameters["keyword"].orEmpty())
                    call.respond(VehicleSearchResponse(service.catalogVersion(), candidates.map(VehicleSearchCandidate::toResponse)))
                }
                get("/catalog/version") {
                    call.respond(VehicleCatalogVersionResponse(service.catalogVersion()))
                }
                get("/catalog") {
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 500
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val page = service.catalog(limit, offset)
                    call.respond(VehicleCatalogResponse(page.revision, page.total, page.items.map(VehicleSearchCandidate::toResponse)))
                }
                get("/catalog/full") {
                    val version = call.request.queryParameters["version"]?.toLongOrNull()
                        ?: throw IllegalArgumentException("缺少目录版本")
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val page = service.fullCatalog(version, limit, offset)
                    call.respond(
                        VehicleFullCatalogResponse(
                            catalogVersion = page.revision,
                            total = page.total,
                            items = page.items.map { it.toResponse(page.revision) },
                        ),
                    )
                }
                get("/{vehicleId}") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                    val vehicleId = call.vehicleId()
                    val detail = service.findDetail(vehicleId)
                    if (detail == null) {
                        call.auditVehicleDetail(actorId, vehicleId, null, "FAILURE")
                        throw VehicleNotFoundException()
                    }
                    call.auditVehicleDetail(actorId, vehicleId, detail.normalizedPlate, "SUCCESS")
                    call.respond(detail.toResponse(service.catalogVersion()))
                }
            }
        }
    }
}

private fun ApplicationCall.vehicleId(): Long = parameters["vehicleId"]?.toLongOrNull()
    ?: throw IllegalArgumentException("车辆标识无效")

private fun ApplicationCall.auditVehicleDetail(
    actorId: Long,
    vehicleId: Long,
    normalizedPlate: String?,
    resultStatus: String,
) {
    application.attributes.getOrNull(AuditLogWriterKey)?.write(
        AuditEvent(
            actorId = actorId,
            actionType = "VEHICLE_DETAIL_VIEW",
            targetType = "VEHICLE",
            targetId = vehicleId,
            resultStatus = resultStatus,
            requestId = callId,
            metadata = buildJsonObject {
                normalizedPlate?.let { put("normalizedPlate", JsonPrimitive(it)) }
                put("device", JsonPrimitive(deviceCategory()))
            },
        ),
    )
}

private fun ApplicationCall.deviceCategory(): String = when {
    request.headers["User-Agent"].orEmpty().contains("Android", ignoreCase = true) -> "ANDROID"
    request.headers["User-Agent"].orEmpty().contains("iPhone", ignoreCase = true) -> "IOS"
    else -> "OTHER"
}

private fun VehicleSearchCandidate.toResponse(): VehicleSearchCandidateResponse = VehicleSearchCandidateResponse(
    id = id,
    plateNumber = plateNumber,
    category = category.name,
    categoryLabel = category.displayName,
)

private fun VehicleDetail.toResponse(catalogVersion: Long): VehicleDetailResponse = VehicleDetailResponse(
    catalogVersion = catalogVersion,
    id = id,
    plateNumber = plateNumber,
    normalizedPlate = normalizedPlate,
    category = category.name,
    categoryLabel = category.displayName,
    vehicleType = vehicleType,
    attributes = attributes,
    residentProfile = residentProfile?.let {
        ResidentVehicleProfileResponse(
            ownerName = it.ownerName,
            identityCardNumber = it.identityCardNumber,
            contactPhone = it.contactPhone,
            remarks = it.remarks,
        )
    },
    longTermProfile = longTermProfile?.let {
        LongTermVehicleProfileResponse(
            organizationName = it.organizationName,
            passHolder = it.passHolder,
            passageDetails = it.passageDetails,
            remarks = it.remarks,
        )
    },
)

@Serializable
private data class VehicleSearchResponse(
    val catalogVersion: Long,
    val candidates: List<VehicleSearchCandidateResponse>,
)

@Serializable
private data class VehicleCatalogVersionResponse(val catalogVersion: Long)

@Serializable
private data class VehicleCatalogResponse(val catalogVersion: Long, val total: Int, val items: List<VehicleSearchCandidateResponse>)

@Serializable
private data class VehicleFullCatalogResponse(
    val catalogVersion: Long,
    val total: Int,
    val items: List<VehicleDetailResponse>,
)

@Serializable
private data class VehicleSearchCandidateResponse(
    val id: Long,
    val plateNumber: String,
    val category: String,
    val categoryLabel: String,
)

@Serializable
private data class VehicleDetailResponse(
    val catalogVersion: Long,
    val id: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: String,
    val categoryLabel: String,
    val vehicleType: String?,
    val attributes: JsonObject,
    val residentProfile: ResidentVehicleProfileResponse?,
    val longTermProfile: LongTermVehicleProfileResponse?,
)

@Serializable
private data class ResidentVehicleProfileResponse(
    val ownerName: String,
    val identityCardNumber: String,
    val contactPhone: String?,
    val remarks: String?,
)

@Serializable
private data class LongTermVehicleProfileResponse(
    val organizationName: String?,
    val passHolder: String?,
    val passageDetails: String?,
    val remarks: String?,
)
