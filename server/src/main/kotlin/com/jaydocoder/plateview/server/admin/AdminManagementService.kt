package com.jaydocoder.plateview.server.admin

import com.jaydocoder.plateview.server.auth.AvatarUpload
import com.jaydocoder.plateview.server.auth.isPrimaryAdministrator
import com.jaydocoder.plateview.server.vehicle.VehicleCategory
import com.jaydocoder.plateview.server.vehicle.normalizePlate
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Instant
import javax.sql.DataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.mindrot.jbcrypt.BCrypt

internal class AdminManagementService(
    private val dataSource: DataSource,
) {
    fun isPrimaryAdministrator(actorId: Long): Boolean = dataSource.connection.use { it.isPrimaryAdministrator(actorId) }

    fun listVehicles(keyword: String?, limit: Int, offset: Int): AdminVehiclePage {
        val normalizedKeyword = keyword?.takeIf(String::isNotBlank)?.let(::normalizePlate)
        return dataSource.connection.use { connection ->
            val items = connection.prepareStatement(SELECT_VEHICLES).use { statement ->
                statement.setString(1, normalizedKeyword?.let { "%$it%" })
                statement.setString(2, normalizedKeyword?.let { "%$it%" })
                statement.setInt(3, limit.coerceIn(1, MAX_PAGE_SIZE))
                statement.setInt(4, offset.coerceAtLeast(0))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toVehicleListItem())
                    }
                }
            }
            val total = connection.prepareStatement(COUNT_VEHICLES).use { statement ->
                statement.setString(1, normalizedKeyword?.let { "%$it%" })
                statement.setString(2, normalizedKeyword?.let { "%$it%" })
                statement.executeQuery().use { result -> result.next(); result.getInt(1) }
            }
            AdminVehiclePage(items = items, total = total)
        }
    }

    fun getVehicle(vehicleId: Long): AdminVehicleRecord = dataSource.connection.use { connection ->
        connection.prepareStatement(SELECT_VEHICLE).use { statement ->
            statement.setLong(1, vehicleId.requirePositive("车辆标识"))
            statement.executeQuery().use { result ->
                if (result.next()) result.toVehicleRecord() else throw AdminResourceNotFoundException("车辆不存在")
            }
        }
    }

    fun vehicleCreationCapabilities(actorId: Long): AdminVehicleCreationCapabilities = dataSource.connection.use { connection ->
        AdminVehicleCreationPolicy.capabilities(connection.isPrimaryAdministrator(actorId))
    }

    fun createVehicle(command: AdminVehicleCommand, actorId: Long): AdminVehicleRecord {
        command.validate()
        val vehicleId = try {
            inTransaction { connection ->
                AdminVehicleCreationPolicy.requireCreationAllowed(
                    isPrimaryAdministrator = connection.isPrimaryAdministrator(actorId),
                    category = command.category,
                )
                connection.prepareStatement(INSERT_VEHICLE, Statement.RETURN_GENERATED_KEYS).use { statement ->
                    statement.setString(1, command.plateNumber.trim())
                    statement.setString(2, normalizePlate(command.plateNumber))
                    statement.setString(3, command.category.name)
                    statement.setNullableString(4, command.vehicleType.trimToNull())
                    statement.setString(5, command.status.name)
                    statement.setString(6, Json.encodeToString(JsonObject.serializer(), command.attributes))
                    statement.setLong(7, actorId)
                    statement.setLong(8, actorId)
                    statement.executeUpdate()
                    statement.generatedKeys.use { keys ->
                        if (!keys.next()) error("创建车辆后未返回标识")
                        keys.getLong(1)
                    }
                }.also { vehicleId -> upsertProfiles(connection, vehicleId, command, actorId) }
            }
        } catch (exception: SQLException) {
            throw exception.toAdminException()
        }
        return getVehicle(vehicleId)
    }

    fun updateVehicle(vehicleId: Long, command: AdminVehicleCommand, expectedVersion: Int, actorId: Long): AdminVehicleRecord {
        vehicleId.requirePositive("车辆标识")
        expectedVersion.requireNonNegative("车辆版本")
        command.validate()
        try {
            inTransaction { connection ->
                val existingCategory = connection.findVehicleCategoryForUpdate(vehicleId)
                    ?: throw AdminResourceNotFoundException("车辆不存在")
                AdminVehicleCreationPolicy.requireUpdateAllowed(
                    isPrimaryAdministrator = connection.isPrimaryAdministrator(actorId),
                    originalCategory = existingCategory,
                    requestedCategory = command.category,
                )
                val changed = connection.prepareStatement(UPDATE_VEHICLE).use { statement ->
                    statement.setString(1, command.plateNumber.trim())
                    statement.setString(2, normalizePlate(command.plateNumber))
                    statement.setString(3, command.category.name)
                    statement.setNullableString(4, command.vehicleType.trimToNull())
                    statement.setString(5, command.status.name)
                    statement.setString(6, Json.encodeToString(JsonObject.serializer(), command.attributes))
                    statement.setLong(7, actorId)
                    statement.setLong(8, vehicleId)
                    statement.setInt(9, expectedVersion)
                    statement.executeUpdate()
                }
                if (changed == 0) throw vehicleWriteFailure(connection, vehicleId)
                upsertProfiles(connection, vehicleId, command, actorId)
            }
        } catch (exception: SQLException) {
            throw exception.toAdminException()
        }
        return getVehicle(vehicleId)
    }

    fun deactivateVehicle(vehicleId: Long, expectedVersion: Int, actorId: Long): AdminVehicleRecord {
        vehicleId.requirePositive("车辆标识")
        expectedVersion.requireNonNegative("车辆版本")
        inTransaction { connection ->
            val changed = connection.prepareStatement(DEACTIVATE_VEHICLE).use { statement ->
                statement.setLong(1, actorId)
                statement.setLong(2, vehicleId)
                statement.setInt(3, expectedVersion)
                statement.executeUpdate()
            }
            if (changed == 0) throw vehicleWriteFailure(connection, vehicleId)
        }
        return getVehicle(vehicleId)
    }

    fun listUsers(limit: Int, offset: Int): List<AdminUserRecord> = dataSource.connection.use { connection ->
        connection.prepareStatement(SELECT_USERS).use { statement ->
            statement.setInt(1, limit.coerceIn(1, MAX_PAGE_SIZE))
            statement.setInt(2, offset.coerceAtLeast(0))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.toUserRecord())
                }
            }
        }
    }

    fun createUser(command: AdminUserCreateCommand, actorId: Long): AdminUserRecord {
        command.validate()
        val userId = try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(INSERT_USER, Statement.RETURN_GENERATED_KEYS).use { statement ->
                    statement.setString(1, command.username.trim())
                    statement.setString(2, BCrypt.hashpw(command.password, BCrypt.gensalt()))
                    statement.setString(3, command.role.name)
                    statement.setLong(4, actorId)
                    statement.setLong(5, actorId)
                    statement.executeUpdate()
                    statement.generatedKeys.use { keys ->
                        if (!keys.next()) error("创建账号后未返回标识")
                        keys.getLong(1)
                    }
                }
            }
        } catch (exception: SQLException) {
            if (exception.sqlState == UNIQUE_VIOLATION) throw AdminValidationException("账号名称已存在")
            throw exception
        }
        return getUser(userId)
    }

    fun updateUser(userId: Long, command: AdminUserUpdateCommand, expectedVersion: Int, actorId: Long): AdminUserRecord {
        userId.requirePositive("账号标识")
        expectedVersion.requireNonNegative("账号版本")
        command.validate()
        inTransaction { connection ->
            val existing = connection.findUserForUpdate(userId) ?: throw AdminResourceNotFoundException("账号不存在")
            val profileChangeRequested = command.username != null || command.password != null || command.realName != null
            AdminUserProfilePolicy.requireModificationAllowed(
                canManageOtherUserProfiles = connection.isPrimaryAdministrator(actorId),
                profileChangeRequested = profileChangeRequested,
            )
            command.username?.trim()?.let { username ->
                connection.prepareStatement("SELECT 1 FROM users WHERE username = ? AND id <> ?").use { statement ->
                    statement.setString(1, username)
                    statement.setLong(2, userId)
                    statement.executeQuery().use { result ->
                        if (result.next()) throw AdminValidationException("账号名称已存在")
                    }
                }
            }
            if (userId == actorId && (command.role != AdminRole.ADMIN || command.status != AdminUserStatus.ACTIVE)) {
                throw AdminValidationException("不能停用或降级当前登录管理员账号")
            }
            if (existing.role == AdminRole.ADMIN && existing.status == AdminUserStatus.ACTIVE &&
                (command.role != AdminRole.ADMIN || command.status != AdminUserStatus.ACTIVE) &&
                connection.activeAdministratorCount() <= 1
            ) {
                throw AdminConflictException("至少保留一个启用的管理员账号")
            }
            val changed = connection.prepareStatement(UPDATE_USER).use { statement ->
                statement.setNullableString(1, command.username?.trim())
                statement.setNullableString(2, command.password?.let { BCrypt.hashpw(it, BCrypt.gensalt()) })
                statement.setNullableString(3, command.realName?.trim())
                statement.setString(4, command.role.name)
                statement.setString(5, command.status.name)
                statement.setBoolean(6, profileChangeRequested)
                statement.setLong(7, actorId)
                statement.setLong(8, userId)
                statement.setInt(9, expectedVersion)
                statement.executeUpdate()
            }
            if (changed == 0) throw AdminConflictException("账号已被其他管理员修改，请刷新后重试")
            if (profileChangeRequested) connection.revokeUserSessions(userId)
        }
        return getUser(userId)
    }

    fun updateUserAvatar(userId: Long, avatar: AvatarUpload, expectedVersion: Int, actorId: Long): AdminUserRecord {
        userId.requirePositive("账号标识")
        expectedVersion.requireNonNegative("账号版本")
        inTransaction { connection ->
            connection.requirePrimaryAdministrator(actorId)
            val changed = connection.prepareStatement(UPDATE_USER_AVATAR).use { statement ->
                statement.setBytes(1, avatar.content)
                statement.setString(2, avatar.contentType)
                statement.setLong(3, actorId)
                statement.setLong(4, userId)
                statement.setInt(5, expectedVersion)
                statement.executeUpdate()
            }
            if (changed == 0) throw AdminConflictException("账号已被其他管理员修改，请刷新后重试")
        }
        return getUser(userId)
    }

    fun deleteUserAvatar(userId: Long, expectedVersion: Int, actorId: Long): AdminUserRecord {
        userId.requirePositive("账号标识")
        expectedVersion.requireNonNegative("账号版本")
        inTransaction { connection ->
            connection.requirePrimaryAdministrator(actorId)
            val changed = connection.prepareStatement(DELETE_USER_AVATAR).use { statement ->
                statement.setLong(1, actorId)
                statement.setLong(2, userId)
                statement.setInt(3, expectedVersion)
                statement.executeUpdate()
            }
            if (changed == 0) throw AdminConflictException("账号已被其他管理员修改，请刷新后重试")
        }
        return getUser(userId)
    }

    fun userAvatar(userId: Long): AdminAvatarContent? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT avatar_content, avatar_content_type FROM users WHERE id = ?").use { statement ->
            statement.setLong(1, userId.requirePositive("账号标识"))
            statement.executeQuery().use { result ->
                if (!result.next()) throw AdminResourceNotFoundException("账号不存在")
                result.getBytes("avatar_content")?.let { AdminAvatarContent(it, result.getString("avatar_content_type")) }
            }
        }
    }

    fun listImportBatches(limit: Int, offset: Int): List<AdminImportBatchSummary> = dataSource.connection.use { connection ->
        connection.prepareStatement(SELECT_IMPORT_BATCHES).use { statement ->
            statement.setInt(1, limit.coerceIn(1, MAX_PAGE_SIZE))
            statement.setInt(2, offset.coerceAtLeast(0))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            AdminImportBatchSummary(
                                id = result.getLong("id"),
                                sourceFileName = result.getString("source_file_name"),
                                status = result.getString("status"),
                                totalRows = result.getInt("total_rows"),
                                validRows = result.getInt("valid_rows"),
                                duplicateRows = result.getInt("duplicate_rows"),
                                errorRows = result.getInt("error_rows"),
                                version = result.getInt("version"),
                                createdAt = result.getTimestamp("created_at").toIsoString(),
                                publishedAt = result.getTimestamp("published_at").toIsoString(),
                                rollbackAt = result.getTimestamp("rollback_at").toIsoString(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun listAuditEntries(filter: AdminAuditFilter, limit: Int, offset: Int): AdminAuditPage = dataSource.connection.use { connection ->
        val normalizedFilter = filter.normalized()
        val items = connection.queryAuditEntries(normalizedFilter, limit, offset)
        val summary = connection.queryAuditSummary(normalizedFilter)
        val actors = connection.queryAuditActors(normalizedFilter.copy(actorId = null, actionType = null))
        val actionTypes = connection.queryAuditActionTypes(normalizedFilter.copy(actionType = null))
        AdminAuditPage(items, summary, actors, actionTypes)
    }

    private fun getUser(userId: Long): AdminUserRecord = dataSource.connection.use { connection ->
        connection.prepareStatement(SELECT_USER).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { result ->
                if (result.next()) result.toUserRecord() else throw AdminResourceNotFoundException("账号不存在")
            }
        }
    }

    private fun upsertProfiles(connection: Connection, vehicleId: Long, command: AdminVehicleCommand, actorId: Long) {
        command.residentProfile?.let { profile ->
            connection.prepareStatement(UPSERT_RESIDENT_PROFILE).use { statement ->
                statement.setLong(1, vehicleId)
                statement.setString(2, profile.ownerName.trim())
                statement.setString(3, profile.identityCardNumber.trim())
                statement.setNullableString(4, profile.contactPhone.trimToNull())
                statement.setNullableString(5, profile.remarks.trimToNull())
                statement.setLong(6, actorId)
                statement.setLong(7, actorId)
                statement.executeUpdate()
            }
            connection.prepareStatement(DELETE_LONG_TERM_PROFILE).use { statement ->
                statement.setLong(1, vehicleId)
                statement.executeUpdate()
            }
        }
        command.longTermProfile?.let { profile ->
            connection.prepareStatement(UPSERT_LONG_TERM_PROFILE).use { statement ->
                statement.setLong(1, vehicleId)
                statement.setNullableString(2, profile.organizationName.trimToNull())
                statement.setNullableString(3, profile.passHolder.trimToNull())
                statement.setNullableString(4, profile.passageDetails.trimToNull())
                statement.setNullableString(5, profile.remarks.trimToNull())
                statement.setLong(6, actorId)
                statement.setLong(7, actorId)
                statement.executeUpdate()
            }
            connection.prepareStatement(DELETE_RESIDENT_PROFILE).use { statement ->
                statement.setLong(1, vehicleId)
                statement.executeUpdate()
            }
        }
    }

    private fun vehicleWriteFailure(connection: Connection, vehicleId: Long): RuntimeException = connection.prepareStatement(
        "SELECT 1 FROM vehicles WHERE id = ?",
    ).use { statement ->
        statement.setLong(1, vehicleId)
        statement.executeQuery().use { result ->
            if (result.next()) AdminConflictException("车辆已被其他管理员修改，请刷新后重试")
            else AdminResourceNotFoundException("车辆不存在")
        }
    }

    private fun <T> inTransaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        val originalAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (throwable: Throwable) {
            connection.rollback()
            throw throwable
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }

    private fun ResultSet.toVehicleListItem(): AdminVehicleListItem = AdminVehicleListItem(
        id = getLong("id"),
        plateNumber = getString("plate_number"),
        category = VehicleCategory.valueOf(getString("category")),
        status = AdminVehicleStatus.valueOf(getString("status")),
        version = getInt("version"),
        vehicleType = getString("vehicle_type"),
    )

    private fun ResultSet.toVehicleRecord(): AdminVehicleRecord = AdminVehicleRecord(
        id = getLong("id"),
        plateNumber = getString("plate_number"),
        normalizedPlate = getString("normalized_plate"),
        category = VehicleCategory.valueOf(getString("category")),
        status = AdminVehicleStatus.valueOf(getString("status")),
        version = getInt("version"),
        vehicleType = getString("vehicle_type"),
        attributes = Json.parseToJsonElement(getString("attributes")).let { it as? JsonObject ?: JsonObject(emptyMap()) },
        residentProfile = getObject("resident_profile_id")?.let {
            AdminResidentProfile(
                ownerName = getString("owner_name").orEmpty(),
                identityCardNumber = getString("identity_card_number").orEmpty(),
                contactPhone = getString("contact_phone"),
                remarks = getString("resident_remarks"),
            )
        },
        longTermProfile = getObject("long_term_profile_id")?.let {
            AdminLongTermProfile(
                organizationName = getString("organization_name"),
                passHolder = getString("pass_holder"),
                passageDetails = getString("passage_details"),
                remarks = getString("long_term_remarks"),
            )
        },
    )

    private fun ResultSet.toUserRecord(): AdminUserRecord = AdminUserRecord(
        id = getLong("id"),
        username = getString("username"),
        role = AdminRole.valueOf(getString("role")),
        status = AdminUserStatus.valueOf(getString("status")),
        version = getInt("version"),
        createdAt = getTimestamp("created_at").toIsoString(),
        updatedAt = getTimestamp("updated_at").toIsoString(),
        avatarVersion = getLong("avatar_version"),
        hasAvatar = getBytes("avatar_content") != null,
        realName = getString("real_name"),
    )

    private fun Connection.findUserForUpdate(userId: Long): AdminUserRecord? = prepareStatement(
        "SELECT id, username, role, status, version, created_at, updated_at, avatar_version, avatar_content, real_name FROM users WHERE id = ? FOR UPDATE",
    ).use { statement ->
        statement.setLong(1, userId)
        statement.executeQuery().use { result -> if (result.next()) result.toUserRecord() else null }
    }

    private fun Connection.findVehicleCategoryForUpdate(vehicleId: Long): VehicleCategory? = prepareStatement(
        "SELECT category FROM vehicles WHERE id = ? FOR UPDATE",
    ).use { statement ->
        statement.setLong(1, vehicleId)
        statement.executeQuery().use { result ->
            if (result.next()) VehicleCategory.valueOf(result.getString("category")) else null
        }
    }

    private fun Connection.requirePrimaryAdministrator(actorId: Long) {
        if (!isPrimaryAdministrator(actorId)) throw AdminPermissionException("仅admin账号可以修改其他账号的用户名、密码或头像")
    }

    private fun Connection.revokeUserSessions(userId: Long) {
        prepareStatement("UPDATE refresh_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL").use { statement ->
            statement.setLong(1, userId)
            statement.executeUpdate()
        }
    }

    private fun Connection.activeAdministratorCount(): Int = prepareStatement(
        "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'",
    ).use { statement ->
        statement.executeQuery().use { result -> result.next(); result.getInt(1) }
    }

    private fun Connection.queryAuditEntries(filter: AdminAuditFilter, limit: Int, offset: Int): List<AdminAuditEntry> =
        prepareStatement(AUDIT_SELECT_PREFIX + filter.whereClause() + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?").use { statement ->
            var parameterIndex = statement.bindAuditFilter(filter)
            statement.setInt(parameterIndex++, limit.coerceIn(1, MAX_PAGE_SIZE))
            statement.setInt(parameterIndex, offset.coerceAtLeast(0))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.toAuditEntry())
                }
            }
        }

    private fun Connection.queryAuditSummary(filter: AdminAuditFilter): AdminAuditSummary =
        prepareStatement(AUDIT_SUMMARY_PREFIX + filter.whereClause()).use { statement ->
            statement.bindAuditFilter(filter)
            statement.executeQuery().use { result ->
                result.next()
                AdminAuditSummary(
                    total = result.getInt("total"),
                    successCount = result.getInt("success_count"),
                    abnormalCount = result.getInt("abnormal_count"),
                    activeActorCount = result.getInt("active_actor_count"),
                )
            }
        }

    private fun Connection.queryAuditActors(filter: AdminAuditFilter): List<AdminAuditActor> =
        prepareStatement(AUDIT_ACTORS_PREFIX + filter.whereClause() + " AND a.actor_id IS NOT NULL GROUP BY a.actor_id, u.username ORDER BY u.username, a.actor_id").use { statement ->
            statement.bindAuditFilter(filter)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(AdminAuditActor(result.getLong("id"), result.getString("username")))
                }
            }
        }

    private fun Connection.queryAuditActionTypes(filter: AdminAuditFilter): List<String> =
        prepareStatement(AUDIT_ACTION_TYPES_PREFIX + filter.whereClause() + " GROUP BY a.action_type ORDER BY a.action_type").use { statement ->
            statement.bindAuditFilter(filter)
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.getString("action_type")) } }
        }

    private fun ResultSet.toAuditEntry(): AdminAuditEntry = AdminAuditEntry(
        id = getLong("id"),
        actorUsername = getString("username"),
        actionType = getString("action_type"),
        targetType = getString("target_type"),
        targetId = getLongOrNull("target_id"),
        resultStatus = getString("result_status"),
        createdAt = getTimestamp("created_at").toIsoString().orEmpty(),
    )

    private fun AdminAuditFilter.whereClause(): String = buildString {
        append(" WHERE 1 = 1")
        range.intervalLiteral?.let { append(" AND a.created_at >= now() - INTERVAL '$it'") }
        if (actorId != null) append(" AND a.actor_id = ?")
        if (actionType != null) append(" AND a.action_type = ?")
        when (result) {
            AdminAuditResult.SUCCESS -> append(" AND a.result_status = 'SUCCESS'")
            AdminAuditResult.ABNORMAL -> append(" AND a.result_status IN ('FAILURE', 'DENIED')")
            AdminAuditResult.ALL -> Unit
        }
        if (keyword != null) {
            append(" AND (u.username ILIKE ? ESCAPE '\\' OR a.action_type ILIKE ? ESCAPE '\\' OR a.target_type ILIKE ? ESCAPE '\\' OR CAST(a.target_id AS TEXT) ILIKE ? ESCAPE '\\')")
        }
    }

    private fun java.sql.PreparedStatement.bindAuditFilter(filter: AdminAuditFilter): Int {
        var parameterIndex = 1
        filter.actorId?.let { setLong(parameterIndex++, it) }
        filter.actionType?.let { setString(parameterIndex++, it) }
        filter.keyword?.let { keyword ->
            val pattern = "%${keyword.escapeLike()}%"
            repeat(4) { setString(parameterIndex++, pattern) }
        }
        return parameterIndex
    }

    private fun SQLException.toAdminException(): RuntimeException = when (sqlState) {
        UNIQUE_VIOLATION -> AdminValidationException("启用状态下的规范化车牌已存在")
        else -> AdminPersistenceException("管理数据操作失败", this)
    }

    private companion object {
        const val MAX_PAGE_SIZE = 200
        const val UNIQUE_VIOLATION = "23505"

        const val SELECT_VEHICLES = """
            SELECT id, plate_number, category, status, version, vehicle_type
            FROM vehicles
            WHERE (? IS NULL OR normalized_plate LIKE ?)
            ORDER BY status, normalized_plate, id
            LIMIT ? OFFSET ?
        """

        const val COUNT_VEHICLES = """
            SELECT COUNT(*)
            FROM vehicles
            WHERE (? IS NULL OR normalized_plate LIKE ?)
        """

        const val SELECT_VEHICLE = """
            SELECT v.id, v.plate_number, v.normalized_plate, v.category, v.status, v.version, v.vehicle_type,
                   v.attributes::text AS attributes,
                   rp.id AS resident_profile_id, rp.owner_name, rp.identity_card_number, rp.contact_phone,
                   rp.remarks AS resident_remarks,
                   lp.id AS long_term_profile_id, lp.organization_name, lp.pass_holder, lp.passage_details,
                   lp.remarks AS long_term_remarks
            FROM vehicles v
            LEFT JOIN resident_profiles rp ON rp.vehicle_id = v.id
            LEFT JOIN long_term_profiles lp ON lp.vehicle_id = v.id
            WHERE v.id = ?
        """

        const val INSERT_VEHICLE = """
            INSERT INTO vehicles (
                plate_number, normalized_plate, category, vehicle_type, status, attributes, created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?)
        """

        const val UPDATE_VEHICLE = """
            UPDATE vehicles
            SET plate_number = ?, normalized_plate = ?, category = ?, vehicle_type = ?, status = ?,
                attributes = CAST(? AS JSONB), version = version + 1, updated_by = ?
            WHERE id = ? AND version = ?
        """

        const val DEACTIVATE_VEHICLE = """
            UPDATE vehicles
            SET status = 'INACTIVE', version = version + 1, updated_by = ?
            WHERE id = ? AND version = ?
        """

        const val UPSERT_RESIDENT_PROFILE = """
            INSERT INTO resident_profiles (
                vehicle_id, owner_name, identity_card_number, contact_phone, remarks, created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (vehicle_id) DO UPDATE SET
                owner_name = EXCLUDED.owner_name,
                identity_card_number = EXCLUDED.identity_card_number,
                contact_phone = EXCLUDED.contact_phone,
                remarks = EXCLUDED.remarks,
                version = resident_profiles.version + 1,
                updated_by = EXCLUDED.updated_by
        """

        const val UPSERT_LONG_TERM_PROFILE = """
            INSERT INTO long_term_profiles (
                vehicle_id, organization_name, pass_holder, passage_details, remarks, created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (vehicle_id) DO UPDATE SET
                organization_name = EXCLUDED.organization_name,
                pass_holder = EXCLUDED.pass_holder,
                passage_details = EXCLUDED.passage_details,
                remarks = EXCLUDED.remarks,
                version = long_term_profiles.version + 1,
                updated_by = EXCLUDED.updated_by
        """

        const val DELETE_RESIDENT_PROFILE = "DELETE FROM resident_profiles WHERE vehicle_id = ?"
        const val DELETE_LONG_TERM_PROFILE = "DELETE FROM long_term_profiles WHERE vehicle_id = ?"

        const val SELECT_USERS = """
            SELECT id, username, role, status, version, created_at, updated_at, avatar_version, avatar_content, real_name
            FROM users
            ORDER BY username, id
            LIMIT ? OFFSET ?
        """

        const val SELECT_USER = """
            SELECT id, username, role, status, version, created_at, updated_at, avatar_version, avatar_content, real_name
            FROM users WHERE id = ?
        """

        const val INSERT_USER = """
            INSERT INTO users (username, password_hash, role, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?)
        """

        const val UPDATE_USER = """
            UPDATE users
            SET username = COALESCE(?, username), password_hash = COALESCE(?, password_hash), real_name = COALESCE(?, real_name),
                role = ?, status = ?, auth_version = auth_version + CASE WHEN ? THEN 1 ELSE 0 END,
                version = version + 1, updated_by = ?
            WHERE id = ? AND version = ?
        """

        const val UPDATE_USER_AVATAR = """
            UPDATE users SET avatar_content = ?, avatar_content_type = ?, avatar_version = avatar_version + 1,
                version = version + 1, updated_by = ?
            WHERE id = ? AND version = ?
        """

        const val DELETE_USER_AVATAR = """
            UPDATE users SET avatar_content = NULL, avatar_content_type = NULL, avatar_version = avatar_version + 1,
                version = version + 1, updated_by = ?
            WHERE id = ? AND version = ?
        """

        const val SELECT_IMPORT_BATCHES = """
            SELECT id, source_file_name, status, total_rows, valid_rows, duplicate_rows, error_rows,
                   version, created_at, published_at, rollback_at
            FROM import_batches
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
        """

        const val AUDIT_SELECT_PREFIX = """
            SELECT a.id, u.username, a.action_type, a.target_type, a.target_id, a.result_status, a.created_at
            FROM audit_logs a
            LEFT JOIN users u ON u.id = a.actor_id
        """

        const val AUDIT_SUMMARY_PREFIX = """
            SELECT COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE a.result_status = 'SUCCESS') AS success_count,
                   COUNT(*) FILTER (WHERE a.result_status IN ('FAILURE', 'DENIED')) AS abnormal_count,
                   COUNT(DISTINCT a.actor_id) AS active_actor_count
            FROM audit_logs a
            LEFT JOIN users u ON u.id = a.actor_id
        """

        const val AUDIT_ACTORS_PREFIX = """
            SELECT a.actor_id AS id, u.username
            FROM audit_logs a
            LEFT JOIN users u ON u.id = a.actor_id
        """

        const val AUDIT_ACTION_TYPES_PREFIX = """
            SELECT a.action_type
            FROM audit_logs a
            LEFT JOIN users u ON u.id = a.actor_id
        """
    }
}

