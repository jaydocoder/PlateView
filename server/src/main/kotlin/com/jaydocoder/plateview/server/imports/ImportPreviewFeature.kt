package com.jaydocoder.plateview.server.imports

import com.jaydocoder.plateview.server.auth.requireAdministrator
import com.jaydocoder.plateview.server.infrastructure.database.AuditEvent
import com.jaydocoder.plateview.server.infrastructure.database.AuditLogWriterKey
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import com.jaydocoder.plateview.server.infrastructure.web.ApiErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.io.readByteArray

internal fun Application.configureImportPreviewFeature() {
    val dataSource = attributes.getOrNull(DataSourceKey) ?: return
    val service = ImportWorkflowService(dataSource)
    routing {
        authenticate("access-token") {
            route("/admin/imports") {
                post("/preview") {
                    val actorId = call.requireAdministrator() ?: return@post
                    val file = call.receiveMultipart().readExcelFile()
                    if (file == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("IMPORT_FILE_REQUIRED", "请上传Excel文件", call.callId))
                        return@post
                    }
                    if (!file.name.isExcelFile()) {
                        call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("IMPORT_FILE_INVALID", "仅支持xlsx或xls格式的Excel文件", call.callId))
                        return@post
                    }
                    val batch = service.preview(file.name, file.bytes, actorId)
                    call.auditImport(actorId, "IMPORT_PREVIEW", batch.id, batch.stats)
                    call.respond(HttpStatusCode.Created, batch.toResponse())
                }

                get("/{batchId}") {
                    val actorId = call.requireAdministrator() ?: return@get
                    val batchId = call.batchId()
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val batch = service.getBatch(batchId, limit, offset)
                    call.auditImport(actorId, "IMPORT_VIEW", batch.id, batch.stats)
                    call.respond(batch.toResponse())
                }

                post("/{batchId}/rows/resolutions") {
                    val actorId = call.requireAdministrator() ?: return@post
                    val batchId = call.batchId()
                    val request = call.receive<ImportRowResolutionsRequest>()
                    val changes = request.rows.map { row ->
                        ImportRowResolutionChange(
                            rowId = row.rowId,
                            resolution = try {
                                ImportResolution.valueOf(row.resolution)
                            } catch (_: IllegalArgumentException) {
                                throw IllegalArgumentException("导入行处置无效")
                            },
                        )
                    }
                    val batch = service.updateResolutions(batchId, changes, actorId)
                    call.auditImport(actorId, "IMPORT_RESOLUTION", batch.id, batch.stats)
                    call.respond(batch.toResponse())
                }

                post("/{batchId}/publish") {
                    val actorId = call.requireAdministrator() ?: return@post
                    val batch = service.publish(call.batchId(), actorId)
                    call.auditImport(actorId, "IMPORT_PUBLISH", batch.id, batch.stats)
                    call.respond(batch.toResponse())
                }

                post("/{batchId}/rollback") {
                    val actorId = call.requireAdministrator() ?: return@post
                    val batch = service.rollback(call.batchId(), actorId)
                    call.auditImport(actorId, "IMPORT_ROLLBACK", batch.id, batch.stats)
                    call.respond(batch.toResponse())
                }
            }
        }
    }
}

private suspend fun MultiPartData.readExcelFile(): UploadedExcel? {
    var result: UploadedExcel? = null
    while (true) {
        val part = readPart() ?: break
        try {
            if (part is PartData.FileItem && result == null) {
                val bytes = part.provider().readRemaining(MAX_UPLOAD_BYTES.toLong() + 1).readByteArray()
                result = UploadedExcel(part.originalFileName ?: "upload.xlsx", bytes)
            }
        } finally {
            part.dispose()
        }
    }
    return result
}

private fun ApplicationCall.batchId(): Long = parameters["batchId"]?.toLongOrNull()
    ?: throw IllegalArgumentException("导入批次标识无效")

private fun ApplicationCall.auditImport(actorId: Long, action: String, batchId: Long, stats: ImportBatchStats) {
    application.attributes.getOrNull(AuditLogWriterKey)?.write(
        AuditEvent(
            actorId = actorId,
            actionType = action,
            targetType = "IMPORT_BATCH",
            targetId = batchId,
            resultStatus = "SUCCESS",
            requestId = callId,
            metadata = buildJsonObject {
                put("totalRows", JsonPrimitive(stats.totalRows))
                put("errorRows", JsonPrimitive(stats.errorRows))
                put("pendingReviewRows", JsonPrimitive(stats.pendingReviewRows))
            },
        ),
    )
}

private fun String.isExcelFile(): Boolean = lowercase().let { it.endsWith(".xlsx") || it.endsWith(".xls") }

private fun ImportBatchView.toResponse(): ImportBatchResponse = ImportBatchResponse(
    id = id,
    sourceFileName = sourceFileName,
    status = status,
    stats = ImportBatchStatsResponse(
        totalRows = stats.totalRows,
        newRows = stats.newRows,
        updateRows = stats.updateRows,
        duplicateRows = stats.duplicateRows,
        errorRows = stats.errorRows,
        warningRows = stats.warningRows,
        publishableRows = stats.publishableRows,
        pendingReviewRows = stats.pendingReviewRows,
    ),
    createdAt = createdAt,
    publishedAt = publishedAt,
    rollbackAt = rollbackAt,
    rows = rows.map { row ->
        ImportRowResponse(
            id = row.id,
            sourceSheetName = row.sourceSheetName,
            sourceRowNumber = row.sourceRowNumber,
            sourceItemIndex = row.sourceItemIndex,
            plateNumber = row.plateNumber,
            normalizedPlate = row.normalizedPlate,
            category = row.category,
            primarySubject = row.primarySubject,
            resultStatus = row.resultStatus,
            plannedAction = row.plannedAction,
            resolution = row.resolution,
            errorMessage = row.errorMessage,
            warningMessage = row.warningMessage,
        )
    },
)

private data class UploadedExcel(val name: String, val bytes: ByteArray)

@Serializable
private data class ImportRowResolutionsRequest(val rows: List<ImportRowResolutionRequest>)

@Serializable
private data class ImportRowResolutionRequest(val rowId: Long, val resolution: String)

@Serializable
private data class ImportBatchResponse(
    val id: Long,
    val sourceFileName: String,
    val status: String,
    val stats: ImportBatchStatsResponse,
    val createdAt: String?,
    val publishedAt: String?,
    val rollbackAt: String?,
    val rows: List<ImportRowResponse>,
)

@Serializable
private data class ImportBatchStatsResponse(
    val totalRows: Int,
    val newRows: Int,
    val updateRows: Int,
    val duplicateRows: Int,
    val errorRows: Int,
    val warningRows: Int,
    val publishableRows: Int,
    val pendingReviewRows: Int,
)

@Serializable
private data class ImportRowResponse(
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
)

private const val DEFAULT_PAGE_SIZE = 200
private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
