package com.jaydocoder.plateview.feature.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthLogoutTest {
    @Test
    fun `退出时先清除会话再清理当前账号缓存`() = runTest {
        val operations = mutableListOf<String>()

        completeLogout(
            userId = 18,
            clearSession = { operations += "清除会话" },
            clearUserCache = { userId -> operations += "清理缓存$userId" },
        )

        assertEquals(listOf("清除会话", "清理缓存18"), operations)
    }

    @Test
    fun `缓存清理失败也不能让退出失败或恢复会话`() = runTest {
        var sessionCleared = false

        completeLogout(
            userId = 19,
            clearSession = { sessionCleared = true },
            clearUserCache = { error("缓存目录暂时被占用") },
        )

        assertTrue(sessionCleared)
    }

    @Test
    fun `没有当前账号时仍然清除会话且不执行缓存清理`() = runTest {
        var sessionCleared = false
        var cacheClearCalls = 0

        completeLogout(
            userId = null,
            clearSession = { sessionCleared = true },
            clearUserCache = { cacheClearCalls += 1 },
        )

        assertTrue(sessionCleared)
        assertEquals(0, cacheClearCalls)
    }
}
