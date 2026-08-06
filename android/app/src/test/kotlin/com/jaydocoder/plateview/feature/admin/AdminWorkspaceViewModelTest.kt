package com.jaydocoder.plateview.feature.admin

import android.net.Uri
import com.jaydocoder.plateview.data.admin.AdminImportFileReader
import com.jaydocoder.plateview.data.admin.SelectedAdminImportFile
import com.jaydocoder.plateview.domain.admin.AdminRepository
import com.jaydocoder.plateview.domain.admin.ImportBatchStats
import com.jaydocoder.plateview.domain.admin.ManagedAuditEntry
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedImportBatchSummary
import com.jaydocoder.plateview.domain.admin.ManagedUser
import com.jaydocoder.plateview.domain.admin.ManagedVehicle
import com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary
import com.jaydocoder.plateview.domain.admin.UserCreateCommand
import com.jaydocoder.plateview.domain.admin.UserUpdateCommand
import com.jaydocoder.plateview.domain.admin.VehicleWriteCommand
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import com.jaydocoder.plateview.feature.search.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AdminWorkspaceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `管理员打开工作台时加载概览数据`() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = createViewModel(repository = repository)

        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.vehicles.size)
        assertEquals(1, viewModel.uiState.value.users.size)
        assertEquals(1, viewModel.uiState.value.importBatches.size)
    }

    @Test
    fun `普通用户不能加载管理员数据`() = runTest {
        val viewModel = createViewModel(role = "USER")

        advanceUntilIdle()

        assertEquals(AdminFailure.PermissionDenied, viewModel.uiState.value.failure)
    }

    @Test
    fun `村民车辆缺少核验字段时不会提交仓库`() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.createVehicle()
        viewModel.updateVehicleEditor { it.copy(plateNumber = "新A12345") }
        viewModel.saveVehicle()

        assertEquals(0, repository.createdVehicleCount)
        assertTrue(viewModel.uiState.value.vehicleEditor?.error?.contains("姓名") == true)
    }

    @Test
    fun `账号版本冲突显示刷新提示`() = runTest {
        val repository = FakeAdminRepository(updateUserFailure = HttpException(Response.error<Any>(409, "".toResponseBody())))
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.editUser(11)
        viewModel.saveUser()
        advanceUntilIdle()

        assertEquals(AdminFailure.Conflict, viewModel.uiState.value.failure)
    }

    private fun createViewModel(
        repository: FakeAdminRepository = FakeAdminRepository(),
        role: String = "ADMIN",
    ): AdminWorkspaceViewModel = AdminWorkspaceViewModel(
        repository = repository,
        sessionProvider = FakeAdminSessionProvider(role),
        importFileReader = FakeAdminImportFileReader(),
    )
}

private class FakeAdminRepository(
    private val updateUserFailure: Throwable? = null,
) : AdminRepository {
    var createdVehicleCount = 0

    private val vehicle = ManagedVehicleSummary(101, "新A12345", "RESIDENT", "村民车辆", "ACTIVE", 0, null)
    private val user = ManagedUser(11, "operator", "USER", "ACTIVE", 0, null, null)
    private val batch = ManagedImportBatchSummary(1, "测试.xlsx", "VALIDATED", 1, 1, 0, 0, 0, null, null, null)

    override suspend fun listVehicles(accessToken: String, keyword: String?): List<ManagedVehicleSummary> = listOf(vehicle)
    override suspend fun getVehicle(accessToken: String, vehicleId: Long): ManagedVehicle = error("本测试不编辑已有车辆")
    override suspend fun createVehicle(accessToken: String, command: VehicleWriteCommand): ManagedVehicle {
        createdVehicleCount += 1
        error("本测试不需要返回车辆")
    }
    override suspend fun updateVehicle(accessToken: String, vehicleId: Long, version: Int, command: VehicleWriteCommand): ManagedVehicle = error("本测试不更新车辆")
    override suspend fun deactivateVehicle(accessToken: String, vehicleId: Long, version: Int): ManagedVehicle = error("本测试不停用车辆")
    override suspend fun listUsers(accessToken: String): List<ManagedUser> = listOf(user)
    override suspend fun createUser(accessToken: String, command: UserCreateCommand): ManagedUser = error("本测试不创建账号")
    override suspend fun updateUser(accessToken: String, userId: Long, version: Int, command: UserUpdateCommand): ManagedUser {
        updateUserFailure?.let { throw it }
        return user
    }
    override suspend fun listImportBatches(accessToken: String): List<ManagedImportBatchSummary> = listOf(batch)
    override suspend fun getImportBatch(accessToken: String, batchId: Long): ManagedImportBatch = error("本测试不读取导入详情")
    override suspend fun previewImport(accessToken: String, fileName: String, content: ByteArray): ManagedImportBatch = error("本测试不上传文件")
    override suspend fun updateImportResolution(accessToken: String, batchId: Long, rowId: Long, resolution: String): ManagedImportBatch = error("本测试不处理导入行")
    override suspend fun publishImport(accessToken: String, batchId: Long): ManagedImportBatch = error("本测试不发布")
    override suspend fun rollbackImport(accessToken: String, batchId: Long): ManagedImportBatch = error("本测试不回滚")
    override suspend fun listAuditEntries(accessToken: String): List<ManagedAuditEntry> = emptyList()
}

private class FakeAdminSessionProvider(role: String) : AuthSessionProvider {
    override val session: Flow<AuthSession?> = MutableStateFlow(
        AuthSession("测试令牌", "测试刷新令牌", "admin", role),
    )
    override suspend fun logout() = Unit
}

private class FakeAdminImportFileReader : AdminImportFileReader {
    override suspend fun read(uri: Uri): SelectedAdminImportFile = SelectedAdminImportFile("测试.xlsx", byteArrayOf())
}
