package com.jaydocoder.plateview.feature.update

import com.jaydocoder.plateview.domain.update.AppUpdate
import com.jaydocoder.plateview.domain.update.AppUpdateRepository
import com.jaydocoder.plateview.domain.update.UpdateCheckResult
import com.jaydocoder.plateview.domain.update.UpdateDownloadProgress
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import com.jaydocoder.plateview.feature.search.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `检测到新发行版时仅显示更新入口而不自动弹窗`() = runTest {
        val update = AppUpdate("0.3.3", "修复查询排序", "https://example.com/app-release.apk")
        val viewModel = createViewModel(update = update)

        viewModel.checkForUpdate()
        advanceUntilIdle()

        assertEquals(update, viewModel.uiState.value.update)
        assertEquals(false, viewModel.uiState.value.isUpdateDialogVisible)
        assertEquals(UpdateDownloadState.Idle, viewModel.uiState.value.downloadState)
    }

    @Test
    fun `用户打开更新详情后稍后处理仍保留更新入口`() = runTest {
        val update = AppUpdate("0.3.3", "修复查询排序", "https://example.com/app-release.apk")
        val viewModel = createViewModel(update = update)

        viewModel.checkForUpdate()
        advanceUntilIdle()
        viewModel.openUpdateDialog()
        assertEquals(true, viewModel.uiState.value.isUpdateDialogVisible)

        viewModel.dismissUpdateDialog()
        advanceUntilIdle()

        assertEquals(update, viewModel.uiState.value.update)
        assertEquals(false, viewModel.uiState.value.isUpdateDialogVisible)
    }

    @Test
    fun `下载完成后进入安装就绪状态`() = runTest {
        val apk = File("/tmp/PlateView-0.3.3.apk")
        val viewModel = createViewModel(
            update = AppUpdate("0.3.3", "修复查询排序", "https://example.com/app-release.apk"),
            downloadedFile = apk,
        )

        viewModel.checkForUpdate()
        advanceUntilIdle()
        viewModel.downloadUpdate()
        advanceUntilIdle()

        assertEquals(UpdateDownloadState.ReadyToInstall(apk), viewModel.uiState.value.downloadState)
    }

    @Test
    fun `下载失败时显示可重试状态`() = runTest {
        val viewModel = createViewModel(
            update = AppUpdate("0.3.3", "修复查询排序", "https://example.com/app-release.apk"),
            downloadFailure = IllegalStateException("网络不可用"),
        )

        viewModel.checkForUpdate()
        advanceUntilIdle()
        viewModel.downloadUpdate()
        advanceUntilIdle()

        val state = viewModel.uiState.value.downloadState
        assertTrue(state is UpdateDownloadState.Failed)
        assertTrue((state as UpdateDownloadState.Failed).message.matchesDiagnosticMessage())
    }

    @Test
    fun `用户手动检查且没有新版本时显示已是最新版本`() = runTest {
        val viewModel = createViewModel()

        viewModel.checkForUpdateFromUser()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.update)
        assertEquals(true, viewModel.uiState.value.isManualCheckDialogVisible)
        assertEquals(ManualUpdateCheckState.Latest, viewModel.uiState.value.manualCheckState)
    }

    @Test
    fun `用户手动检查发现新版本时自动打开更新详情`() = runTest {
        val update = AppUpdate("0.3.15", "新增手动检查更新", "https://example.com/app-release.apk")
        val viewModel = createViewModel(update = update)

        viewModel.checkForUpdateFromUser()
        advanceUntilIdle()

        assertEquals(update, viewModel.uiState.value.update)
        assertEquals(true, viewModel.uiState.value.isUpdateDialogVisible)
        assertEquals(false, viewModel.uiState.value.isManualCheckDialogVisible)
    }

    @Test
    fun `用户手动检查失败时显示重试状态`() = runTest {
        val viewModel = createViewModel(checkFailure = IllegalStateException("更新服务不可用"))

        viewModel.checkForUpdateFromUser()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isManualCheckDialogVisible)
        val state = viewModel.uiState.value.manualCheckState
        assertTrue(state is ManualUpdateCheckState.Failed)
        assertTrue((state as ManualUpdateCheckState.Failed).message.matchesDiagnosticMessage())
    }