internal data class AdminVehicleCommand(
    val plateNumber: String,
    val category: VehicleCategory,
    val vehicleType: String?,
    val status: AdminVehicleStatus,
    val attributes: JsonObject,
    val residentProfile: AdminResidentProfile?,
    val longTermProfile: AdminLongTermProfile?,
)

internal data class AdminVehicleCreationCapabilities(
    val creatableCategories: List<VehicleCategory>,
    val canChangeVehicleCategory: Boolean,
)

internal object AdminVehicleCreationPolicy {
    fun capabilities(isPrimaryAdministrator: Boolean): AdminVehicleCreationCapabilities =
        AdminVehicleCreationCapabilities(
            creatableCategories = if (isPrimaryAdministrator) VehicleCategory.entries else listOf(VehicleCategory.OTHER_LONG_TERM),
            canChangeVehicleCategory = isPrimaryAdministrator,
        )

    fun requireCreationAllowed(isPrimaryAdministrator: Boolean, category: VehicleCategory) {
        if (category != VehicleCategory.OTHER_LONG_TERM && !isPrimaryAdministrator) {
            throw AdminValidationException("仅管理员账号可以手工新增五类正式车辆")
        }
    }

    fun requireUpdateAllowed(
        isPrimaryAdministrator: Boolean,
        originalCategory: VehicleCategory,
        requestedCategory: VehicleCategory,
    ) {
        if (originalCategory != requestedCategory && !isPrimaryAdministrator) {
            throw AdminValidationException("仅管理员账号可以修改车辆类别")
        }
    }
}

