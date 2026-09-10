package com.jaydocoder.plateview.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class RequestIdInterceptorTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .addInterceptor(RequestIdInterceptor())
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `请求自动携带诊断编号`() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(server.url("/admin/vehicles")).build()).execute().use { }

        assertNotNull(server.takeRequest().getHeader("X-Request-ID"))
    }

    @Test
    fun `已有诊断编号会原样保留`() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(
            Request.Builder()
                .url(server.url("/admin/vehicles"))
                .header("X-Request-ID", "client-request-123")
                .build(),
        ).execute().use { }

        assertEquals("client-request-123", server.takeRequest().getHeader("X-Request-ID"))
    }
}
