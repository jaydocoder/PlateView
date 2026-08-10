package com.jaydocoder.plateview.domain.update

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `带 v 前缀的发行版版本可比较`() {
        val installed = requireNotNull(AppVersion.parse("0.3.2"))
        val release = requireNotNull(AppVersion.parse("v0.3.10"))

        assertTrue(release > installed)
    }

    @Test
    fun `缺少补位的版本视为相同`() {
        val shortVersion = requireNotNull(AppVersion.parse("1.2"))
        val completedVersion = requireNotNull(AppVersion.parse("v1.2.0"))

        assertTrue(shortVersion.compareTo(completedVersion) == 0)
    }

    @Test
    fun `非数字版本不会参与更新判断`() {
        assertNull(AppVersion.parse("最新版本"))
    }
}
