package com.jaydocoder.plateview.server.schedule

import com.jaydocoder.plateview.server.auth.isPrimaryAdministrator
import com.jaydocoder.plateview.server.auth.requireAdministrator
import com.jaydocoder.plateview.server.infrastructure.database.AuditEvent
import com.jaydocoder.plateview.server.infrastructure.database.AuditLogWriterKey
import com.jaydocoder.plateview.server.infrastructure.database.DataSourceKey
import com.jaydocoder.plateview.server.infrastructure.web.ApiErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.sql.DataSource
import kotlinx.serialization.Serializable

private val firstScheduleWeek = LocalDate.of(2026, 7, 27)
private val scheduleZone = ZoneId.of("Asia/Shanghai")

internal fun Application.configureScheduleFeature() {
    val dataSource = attributes.getOrNull(DataSourceKey) ?: return
    val service = ScheduleService(dataSource)
    routing {
        authenticate("access-token") {
            route("/schedule") {
                get("/week") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                    val anchor = call.request.queryParameters["date"]?.let(::parseDate) ?: LocalDate.now()
                    call.respond(service.weekFor(actorId, anchor).toResponse())
                }
                get("/month") {
                    val actorId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asLong()
                    val month = call.request.queryParameters["month"]?.let(::parseMonth) ?: YearMonth.now()
                    call.respond(service.monthFor(actorId, month).toResponse())
                }
            }
            route("/admin/schedules") {
                get("/configuration") {
                    val actorId = call.requirePrimaryAdministrator() ?: return@get
                    call.respond(service.configuration(actorId).toResponse())
                }
                put("/configuration") {
                    val actorId = call.requirePrimaryAdministrator() ?: return@put
                    val configuration = service.updateConfiguration(actorId, call.receive<ScheduleConfigurationRequest>().toCommand())
                    call.auditSchedule(actorId, "SCHEDULE_CONFIGURATION_UPDATE", actorId)
                    call.respond(configuration.toResponse())
                }
                get("/templates") {
                    val actorId = call.requirePrimaryAdministrator() ?: return@get
                    call.respond(TemplatesResponse(service.templates(actorId).map(ScheduleTemplateSummary::toResponse)))
                }
                post("/templates") {
                    val actorId = call.requirePrimaryAdministrator() ?: return@post
                    val template = service.createTemplate(actorId, call.receive<ScheduleTemplateRequest>().toCommand())
                    call.auditSchedule(actorId, "SCHEDULE_TEMPLATE_CREATE", template.id)
                    call.respond(HttpStatusCode.Created, template.toResponse())
                }
                put("/templates/{templateId}") {
                    val actorId = call.requirePrimaryAdministrator() ?: return@put
                    val template = service.updateTemplate(call.templateId(), actorId, call.receive<ScheduleTemplateRequest>().toCommand())
                    call.auditSchedule(actorId, "SCHEDULE_TEMPLATE_UPDATE", template.id)
                    call.respond(template.toResponse())
                }
                delete("/templates/{templateId}") {
                    val actorId = call.requirePrimaryAdministrator() ?: return@delete
                    service.deleteTemplate(call.templateId(), actorId)
                    call.auditSchedule(actorId, "SCHEDULE_TEMPLATE_DELETE", call.templateId())
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/templates/{templateId}/preview") {
                    val actorId = call.requirePrimaryAdministrator() ?: return@get
                    val from = call.request.queryParameters["effectiveFrom"]?.let(::parseDate) ?: LocalDate.now()
                    call.respond(service.preview(call.templateId(), actorId, from).toResponse())
                }
                post("/applications") {
                    val actorId = call.requirePrimaryAdministrator() ?: return@post
                    val request = call.receive<ScheduleApplicationRequest>()
                    val application = service.applyTemplate(request.templateId, parseDate(request.effectiveFrom), actorId)
                    call.auditSchedule(actorId, "SCHEDULE_TEMPLATE_APPLY", application.id)
                    call.respond(HttpStatusCode.Created, application.toResponse())
                }
            }
        }
    }
}

