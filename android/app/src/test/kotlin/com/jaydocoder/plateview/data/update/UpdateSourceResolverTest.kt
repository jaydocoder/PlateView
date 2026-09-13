package com.jaydocoder.plateview.data.update

import com.jaydocoder.plateview.domain.update.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateSourceResolverTest {
    @Test
    fun `服务器版本高于GitHub时选择服务器新版本`() {
        val update = resolveAvailableUpdate(
            installedVersion = AppVersion.parse("0.3.26")!!,
            githubRelease = GitHubReleaseDto("v0.3.27", "GitHub说明", listOf(GitHubReleaseAssetDto("app-release.apk", "https://github.com/app.apk"))),
            serverUpdate = ServerUpdateDto("0.3.28", "服务器说明", "https://server/app.apk", "a".repeat(64)),
        )

        assertEquals("0.3.28", update?.versionName)
        assertEquals(listOf("https://server/app.apk"), update?.downloadUrls)
    }

    @Test
    fun `同版本优先GitHub并保留服务器备用下载地址`() {
        val update = resolveAvailableUpdate(
            installedVersion = AppVersion.parse("0.3.26")!!,
            githubRelease = GitHubReleaseDto("v0.3.27", "GitHub说明", listOf(GitHubReleaseAssetDto("app-release.apk", "https://github.com/app.apk"))),
            serverUpdate = ServerUpdateDto("0.3.27", "服务器说明", "https://server/app.apk", "b".repeat(64)),
        )

        assertEquals(listOf("https://github.com/app.apk", "https://server/app.apk"), update?.downloadUrls)
        assertEquals("b".repeat(64), update?.sha256)
    }

    @Test
    fun `所有来源都不高于安装版本时没有更新`() {
        val update = resolveAvailableUpdate(
            installedVersion = AppVersion.parse("0.3.27")!!,
            githubRelease = GitHubReleaseDto("v0.3.27", null, emptyList()),
            serverUpdate = ServerUpdateDto("0.3.26", null, "https://server/app.apk", "c".repeat(64)),
        )

        assertNull(update)
    }
}
