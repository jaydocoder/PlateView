package com.jaydocoder.plateview.server

import io.ktor.client.request.get
import io.ktor.client.request.header
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

    @Test
    fun `客户端请求标识会原样回传到统一错误响应`() = testApplication {
        application {
            module()
            routing {
                get("/test-request-id") {
                    throw IllegalArgumentException("测试参数无效")
                }
            }
        }

        val response = client.get("/test-request-id") {
            header("X-Request-ID", "client-request-123")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("client-request-123", response.headers["X-Request-ID"])
        assertTrue(response.bodyAsText().contains("client-request-123"))
    }

    @Test
    fun `未处理异常返回统一内部错误和请求标识`() = testApplication {
        application {
            module()
            routing {
                get("/test-unexpected-error") {
                    error("内部测试异常")
                }
            }
        }

        val response = client.get("/test-unexpected-error") {
            header("X-Request-ID", "server-error-123")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("server-error-123", response.headers["X-Request-ID"])
        assertTrue(response.bodyAsText().contains("INTERNAL_ERROR"))
        assertTrue(response.bodyAsText().contains("server-error-123"))
    }
}