private fun parseDate(value: String): LocalDate = try {
    LocalDate.parse(value)
} catch (_: Exception) {
    throw ScheduleValidationException("日期格式应为YYYY-MM-DD")
}

private fun parseMonth(value: String): YearMonth = try {
    YearMonth.parse(value)
} catch (_: Exception) {
    throw ScheduleValidationException("月份格式应为YYYY-MM")
}

private fun io.ktor.server.application.ApplicationCall.templateId(): Long = parameters["templateId"]?.toLongOrNull()
    ?.takeIf { it > 0 } ?: throw ScheduleValidationException("排班模板标识无效")

private suspend fun io.ktor.server.application.ApplicationCall.requirePrimaryAdministrator(): Long? {
    val actorId = requireAdministrator() ?: return null
    val source = application.attributes[DataSourceKey]
    if (source.connection.use { it.isPrimaryAdministrator(actorId) }) return actorId
    application.attributes.getOrNull(AuditLogWriterKey)?.write(AuditEvent(actorId, "SCHEDULE_ADMIN_ACCESS", "SCHEDULE", null, "DENIED", callId, kotlinx.serialization.json.JsonObject(emptyMap())))
    respond(HttpStatusCode.Forbidden, ApiErrorResponse("PRIMARY_ADMIN_REQUIRED", "仅admin账号可以管理排班", callId))
    return null
}

private fun io.ktor.server.application.ApplicationCall.auditSchedule(actorId: Long, action: String, targetId: Long) {
    application.attributes.getOrNull(AuditLogWriterKey)?.write(AuditEvent(actorId, action, "SCHEDULE_TEMPLATE", targetId, "SUCCESS", callId, kotlinx.serialization.json.JsonObject(emptyMap())))
}

