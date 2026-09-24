package com.jaydocoder.plateview.feature.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthLogoutTest {
    @Test
    fun `退出时只清除会话并保留设备持久化缓存`() = runTest {
        val operations = mutableListOf<String>()

        completeLogout(
            clearSession = { operations += "清除会话" },
        )

        assertTrue(operations == listOf("清除会话"))
    }

    @Test
    fun `清除会话完成后退出成功`() = runTest {
        var sessionCleared = false

        completeLogout(
            clearSession = { sessionCleared = true },
        )

        assertTrue(sessionCleared)
    }
}
