package com.jaydocoder.plateview.feature.admin

import android.net.Uri
import com.jaydocoder.plateview.data.admin.AdminImportFileReader
import com.jaydocoder.plateview.data.admin.SelectedAdminImportFile
import com.jaydocoder.plateview.domain.admin.AdminRepository
import com.jaydocoder.plateview.domain.admin.ImportBatchStats
import com.jaydocoder.plateview.domain.admin.ManagedAuditEntry
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedImportBatchSummary
import com.jaydocoder.plateview.domain.admin.ManagedImportRow
import com.jaydocoder.plateview.domain.admin.ManagedUser
import com.jaydocoder.plateview.domain.admin.ManagedVehicle
import com.jaydocoder.plateview.domain.admin.ManagedVehiclePage
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
import kotlinx.coroutines.test.advanceTimeBy
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

    @Test
    fun `车辆档案滚动加载下一页并保留真实总数`() = runTest {
        val firstVehicle = ManagedVehicleSummary(101, "新A12345", "RESIDENT", "村民车辆", "ACTIVE", 0, null)
        val secondVehicle = ManagedVehicleSummary(102, "新A12346", "RESIDENT", "村民车辆", "ACTIVE", 0, null)
        val repository = FakeAdminRepository(
            vehiclePages = listOf(listOf(firstVehicle), listOf(secondVehicle)),
            vehicleTotal = 2,
        )
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.selectTab(AdminTab.Vehicles)
        advanceUntilIdle()
        viewModel.loadMoreVehicles()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.vehicleTotalCount)
        assertEquals(listOf(firstVehicle, secondVehicle), viewModel.uiState.value.vehicles)
        assertEquals(listOf(0, 0, 1), repository.vehicleOffsets)
    }

    @Test
    fun `车辆档案搜索会重置分页并传递车牌关键字`() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.selectTab(AdminTab.Vehicles)
        advanceUntilIdle()
        viewModel.updateVehicleSearchQuery("新A1")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals("新A1", repository.vehicleKeywords.last())
        assertEquals(0, repository.vehicleOffsets.last())
    }

    @Test
    fun `导入预览滚动加载下一页并在完整加载后停止请求`() = runTest {
        val firstRow = ManagedImportRow(201, "驻景区单位", 3, 0, "新A12345", "SCENIC_UNIT", "测试单位", "VALID", "CREATE", "PUBLISH", null, null)
        val secondRow = ManagedImportRow(202, "驻景区单位", 4, 0, "新A12346", "SCENIC_UNIT", "测试单位", "VALID", "CREATE", "PUBLISH", null, null)
        val repository = FakeAdminRepository(
            importPages = listOf(listOf(firstRow), listOf(secondRow)),
            importTotal = 2,
        )
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.openImportBatch(1)
        advanceUntilIdle()
        viewModel.loadMoreImportRows()
        advanceUntilIdle()
        viewModel.loadMoreImportRows()
        advanceUntilIdle()

        assertEquals(listOf(0, 1), repository.importOffsets)
        assertEquals(listOf(firstRow, secondRow), viewModel.uiState.value.selectedImportBatch?.rows)
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
    private val vehiclePages: List<List<ManagedVehicleSummary>> = emptyList(),
    private val vehicleTotal: Int = 1,
    private val importPages: List<List<ManagedImportRow>> = emptyList(),
    private val importTotal: Int = 0,
) : AdminRepository {
    var createdVehicleCount = 0
    val vehicleOffsets = mutableListOf<Int>()
    val vehicleKeywords = mutableListOf<String?>()
    val importOffsets = mutableListOf<Int>()

    private val vehicle = ManagedVehicleSummary(101, "新A12345", "RESIDENT", "村民车辆", "ACTIVE", 0, null)
    private val user = ManagedUser(11, "operator", "USER", "ACTIVE", 0, null, null)
    private val batch = ManagedImportBatchSummary(1, "测试.xlsx", "VALIDATED", 1, 1, 0, 0, 0, null, null, null)

    override suspend fun listVehicles(
        accessToken: String,
        keyword: String?,
        limit: Int,
        offset: Int,
    ): ManagedVehiclePage {
        vehicleOffsets += offset
        vehicleKeywords += keyword
        val page = vehiclePages.getOrElse(if (offset == 0) 0 else 1) { listOf(vehicle) }
        return ManagedVehiclePage(page, vehicleTotal)
    }
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
    override suspend fun getImportBatch(
        accessToken: String,
        batchId: Long,
        limit: Int,
        offset: Int,
    ): ManagedImportBatch {
        importOffsets += offset
        val page = importPages.getOrElse(if (offset == 0) 0 else 1) { emptyList() }
        return ManagedImportBatch(
            id = batchId,
            sourceFileName = "测试导入.xlsx",
            status = "VALIDATED",
            stats = ImportBatchStats(importTotal, importTotal, 0, 0, 0, 0, importTotal, 0),
            createdAt = null,
            publishedAt = null,
            rollbackAt = null,
            rows = page,
        )
    }
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