internal class ScheduleService(
    private val dataSource: DataSource,
    private val clock: Clock = Clock.system(scheduleZone),
) {
    fun isScheduleEnabled(userId: Long): Boolean = dataSource.connection.use { connection ->
        connection.isPrimaryAdministrator(userId) || connection.prepareStatement("SELECT 1 FROM schedule_participants WHERE account_id = ? AND enabled = TRUE").use {
            it.setLong(1, userId); it.executeQuery().use(ResultSet::next)
        }
    }

    fun weekFor(actorId: Long, anchor: LocalDate): ScheduleWeek {
        val weekStart = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        dataSource.connection.use { connection ->
            if (connection.isPrimaryAdministrator(actorId)) return ScheduleWeek(weekStart, weekNumber(weekStart), emptyList())
            if (!connection.isScheduleParticipant(actorId)) throw SchedulePermissionException("当前账号未加入排班")
        }
        return ScheduleWeek(weekStart, weekNumber(weekStart), (0L..6L).flatMap { offset -> dayAssignments(weekStart.plusDays(offset)) })
    }

    fun monthFor(actorId: Long, month: YearMonth): ScheduleMonth {
        dataSource.connection.use { connection ->
            if (connection.isPrimaryAdministrator(actorId)) {
                return ScheduleMonth(month, (1..month.lengthOfMonth()).map { day -> ScheduleMonthDay(month.atDay(day), false) })
            }
            if (!connection.isScheduleParticipant(actorId)) throw SchedulePermissionException("当前账号未加入排班")
        }
        return ScheduleMonth(
            month,
            (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                ScheduleMonthDay(date, dayAssignments(date).any { shift -> shift.persons.any { person -> person.id == actorId } })
            },
        )
    }

    fun participants(actorId: Long): List<ScheduleParticipant> = dataSource.connection.use { connection ->
        connection.requirePrimary(actorId)
        connection.prepareStatement("""
            SELECT u.id, u.username, u.real_name, u.status FROM schedule_participants p
            JOIN users u ON u.id = p.account_id
            WHERE p.enabled = TRUE AND u.status = 'ACTIVE'
            ORDER BY u.id
        """).use { statement ->
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toParticipant()) } }
        }
    }

    fun configuration(actorId: Long): SchedulePlanningConfiguration = dataSource.connection.use { connection ->
        connection.requirePrimary(actorId)
        SchedulePlanningConfiguration(
            cycleDays = connection.configurationCycleDays(),
            participants = connection.enabledParticipants(),
            candidates = connection.scheduleCandidates(),
        )
    }

    fun updateConfiguration(actorId: Long, command: ScheduleConfigurationCommand): SchedulePlanningConfiguration = inTransaction { connection ->
        connection.requirePrimary(actorId)
        command.validate(connection)
        connection.prepareStatement("UPDATE schedule_configuration SET cycle_days = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1").use { statement ->
            statement.setInt(1, command.cycleDays)
            statement.setLong(2, actorId)
            statement.executeUpdate()
        }
        connection.prepareStatement("UPDATE schedule_participants SET enabled = FALSE WHERE enabled = TRUE AND account_id <> ALL (?)").use { statement ->
            statement.setArray(1, connection.createArrayOf("BIGINT", command.participantIds.toTypedArray()))
            statement.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO schedule_participants (account_id, enabled) VALUES (?, TRUE) ON CONFLICT (account_id) DO UPDATE SET enabled = TRUE").use { statement ->
            command.participantIds.forEach { participantId ->
                statement.setLong(1, participantId)
                statement.addBatch()
            }
            statement.executeBatch()
        }
        SchedulePlanningConfiguration(command.cycleDays, connection.enabledParticipants(), connection.scheduleCandidates())
    }

    fun templates(actorId: Long): List<ScheduleTemplateSummary> = dataSource.connection.use { connection ->
        connection.requirePrimary(actorId)
        connection.prepareStatement("""
            SELECT t.id, t.name, v.id AS version_id, v.version_number, v.cycle_days,
                   COALESCE((SELECT ARRAY_AGG(p.account_id ORDER BY p.account_id)
                             FROM schedule_template_version_participants p
                             WHERE p.template_version_id = v.id), ARRAY[]::BIGINT[]) AS participant_ids,
                   (SELECT MAX(a.effective_from)
                    FROM schedule_applications a
                    JOIN schedule_template_versions applied_version ON applied_version.id = a.template_version_id
                    WHERE applied_version.template_id = t.id) AS effective_from,
                   CASE WHEN t.id = (
                       SELECT applied_version.template_id
                       FROM schedule_applications a
                       JOIN schedule_template_versions applied_version ON applied_version.id = a.template_version_id
                       WHERE a.effective_from <= ?
                       ORDER BY a.effective_from DESC
                       LIMIT 1
                   ) THEN 'ACTIVE' ELSE 'INACTIVE' END AS status
            FROM schedule_templates t
            JOIN schedule_template_versions v ON v.template_id = t.id
            WHERE t.archived_at IS NULL AND v.version_number = (SELECT MAX(v2.version_number) FROM schedule_template_versions v2 WHERE v2.template_id = t.id)
            ORDER BY t.updated_at DESC, t.id DESC
        """).use { statement ->
            statement.setObject(1, scheduleCurrentDate(clock))
            statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toTemplateSummary()) } }
        }
    }

    fun createTemplate(actorId: Long, command: ScheduleTemplateCommand): ScheduleTemplateSummary = inTransaction { connection ->
        connection.requirePrimary(actorId)
        command.validate(connection)
        val templateId = connection.prepareStatement("INSERT INTO schedule_templates (name, created_by) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setString(1, command.name.trim()); statement.setLong(2, actorId); statement.executeUpdate()
            statement.generatedKeys.use { keys -> keys.next(); keys.getLong(1) }
        }
        val versionId = insertVersion(connection, templateId, 1, actorId, command)
        ScheduleTemplateSummary(templateId, command.name.trim(), versionId, 1, command.cycleDays, command.participantIds, null, "INACTIVE")
    }

    fun updateTemplate(templateId: Long, actorId: Long, command: ScheduleTemplateCommand): ScheduleTemplateSummary = inTransaction { connection ->
        connection.requirePrimary(actorId)
        command.validate(connection)
        val nextVersion = connection.prepareStatement("SELECT COALESCE(MAX(version_number), 0) + 1 FROM schedule_template_versions WHERE template_id = ?").use {
            it.setLong(1, templateId); it.executeQuery().use { result -> result.next(); result.getInt(1) }
        }
        val updated = connection.prepareStatement("UPDATE schedule_templates SET name = ? WHERE id = ? AND archived_at IS NULL").use {
            it.setString(1, command.name.trim()); it.setLong(2, templateId); it.executeUpdate()
        }
        if (updated == 0) throw ScheduleNotFoundException("排班模板不存在")
        val versionId = insertVersion(connection, templateId, nextVersion, actorId, command)
        ScheduleTemplateSummary(templateId, command.name.trim(), versionId, nextVersion, command.cycleDays, command.participantIds, null, "INACTIVE")
    }

    fun deleteTemplate(templateId: Long, actorId: Long) = inTransaction { connection ->
        connection.requirePrimary(actorId)
        val used = connection.prepareStatement("SELECT 1 FROM schedule_applications a JOIN schedule_template_versions v ON v.id = a.template_version_id WHERE v.template_id = ?").use {
            it.setLong(1, templateId); it.executeQuery().use(ResultSet::next)
        }
        if (used) throw ScheduleValidationException("已应用的模板不能删除，请保留历史版本")
        val changed = connection.prepareStatement("DELETE FROM schedule_templates WHERE id = ?").use { it.setLong(1, templateId); it.executeUpdate() }
        if (changed == 0) throw ScheduleNotFoundException("排班模板不存在")
    }

    fun applyTemplate(templateId: Long, effectiveFrom: LocalDate, actorId: Long): ScheduleApplication = inTransaction { connection ->
        connection.requirePrimary(actorId)
        val version = latestVersion(connection, templateId) ?: throw ScheduleNotFoundException("排班模板不存在")
        val id = connection.prepareStatement("""
            INSERT INTO schedule_applications (template_version_id, effective_from, applied_by)
            VALUES (?, ?, ?)
            ON CONFLICT (effective_from) DO UPDATE SET template_version_id = EXCLUDED.template_version_id, applied_by = EXCLUDED.applied_by, applied_at = CURRENT_TIMESTAMP
            RETURNING id
        """).use { statement ->
            statement.setLong(1, version.id); statement.setObject(2, effectiveFrom); statement.setLong(3, actorId)
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }
        connection.replaceEnabledScheduleParticipants(version.id)
        ScheduleApplication(id, templateId, version.versionNumber, effectiveFrom)
    }

    fun preview(templateId: Long, actorId: Long, effectiveFrom: LocalDate): ScheduleWeek {
        dataSource.connection.use { connection ->
            connection.requirePrimary(actorId)
            val version = latestVersion(connection, templateId) ?: throw ScheduleNotFoundException("排班模板不存在")
            val values = (0 until version.cycleDays).flatMap { offset -> assignmentsForVersion(connection, version.id, offset + 1, effectiveFrom.plusDays(offset.toLong())) }
            return ScheduleWeek(effectiveFrom, 0, values)
        }
    }

    private fun dayAssignments(date: LocalDate): List<ScheduleShift> = dataSource.connection.use { connection ->
        val application = connection.prepareStatement("""
            SELECT a.effective_from, v.id, v.cycle_days FROM schedule_applications a
            JOIN schedule_template_versions v ON v.id = a.template_version_id
            WHERE a.effective_from <= ? ORDER BY a.effective_from DESC LIMIT 1
        """).use { statement ->
            statement.setObject(1, date); statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                ScheduleApplicationResolution(result.getObject("effective_from", LocalDate::class.java), result.getLong("id"), result.getInt("cycle_days"))
            }
        } ?: return emptyList()
        val cycleDay = scheduleCycleDay(application.effectiveFrom, date, application.cycleDays)
        return assignmentsForVersion(connection, application.versionId, cycleDay, date)
    }

    private fun assignmentsForVersion(connection: Connection, versionId: Long, cycleDay: Int, date: LocalDate): List<ScheduleShift> = connection.prepareStatement("""
        SELECT a.shift_type, u.id, u.username, u.real_name FROM schedule_shift_assignments a
        JOIN users u ON u.id = a.account_id
        WHERE a.template_version_id = ? AND a.cycle_day = ? ORDER BY a.shift_type, u.id
    """).use { statement ->
        statement.setLong(1, versionId); statement.setInt(2, cycleDay)
        statement.executeQuery().use { result ->
            val names = linkedMapOf<ShiftType, MutableList<SchedulePerson>>()
            while (result.next()) names.getOrPut(ShiftType.valueOf(result.getString("shift_type"))) { mutableListOf() }.add(result.toPerson())
            ShiftType.entries.mapNotNull { type -> names[type]?.let { ScheduleShift(date, type, it) } }
        }
    }

    private fun insertVersion(connection: Connection, templateId: Long, versionNumber: Int, actorId: Long, command: ScheduleTemplateCommand): Long {
        connection.enableScheduleParticipants(command.participantIds)
        val versionId = connection.prepareStatement("INSERT INTO schedule_template_versions (template_id, version_number, cycle_days, created_by) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setLong(1, templateId); statement.setInt(2, versionNumber); statement.setInt(3, command.cycleDays); statement.setLong(4, actorId); statement.executeUpdate()
            statement.generatedKeys.use { keys -> keys.next(); keys.getLong(1) }
        }
        connection.prepareStatement("INSERT INTO schedule_template_version_participants (template_version_id, account_id) VALUES (?, ?)").use { statement ->
            command.participantIds.forEach { participantId ->
                statement.setLong(1, versionId)
                statement.setLong(2, participantId)
                statement.addBatch()
            }
            statement.executeBatch()
        }
        connection.prepareStatement("INSERT INTO schedule_shift_assignments (template_version_id, cycle_day, shift_type, account_id) VALUES (?, ?, ?, ?)").use { statement ->
            command.assignments.forEach { assignment -> assignment.accountIds.forEach { accountId ->
                statement.setLong(1, versionId); statement.setInt(2, assignment.cycleDay); statement.setString(3, assignment.shiftType.name); statement.setLong(4, accountId); statement.addBatch()
            } }
            statement.executeBatch()
        }
        return versionId
    }

    private fun latestVersion(connection: Connection, templateId: Long): TemplateVersion? = connection.prepareStatement("""
        SELECT v.id, v.version_number, v.cycle_days FROM schedule_templates t
        JOIN schedule_template_versions v ON v.template_id = t.id
        WHERE t.id = ? AND t.archived_at IS NULL ORDER BY v.version_number DESC LIMIT 1
    """).use { statement ->
        statement.setLong(1, templateId); statement.executeQuery().use { result -> if (result.next()) TemplateVersion(result.getLong(1), result.getInt(2), result.getInt(3)) else null }
    }

    private fun ScheduleConfigurationCommand.validate(connection: Connection) {
        if (cycleDays !in 1..15) throw ScheduleValidationException("循环天数应为1至15天")
        if (participantIds.isEmpty() || participantIds.size != participantIds.distinct().size) throw ScheduleValidationException("请至少选择一名不重复的排班成员")
        val candidateIds = connection.scheduleCandidates().mapTo(mutableSetOf()) { it.id }
        if (participantIds.any { it !in candidateIds }) throw ScheduleValidationException("排班成员必须是具有真实姓名的有效账号")
    }

    private fun ScheduleTemplateCommand.validate(connection: Connection) {
        if (name.trim().length !in 1..64) throw ScheduleValidationException("模板名称长度应为1至64个字符")
        if (cycleDays !in 1..15) throw ScheduleValidationException("循环天数应为1至15天")
        if (participantIds.isEmpty() || participantIds.size != participantIds.distinct().size) throw ScheduleValidationException("请至少选择一名不重复的排班成员")
        val candidateIds = connection.scheduleCandidates().mapTo(mutableSetOf()) { it.id }
        if (participantIds.any { it !in candidateIds }) throw ScheduleValidationException("排班成员必须是具有真实姓名的有效账号")
        val expected = (1..cycleDays).flatMap { day -> ShiftType.entries.map { type -> day to type } }.toSet()
        val actual = assignments.map { it.cycleDay to it.shiftType }.toSet()
        if (actual != expected || assignments.size != expected.size) throw ScheduleValidationException("每一天必须完整配置四类班次")
        assignments.forEach { assignment ->
            if (assignment.accountIds.size !in 1..3 || assignment.accountIds.distinct().size != assignment.accountIds.size) throw ScheduleValidationException("每个班次应选择1至3名不重复人员")
            if (assignment.cycleDay !in 1..cycleDays) throw ScheduleValidationException("班次日期超出轮回范围")
        }
        if (assignments.flatMap { it.accountIds }.any { it !in participantIds }) throw ScheduleValidationException("班次人员必须来自本模板的排班成员")
    }

    private fun Connection.requirePrimary(actorId: Long) { if (!isPrimaryAdministrator(actorId)) throw SchedulePermissionException("仅admin账号可以管理排班") }
    private fun Connection.configurationCycleDays(): Int = prepareStatement("SELECT cycle_days FROM schedule_configuration WHERE id = 1").use { statement ->
        statement.executeQuery().use { result -> if (result.next()) result.getInt(1) else 9 }
    }
    private fun Connection.enabledParticipants(): List<ScheduleParticipant> = prepareStatement("""
        SELECT u.id, u.username, u.real_name, u.status
        FROM schedule_participants p JOIN users u ON u.id = p.account_id
        WHERE p.enabled = TRUE AND u.status = 'ACTIVE'
        ORDER BY u.real_name, u.username
    """).use { statement -> statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toParticipant()) } } }
    private fun Connection.enableScheduleParticipants(participantIds: List<Long>) = prepareStatement("""
        INSERT INTO schedule_participants (account_id, enabled) VALUES (?, TRUE)
        ON CONFLICT (account_id) DO UPDATE SET enabled = TRUE
    """.trimIndent()).use { statement ->
        participantIds.forEach { participantId ->
            statement.setLong(1, participantId)
            statement.addBatch()
        }
        statement.executeBatch()
    }
    private fun Connection.replaceEnabledScheduleParticipants(versionId: Long) = prepareStatement("""
        UPDATE schedule_participants p
        SET enabled = EXISTS (
            SELECT 1 FROM schedule_template_version_participants v
            WHERE v.template_version_id = ? AND v.account_id = p.account_id
        )
    """.trimIndent()).use { statement ->
        statement.setLong(1, versionId)
        statement.executeUpdate()
    }
    private fun Connection.scheduleCandidates(): List<ScheduleParticipant> = prepareStatement("""
        SELECT u.id, u.username, u.real_name, u.status
        FROM users u
        WHERE u.status = 'ACTIVE' AND u.username <> 'admin' AND NULLIF(BTRIM(u.real_name), '') IS NOT NULL
        ORDER BY u.real_name, u.username
    """).use { statement -> statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toParticipant()) } } }
    private fun Connection.isScheduleParticipant(userId: Long): Boolean = prepareStatement("SELECT 1 FROM schedule_participants p JOIN users u ON u.id = p.account_id WHERE p.account_id = ? AND p.enabled = TRUE AND u.status = 'ACTIVE'").use { it.setLong(1, userId); it.executeQuery().use(ResultSet::next) }
    private fun ResultSet.toParticipant() = ScheduleParticipant(getLong("id"), getString("username"), getString("real_name") ?: getString("username"), getString("status"))
    private fun ResultSet.toPerson() = SchedulePerson(getLong("id"), getString("username"), getString("real_name") ?: getString("username"))
    private fun ResultSet.toTemplateSummary() = ScheduleTemplateSummary(
        getLong("id"),
        getString("name"),
        getLong("version_id"),
        getInt("version_number"),
        getInt("cycle_days"),
        getLongArray("participant_ids"),
        getObject("effective_from", LocalDate::class.java),
        getString("status"),
    )
    private fun ResultSet.getLongArray(column: String): List<Long> = when (val values = getArray(column)?.array) {
        is LongArray -> values.toList()
        is Array<*> -> values.map { (it as Number).toLong() }
        else -> emptyList()
    }
    private fun <T> inTransaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try { block(connection).also { connection.commit() } } catch (exception: Exception) { connection.rollback(); throw exception }
    }
}

