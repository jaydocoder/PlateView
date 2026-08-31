package com.jaydocoder.plateview.data.schedule

import com.jaydocoder.plateview.domain.schedule.ScheduleApplication
import com.jaydocoder.plateview.domain.schedule.ScheduleAssignmentCommand
import com.jaydocoder.plateview.domain.schedule.ScheduleParticipant
import com.jaydocoder.plateview.domain.schedule.SchedulePerson
import com.jaydocoder.plateview.domain.schedule.ScheduleMonth
import com.jaydocoder.plateview.domain.schedule.ScheduleMonthDay
import com.jaydocoder.plateview.domain.schedule.SchedulePlanningConfiguration
import com.jaydocoder.plateview.domain.schedule.SchedulePlanningConfigurationCommand
import com.jaydocoder.plateview.domain.schedule.ScheduleRepository
import com.jaydocoder.plateview.domain.schedule.ScheduleShift
import com.jaydocoder.plateview.domain.schedule.ScheduleShiftType
import com.jaydocoder.plateview.domain.schedule.ScheduleTemplateCommand
import com.jaydocoder.plateview.domain.schedule.ScheduleTemplateSummary
import com.jaydocoder.plateview.domain.schedule.ScheduleWeek
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ScheduleApi {
    @GET("schedule/week") suspend fun week(@Header("Authorization") authorization: String, @Query("date") date: String): ScheduleWeekDto
    @GET("schedule/month") suspend fun month(@Header("Authorization") authorization: String, @Query("month") month: String): ScheduleMonthDto
    @GET("admin/schedules/configuration") suspend fun configuration(@Header("Authorization") authorization: String): SchedulePlanningConfigurationDto
    @PUT("admin/schedules/configuration") suspend fun updateConfiguration(@Header("Authorization") authorization: String, @Body request: SchedulePlanningConfigurationRequestDto): SchedulePlanningConfigurationDto
    @GET("admin/schedules/templates") suspend fun templates(@Header("Authorization") authorization: String): TemplatesDto
    @POST("admin/schedules/templates") suspend fun createTemplate(@Header("Authorization") authorization: String, @Body request: ScheduleTemplateRequestDto): ScheduleTemplateDto
    @PUT("admin/schedules/templates/{templateId}") suspend fun updateTemplate(@Header("Authorization") authorization: String, @Path("templateId") templateId: Long, @Body request: ScheduleTemplateRequestDto): ScheduleTemplateDto
    @DELETE("admin/schedules/templates/{templateId}") suspend fun deleteTemplate(@Header("Authorization") authorization: String, @Path("templateId") templateId: Long)
    @GET("admin/schedules/templates/{templateId}/preview") suspend fun preview(@Header("Authorization") authorization: String, @Path("templateId") templateId: Long, @Query("effectiveFrom") effectiveFrom: String): ScheduleWeekDto
    @POST("admin/schedules/applications") suspend fun apply(@Header("Authorization") authorization: String, @Body request: ScheduleApplicationRequestDto): ScheduleApplicationDto
}

data class ScheduleWeekDto(val weekStart: String, val weekNumber: Int, val shifts: List<ScheduleShiftDto>)
data class ScheduleMonthDto(val month: String, val days: List<ScheduleMonthDayDto>)
data class ScheduleMonthDayDto(val date: String, val hasShift: Boolean)
data class ScheduleShiftDto(val date: String, val shiftType: String, val persons: List<SchedulePersonDto>)
data class SchedulePersonDto(val id: Long, val username: String, val realName: String)
data class ParticipantsDto(val items: List<ScheduleParticipantDto>)
data class ScheduleParticipantDto(val id: Long, val username: String, val realName: String, val status: String)
data class SchedulePlanningConfigurationDto(val cycleDays: Int, val participants: List<ScheduleParticipantDto>, val candidates: List<ScheduleParticipantDto>)
data class SchedulePlanningConfigurationRequestDto(val cycleDays: Int, val participantIds: List<Long>)
data class TemplatesDto(val items: List<ScheduleTemplateDto>)
data class ScheduleTemplateDto(
    val id: Long,
    val name: String,
    val versionId: Long,
    val versionNumber: Int,
    val cycleDays: Int,
    val participantIds: List<Long>,
    val effectiveFrom: String?,
    val status: String,
)
data class ScheduleTemplateRequestDto(
    val name: String,
    val cycleDays: Int,
    val participantIds: List<Long>,
    val assignments: List<ScheduleAssignmentDto>,
)
data class ScheduleAssignmentDto(val cycleDay: Int, val shiftType: String, val accountIds: List<Long>)
data class ScheduleApplicationRequestDto(val templateId: Long, val effectiveFrom: String)
data class ScheduleApplicationDto(val id: Long, val templateId: Long, val versionNumber: Int, val effectiveFrom: String)

