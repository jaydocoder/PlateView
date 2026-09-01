package com.jaydocoder.plateview.server.admin

import com.jaydocoder.plateview.server.auth.requireAdministrator
import com.jaydocoder.plateview.server.auth.receiveAvatarUpload
import com.jaydocoder.plateview.server.infrastructure.database.AuditEvent
import com.jaydocoder.plateview.server.infrastructure.database.AuditLogWriterKey
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import com.jaydocoder.plateview.server.vehicle.VehicleCategory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

internal fun Application.configureAdminManagementFeature() {
    val dataSource = attributes.getOrNull(DataSourceKey) ?: return
    val service = AdminManagementService(dataSource)

    routing {
        authenticate("access-token") {
            route("/admin") {
                route("/vehicles") {
                    get("/creation-capabilities") {
                        val actorId = call.requireAdministrator() ?: return@get
                        call.respond(service.vehicleCreationCapabilities(actorId).toResponse())
                    }
                    get {
                        val actorId = call.requireAdministrator() ?: return@get
                        val page = service.listVehicles(
                            keyword = call.request.queryParameters["keyword"],
                            limit = call.pageLimit(),
                            offset = call.pageOffset(),
                        )
                        call.auditAdmin(actorId, "VEHICLE_LIST", "VEHICLE", null)
                        call.respond(AdminVehicleListResponse(page.items.map(AdminVehicleListItem::toResponse), page.total))
                    }
                    post {
                        val actorId = call.requireAdministrator() ?: return@post
                        val vehicle = try {
                            service.createVehicle(call.receive<AdminVehicleUpsertRequest>().toCommand(), actorId)
                        } catch (exception: AdminValidationException) {
                            call.auditAdmin(actorId, "VEHICLE_CREATE", "VEHICLE", null, resultStatus = "FAILURE")
                            throw exception
                        }
                        call.auditAdmin(actorId, "VEHICLE_CREATE", "VEHICLE", vehicle.id)
                        call.respond(HttpStatusCode.Created, vehicle.toResponse())
                    }
                    get("/{vehicleId}") {
                        val actorId = call.requireAdministrator() ?: return@get
                        val vehicle = service.getVehicle(call.vehicleId())
                        call.auditAdmin(actorId, "VEHICLE_VIEW", "VEHICLE", vehicle.id)
                        call.respond(vehicle.toResponse())
                    }
                    put("/{vehicleId}") {
                        val actorId = call.requireAdministrator() ?: return@put
                        val vehicle = service.updateVehicle(
                            vehicleId = call.vehicleId(),
                            command = call.receive<AdminVehicleUpsertRequest>().toCommand(),
                            expectedVersion = call.expectedVersion(),
                            actorId = actorId,
                        )
                        call.auditAdmin(actorId, "VEHICLE_UPDATE", "VEHICLE", vehicle.id)
                        call.respond(vehicle.toResponse())
                    }
                    delete("/{vehicleId}") {
                        val actorId = call.requireAdministrator() ?: return@delete
                        val vehicle = service.deactivateVehicle(call.vehicleId(), call.expectedVersion(), actorId)
                        call.auditAdmin(actorId, "VEHICLE_DEACTIVATE", "VEHICLE", vehicle.id)
                        call.respond(vehicle.toResponse())
                    }
                }

                route("/users") {
                    get {
                        val actorId = call.requireAdministrator() ?: return@get
                        val canManageProfiles = service.isPrimaryAdministrator(actorId)
                        val users = service.listUsers(call.pageLimit(), call.pageOffset())
                        call.auditAdmin(actorId, "USER_LIST", "USER", null)
                        call.respond(AdminUserListResponse(users.map { it.toResponse(canManageProfiles) }))
                    }
                    post {
                        val actorId = call.requireAdministrator() ?: return@post
                        val user = service.createUser(call.receive<AdminUserCreateRequest>().toCommand(), actorId)
                        call.auditAdmin(actorId, "USER_CREATE", "USER", user.id)
                        call.respond(HttpStatusCode.Created, user.toResponse(service.isPrimaryAdministrator(actorId)))
                    }
                    put("/{userId}") {
                        val actorId = call.requireAdministrator() ?: return@put
                        val user = service.updateUser(
                            userId = call.userId(),
                            command = call.receive<AdminUserUpdateRequest>().toCommand(),
                            expectedVersion = call.expectedVersion(),
                            actorId = actorId,
                        )
                        call.auditAdmin(actorId, "USER_UPDATE", "USER", user.id)
                        call.respond(user.toResponse(service.isPrimaryAdministrator(actorId)))
                    }
                    post("/{userId}/avatar") {
                        val actorId = call.requireAdministrator() ?: return@post
                        val user = service.updateUserAvatar(
                            userId = call.userId(),
                            avatar = call.receiveAvatarUpload(),
                            expectedVersion = call.expectedVersion(),
                            actorId = actorId,
                        )
                        call.auditAdmin(actorId, "USER_AVATAR_UPDATE", "USER", user.id)
                        call.respond(user.toResponse(service.isPrimaryAdministrator(actorId)))
                    }
                    post("/{userId}/avatar/delete") {
                        val actorId = call.requireAdministrator() ?: return@post
                        val user = service.deleteUserAvatar(call.userId(), call.expectedVersion(), actorId)
                        call.auditAdmin(actorId, "USER_AVATAR_DELETE", "USER", user.id)
                        call.respond(user.toResponse(service.isPrimaryAdministrator(actorId)))
                    }
                    get("/{userId}/avatar") {
                        call.requireAdministrator() ?: return@get
                        val avatar = service.userAvatar(call.userId())
                        if (avatar == null) {
                            call.respond(HttpStatusCode.NotFound)
                        } else {
                            call.response.headers.append(io.ktor.http.HttpHeaders.ContentType, avatar.contentType)
                            call.respondBytes(avatar.content)
                        }
                    }
                }

                get("/imports") {
                    val actorId = call.requireAdministrator() ?: return@get
                    val batches = service.listImportBatches(call.pageLimit(), call.pageOffset())
                    call.auditAdmin(actorId, "IMPORT_LIST", "IMPORT_BATCH", null)
                    call.respond(AdminImportBatchListResponse(batches.map(AdminImportBatchSummary::toResponse)))
                }

                get("/audit") {
                    val actorId = call.requireAdministrator() ?: return@get
                    val page = service.listAuditEntries(
                        filter = AdminAuditFilter(
                            range = AdminAuditRange.fromRequest(call.request.queryParameters["range"]),
                            actorId = call.request.queryParameters["actorId"]?.toLongOrNull()
                                ?: call.request.queryParameters["actorId"]?.let { throw AdminValidationException("审计操作人标识无效") },
                            actionType = call.request.queryParameters["actionType"],
                            result = AdminAuditResult.fromRequest(call.request.queryParameters["result"]),
                            keyword = call.request.queryParameters["keyword"],
                        ),
                        limit = call.auditPageLimit(),
                        offset = call.pageOffset(),
                    )
                    call.auditAdmin(actorId, "AUDIT_LIST", "AUDIT", null)
                    call.respond(
                        AdminAuditListResponse(
                            items = page.items.map(AdminAuditEntry::toResponse),
                            total = page.summary.total,
                            summary = page.summary.toResponse(),
                            actors = page.actors.map(AdminAuditActor::toResponse),
                            actionTypes = page.actionTypes,
                        ),
                    )
                }
            }
        }
    }
}

