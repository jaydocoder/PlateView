package com.jaydocoder.plateview.feature.admin

import android.net.Uri
import com.jaydocoder.plateview.data.admin.AdminImportFileReader
import com.jaydocoder.plateview.data.admin.SelectedAdminImportFile
import com.jaydocoder.plateview.domain.admin.AdminRepository
import com.jaydocoder.plateview.domain.admin.AuditFilter
import com.jaydocoder.plateview.domain.admin.AuditRange
import com.jaydocoder.plateview.domain.admin.ManagedAuditPage
import com.jaydocoder.plateview.domain.admin.ManagedAuditSummary
import com.jaydocoder.plateview.domain.admin.ImportBatchStats
import com.jaydocoder.plateview.domain.admin.ImportRowFilter
import com.jaydocoder.plateview.domain.admin.ManagedAuditEntry
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedImportBatchSummary
import com.jaydocoder.plateview.domain.admin.ManagedImportRow
import com.jaydocoder.plateview.domain.admin.ManagedImportRowDetail
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
    fun `编辑车辆仅显示局部读取状态并保留管理页面`() = runTest {
        val detailGate = CompletableDeferred<ManagedVehicle>()
        val repository = FakeAdminRepository(vehicleDetailGate = detailGate)
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.editVehicle(101)
        runCurrent()

        assertTrue(viewModel.uiState.value.isVehicleEditorLoading)
        assertTrue(!viewModel.uiState.value.isLoading)
        detailGate.complete(ManagedVehicle(
            id = 101,
            plateNumber = "新A12345",
            normalizedPlate = "新A12345",
            category = "RESIDENT",
            categoryLabel = "村民车辆",
            status = "ACTIVE",
            version = 0,
            vehicleType = null,
            attributes = emptyMap(),
            residentProfile = null,
            longTermProfile = null,
        ))
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.isVehicleEditorLoading)
        assertEquals("新A12345", viewModel.uiState.value.vehicleEditor?.plateNumber)
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

    @Test
    fun `导入差异筛选会重置分页并传递筛选条件`() = runTest {
        val initialRow = ManagedImportRow(201, "驻景区单位", 3, 0, "新A12345", "SCENIC_UNIT", "测试单位", "DUPLICATE", "UPDATE", "PENDING", null, null)
        val deactivateRow = ManagedImportRow(202, "系统差异检测", 0, 0, "新A12346", "SCENIC_UNIT", "测试单位", "VALID", "DEACTIVATE", "PENDING", null, null)
        val repository = FakeAdminRepository(
            importPagesByFilter = mapOf(
                ImportRowFilter.REVIEW to listOf(listOf(initialRow)),
                ImportRowFilter.DEACTIVATE to listOf(listOf(deactivateRow)),
            ),
            importTotal = 1,
        )
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.openImportBatch(1)
        advanceUntilIdle()
        viewModel.updateImportRowFilter(ImportRowFilter.DEACTIVATE)
        advanceUntilIdle()

        assertEquals(listOf(0, 0), repository.importOffsets)
        assertEquals(listOf(ImportRowFilter.REVIEW, ImportRowFilter.DEACTIVATE), repository.importFilters)
        assertEquals(listOf(deactivateRow), viewModel.uiState.value.selectedImportBatch?.rows)
    }

    @Test
    fun `打开导入差异详情时展示仓库返回结果`() = runTest {
        val detail = ManagedImportRowDetail(
            row = ManagedImportRow(202, "系统差异检测", 0, 0, "新A12346", "SCENIC_UNIT", "测试单位", "VALID", "DEACTIVATE", "PENDING", null, null),
            sections = emptyList(),
            sourceValues = emptyList(),
        )
        val repository = FakeAdminRepository(importDetail = detail)
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.openImportBatch(1)
        advanceUntilIdle()
        viewModel.openImportRowDetail(202)
        advanceUntilIdle()

        assertEquals(detail, viewModel.uiState.value.selectedImportRowDetail)
        assertTrue(!viewModel.uiState.value.isImportDetailLoading)
    }

    @Test
    fun `审计筛选重置分页并追加下一页`() = runTest {
        val first = ManagedAuditEntry(301, "admin", "LOGIN", "AUTH", null, "SUCCESS", "2026-08-09T10:00:00Z")
        val second = ManagedAuditEntry(302, "admin", "VEHICLE_UPDATE", "VEHICLE", 9, "FAILURE", "2026-08-09T09:00:00Z")
        val repository = FakeAdminRepository(auditPages = listOf(listOf(first), listOf(second)), auditTotal = 2)
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.selectTab(AdminTab.Audit)
        advanceUntilIdle()
        viewModel.updateAuditRange(AuditRange.WEEK)
        advanceUntilIdle()
        viewModel.loadMoreAuditEntries()
        advanceUntilIdle()

        assertEquals(listOf(0, 0, 1), repository.auditOffsets)
        assertEquals(AuditRange.WEEK, repository.auditFilters.last().range)
        assertEquals(listOf(first, second), viewModel.uiState.value.auditEntries)
        assertEquals(1, viewModel.uiState.value.auditSummary.abnormalCount)
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
    private val importPagesByFilter: Map<ImportRowFilter, List<List<ManagedImportRow>>> = emptyMap(),
    private val importTotal: Int = 0,
    private val auditPages: List<List<ManagedAuditEntry>> = emptyList(),
    private val auditTotal: Int = 0,
    private val vehicleDetailGate: CompletableDeferred<ManagedVehicle>? = null,
    private val importDetail: ManagedImportRowDetail? = null,
) : AdminRepository {
    var createdVehicleCount = 0
    val vehicleOffsets = mutableListOf<Int>()
    val vehicleKeywords = mutableListOf<String?>()
    val importOffsets = mutableListOf<Int>()
    val importFilters = mutableListOf<ImportRowFilter>()
    val auditOffsets = mutableListOf<Int>()
    val auditFilters = mutableListOf<AuditFilter>()

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
    override suspend fun getVehicle(accessToken: String, vehicleId: Long): ManagedVehicle = vehicleDetailGate?.await()
        ?: error("本测试不编辑已有车辆")
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
        filter: ImportRowFilter,
    ): ManagedImportBatch {
        importOffsets += offset
        importFilters += filter
        val pages = importPagesByFilter[filter] ?: importPages
        val page = pages.getOrElse(if (offset == 0) 0 else 1) { emptyList() }
        return ManagedImportBatch(
            id = batchId,
            sourceFileName = "测试导入.xlsx",
            status = "VALIDATED",
            stats = ImportBatchStats(
                totalRows = importTotal,
                newRows = importTotal,
                updateRows = 0,
                publishableRows = importTotal,
            ),
            createdAt = null,
            publishedAt = null,
            rollbackAt = null,
            rowTotal = importTotal,
            rows = page,
        )
    }
    override suspend fun getImportRowDetail(accessToken: String, batchId: Long, rowId: Long): ManagedImportRowDetail =
        importDetail ?: error("本测试不读取导入详情")
    override suspend fun previewImport(accessToken: String, fileName: String, content: ByteArray): ManagedImportBatch = error("本测试不上传文件")
    override suspend fun updateImportResolution(accessToken: String, batchId: Long, rowId: Long, resolution: String): ManagedImportBatch = error("本测试不处理导入行")
    override suspend fun publishImport(accessToken: String, batchId: Long): ManagedImportBatch = error("本测试不发布")
    override suspend fun rollbackImport(accessToken: String, batchId: Long): ManagedImportBatch = error("本测试不回滚")
    override suspend fun listAuditEntries(
        accessToken: String,
        filter: AuditFilter,
        limit: Int,
        offset: Int,
    ): ManagedAuditPage {
        auditOffsets += offset
        auditFilters += filter
        val page = auditPages.getOrElse(if (offset == 0) 0 else 1) { emptyList() }
        return ManagedAuditPage(
            items = page,
            total = auditTotal,
            summary = ManagedAuditSummary(auditTotal, auditTotal - page.count { it.resultStatus != "SUCCESS" }, page.count { it.resultStatus != "SUCCESS" }, 1),
            actors = emptyList(),
            actionTypes = emptyList(),
        )
    }
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