internal object AdminUserProfilePolicy {
    fun requireModificationAllowed(canManageOtherUserProfiles: Boolean, profileChangeRequested: Boolean) {
        if (profileChangeRequested && !canManageOtherUserProfiles) {
            throw AdminPermissionException("仅admin账号可以修改其他账号的用户名、密码或头像")
        }
    }
}

internal data class AdminVehicleListItem(
    val id: Long,
    val plateNumber: String,
    val category: VehicleCategory,
    val status: AdminVehicleStatus,
    val version: Int,
    val vehicleType: String?,
)

internal data class AdminVehiclePage(
    val items: List<AdminVehicleListItem>,
    val total: Int,
)

internal data class AdminVehicleRecord(
    val id: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: VehicleCategory,
    val status: AdminVehicleStatus,
    val version: Int,
    val vehicleType: String?,
    val attributes: JsonObject,
    val residentProfile: AdminResidentProfile?,
    val longTermProfile: AdminLongTermProfile?,
)

internal data class AdminResidentProfile(
    val ownerName: String,
    val identityCardNumber: String,
    val contactPhone: String?,
    val remarks: String?,
)

internal data class AdminLongTermProfile(
    val organizationName: String?,
    val passHolder: String?,
    val passageDetails: String?,
    val remarks: String?,
)

