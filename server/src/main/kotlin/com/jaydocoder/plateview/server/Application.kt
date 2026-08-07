package com.jaydocoder.plateview.server

import com.jaydocoder.plateview.server.infrastructure.database.configureDatabaseMigration
import com.jaydocoder.plateview.server.infrastructure.database.configureDatabaseRuntime
import com.jaydocoder.plateview.server.infrastructure.cache.configureRedisCache
import com.jaydocoder.plateview.server.admin.configureAdminManagementFeature
import com.jaydocoder.plateview.server.auth.configureAuthenticationFeature
import com.jaydocoder.plateview.server.infrastructure.web.configureErrorHandling
import com.jaydocoder.plateview.server.infrastructure.web.configureRequestContext
import com.jaydocoder.plateview.server.imports.configureImportPreviewFeature
import com.jaydocoder.plateview.server.vehicle.configureVehicleQueryFeature
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

fun Application.module() {
    configureDatabaseMigration()
    configureDatabaseRuntime()
    configureRedisCache()
    configureRequestContext()
    configureErrorHandling()

    install(ContentNegotiation) {
        json()
    }

    configureAuthenticationFeature()
    configureImportPreviewFeature()
    configureVehicleQueryFeature()
    configureAdminManagementFeature()

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
