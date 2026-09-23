package com.jaydocoder.plateview.server.workorder

import com.jaydocoder.plateview.server.auth.isPrimaryAdministrator
import com.jaydocoder.plateview.server.client.ClientPolicyService
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import com.jaydocoder.plateview.server.infrastructure.database.AuditEvent
import com.jaydocoder.plateview.server.infrastructure.database.AuditLogWriterKey
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject

internal fun Application.configureWorkOrderFeature() {
    val dataSource = attributes.getOrNull(DataSourceKey) ?: return
    val service = WorkOrderService(dataSource)
    val policyService = ClientPolicyService(dataSource)
    val collectorToken = System.getenv("WECHAT_COLLECTOR_TOKEN").orEmpty()
    val imageStorage = WorkOrderImageStorage(File(System.getenv("WORK_ORDER_IMAGE_DIR") ?: "./data/work-order-images"))

    routing {
        route("/internal/wechat") {
            post("/messages/batch") {
                call.requireCollector(collectorToken)
                val request = call.receive<WorkOrderMessageBatchRequest>()
                require(request.messages.size in 1..200) { "每批微信消息数量应为1至200条" }
                call.respond(service.ingest(request.toModel()).toResponse())
            }
            post("/sync/reconcile") {
                call.requireCollector(collectorToken)
                val request = call.receive<WechatReconcileRequest>()
                call.respond(service.reconcile(request.toModel()).toResponse())
            }
            post("/heartbeat") {
                call.requireCollector(collectorToken)
                val request = call.receive<WorkOrderHeartbeatRequest>()
                require(request.status in COLLECTOR_STATUSES) { "微信采集状态无效" }
                service.updateHeartbeat(request.toModel())
                call.respond(HttpStatusCode.NoContent)
            }
            post("/images") {
                call.requireCollector(collectorToken)
                val upload = call.receiveImageUpload(imageStorage)
                call.respond(WorkOrderImageUploadResponse(service.upsertImage(upload)))
            }
            post("/attachments") {
                call.requireCollector(collectorToken)
                val upload = call.receiveImageUpload(imageStorage)
                call.respond(WorkOrderImageUploadResponse(service.upsertImage(upload)))
            }
        }

        authenticate("access-token") {
            route("/work-orders") {
                get("/home-search") {
                    val userId = call.requireWorkOrderAccess(service)
                    val keyword = call.request.queryParameters["keyword"].orEmpty()
                    val limits = policyService.resultLimits(userId)
                    val (workOrderResult, messageResult) = supervisorScope {
                        val workOrders = async(Dispatchers.IO) {
                            if (limits.workOrder == 0) Result.success(emptyList())
                            else runSearchSection { service.search(keyword, limits.workOrder + 1) }
                        }
                        val messages = async(Dispatchers.IO) {
                            if (limits.wechatMessage == 0) Result.success(WechatMessagePage(emptyList(), null))
                            else runSearchSection { service.searchMessages(keyword, 0, limits.wechatMessage) }
                        }
                        workOrders.await() to messages.await()
                    }
                    val workOrders = workOrderResult.getOrDefault(emptyList())
                    val messages = messageResult.getOrDefault(WechatMessagePage(emptyList(), null))
                    call.respond(
                        WorkOrderHomeSearchResponse(
                            workOrderCandidates = workOrders.take(limits.workOrder).map(WorkOrderRecord::toResponse),
                            wechatMessages = messages.records.map(WechatMessageRecord::toResponse),
                            workOrderHasMore = workOrders.size > limits.workOrder,
                            wechatMessageHasMore = messages.nextOffset != null,
                            catalogVersion = service.catalogVersion(),
                            workOrderFailed = workOrderResult.isFailure,
                            wechatMessageFailed = messageResult.isFailure,
                        ),
                    )
                }
                get("/search") {
                    val userId = call.requireWorkOrderAccess(service)
                    val limit = policyService.resultLimits(userId).workOrder
                    if (limit == 0) throw WorkOrderPermissionException()
                    val keyword = call.request.queryParameters["keyword"].orEmpty()
                    call.respond(WorkOrderSearchResponse(service.catalogVersion(), service.search(keyword, limit).map(WorkOrderRecord::toResponse)))
                }
                get("/messages/search") {
                    val userId = call.requireWorkOrderAccess(service)
                    val maximum = policyService.resultLimits(userId).wechatMessage
                    if (maximum == 0) throw WorkOrderPermissionException()
                    val keyword = call.request.queryParameters["keyword"].orEmpty()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: maximum).coerceIn(1, maximum)
                    call.respond(service.searchMessages(keyword, offset, limit).toResponse())
                }
                get("/messages/{messageId}") {
                    val userId = call.requireWorkOrderAccess(service)
                    if (policyService.resultLimits(userId).wechatMessage == 0) throw WorkOrderPermissionException()
                    val messageId = call.parameters["messageId"]?.toLongOrNull() ?: throw IllegalArgumentException("微信消息标识无效")
                    call.respond(service.messageDetail(messageId).toResponse())
                }
                get("/messages/{messageId}/attachments/{attachmentId}") {
                    val userId = call.requireWorkOrderAccess(service)
                    if (policyService.resultLimits(userId).wechatMessage == 0) throw WorkOrderPermissionException()
                    val messageId = call.parameters["messageId"]?.toLongOrNull() ?: throw IllegalArgumentException("微信消息标识无效")
                    val attachmentId = call.parameters["attachmentId"]?.toLongOrNull() ?: throw IllegalArgumentException("微信附件标识无效")
                    val requestedVariant = call.request.queryParameters["variant"] ?: "preview"
                    val variant = service.messageAttachmentVariant(messageId, attachmentId, requestedVariant)
                    val file = imageStorage.resolve(variant.relativePath)
                    call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
                    call.response.headers.append(HttpHeaders.ContentType, variant.contentType)
                    call.respondFile(file)
                }
                get("/catalog/version") {
                    val userId = call.requireWorkOrderAccess(service)
                    if (policyService.resultLimits(userId).workOrder == 0) throw WorkOrderPermissionException()
                    call.respond(WorkOrderCatalogVersionResponse(service.catalogVersion()))
                }
                get("/catalog/changes") {
                    val userId = call.requireWorkOrderAccess(service)
                    if (policyService.resultLimits(userId).workOrder == 0) throw WorkOrderPermissionException()
                    val afterVersion = call.request.queryParameters["afterVersion"]?.toLongOrNull() ?: 0L
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
                    call.respond(service.changes(afterVersion, limit).toResponse())
                }
                get("/attachments") {
                    val userId = call.requireWorkOrderAccess(service)
                    val limits = policyService.resultLimits(userId)
                    if (limits.workOrder == 0 && limits.wechatMessage == 0) throw WorkOrderPermissionException()
                    val afterId = call.request.queryParameters["afterId"]?.toLongOrNull() ?: 0L
                    val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 200).coerceIn(1, 500)
                    call.respond(service.attachmentCatalog(afterId, limit, limits.workOrder > 0, limits.wechatMessage > 0).toResponse())
                }
                get("/attachments/manifest") {
                    val userId = call.requireWorkOrderAccess(service)
                    val limits = policyService.resultLimits(userId)
                    val afterId = call.request.queryParameters["afterId"]?.toLongOrNull() ?: 0L
                    val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 200).coerceIn(1, 500)
                    val page = service.attachmentCatalog(afterId, limit, limits.workOrder > 0, limits.wechatMessage > 0)
                    call.respond(page.toManifestResponse(service.catalogVersion()))
                }
                post("/attachments/cache-status") {
                    val userId = call.requireWorkOrderAccess(service)
                    val request = call.receive<AttachmentCacheStatusRequest>()
                    service.saveAttachmentCacheStatus(userId, request)
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/attachments/{attachmentId}") {
                    val userId = call.requireWorkOrderAccess(service)
                    val attachmentId = call.parameters["attachmentId"]?.toLongOrNull()
                        ?: throw IllegalArgumentException("微信附件标识无效")
                    val limits = policyService.resultLimits(userId)
                    val workOrderAttachment = service.attachmentBelongsToWorkOrder(attachmentId)
                    if ((workOrderAttachment && limits.workOrder == 0) || (!workOrderAttachment && limits.wechatMessage == 0)) {
                        throw WorkOrderPermissionException()
                    }
                    val requestedVariant = call.request.queryParameters["variant"] ?: "original"
                    val variant = service.attachmentVariant(attachmentId, requestedVariant)
                    val file = imageStorage.resolve(variant.relativePath)
                    val etag = variant.sha256?.let { "\"$it-$requestedVariant\"" }
                    if (etag != null && call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                        call.respond(HttpStatusCode.NotModified)
                        return@get
                    }
                    etag?.let { call.response.headers.append(HttpHeaders.ETag, it) }
                    call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
                    call.response.headers.append(HttpHeaders.ContentType, variant.contentType)
                    call.respondFile(file)
                }
                get("/{recordId}") {
                    val userId = call.requireWorkOrderAccess(service)
                    if (policyService.resultLimits(userId).workOrder == 0) throw WorkOrderPermissionException()
                    call.respond(service.detail(call.recordId()).toResponse())
                }
                get("/{recordId}/history") {
                    val userId = call.requireWorkOrderAccess(service)
                    if (policyService.resultLimits(userId).workOrder == 0) throw WorkOrderPermissionException()
                    call.respond(WorkOrderHistoryResponse(service.history(call.recordId()).map(WorkOrderRecord::toResponse)))
                }
                get("/{recordId}/images/{imageId}") {
                    val userId = call.requireWorkOrderAccess(service)
                    if (policyService.resultLimits(userId).workOrder == 0) throw WorkOrderPermissionException()
                    val recordId = call.recordId()
                    val imageId = call.parameters["imageId"]?.toLongOrNull() ?: throw IllegalArgumentException("图片标识无效")
                    val variant = service.imageVariant(recordId, imageId, call.request.queryParameters["variant"] ?: "preview")
                    val file = imageStorage.resolve(variant.relativePath)
                    val etag = variant.sha256?.let { "\"$it-${call.request.queryParameters["variant"] ?: "preview"}\"" }
                    if (etag != null && call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                        call.respond(HttpStatusCode.NotModified)
                        return@get
                    }
                    etag?.let { call.response.headers.append(HttpHeaders.ETag, it) }
                    call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
                    call.response.headers.append(HttpHeaders.ContentType, variant.contentType)
                    call.respondFile(file)
                }
            }

            route("/admin/wechat-sync") {
                get("/status") {
                    call.requirePrimaryAdministrator(dataSource)
                    call.respond(WechatSyncStatusResponse(service.syncStatus().map(WechatSourceStatus::toResponse)))
                }
                get("/cache-status") {
                    call.requirePrimaryAdministrator(dataSource)
                    call.respond(service.attachmentCacheStatusSummary().toResponse())
                }
                get("/issues") {
                    call.requirePrimaryAdministrator(dataSource)
                    val stats = service.syncAttachmentAssociationStats()
                    call.respond(
                        WechatSyncIssuesResponse(
                            items = service.syncIssues().map(WechatSyncIssue::toResponse),
                            totalAttachmentCount = stats.total,
                            completedAttachmentCount = stats.completed,
                            pendingAttachmentCount = stats.pending,
                            integrity = service.syncIntegrity().toResponse(),
                        ),
                    )
                }
                get("/records/search") {
                    call.requirePrimaryAdministrator(dataSource)
                    val keyword = call.request.queryParameters["keyword"].orEmpty()
                    call.respond(WorkOrderSearchResponse(service.catalogVersion(), service.search(keyword).take(20).map(WorkOrderRecord::toResponse)))
                }
                put("/records/{recordId}") {
                    val actorId = call.requirePrimaryAdministrator(dataSource)
                    val recordId = call.recordId()
                    val corrected = service.correctRecord(recordId, call.receive<WorkOrderCorrectionRequest>().toModel())
                    call.auditWorkOrderAdmin(actorId, "WECHAT_RECORD_CORRECT", "WORK_ORDER", recordId)
                    call.respond(corrected.toResponse())
                }
                put("/images/{imageId}/association") {
                    val actorId = call.requirePrimaryAdministrator(dataSource)
                    val imageId = call.parameters["imageId"]?.toLongOrNull() ?: throw IllegalArgumentException("图片标识无效")
                    service.associateImage(imageId, call.receive<ImageAssociationRequest>().recordId)
                    call.auditWorkOrderAdmin(actorId, "WECHAT_IMAGE_ASSOCIATE", "WORK_ORDER_IMAGE", imageId)
                    call.respond(HttpStatusCode.NoContent)
                }
                delete("/images/{imageId}/association") {
                    val actorId = call.requirePrimaryAdministrator(dataSource)
                    val imageId = call.parameters["imageId"]?.toLongOrNull() ?: throw IllegalArgumentException("图片标识无效")
                    service.associateImage(imageId, null)
                    call.auditWorkOrderAdmin(actorId, "WECHAT_IMAGE_DISASSOCIATE", "WORK_ORDER_IMAGE", imageId)
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/images/{imageId}/ignore") {
                    val actorId = call.requirePrimaryAdministrator(dataSource)
                    val imageId = call.parameters["imageId"]?.toLongOrNull() ?: throw IllegalArgumentException("图片标识无效")
                    service.ignoreImage(imageId)
                    call.auditWorkOrderAdmin(actorId, "WECHAT_IMAGE_IGNORE", "WORK_ORDER_IMAGE", imageId)
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/images/{imageId}") {
                    call.requirePrimaryAdministrator(dataSource)
                    val imageId = call.parameters["imageId"]?.toLongOrNull() ?: throw IllegalArgumentException("附件标识无效")
                    val requestedVariant = call.request.queryParameters["variant"] ?: "preview"
                    val variant = service.adminImageVariant(imageId, requestedVariant)
                    val file = imageStorage.resolve(variant.relativePath)
                    call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
                    call.response.headers.append(HttpHeaders.ContentType, variant.contentType)
                    call.respondFile(file)
                }
                get("/passage-senders") {
                    call.requirePrimaryAdministrator(dataSource)
                    call.respond(WechatPassageSendersResponse(service.passageSenders().map(WechatPassageSender::toResponse)))
                }
                put("/passage-senders/{senderUsername}") {
                    val actorId = call.requirePrimaryAdministrator(dataSource)
                    val senderUsername = call.parameters["senderUsername"].orEmpty()
                    val request = call.receive<WechatPassageSenderRequest>()
                    service.savePassageSender(WechatPassageSender(senderUsername, request.originalDisplayName, request.displayAlias, request.enabled))
                    call.auditWorkOrderAdmin(actorId, "WECHAT_PASSAGE_SENDER_UPDATE", "WECHAT_PASSAGE_SENDER", null)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.requireCollector(expectedToken: String) {
    val supplied = request.headers["X-PlateView-Collector-Token"].orEmpty()
    val valid = expectedToken.isNotBlank() && MessageDigest.isEqual(expectedToken.toByteArray(), supplied.toByteArray())
    if (!valid) {
        throw CollectorAuthenticationException()
    }
}

private fun ApplicationCall.requireWorkOrderAccess(service: WorkOrderService): Long {
    val userId = principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
    if (!service.hasAccess(userId)) throw WorkOrderPermissionException()
    return userId
}

private suspend fun ApplicationCall.requirePrimaryAdministrator(dataSource: javax.sql.DataSource): Long {
    val userId = principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
    if (!dataSource.connection.use { it.isPrimaryAdministrator(userId) }) throw WorkOrderPermissionException()
    return userId
}

private fun ApplicationCall.auditWorkOrderAdmin(actorId: Long, action: String, targetType: String, targetId: Long?) {
    application.attributes.getOrNull(AuditLogWriterKey)?.write(
        AuditEvent(actorId, action, targetType, targetId, "SUCCESS", callId, buildJsonObject { }),
    )
}

private fun ApplicationCall.recordId(): Long = parameters["recordId"]?.toLongOrNull()
    ?: throw IllegalArgumentException("微信车单标识无效")

private suspend fun ApplicationCall.receiveImageUpload(storage: WorkOrderImageStorage): WorkOrderImageUpload {
    val fields = mutableMapOf<String, String>()
    val files = mutableMapOf<String, StoredUpload>()
    val multipart = receiveMultipart()
    while (true) {
        val part = multipart.readPart() ?: break
        try {
            when (part) {
                is PartData.FormItem -> part.name?.let { fields[it] = part.value }
                is PartData.FileItem -> {
                    val name = part.name
                    if (name in IMAGE_VARIANTS) {
                        val bytes = part.provider().readRemaining(MAX_IMAGE_PART_BYTES + 1L).readBytes()
                        require(bytes.size <= MAX_IMAGE_PART_BYTES) { "微信图片文件过大" }
                        files[requireNotNull(name)] = StoredUpload(bytes, part.contentType?.toString() ?: "application/octet-stream")
                    }
                }
                else -> Unit
            }
        } finally {
            part.dispose()
        }
    }
    val sourceKey = fields.required("sourceKey")
    val attachmentId = fields.required("localAttachmentId")
    val sourceQuality = fields["sourceQuality"]?.uppercase() ?: "UNKNOWN"
    require(sourceQuality in ATTACHMENT_SOURCE_QUALITIES) { "微信附件资源质量无效" }
    val paths = storage.save(sourceKey, attachmentId, files)
    val availability = when {
        files["original"] != null -> "AVAILABLE"
        files["preview"] != null || files["thumbnail"] != null -> "THUMBNAIL_ONLY"
        else -> "METADATA_ONLY"
    }
    return WorkOrderImageUpload(
        sourceKey = sourceKey,
        sourceName = fields.required("sourceName"),
        localAttachmentId = attachmentId,
        localMessageId = fields["localMessageId"],
        senderUsername = fields["senderUsername"],
        senderDisplay = fields["senderDisplay"],
        sentAt = Instant.parse(fields.required("sentAt")),
        sha256 = fields["sha256"],
        originalContentType = files["original"]?.contentType ?: fields["originalContentType"],
        originalSize = files["original"]?.bytes?.size?.toLong(),
        originalPath = paths["original"],
        previewPath = paths["preview"],
        previewSize = files["preview"]?.bytes?.size?.toLong(),
        thumbnailPath = paths["thumbnail"],
        thumbnailSize = files["thumbnail"]?.bytes?.size?.toLong(),
        availability = availability,
        sourceQuality = sourceQuality,
        attachmentKind = fields["attachmentKind"] ?: "IMAGE",
        fileName = fields["fileName"],
        pageCount = fields["pageCount"]?.toIntOrNull(),
    )
}

private class WorkOrderImageStorage(private val root: File) {
    init { require(root.exists() || root.mkdirs()) { "无法创建微信车单图片目录" } }

    fun save(sourceKey: String, attachmentId: String, files: Map<String, StoredUpload>): Map<String, String> {
        val directory = File(root, "${safe(sourceKey)}/${safe(attachmentId)}").also {
            require(it.exists() || it.mkdirs()) { "无法创建微信车单图片目录" }
        }
        return files.mapValues { (variant, upload) ->
            val extension = when (upload.contentType.substringBefore(';')) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "application/pdf" -> "pdf"
                else -> "bin"
            }
            val contentHash = MessageDigest.getInstance("SHA-256").digest(upload.bytes)
                .joinToString("") { byte -> "%02x".format(byte) }
                .take(16)
            val relative = "${safe(sourceKey)}/${safe(attachmentId)}/$variant-$contentHash.$extension"
            val target = File(root, relative)
            val temporary = File(directory, "$variant.tmp")
            temporary.writeBytes(upload.bytes)
            require(temporary.renameTo(target) || run { temporary.copyTo(target, overwrite = true); temporary.delete(); true }) { "无法保存微信车单图片" }
            relative
        }
    }

    fun resolve(relativePath: String): File {
        val file = File(root, relativePath).canonicalFile
        require(file.path.startsWith(root.canonicalFile.path + File.separator) && file.isFile) { "微信车单图片不存在" }
        return file
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._@-]"), "_")
}

private data class StoredUpload(val bytes: ByteArray, val contentType: String)
private fun Map<String, String>.required(name: String): String = get(name)?.takeIf(String::isNotBlank)
    ?: throw IllegalArgumentException("缺少图片字段：$name")

@Serializable private data class WorkOrderMessageBatchRequest(
    val batchId: String? = null,
    val syncRunId: String? = null,
    val sourceKey: String,
    val sourceName: String,
    val fromTimestamp: Long = 0,
    val fromLocalMessageId: String = "",
    val toTimestamp: Long? = null,
    val toLocalMessageId: String? = null,
    val messageCount: Int? = null,
    val batchSha256: String? = null,
    val messages: List<WorkOrderIncomingMessageRequest>,
) {
    fun toModel() = WorkOrderMessageBatch(
        batchId, syncRunId, sourceKey, sourceName, fromTimestamp, fromLocalMessageId,
        toTimestamp, toLocalMessageId, messageCount, batchSha256, messages.map(WorkOrderIncomingMessageRequest::toModel),
    )
}
@Serializable private data class WorkOrderIncomingMessageRequest(val localMessageId: String, val senderUsername: String? = null, val senderDisplay: String? = null, val senderGroupNickname: String? = null, val rawContent: String, val sentAt: String, val contentFingerprint: String) {
    fun toModel() = WorkOrderIncomingMessage(localMessageId, senderUsername, senderDisplay, senderGroupNickname, rawContent, Instant.parse(sentAt), contentFingerprint)
}
@Serializable private data class WorkOrderHeartbeatRequest(val sourceKey: String, val sourceName: String, val status: String, val latestMessageAt: String? = null, val backlogCount: Int = 0, val errorCode: String? = null) {
    fun toModel() = WorkOrderHeartbeat(sourceKey, sourceName, status, latestMessageAt?.let(Instant::parse), backlogCount, errorCode)
}
@Serializable private data class WechatReconcileRequest(
    val sourceKey: String,
    val from: String,
    val to: String,
    val localCount: Int,
    val localDigest: String,
    val localMessageIds: List<String> = emptyList(),
) {
    fun toModel() = WechatReconcile(sourceKey, Instant.parse(from), Instant.parse(to), localCount, localDigest, localMessageIds)
}
@Serializable private data class WechatReconcileResponse(
    val sourceKey: String,
    val serverCount: Int,
    val countMatch: Boolean,
    val digestMatch: Boolean,
    val missingLocalMessageIds: List<String>,
    val duplicateCandidates: List<String>,
    val missingAttachments: List<String>,
    val metadataOnlyAttachments: List<String>,
)
@Serializable private data class WorkOrderIngestResponse(
    val inserted: Int,
    val duplicate: Int,
    val catalogVersion: Long,
    val batchId: String? = null,
    val status: String = "ACCEPTED",
    val acceptedCount: Int = 0,
    val acceptedThroughTimestamp: Long? = null,
    val acceptedThroughLocalMessageId: String? = null,
)
private fun WorkOrderIngestResult.toResponse() = WorkOrderIngestResponse(
    inserted = inserted,
    duplicate = duplicate,
    catalogVersion = catalogVersion,
    batchId = batchId,
    acceptedCount = inserted + duplicate,
    acceptedThroughTimestamp = acceptedThroughTimestamp,
    acceptedThroughLocalMessageId = acceptedThroughLocalMessageId,
)
private fun WechatReconcileResult.toResponse() = WechatReconcileResponse(
    sourceKey, serverCount, countMatch, digestMatch, missingLocalMessageIds, duplicateCandidates, missingAttachments, metadataOnlyAttachments,
)
@Serializable private data class WorkOrderSearchResponse(val catalogVersion: Long, val candidates: List<WorkOrderResponse>)
@Serializable private data class WorkOrderHomeSearchResponse(
    val workOrderCandidates: List<WorkOrderResponse>,
    val wechatMessages: List<WechatMessageResponse>,
    val workOrderHasMore: Boolean,
    val wechatMessageHasMore: Boolean,
    val catalogVersion: Long,
    val workOrderFailed: Boolean,
    val wechatMessageFailed: Boolean,
)

private inline fun <T> runSearchSection(block: () -> T): Result<T> = runCatching(block).onFailure {
    if (it is CancellationException) throw it
}
@Serializable private data class WorkOrderCatalogVersionResponse(val catalogVersion: Long)
@Serializable private data class WorkOrderChangeResponse(val catalogVersion: Long, val nextVersion: Long, val hasMore: Boolean, val records: List<WorkOrderResponse>)
private fun WorkOrderChangePage.toResponse() = WorkOrderChangeResponse(catalogVersion, nextVersion, hasMore, records.map(WorkOrderRecord::toResponse))
@Serializable private data class WorkOrderHistoryResponse(val records: List<WorkOrderResponse>)
@Serializable private data class WorkOrderResponse(
    val id: Long, val orderNumber: String?, val rawPlate: String?, val normalizedPlate: String?, val vehicleType: String?, val declaredPeople: Int?,
    val rawValidTime: String?, val location: String?, val verificationMethod: String?, val reason: String?, val remarks: String?, val status: String,
    val parseQuality: String, val catalogRevision: Long, val rawContent: String, val sentAt: String, val sourceKey: String, val sourceName: String,
    val senderUsername: String?, val senderDisplay: String?, val senderGroupNickname: String?, val people: List<WorkOrderPersonResponse>, val images: List<WorkOrderImageResponse>,
    val vehicles: List<WorkOrderVehicleResponse>, val displayName: String?,
)
private fun WorkOrderRecord.toResponse() = WorkOrderResponse(
    id, orderNumber, rawPlate, normalizedPlate, vehicleType, declaredPeople, rawValidTime, location, verificationMethod,
    reason, remarks, status, parseQuality, catalogRevision, rawContent, sentAt.toString(), sourceKey, sourceName,
    senderUsername, senderDisplay, senderGroupNickname,
    people.map { WorkOrderPersonResponse(it.rawLine, it.name, it.identityNumber) },
    images.map { WorkOrderImageResponse(it.id, it.sha256, it.contentType, it.originalSize, it.previewAvailable, it.thumbnailAvailable, it.availability, it.kind, it.fileName, it.pageCount, it.sourceQuality) },
    vehicles.map { WorkOrderVehicleResponse(it.rawDescription, it.rawPlate, it.normalizedPlate, it.vehicleType) },
    displayName,
)
@Serializable private data class WorkOrderPersonResponse(val rawLine: String, val name: String?, val identityNumber: String?)
@Serializable private data class WorkOrderVehicleResponse(val rawDescription: String, val rawPlate: String, val normalizedPlate: String, val vehicleType: String?)
@Serializable private data class WorkOrderImageResponse(
    val id: Long, val sha256: String?, val contentType: String?, val originalSize: Long?, val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean, val availability: String, val kind: String = "IMAGE", val fileName: String? = null,
    val pageCount: Int? = null, val sourceQuality: String = "UNKNOWN",
)
@Serializable private data class WechatMessagePageResponse(val records: List<WechatMessageResponse>, val nextOffset: Int?)
private fun WechatMessagePage.toResponse() = WechatMessagePageResponse(records.map(WechatMessageRecord::toResponse), nextOffset)
@Serializable private data class WechatMessageResponse(
    val id: Long,
    val businessType: String,
    val rawContent: String,
    val matchedSnippet: String,
    val sentAt: String,
    val sourceKey: String,
    val sourceName: String,
    val senderUsername: String?,
    val senderDisplay: String?,
    val senderGroupNickname: String?,
    val displayName: String,
    val plateNumbers: List<String>,
    val attachments: List<WorkOrderAttachmentResponse>,
)
private fun WechatMessageRecord.toResponse() = WechatMessageResponse(
    id, businessType, rawContent, matchedSnippet, sentAt.toString(), sourceKey, sourceName, senderUsername,
    senderDisplay, senderGroupNickname, displayName, plateNumbers, attachments.map(WorkOrderAttachment::toResponse),
)
@Serializable private data class WorkOrderAttachmentResponse(
    val id: Long,
    val kind: String,
    val fileName: String?,
    val sha256: String?,
    val contentType: String?,
    val originalSize: Long?,
    val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean,
    val availability: String,
    val pageCount: Int?,
    val sourceQuality: String = "UNKNOWN",
)
private fun WorkOrderAttachment.toResponse() = WorkOrderAttachmentResponse(
    id, kind, fileName, sha256, contentType, originalSize, previewAvailable, thumbnailAvailable, availability, pageCount, sourceQuality,
)
@Serializable private data class WorkOrderAttachmentCatalogResponse(
    val items: List<WorkOrderAttachmentCatalogItemResponse>,
    val nextAfterId: Long?,
)
@Serializable private data class WorkOrderAttachmentCatalogItemResponse(
    val id: Long,
    val kind: String,
    val fileName: String?,
    val sha256: String?,
    val contentType: String?,
    val originalSize: Long?,
    val originalAvailable: Boolean,
    val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean,
    val availability: String,
    val sourceQuality: String,
    val pageCount: Int?,
)
private fun WorkOrderAttachmentCatalogPage.toResponse() = WorkOrderAttachmentCatalogResponse(
    items = items.map {
        WorkOrderAttachmentCatalogItemResponse(
            it.id, it.kind, it.fileName, it.sha256, it.contentType, it.originalSize, it.originalAvailable,
            it.previewAvailable, it.thumbnailAvailable, it.availability, it.sourceQuality, it.pageCount,
        )
    },
    nextAfterId = items.lastOrNull()?.id?.takeIf { hasMore },
)
private fun WorkOrderAttachmentCatalogPage.toManifestResponse(revision: Long) = WorkOrderAttachmentManifestResponse(
    manifestRevision = revision,
    items = items.map { item ->
        WorkOrderAttachmentManifestItemResponse(
            item.id, item.kind, item.fileName, item.originalSize, item.sha256, item.sourceQuality,
            item.originalAvailable, item.previewAvailable, item.thumbnailAvailable,
        )
    },
    nextAfterId = items.lastOrNull()?.id?.takeIf { hasMore },
)
@Serializable private data class WechatPassageSendersResponse(val items: List<WechatPassageSenderResponse>)
@Serializable private data class WechatPassageSenderResponse(val senderUsername: String, val originalDisplayName: String?, val displayAlias: String, val enabled: Boolean)
private fun WechatPassageSender.toResponse() = WechatPassageSenderResponse(senderUsername, originalDisplayName, displayAlias, enabled)
@Serializable private data class WechatPassageSenderRequest(val originalDisplayName: String? = null, val displayAlias: String, val enabled: Boolean = true)
@Serializable private data class WorkOrderImageUploadResponse(val imageId: Long)
@Serializable private data class WechatSyncStatusResponse(val sources: List<WechatSourceStatusResponse>)
@Serializable private data class AttachmentCacheStatusSummaryResponse(
    val clientCount: Int,
    val completedCount: Int,
    val pendingCount: Int,
    val failedCount: Int,
    val totalBytes: Long,
)
@Serializable private data class WechatSourceStatusResponse(val sourceKey: String, val displayName: String, val status: String, val latestMessageAt: String?, val lastHeartbeatAt: String?, val lastUploadedAt: String?, val backlogCount: Int, val errorCode: String?)
private fun WechatSourceStatus.toResponse() = WechatSourceStatusResponse(sourceKey, displayName, status, latestMessageAt?.toString(), lastHeartbeatAt?.toString(), lastUploadedAt?.toString(), backlogCount, errorCode)
private fun AttachmentCacheStatusSummary.toResponse() = AttachmentCacheStatusSummaryResponse(clientCount, completedCount, pendingCount, failedCount, totalBytes)
@Serializable private data class WechatSyncIssuesResponse(
    val items: List<WechatSyncIssueResponse>,
    val totalAttachmentCount: Int,
    val completedAttachmentCount: Int,
    val pendingAttachmentCount: Int,
    val integrity: WechatSyncIntegrityResponse,
)
@Serializable private data class WechatSyncIntegrityResponse(
    val unconfirmedBatchCount: Int,
    val retryTaskCount: Int,
    val metadataOnlyAttachmentCount: Int,
    val failedTaskCount: Int,
    val status: String,
)
private fun WechatSyncIntegrity.toResponse() = WechatSyncIntegrityResponse(unconfirmedBatchCount, retryTaskCount, metadataOnlyAttachmentCount, failedTaskCount, status)
@Serializable private data class WechatSyncIssueResponse(
    val type: String, val recordId: Long?, val imageId: Long?, val sourceName: String, val sentAt: String, val summary: String,
    val attachmentKind: String?, val fileName: String?, val pageCount: Int?, val sha256: String?, val sourceQuality: String,
    val candidates: List<WechatAttachmentCandidateResponse>,
)
@Serializable private data class WechatAttachmentCandidateResponse(val recordId: Long, val orderNumber: String?, val sentAt: String, val summary: String)
private fun WechatSyncIssue.toResponse() = WechatSyncIssueResponse(
    type, recordId, imageId, sourceName, sentAt.toString(), summary, attachmentKind, fileName, pageCount, sha256, sourceQuality,
    candidates.map { WechatAttachmentCandidateResponse(it.recordId, it.orderNumber, it.sentAt.toString(), it.summary) },
)
@Serializable private data class WorkOrderCorrectionRequest(
    val orderNumber: String? = null, val rawPlate: String? = null, val vehicleType: String? = null, val declaredPeople: Int? = null,
    val rawValidTime: String? = null, val location: String? = null, val verificationMethod: String? = null,
    val reason: String? = null, val remarks: String? = null, val status: String = "ACTIVE",
) {
    fun toModel() = WorkOrderCorrection(orderNumber, rawPlate, vehicleType, declaredPeople, rawValidTime, location, verificationMethod, reason, remarks, status)
}
@Serializable private data class ImageAssociationRequest(val recordId: Long)
@Serializable private data class WorkOrderAttachmentManifestResponse(
    val manifestRevision: Long,
    val items: List<WorkOrderAttachmentManifestItemResponse>,
    val nextAfterId: Long?,
)
@Serializable private data class WorkOrderAttachmentManifestItemResponse(
    val attachmentId: Long,
    val kind: String,
    val fileName: String?,
    val originalSize: Long?,
    val sha256: String?,
    val sourceQuality: String,
    val originalAvailable: Boolean,
    val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean,
)
@Serializable internal data class AttachmentCacheStatusRequest(
    val clientInstanceId: String,
    val manifestRevision: Long,
    val items: List<AttachmentCacheStatusItemRequest>,
)
@Serializable internal data class AttachmentCacheStatusItemRequest(
    val attachmentId: Long,
    val variant: String,
    val status: String,
    val downloadedBytes: Long = 0,
    val expectedSize: Long? = null,
    val sha256: String? = null,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
)
internal class CollectorAuthenticationException : RuntimeException("微信采集凭据无效")

private val COLLECTOR_STATUSES = setOf("OFFLINE", "WECHAT_NOT_READY", "CATCHING_UP", "HEALTHY", "KEY_MISSING", "UPLOAD_FAILED")
private val IMAGE_VARIANTS = setOf("original", "preview", "thumbnail")
private val ATTACHMENT_SOURCE_QUALITIES = setOf("UNKNOWN", "THUMBNAIL", "HIGH_DEFINITION", "ORIGINAL")
private const val MAX_IMAGE_PART_BYTES = 30 * 1024 * 1024