private fun ApplicationCall.vehicleId(): Long = parameters["vehicleId"]?.toLongOrNull()
    ?: throw AdminValidationException("车辆标识无效")

private fun ApplicationCall.userId(): Long = parameters["userId"]?.toLongOrNull()
    ?: throw AdminValidationException("账号标识无效")

private fun ApplicationCall.expectedVersion(): Int = request.headers["If-Match-Version"]?.toIntOrNull()
    ?: throw AdminValidationException("缺少或无效的版本号")

private fun ApplicationCall.pageLimit(): Int = request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT
private fun ApplicationCall.pageOffset(): Int = request.queryParameters["offset"]?.toIntOrNull() ?: 0
private fun ApplicationCall.auditPageLimit(): Int = request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_AUDIT_PAGE_LIMIT

private fun ApplicationCall.auditAdmin(
    actorId: Long,
    action: String,
    targetType: String,
    targetId: Long?,
    resultStatus: String = "SUCCESS",
) {
    application.attributes.getOrNull(AuditLogWriterKey)?.write(
        AuditEvent(
            actorId = actorId,
            actionType = action,
            targetType = targetType,
            targetId = targetId,
            resultStatus = resultStatus,
            requestId = callId,
            metadata = buildJsonObject { },
        ),
    )
}

@Serializable
private data class AdminVehicleUpsertRequest(
    val plateNumber: String,
    val category: String,
    val vehicleType: String? = null,
    val status: String = "ACTIVE",
    val attributes: JsonObject = JsonObject(emptyMap()),
    val residentProfile: AdminResidentProfileRequest? = null,
    val longTermProfile: AdminLongTermProfileRequest? = null,
) {
    fun toCommand(): AdminVehicleCommand = AdminVehicleCommand(
        plateNumber = plateNumber,
        category = parseEnum(category, "车辆类别") { AdminValidationException("车辆类别无效") },
        vehicleType = vehicleType,
        status = parseEnum(status, "车辆状态") { AdminValidationException("车辆状态无效") },
        attributes = attributes,
        residentProfile = residentProfile?.toModel(),
        longTermProfile = longTermProfile?.toModel(),
    )
}