internal enum class AdminVehicleStatus { ACTIVE, INACTIVE }
internal enum class AdminRole { ADMIN, USER }
internal enum class AdminUserStatus { ACTIVE, DISABLED }

internal data class AdminUserCreateCommand(
    val username: String,
    val password: String,
    val role: AdminRole,
)

internal data class AdminUserUpdateCommand(
    val role: AdminRole,
    val status: AdminUserStatus,
    val username: String? = null,
    val password: String? = null,
    val realName: String? = null,
)

internal data class AdminUserRecord(
    val id: Long,
    val username: String,
    val role: AdminRole,
    val status: AdminUserStatus,
    val version: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val avatarVersion: Long,
    val hasAvatar: Boolean,
    val realName: String?,
)

internal data class AdminAvatarContent(val content: ByteArray, val contentType: String)

internal data class AdminImportBatchSummary(
    val id: Long,
    val sourceFileName: String,
    val status: String,
    val totalRows: Int,
    val validRows: Int,
    val duplicateRows: Int,
    val errorRows: Int,
    val version: Int,
    val createdAt: String?,
    val publishedAt: String?,
    val rollbackAt: String?,
)

internal data class AdminAuditEntry(
    val id: Long,
    val actorUsername: String?,
    val actionType: String,
    val targetType: String,
    val targetId: Long?,
    val resultStatus: String,
    val createdAt: String,
)

