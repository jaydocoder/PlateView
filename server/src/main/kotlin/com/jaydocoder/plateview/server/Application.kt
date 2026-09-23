package com.jaydocoder.plateview.server

import com.jaydocoder.plateview.server.infrastructure.database.configureDatabaseMigration
import com.jaydocoder.plateview.server.infrastructure.database.configureDatabaseRuntime
import com.jaydocoder.plateview.server.admin.configureAdminManagementFeature
import com.jaydocoder.plateview.server.auth.configureAuthenticationFeature
import com.jaydocoder.plateview.server.infrastructure.web.configureErrorHandling
import com.jaydocoder.plateview.server.infrastructure.web.configureRequestContext
import com.jaydocoder.plateview.server.infrastructure.web.configureRequestObservability
import com.jaydocoder.plateview.server.infrastructure.web.configureSentry
import com.jaydocoder.plateview.server.imports.configureImportPreviewFeature
import com.jaydocoder.plateview.server.vehicle.configureVehicleQueryFeature
import com.jaydocoder.plateview.server.statistics.configureVehicleStatisticsFeature
import com.jaydocoder.plateview.server.schedule.configureScheduleFeature
import com.jaydocoder.plateview.server.workorder.configureWorkOrderFeature
import com.jaydocoder.plateview.server.client.configureClientPolicyFeature
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

fun Application.module() {
    configureDatabaseMigration()
    configureDatabaseRuntime()
    configureSentry()
    configureRequestContext()
    configureRequestObservability()
    configureErrorHandling()

    install(ContentNegotiation) {
        json()
    }
    install(PartialContent)

    configureAuthenticationFeature()
    configureClientPolicyFeature()
    configureImportPreviewFeature()
    configureVehicleQueryFeature()
    configureVehicleStatisticsFeature()
    configureScheduleFeature()
    configureAdminManagementFeature()
    configureWorkOrderFeature()

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
        }
    }
}

@Serializable
data class HealthResponse(
    val status: String,
)