@Test
fun `查询首页首次检测普通更新自动显示且稍后处理后不再显示`() = runTest {
    val update = AppUpdate("0.3.27", "更新提示策略", "https://example.com/app-release.apk")
    val viewModel = createViewModel(update = update)

    viewModel.onQueryScreenVisible()
    advanceUntilIdle()
    assertEquals(true, viewModel.uiState.value.isUpdateDialogVisible)

    viewModel.dismissUpdateDialog()
    advanceUntilIdle()
    assertEquals(false, viewModel.uiState.value.isUpdateDialogVisible)

    viewModel.onQueryScreenVisible()
    advanceUntilIdle()
    assertEquals(false, viewModel.uiState.value.isUpdateDialogVisible)
}

@Test
fun `强制更新在查询首页无法通过关闭操作绕过`() = runTest {
    val viewModel = createViewModel(
        update = AppUpdate("0.3.27", "强制更新", "https://example.com/app-release.apk"),
        updatePolicy = "FORCED",
    )

    viewModel.onQueryScreenVisible()
    advanceUntilIdle()
    viewModel.dismissUpdateDialog()

    assertEquals(true, viewModel.uiState.value.isForceUpdate)
    assertEquals(true, viewModel.uiState.value.isUpdateDialogVisible)
}

@Test
fun `强制更新无法连接服务且没有缓存时阻止查询首页`() = runTest {
    val viewModel = createViewModel(updatePolicy = "FORCED", unavailable = true)

    viewModel.onQueryScreenVisible()
    advanceUntilIdle()

    assertEquals(true, viewModel.uiState.value.isForceUpdateUnavailable)
    assertEquals(true, viewModel.uiState.value.isUpdateDialogVisible)
}

@Test
fun `启动检查早于查询首页时强制更新仍会阻止查询`() = runTest {
    val viewModel = createViewModel(updatePolicy = "FORCED", unavailable = true)

    viewModel.checkForUpdate()
    advanceUntilIdle()
    assertEquals(false, viewModel.uiState.value.isForceUpdateUnavailable)

    viewModel.onQueryScreenVisible()
    advanceUntilIdle()

    assertEquals(true, viewModel.uiState.value.isForceUpdateUnavailable)
    assertEquals(true, viewModel.uiState.value.isUpdateDialogVisible)
}
}

private fun String.matchesDiagnosticMessage(): Boolean =
    startsWith("操作未完成\n诊断编号：")

private class FakeAppUpdateRepository(
    private val update: AppUpdate? = null,
    private val downloadedFile: File = File("/tmp/PlateView.apk"),
    private val downloadFailure: Throwable? = null,
    private val checkFailure: Throwable? = null,
    private val unavailable: Boolean = false,
) : AppUpdateRepository {
    override suspend fun checkForUpdate(): UpdateCheckResult {
        checkFailure?.let { throw it }
        if (unavailable) return UpdateCheckResult.Unavailable
        return update?.let(UpdateCheckResult::Available) ?: UpdateCheckResult.UpToDate
    }

    override suspend fun download(
        update: AppUpdate,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): File {
        onProgress(UpdateDownloadProgress(50, 100))
        downloadFailure?.let { throw it }
        return downloadedFile
    }
}

private class FakeAuthSessionProvider(updatePolicy: String) : AuthSessionProvider {
    override val session: Flow<AuthSession?> = flowOf(
        AuthSession("token", "refresh", "operator", "USER", userId = 7, updatePolicy = updatePolicy),
    )

    override suspend fun logout() = Unit
}

private class FakeUpdatePromptStateRepository : UpdatePromptStateRepository {
    private val handledVersions = mutableMapOf<Long, String>()
    private val cachedUpdates = mutableMapOf<Long, AppUpdate>()

    override suspend fun handledVersion(userId: Long): String? = handledVersions[userId]
    override suspend fun markHandled(userId: Long, versionName: String) { handledVersions[userId] = versionName }
    override suspend fun cacheForcedUpdate(userId: Long, update: AppUpdate) { cachedUpdates[userId] = update }
    override suspend fun cachedForcedUpdate(userId: Long): AppUpdate? = cachedUpdates[userId]
    override suspend fun clearCachedForcedUpdate(userId: Long) { cachedUpdates.remove(userId) }
}

private fun createViewModel(
    update: AppUpdate? = null,
    downloadedFile: File = File("/tmp/PlateView.apk"),
    downloadFailure: Throwable? = null,
    checkFailure: Throwable? = null,
    unavailable: Boolean = false,
    updatePolicy: String = "OPTIONAL",
) = AppUpdateViewModel(
    repository = FakeAppUpdateRepository(update, downloadedFile, downloadFailure, checkFailure, unavailable),
    sessionProvider = FakeAuthSessionProvider(updatePolicy),
    promptStateRepository = FakeUpdatePromptStateRepository(),
)