private fun weekNumber(weekStart: LocalDate): Int = ChronoUnit.WEEKS.between(firstScheduleWeek, weekStart).toInt() + 1
internal fun scheduleCycleDay(effectiveFrom: LocalDate, date: LocalDate, cycleDays: Int): Int {
    require(cycleDays in 1..15) { "轮回天数应为1至15天" }
    require(!date.isBefore(effectiveFrom)) { "排班日期不能早于模板生效日期" }
    return ((ChronoUnit.DAYS.between(effectiveFrom, date) % cycleDays) + 1).toInt()
}

internal fun scheduleCurrentDate(clock: Clock): LocalDate = LocalDate.now(clock.withZone(scheduleZone))
private data class ScheduleApplicationResolution(val effectiveFrom: LocalDate, val versionId: Long, val cycleDays: Int)
private data class TemplateVersion(val id: Long, val versionNumber: Int, val cycleDays: Int)
internal enum class ShiftType { MORNING, AFTERNOON, SMALL_NIGHT, NIGHT }
internal data class SchedulePerson(val id: Long, val username: String, val realName: String)
internal data class ScheduleParticipant(val id: Long, val username: String, val realName: String, val status: String)
internal data class SchedulePlanningConfiguration(val cycleDays: Int, val participants: List<ScheduleParticipant>, val candidates: List<ScheduleParticipant>)
internal data class ScheduleShift(val date: LocalDate, val shiftType: ShiftType, val persons: List<SchedulePerson>)
internal data class ScheduleWeek(val weekStart: LocalDate, val weekNumber: Int, val shifts: List<ScheduleShift>)
internal data class ScheduleMonthDay(val date: LocalDate, val hasShift: Boolean)
internal data class ScheduleMonth(val month: YearMonth, val days: List<ScheduleMonthDay>)
internal data class ScheduleTemplateSummary(
    val id: Long,
    val name: String,
    val versionId: Long,
    val versionNumber: Int,
    val cycleDays: Int,
    val participantIds: List<Long>,
    val effectiveFrom: LocalDate?,
    val status: String,
)
internal data class ScheduleApplication(val id: Long, val templateId: Long, val versionNumber: Int, val effectiveFrom: LocalDate)
internal data class ScheduleConfigurationCommand(val cycleDays: Int, val participantIds: List<Long>)
internal data class ScheduleTemplateCommand(
    val name: String,
    val cycleDays: Int,
    val participantIds: List<Long>,
    val assignments: List<ScheduleAssignmentCommand>,
)
internal data class ScheduleAssignmentCommand(val cycleDay: Int, val shiftType: ShiftType, val accountIds: List<Long>)
internal class ScheduleValidationException(message: String) : IllegalArgumentException(message)
internal class SchedulePermissionException(message: String) : IllegalStateException(message)
internal class ScheduleNotFoundException(message: String) : NoSuchElementException(message)

