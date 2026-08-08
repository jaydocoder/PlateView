package com.jaydocoder.plateview.feature.search

import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.history.SearchHistoryRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import com.jaydocoder.plateview.domain.vehicle.CachedVehicleDetail
import com.jaydocoder.plateview.domain.vehicle.CatalogSyncResult
import com.jaydocoder.plateview.domain.vehicle.VehicleCatalogPage
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.domain.vehicle.VehicleFullCatalogPage
import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `没有有效字符时不请求车辆仓库`() = runTest {
        val vehicleRepository = FakeVehicleRepository()
        val viewModel = createViewModel(vehicleRepository = vehicleRepository)

        viewModel.updateQuery("·")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertTrue(vehicleRepository.searchKeywords.isEmpty())
        assertEquals(SearchResultState.AwaitingInput, viewModel.uiState.value.resultState)
    }

    @Test
    fun `首个有效字符会归一化后查询并显示候选`() = runTest {
        val candidate = VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆")
        val vehicleRepository = FakeVehicleRepository(searchResult = listOf(candidate))
        val viewModel = createViewModel(vehicleRepository = vehicleRepository)

        viewModel.updateQuery(" 新 ")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(listOf("新"), vehicleRepository.searchKeywords)
        assertEquals(listOf(candidate), viewModel.uiState.value.candidates)
        assertEquals(SearchResultState.Idle, viewModel.uiState.value.resultState)
    }

    @Test
    fun `本地候选优先展示且不调用远程搜索`() = runTest {
        val cached = VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆")
        val vehicleRepository = FakeVehicleRepository()
        val viewModel = createViewModel(
            vehicleRepository = vehicleRepository,
            vehicleCacheRepository = FakeVehicleCacheRepository(localCandidates = listOf(cached)),
        )

        viewModel.updateQuery("新A")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(listOf(cached), viewModel.uiState.value.candidates)
        assertTrue(vehicleRepository.searchKeywords.isEmpty())
    }

    @Test
    fun `无匹配时显示空结果状态`() = runTest {
        val viewModel = createViewModel(vehicleRepository = FakeVehicleRepository())

        viewModel.updateQuery("新A99")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(SearchResultState.Empty, viewModel.uiState.value.resultState)
    }

    @Test
    fun `网络失败时显示可重试错误状态`() = runTest {
        val viewModel = createViewModel(
            vehicleRepository = FakeVehicleRepository(searchFailure = IllegalStateException()),
        )

        viewModel.updateQuery("新A999")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(
            SearchResultState.Error(SearchFailure.ServiceUnavailable),
            viewModel.uiState.value.resultState,
        )
    }

    @Test
    fun `选择候选会保存不含详情的当前账号历史并导航`() = runTest {
        val historyRepository = FakeSearchHistoryRepository()
        val viewModel = createViewModel(historyRepository = historyRepository)
        val candidate = VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆")
        val event = async { viewModel.events.first() }
        runCurrent()

        viewModel.selectCandidate(candidate)
        advanceUntilIdle()

        assertEquals("guard-a", historyRepository.savedUsername)
        assertEquals(candidate, historyRepository.savedCandidate)
        assertEquals(SearchEvent.OpenVehicle(candidate.id), event.await())
    }

    @Test
    fun `录音权限拒绝后保留手动输入路径`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateQuery("新A12")
        viewModel.onVoicePermissionDenied()

        assertEquals("新A12", viewModel.uiState.value.query)
        assertEquals(VoiceInputFailure.PermissionDenied, viewModel.uiState.value.voiceFailure)
    }

    @Test
    fun `语音识别结果回填搜索框并触发归一化查询`() = runTest {
        val vehicleRepository = FakeVehicleRepository()
        val voiceRecognizer = FakeVoiceRecognizer()
        val viewModel = createViewModel(
            vehicleRepository = vehicleRepository,
            voiceRecognizer = voiceRecognizer,
        )

        viewModel.startVoiceInput()
        voiceRecognizer.emitResult(" 新a-12 ")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(" 新a-12 ", viewModel.uiState.value.query)
        assertEquals(listOf("新A12"), vehicleRepository.searchKeywords)
    }

    private fun createViewModel(
        vehicleRepository: FakeVehicleRepository = FakeVehicleRepository(),
        vehicleCacheRepository: VehicleCacheRepository = FakeVehicleCacheRepository(),
        historyRepository: FakeSearchHistoryRepository = FakeSearchHistoryRepository(),
        voiceRecognizer: VoiceRecognizer = FakeVoiceRecognizer(),
    ): SearchViewModel = SearchViewModel(
        vehicleRepository = vehicleRepository,
        vehicleCacheRepository = vehicleCacheRepository,
        historyRepository = historyRepository,
        sessionProvider = FakeAuthSessionProvider(),
        voiceRecognizer = voiceRecognizer,
    )
}

