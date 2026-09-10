package com.jaydocoder.plateview.data.network

import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

class AppErrorMapperTest {
    @Test
    fun `超时映射为可重试的明确错误`() {
        val error = AppErrorMapper.map("筛选车辆档案", SocketTimeoutException("timeout"))

        assertEquals(AppErrorKind.Timeout, error.kind)
        assertTrue(error.retryable)
        assertEquals("请求超时，车辆档案尚未刷新", error.message)
    }

    @Test
    fun `服务端错误响应保留诊断编号和业务错误码`() {
        val body = """{"code":"ADMIN_CONFLICT","message":"数据已被其他管理员修改","requestId":"srv-123"}""".toResponseBody()
        val error = AppErrorMapper.map(
            "拉黑车辆",
            HttpException(Response.error<Any>(409, body)),
        )

        assertEquals(AppErrorKind.Conflict, error.kind)
        assertEquals("srv-123", error.requestId)
        assertEquals("ADMIN_CONFLICT", error.serverCode)
        assertEquals("数据已被其他管理员修改，请刷新后再试", error.message)
    }

    @Test
    fun `网关繁忙被映射为可重试错误`() {
        val error = AppErrorMapper.map(
            "筛选车辆档案",
            HttpException(Response.error<Any>(503, "暂时繁忙".toResponseBody())),
        )

        assertEquals(AppErrorKind.GatewayUnavailable, error.kind)
        assertTrue(error.retryable)
        assertTrue(error.message.contains("服务正在切换或暂时繁忙"))
    }

    @Test
    fun `无效响应格式显示诊断编号而不暴露原始内容`() {
        val error = AppErrorMapper.map(
            "查询车辆",
            com.google.gson.JsonSyntaxException("身份证号不应暴露"),
        )

        assertEquals(AppErrorKind.InvalidResponse, error.kind)
        assertTrue(error.message.contains("服务器返回的数据格式异常"))
        assertTrue(!error.message.contains("身份证号"))
        assertTrue(error.requestId.isNotBlank())
    }

    @Test
    fun `未知异常仅由统一展示层追加一次诊断编号`() {
        val error = AppErrorMapper.map("检查更新", IllegalStateException("内部细节"))

        assertEquals("操作未完成", error.message)
        assertEquals(1, error.displayText().split("诊断编号：").size - 1)
        assertTrue(!error.displayText().contains("内部细节"))
    }

    @Test(expected = CancellationException::class)
    fun `协程取消不会被映射为界面错误`() {
        CancellationException().rethrowIfCancellation()
    }

    @Test
    fun `预期业务错误不会作为异常事件上报`() {
        val error = AppErrorMapper.map(
            "编辑车辆",
            HttpException(Response.error<Any>(409, "".toResponseBody())),
        )

        assertTrue(!AppErrorTelemetry.shouldReport(error))
    }
}