@Serializable
private data class AdminResidentProfileRequest(
    val ownerName: String,
    val identityCardNumber: String,
    val contactPhone: String? = null,
    val remarks: String? = null,
) {
    fun toModel(): AdminResidentProfile = AdminResidentProfile(ownerName, identityCardNumber, contactPhone, remarks)
}

@Serializable
private data class AdminLongTermProfileRequest(
    val organizationName: String? = null,
    val passHolder: String? = null,
    val passageDetails: String? = null,
    val remarks: String? = null,
) {
    fun toModel(): AdminLongTermProfile = AdminLongTermProfile(organizationName, passHolder, passageDetails, remarks)
}

@Serializable
private data class AdminUserCreateRequest(
    val username: String,
    val password: String,
    val role: String,
    val realName: String? = null,
    val scheduleAccessEnabled: Boolean? = null,
) {
    fun toCommand(): AdminUserCreateCommand = AdminUserCreateCommand(
        username = username,
        password = password,
        role = parseEnum(role, "账号角色") { AdminValidationException("账号角色无效") },
        realName = realName,
        scheduleAccessEnabled = scheduleAccessEnabled ?: false,
    )
}

@Serializable
private data class AdminUserUpdateRequest(
    val role: String,
    val status: String,
    val username: String? = null,
    val password: String? = null,
    val realName: String? = null,
    val scheduleAccessEnabled: Boolean? = null,
) {
    fun toCommand(): AdminUserUpdateCommand = AdminUserUpdateCommand(
        role = parseEnum(role, "账号角色") { AdminValidationException("账号角色无效") },
        status = parseEnum(status, "账号状态") { AdminValidationException("账号状态无效") },
        username = username,
        password = password,
        realName = realName,
        scheduleAccessEnabled = scheduleAccessEnabled,
    )
}

private inline fun <reified T : Enum<T>> parseEnum(value: String, name: String, failure: () -> RuntimeException): T = try {
    enumValueOf<T>(value)
} catch (_: IllegalArgumentException) {
    throw failure()
}

private fun AdminVehicleListItem.toResponse(): AdminVehicleListItemResponse = AdminVehicleListItemResponse(
    id = id,
    plateNumber = plateNumber,
    category = category.name,
    categoryLabel = category.displayName,
    status = status.name,
    version = version,
    vehicleType = vehicleType,
)

private fun AdminVehicleRecord.toResponse(): AdminVehicleResponse = AdminVehicleResponse(
    id = id,
    plateNumber = plateNumber,
    normalizedPlate = normalizedPlate,
    category = category.name,
    categoryLabel = category.displayName,
    status = status.name,
    version = version,
    vehicleType = vehicleType,
    attributes = attributes,
    residentProfile = residentProfile?.let { AdminResidentProfileResponse(it.ownerName, it.identityCardNumber, it.contactPhone, it.remarks) },
    longTermProfile = longTermProfile?.let { AdminLongTermProfileResponse(it.organizationName, it.passHolder, it.passageDetails, it.remarks) },
)

private fun AdminUserRecord.toResponse(includeRealName: Boolean): AdminUserResponse = AdminUserResponse(
    id = id,
    username = username,
    role = role.name,
    status = status.name,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt,
    avatarVersion = avatarVersion,
    hasAvatar = hasAvatar,
    realName = if (includeRealName) realName else null,
    scheduleAccessEnabled = scheduleAccessEnabled,
)

