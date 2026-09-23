package com.jaydocoder.plateview.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicApiAddressTest {
    @Test
    fun `运行时地址重写协议主机端口并保留请求参数`() {
        assertEquals(
            "https://new.example.com:9443/auth/profile?source=app",
            rewriteApiUrl(
                "http://127.0.0.1:8080/auth/profile?source=app",
                "http://127.0.0.1:8080/",
                "https://new.example.com:9443/",
            ),
        )
    }

    @Test
    fun `运行时基础路径替换编译基础路径`() {
        assertEquals(
            "https://new.example.com/service/auth/profile",
            rewriteApiUrl(
                "https://old.example.com/api/auth/profile",
                "https://old.example.com/api/",
                "https://new.example.com/service/",
            ),
        )
    }
}
