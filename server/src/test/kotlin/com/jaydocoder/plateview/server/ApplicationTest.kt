package com.jaydocoder.plateview.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `健康检查返回服务就绪`() = testApplication {
        application {
            module()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ok"))
    }

    @Test
    fun `无效请求返回带请求标识的统一错误`() = testApplication {
        application {
            module()
            routing {
                get("/test-invalid-request") {
                    throw IllegalArgumentException("测试参数无效")
                }
            }
        }

        val response = client.get("/test-invalid-request")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("INVALID_REQUEST"))
        assertTrue(response.headers.contains("X-Request-ID"))
    }
}