private fun AdminImportBatchSummary.toResponse(): AdminImportBatchSummaryResponse = AdminImportBatchSummaryResponse(
    id = id,
    sourceFileName = sourceFileName,
    status = status,
    totalRows = totalRows,
    validRows = validRows,
    duplicateRows = duplicateRows,
    errorRows = errorRows,
    version = version,
    createdAt = createdAt,
    publishedAt = publishedAt,
    rollbackAt = rollbackAt,
)

private fun AdminAuditEntry.toResponse(): AdminAuditEntryResponse = AdminAuditEntryResponse(
    id = id,
    actorUsername = actorUsername,
    actionType = actionType,
    targetType = targetType,
    targetId = targetId,
    resultStatus = resultStatus,
    createdAt = createdAt,
)

private fun AdminAuditSummary.toResponse(): AdminAuditSummaryResponse = AdminAuditSummaryResponse(
    total = total,
    successCount = successCount,
    abnormalCount = abnormalCount,
    activeActorCount = activeActorCount,
)

private fun AdminAuditActor.toResponse(): AdminAuditActorResponse = AdminAuditActorResponse(
    id = id,
    username = username,
)

@Serializable private data class AdminVehicleListResponse(val items: List<AdminVehicleListItemResponse>, val total: Int)
@Serializable private data class AdminVehicleCreationCapabilitiesResponse(
    val creatableCategories: List<String>,
    val canChangeVehicleCategory: Boolean,
)
@Serializable private data class AdminVehicleListItemResponse(val id: Long, val plateNumber: String, val category: String, val categoryLabel: String, val status: String, val version: Int, val vehicleType: String?)
@Serializable private data class AdminVehicleResponse(val id: Long, val plateNumber: String, val normalizedPlate: String, val category: String, val categoryLabel: String, val status: String, val version: Int, val vehicleType: String?, val attributes: JsonObject, val residentProfile: AdminResidentProfileResponse?, val longTermProfile: AdminLongTermProfileResponse?)
@Serializable private data class AdminResidentProfileResponse(val ownerName: String, val identityCardNumber: String, val contactPhone: String?, val remarks: String?)
@Serializable private data class AdminLongTermProfileResponse(val organizationName: String?, val passHolder: String?, val passageDetails: String?, val remarks: String?)
@Serializable private data class AdminUserListResponse(val items: List<AdminUserResponse>)
@Serializable private data class AdminUserResponse(val id: Long, val username: String, val role: String, val status: String, val version: Int, val createdAt: String?, val updatedAt: String?, val avatarVersion: Long, val hasAvatar: Boolean, val realName: String?, val scheduleAccessEnabled: Boolean)
@Serializable private data class AdminImportBatchListResponse(val items: List<AdminImportBatchSummaryResponse>)
@Serializable private data class AdminImportBatchSummaryResponse(val id: Long, val sourceFileName: String, val status: String, val totalRows: Int, val validRows: Int, val duplicateRows: Int, val errorRows: Int, val version: Int, val createdAt: String?, val publishedAt: String?, val rollbackAt: String?)
@Serializable private data class AdminAuditListResponse(
    val items: List<AdminAuditEntryResponse>,
    val total: Int,
    val summary: AdminAuditSummaryResponse,
    val actors: List<AdminAuditActorResponse>,
    val actionTypes: List<String>,
)
@Serializable private data class AdminAuditEntryResponse(val id: Long, val actorUsername: String?, val actionType: String, val targetType: String, val targetId: Long?, val resultStatus: String, val createdAt: String)
@Serializable private data class AdminAuditSummaryResponse(val total: Int, val successCount: Int, val abnormalCount: Int, val activeActorCount: Int)

private fun AdminVehicleCreationCapabilities.toResponse() = AdminVehicleCreationCapabilitiesResponse(
    creatableCategories = creatableCategories.map(VehicleCategory::name),
    canChangeVehicleCategory = canChangeVehicleCategory,
)
@Serializable private data class AdminAuditActorResponse(val id: Long, val username: String?)

private const val DEFAULT_PAGE_LIMIT = 100
private const val DEFAULT_AUDIT_PAGE_LIMIT = 50