private class FakeVehicleRepository(
    private val searchResult: List<VehicleCandidate> = emptyList(),
    private val searchFailure: Throwable? = null,
) : VehicleRepository {
    val searchKeywords = mutableListOf<String>()

    override suspend fun search(accessToken: String, keyword: String): List<VehicleCandidate> {
        searchKeywords += keyword
        searchFailure?.let { throw it }
        return searchResult
    }

    override suspend fun getVehicle(accessToken: String, vehicleId: Long): VehicleDetail =
        error("本测试不调用车辆详情")

    override suspend fun getCatalogVersion(accessToken: String): Long = 1L

    override suspend fun getCatalog(accessToken: String, limit: Int, offset: Int): VehicleCatalogPage =
        VehicleCatalogPage(catalogVersion = 1L, total = 0, candidates = emptyList())

    override suspend fun getFullCatalog(
        accessToken: String,
        version: Long,
        limit: Int,
        offset: Int,
    ): VehicleFullCatalogPage = VehicleFullCatalogPage(catalogVersion = version, total = 0, vehicles = emptyList())
}

private class FakeVehicleCacheRepository(
    private val localCandidates: List<VehicleCandidate> = emptyList(),
) : VehicleCacheRepository {
    override suspend fun search(normalizedKeyword: String): List<VehicleCandidate> = localCandidates

    override suspend fun synchronizeCatalog(
        accessToken: String,
        forceVersionCheck: Boolean,
    ): CatalogSyncResult = CatalogSyncResult(refreshed = false)

    override suspend fun getDetail(vehicleId: Long): CachedVehicleDetail? = null

    override suspend fun clearSnapshot() = Unit
}

private class FakeSearchHistoryRepository : SearchHistoryRepository {
    private val items = MutableStateFlow<List<SearchHistoryItem>>(emptyList())
    var savedUsername: String? = null
    var savedCandidate: VehicleCandidate? = null

    override fun observe(username: String): Flow<List<SearchHistoryItem>> = items

    override suspend fun save(username: String, candidate: VehicleCandidate) {
        savedUsername = username
        savedCandidate = candidate
    }

    override suspend fun delete(username: String, historyId: Long) = Unit

    override suspend fun clear(username: String) = Unit
}

private class FakeAuthSessionProvider : AuthSessionProvider {
    override val session = MutableStateFlow(
        AuthSession(
            accessToken = "测试令牌",
            refreshToken = "测试刷新令牌",
            username = "guard-a",
            role = "USER",
        ),
    )

    override suspend fun logout() = Unit
}

private class FakeVoiceRecognizer : VoiceRecognizer {
    private var onResult: ((String) -> Unit)? = null
    private var onFailure: ((VoiceInputFailure) -> Unit)? = null

    override fun start(
        onResult: (String) -> Unit,
        onFailure: (VoiceInputFailure) -> Unit,
    ) {
        this.onResult = onResult
        this.onFailure = onFailure
    }

    override fun release() = Unit

    fun emitResult(value: String) {
        requireNotNull(onResult).invoke(value)
    }
}