@Serializable private data class ScheduleConfigurationRequest(val cycleDays: Int, val participantIds: List<Long>) {
    fun toCommand() = ScheduleConfigurationCommand(cycleDays, participantIds)
}
@Serializable private data class ScheduleTemplateRequest(
    val name: String,
    val cycleDays: Int,
    val participantIds: List<Long>,
    val assignments: List<ScheduleAssignmentRequest>,
) {
    fun toCommand() = ScheduleTemplateCommand(name, cycleDays, participantIds, assignments.map { ScheduleAssignmentCommand(it.cycleDay, try { ShiftType.valueOf(it.shiftType) } catch (_: Exception) { throw ScheduleValidationException("班次类型无效") }, it.accountIds) })
}
@Serializable private data class ScheduleAssignmentRequest(val cycleDay: Int, val shiftType: String, val accountIds: List<Long>)
@Serializable private data class ScheduleApplicationRequest(val templateId: Long, val effectiveFrom: String)
@Serializable private data class ParticipantsResponse(val items: List<ScheduleParticipantResponse>)
@Serializable private data class ScheduleParticipantResponse(val id: Long, val username: String, val realName: String, val status: String)
@Serializable private data class SchedulePlanningConfigurationResponse(val cycleDays: Int, val participants: List<ScheduleParticipantResponse>, val candidates: List<ScheduleParticipantResponse>)
@Serializable private data class TemplatesResponse(val items: List<ScheduleTemplateResponse>)
@Serializable private data class ScheduleTemplateResponse(
    val id: Long,
    val name: String,
    val versionId: Long,
    val versionNumber: Int,
    val cycleDays: Int,
    val participantIds: List<Long>,
    val effectiveFrom: String?,
    val status: String,
)
@Serializable private data class ScheduleWeekResponse(val weekStart: String, val weekNumber: Int, val shifts: List<ScheduleShiftResponse>)
@Serializable private data class ScheduleMonthResponse(val month: String, val days: List<ScheduleMonthDayResponse>)
@Serializable private data class ScheduleMonthDayResponse(val date: String, val hasShift: Boolean)
@Serializable private data class ScheduleShiftResponse(val date: String, val shiftType: String, val persons: List<SchedulePersonResponse>)
@Serializable private data class SchedulePersonResponse(val id: Long, val username: String, val realName: String)
@Serializable private data class ScheduleApplicationResponse(val id: Long, val templateId: Long, val versionNumber: Int, val effectiveFrom: String)
private fun ScheduleParticipant.toResponse() = ScheduleParticipantResponse(id, username, realName, status)
private fun SchedulePlanningConfiguration.toResponse() = SchedulePlanningConfigurationResponse(cycleDays, participants.map { it.toResponse() }, candidates.map { it.toResponse() })
private fun ScheduleTemplateSummary.toResponse() = ScheduleTemplateResponse(id, name, versionId, versionNumber, cycleDays, participantIds, effectiveFrom?.toString(), status)
private fun ScheduleWeek.toResponse() = ScheduleWeekResponse(weekStart.toString(), weekNumber, shifts.map { it.toResponse() })
private fun ScheduleMonth.toResponse() = ScheduleMonthResponse(month.toString(), days.map { ScheduleMonthDayResponse(it.date.toString(), it.hasShift) })
private fun ScheduleShift.toResponse() = ScheduleShiftResponse(date.toString(), shiftType.name, persons.map { SchedulePersonResponse(it.id, it.username, it.realName) })
private fun ScheduleApplication.toResponse() = ScheduleApplicationResponse(id, templateId, versionNumber, effectiveFrom.toString())