internal data class AdminAuditPage(
    val items: List<AdminAuditEntry>,
    val summary: AdminAuditSummary,
    val actors: List<AdminAuditActor>,
    val actionTypes: List<String>,
)

internal data class AdminAuditSummary(
    val total: Int,
    val successCount: Int,
    val abnormalCount: Int,
    val activeActorCount: Int,
)

internal data class AdminAuditActor(
    val id: Long,
    val username: String?,
)

internal data class AdminAuditFilter(
    val range: AdminAuditRange = AdminAuditRange.THIRTY_DAYS,
    val actorId: Long? = null,
    val actionType: String? = null,
    val result: AdminAuditResult = AdminAuditResult.ALL,
    val keyword: String? = null,
) {
    fun normalized(): AdminAuditFilter = copy(
        actorId = actorId?.requirePositive("审计操作人标识"),
        actionType = actionType?.trim()?.takeIf(String::isNotEmpty)?.also {
            if (it.length > 128) throw AdminValidationException("审计操作类型长度超过128个字符")
        },
        keyword = keyword?.trim()?.takeIf(String::isNotEmpty)?.also {
            if (it.length > 128) throw AdminValidationException("审计关键词长度超过128个字符")
        },
    )
}

internal enum class AdminAuditRange(val requestValue: String, val intervalLiteral: String?) {
    DAY("24h", "24 hours"),
    WEEK("7d", "7 days"),
    THIRTY_DAYS("30d", "30 days"),
    ALL("all", null),
    ;