@Singleton
class NetworkScheduleRepository @Inject constructor(private val api: ScheduleApi) : ScheduleRepository {
    override suspend fun getWeek(accessToken: String, date: LocalDate) = api.week(token(accessToken), date.toString()).toDomain()
    override suspend fun getMonth(accessToken: String, month: YearMonth) = api.month(token(accessToken), month.toString()).toDomain()
    override suspend fun configuration(accessToken: String) = api.configuration(token(accessToken)).toDomain()
    override suspend fun updateConfiguration(accessToken: String, command: SchedulePlanningConfigurationCommand) = api.updateConfiguration(token(accessToken), command.toDto()).toDomain()
    override suspend fun listTemplates(accessToken: String) = api.templates(token(accessToken)).items.map { it.toDomain() }
    override suspend fun createTemplate(accessToken: String, command: ScheduleTemplateCommand) = api.createTemplate(token(accessToken), command.toDto()).toDomain()
    override suspend fun updateTemplate(accessToken: String, templateId: Long, command: ScheduleTemplateCommand) = api.updateTemplate(token(accessToken), templateId, command.toDto()).toDomain()
    override suspend fun deleteTemplate(accessToken: String, templateId: Long) = api.deleteTemplate(token(accessToken), templateId)
    override suspend fun preview(accessToken: String, templateId: Long, effectiveFrom: LocalDate) = api.preview(token(accessToken), templateId, effectiveFrom.toString()).toDomain()
    override suspend fun apply(accessToken: String, templateId: Long, effectiveFrom: LocalDate) = api.apply(token(accessToken), ScheduleApplicationRequestDto(templateId, effectiveFrom.toString())).toDomain()
    private fun token(accessToken: String) = "Bearer $accessToken"
}

private fun ScheduleWeekDto.toDomain() = ScheduleWeek(LocalDate.parse(weekStart), weekNumber, shifts.map { it.toDomain() })
private fun ScheduleMonthDto.toDomain() = ScheduleMonth(YearMonth.parse(month), days.map { ScheduleMonthDay(LocalDate.parse(it.date), it.hasShift) })
private fun ScheduleShiftDto.toDomain() = ScheduleShift(LocalDate.parse(date), ScheduleShiftType.valueOf(shiftType), persons.map { SchedulePerson(it.id, it.username, it.realName) })
private fun ScheduleParticipantDto.toDomain() = ScheduleParticipant(id, username, realName, status)
private fun SchedulePlanningConfigurationDto.toDomain() = SchedulePlanningConfiguration(cycleDays, participants.map { it.toDomain() }, candidates.map { it.toDomain() })
private fun ScheduleTemplateDto.toDomain() = ScheduleTemplateSummary(id, name, versionId, versionNumber, cycleDays, participantIds, effectiveFrom?.let(LocalDate::parse), status)
private fun ScheduleApplicationDto.toDomain() = ScheduleApplication(id, templateId, versionNumber, LocalDate.parse(effectiveFrom))
private fun SchedulePlanningConfigurationCommand.toDto() = SchedulePlanningConfigurationRequestDto(cycleDays, participantIds)
private fun ScheduleTemplateCommand.toDto() = ScheduleTemplateRequestDto(name, cycleDays, participantIds, assignments.map { ScheduleAssignmentDto(it.cycleDay, it.type.name, it.accountIds) })

@Module
@InstallIn(SingletonComponent::class)
object ScheduleNetworkModule { @Provides @Singleton fun provideScheduleApi(retrofit: Retrofit): ScheduleApi = retrofit.create(ScheduleApi::class.java) }

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleBindingModule { @Binds @Singleton abstract fun bindScheduleRepository(repository: NetworkScheduleRepository): ScheduleRepository }
