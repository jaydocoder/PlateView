package com.jaydocoder.plateview.server.vehicle

import com.jaydocoder.plateview.server.infrastructure.database.AuditEvent
import com.jaydocoder.plateview.server.infrastructure.database.AuditLogWriterKey
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import com.jaydocoder.plateview.server.client.ClientPolicyService
import io.ktor.http.HttpStatusCode
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
    val policyService = ClientPolicyService(dataSource)
    routing {
        authenticate("access-token") {
            route("/vehicles") {
                get("/search") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                    val accessScope = service.accessScope(actorId)
                    val limit = policyService.resultLimits(actorId).vehicle
                    if (limit == 0) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("message" to "匹配车辆访问已关闭"))
                        return@get
                    }
                    val candidates = service.search(call.request.queryParameters["keyword"].orEmpty(), accessScope, limit)
                    call.respond(VehicleSearchResponse(service.catalogVersion(accessScope), candidates.map(VehicleSearchCandidate::toResponse)))
                }
                get("/catalog/version") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                    if (policyService.resultLimits(actorId).vehicle == 0) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("message" to "匹配车辆访问已关闭"))
                        return@get
                    }
                    call.respond(VehicleCatalogVersionResponse(service.catalogVersion(service.accessScope(actorId))))
                }
                get("/catalog") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                    if (policyService.resultLimits(actorId).vehicle == 0) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("message" to "匹配车辆访问已关闭"))
                        return@get
                    }
                    val accessScope = service.accessScope(actorId)
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 500
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val page = service.catalog(accessScope, limit, offset)
                    call.respond(VehicleCatalogResponse(page.revision, page.total, page.items.map(VehicleSearchCandidate::toResponse)))
                }
                get("/catalog/full") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                    if (policyService.resultLimits(actorId).vehicle == 0) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("message" to "匹配车辆访问已关闭"))
                        return@get
                    }
                    val accessScope = service.accessScope(actorId)
                    val version = call.request.queryParameters["version"]?.toLongOrNull()
                        ?: throw IllegalArgumentException("缺少目录版本")
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val page = service.fullCatalog(accessScope, version, limit, offset)
                    call.respond(
                        VehicleFullCatalogResponse(
                            catalogVersion = page.revision,
                            total = page.total,
                            items = page.items.map { it.toResponse(page.revision) },
                        ),
                    )
                }
                get("/catalog/changes") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                    if (policyService.resultLimits(actorId).vehicle == 0) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("message" to "匹配车辆访问已关闭"))
                        return@get
                    }
                    val accessScope = service.accessScope(actorId)
                    val afterRevision = call.request.queryParameters["afterRevision"]?.toLongOrNull() ?: 0L
                    val afterId = call.request.queryParameters["afterId"]?.toLongOrNull() ?: 0L
                    val targetRevision = call.request.queryParameters["targetRevision"]?.toLongOrNull()
                        ?: throw IllegalArgumentException("缺少目标目录版本")
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
                    call.respond(service.changes(accessScope, afterRevision, afterId, targetRevision, limit).toResponse())
                }
                get("/{vehicleId}") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                    if (policyService.resultLimits(actorId).vehicle == 0) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("message" to "匹配车辆访问已关闭"))
                        return@get
                    }
                    val vehicleId = call.vehicleId()
                    val accessScope = service.accessScope(actorId)
                    val detail = service.findDetail(vehicleId, accessScope)
                    if (detail == null) {
                        call.auditVehicleDetail(actorId, vehicleId, null, "FAILURE")
                        throw VehicleNotFoundException()
                    }
                    if (call.request.headers[LOCAL_QUERY_EVENT_MODE_HEADER] != LOCAL_QUERY_EVENT_MODE) {
                        service.recordQueryEvent(actorId, detail)
                    }
                    call.auditVehicleDetail(actorId, vehicleId, detail.normalizedPlate, "SUCCESS")
                    call.respond(detail.toResponse(service.catalogVersion(accessScope)))
                }
            }
        }
    }
}

private fun ApplicationCall.vehicleId(): Long = parameters["vehicleId"]?.toLongOrNull()
    ?: throw IllegalArgumentException("车辆标识无效")

private const val LOCAL_QUERY_EVENT_MODE_HEADER = "X-PlateView-Query-Event-Mode"
private const val LOCAL_QUERY_EVENT_MODE = "LOCAL_BATCH"

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
    organizationName = organizationName,
    plateColor = plateColor,
    status = status,
)

private fun VehicleDetail.toResponse(catalogVersion: Long): VehicleDetailResponse = VehicleDetailResponse(
    catalogVersion = catalogVersion,
    id = id,
    plateNumber = plateNumber,
    normalizedPlate = normalizedPlate,
    category = category.name,
    categoryLabel = category.displayName,
    vehicleType = vehicleType,
    status = status,
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
private data class VehicleCatalogChangeResponse(
    val catalogVersion: Long,
    val nextRevision: Long,
    val nextId: Long,
    val hasMore: Boolean,
    val fullSyncRequired: Boolean,
    val items: List<VehicleCatalogChangeItemResponse>,
)

@Serializable
private data class VehicleCatalogChangeItemResponse(
    val revision: Long,
    val entityId: Long,
    val operation: String,
    val record: VehicleDetailResponse?,
)

private fun VehicleCatalogChangePage.toResponse() = VehicleCatalogChangeResponse(
    catalogVersion = catalogVersion,
    nextRevision = nextRevision,
    nextId = nextId,
    hasMore = hasMore,
    fullSyncRequired = fullSyncRequired,
    items = items.map { item ->
        VehicleCatalogChangeItemResponse(
            revision = item.revision,
            entityId = item.entityId,
            operation = item.operation,
            record = item.record?.toResponse(catalogVersion),
        )
    },
)

@Serializable
private data class VehicleSearchCandidateResponse(
    val id: Long,
    val plateNumber: String,
    val category: String,
    val categoryLabel: String,
    val organizationName: String?,
    val plateColor: String?,
    val status: String,
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
    val status: String,
    val attributes: JsonObject,
    val residentProfile: ResidentVehicleProfileResponse?,
    val longTermProfile: LongTermVehicleProfileResponse?,
)

@Serializable
private data class ResidentVehicleProfileResponse(
    val ownerName: String?,
    val identityCardNumber: String?,
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