    companion object {
        fun fromRequest(value: String?): AdminAuditRange = entries.firstOrNull {
            it.requestValue == (value ?: THIRTY_DAYS.requestValue)
        } ?: throw AdminValidationException("审计时间范围无效")
    }
}

internal enum class AdminAuditResult {
    ALL,
    SUCCESS,
    ABNORMAL,
    ;

    companion object {
        fun fromRequest(value: String?): AdminAuditResult = value?.let {
            entries.firstOrNull { result -> result.name == it }
                ?: throw AdminValidationException("审计结果筛选无效")
        } ?: ALL
    }
}

internal class AdminResourceNotFoundException(message: String) : RuntimeException(message)
internal class AdminPermissionException(message: String) : RuntimeException(message)
internal class AdminValidationException(message: String) : RuntimeException(message)
internal class AdminConflictException(message: String) : RuntimeException(message)
internal class AdminPersistenceException(message: String, cause: Throwable) : RuntimeException(message, cause)

internal fun AdminVehicleCommand.validate() {
    val normalizedPlate = normalizePlate(plateNumber)
    if (normalizedPlate.isBlank() || normalizedPlate.length > 32) throw AdminValidationException("车牌号格式无效")
    if (vehicleType.trimToNull()?.length ?: 0 > 128) throw AdminValidationException("车辆类型长度超过128个字符")
    if (attributes.keys.any { it !in ALLOWED_ATTRIBUTE_KEYS }) throw AdminValidationException("存在不支持的车辆附加字段")
    attributes.forEach { (_, value) ->
        val content = (value as? JsonPrimitive)?.contentOrNull
            ?: throw AdminValidationException("车辆附加字段必须为文本")
        if (content.length > 255) throw AdminValidationException("车辆附加字段长度超过255个字符")
    }
    if (category == VehicleCategory.RESIDENT) {
        val profile = residentProfile ?: throw AdminValidationException("村民车辆必须填写姓名和身份证号")
        if (longTermProfile != null || profile.ownerName.isBlank() || profile.identityCardNumber.isBlank()) {
            throw AdminValidationException("村民车辆资料不完整")
        }
        profile.validate()
    } else {
        if (residentProfile != null) throw AdminValidationException("长期车辆不能保存村民资料")
        if (category == VehicleCategory.OTHER_LONG_TERM && longTermProfile?.organizationName.trimToNull() == null) {
            throw AdminValidationException("其他长期通行车辆必须填写单位名称")
        }
        longTermProfile?.validate()
    }
}

