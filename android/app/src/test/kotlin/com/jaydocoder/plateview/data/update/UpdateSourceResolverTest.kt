package com.jaydocoder.plateview.data.update

import com.jaydocoder.plateview.domain.update.AppVersion
import com.jaydocoder.plateview.domain.update.ApkArchitecture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateSourceResolverTest {
    @Test
    fun `优先选择设备支持的arm64架构APK`() {
        val update = resolveAvailableUpdate(
            installedVersion = AppVersion.parse("0.3.26")!!,
            githubRelease = GitHubReleaseDto(
                "v0.3.30",
                null,
                listOf(
                    GitHubReleaseAssetDto("PlateView-v0.3.30-universal.apk", "https://github.com/universal.apk"),
                    GitHubReleaseAssetDto("PlateView-v0.3.30-arm64-v8a.apk", "https://github.com/arm64.apk"),
                ),
            ),
            serverUpdate = null,
            architecture = ApkArchitecture.ARM64_V8A,
        )

        assertEquals(ApkArchitecture.ARM64_V8A, update?.architecture)
        assertEquals(listOf("https://github.com/arm64.apk"), update?.downloadUrls)
    }

    @Test
    fun `缺少设备架构时回退universal`() {
        val update = resolveAvailableUpdate(
            installedVersion = AppVersion.parse("0.3.26")!!,
            githubRelease = null,
            serverUpdate = ServerUpdateDto(
                versionName = "0.3.30",
                sha256 = "a".repeat(64),
                artifacts = mapOf(
                    ApkArchitecture.UNIVERSAL to ServerArtifactDto("PlateView-v0.3.30-universal.apk", "a".repeat(64), 100),
                ),
            ),
            serverBaseUrl = "https://server/updates/",
            architecture = ApkArchitecture.ARMEABI_V7A,
        )

        assertEquals(ApkArchitecture.UNIVERSAL, update?.architecture)
        assertEquals(listOf("https://server/updates/PlateView-v0.3.30-universal.apk"), update?.downloadUrls)
    }

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
    fun `同版本优先配置源并保留GitHub备用下载地址`() {
        val update = resolveAvailableUpdate(
            installedVersion = AppVersion.parse("0.3.26")!!,
            githubRelease = GitHubReleaseDto("v0.3.27", "GitHub说明", listOf(GitHubReleaseAssetDto("app-release.apk", "https://github.com/app.apk"))),
            serverUpdate = ServerUpdateDto("0.3.27", "服务器说明", "https://server/app.apk", "b".repeat(64)),
        )

        assertEquals(listOf("https://server/app.apk", "https://github.com/app.apk"), update?.downloadUrls)
        assertEquals("b".repeat(64), update?.sha256)
    }

    @Test
    fun `相对APK路径由动态更新地址解析`() {
        val update = resolveAvailableUpdate(
            installedVersion = AppVersion.parse("0.3.26")!!,
            githubRelease = null,
            serverUpdate = ServerUpdateDto(
                versionName = "0.3.30",
                sha256 = "d".repeat(64),
                apkPath = "PlateView-v0.3.30.apk",
            ),
            serverBaseUrl = "https://download.example.com/updates/",
        )

        assertEquals(listOf("https://download.example.com/updates/PlateView-v0.3.30.apk"), update?.downloadUrls)
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
