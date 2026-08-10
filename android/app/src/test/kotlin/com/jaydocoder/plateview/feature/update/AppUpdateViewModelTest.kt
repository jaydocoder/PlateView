package com.jaydocoder.plateview.feature.update

import com.jaydocoder.plateview.domain.update.AppUpdate
import com.jaydocoder.plateview.domain.update.AppUpdateRepository
import com.jaydocoder.plateview.domain.update.UpdateDownloadProgress
import com.jaydocoder.plateview.feature.search.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        val viewModel = AppUpdateViewModel(FakeAppUpdateRepository(update = update))

        viewModel.checkForUpdate()
        advanceUntilIdle()

        assertEquals(update, viewModel.uiState.value.update)
        assertEquals(false, viewModel.uiState.value.isUpdateDialogVisible)
        assertEquals(UpdateDownloadState.Idle, viewModel.uiState.value.downloadState)
    }

    @Test
    fun `用户打开更新详情后稍后处理仍保留更新入口`() = runTest {
        val update = AppUpdate("0.3.3", "修复查询排序", "https://example.com/app-release.apk")
        val viewModel = AppUpdateViewModel(FakeAppUpdateRepository(update = update))

        viewModel.checkForUpdate()
        advanceUntilIdle()
        viewModel.openUpdateDialog()
        assertEquals(true, viewModel.uiState.value.isUpdateDialogVisible)

        viewModel.dismissUpdateDialog()

        assertEquals(update, viewModel.uiState.value.update)
        assertEquals(false, viewModel.uiState.value.isUpdateDialogVisible)
    }

    @Test
    fun `下载完成后进入安装就绪状态`() = runTest {
        val apk = File("/tmp/PlateView-0.3.3.apk")
        val viewModel = AppUpdateViewModel(
            FakeAppUpdateRepository(
                update = AppUpdate("0.3.3", "修复查询排序", "https://example.com/app-release.apk"),
                downloadedFile = apk,
            ),
        )

        viewModel.checkForUpdate()
        advanceUntilIdle()
        viewModel.downloadUpdate()
        advanceUntilIdle()

        assertEquals(UpdateDownloadState.ReadyToInstall(apk), viewModel.uiState.value.downloadState)
    }

    @Test
    fun `下载失败时显示可重试状态`() = runTest {
        val viewModel = AppUpdateViewModel(
            FakeAppUpdateRepository(
                update = AppUpdate("0.3.3", "修复查询排序", "https://example.com/app-release.apk"),
                downloadFailure = IllegalStateException("网络不可用"),
            ),
        )

        viewModel.checkForUpdate()
        advanceUntilIdle()
        viewModel.downloadUpdate()
        advanceUntilIdle()

        val state = viewModel.uiState.value.downloadState
        assertTrue(state is UpdateDownloadState.Failed)
        assertEquals("网络不可用", (state as UpdateDownloadState.Failed).message)
    }
}

private class FakeAppUpdateRepository(
    private val update: AppUpdate? = null,
    private val downloadedFile: File = File("/tmp/PlateView.apk"),
    private val downloadFailure: Throwable? = null,
) : AppUpdateRepository {
    override suspend fun findAvailableUpdate(): AppUpdate? = update

    override suspend fun download(
        update: AppUpdate,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): File {
        onProgress(UpdateDownloadProgress(50, 100))
        downloadFailure?.let { throw it }
        return downloadedFile
    }
}