internal fun AdminUserCreateCommand.validate() {
    if (username.trim().length !in 3..64) throw AdminValidationException("账号名称长度应为3至64个字符")
    if (password.length < 6 || password.length > 128) throw AdminValidationException("密码长度应为6至128个字符")
}

internal fun AdminUserUpdateCommand.validate() {
    username?.let { if (it.trim().length !in 3..64) throw AdminValidationException("账号名称长度应为3至64个字符") }
    password?.let { if (it.length !in 6..128) throw AdminValidationException("密码长度应为6至128个字符") }
}

private fun AdminResidentProfile.validate() {
    if (ownerName.trim().length > 128 || identityCardNumber.trim().length > 32 || contactPhone.trimToNull()?.length ?: 0 > 32) {
        throw AdminValidationException("村民资料字段长度无效")
    }
}

private fun AdminLongTermProfile.validate() {
    if (organizationName.trimToNull()?.length ?: 0 > 255) {
        throw AdminValidationException("长期车辆资料字段长度无效")
    }
}

private fun String?.trimToNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
private fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
private fun Int.requireNonNegative(name: String) = also { if (it < 0) throw AdminValidationException("${name}无效") }
private fun Long.requirePositive(name: String) = also { if (it <= 0) throw AdminValidationException("${name}无效") }
private fun java.sql.Timestamp?.toIsoString(): String? = this?.toInstant()?.toString()
private fun ResultSet.getLongOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }
private fun java.sql.PreparedStatement.setNullableString(index: Int, value: String?) {
    if (value == null) setObject(index, null) else setString(index, value)
}

private val ALLOWED_ATTRIBUTE_KEYS = setOf(
    "vehicleUse",
    "passageArea",
    "position",
    "brandModel",
    "approvedCapacity",
    "plateColor",
)
