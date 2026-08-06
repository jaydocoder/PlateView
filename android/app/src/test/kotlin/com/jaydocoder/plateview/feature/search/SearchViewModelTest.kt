package com.jaydocoder.plateview.feature.search

import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.history.SearchHistoryRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
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
    fun `有效字符少于四位时不请求车辆仓库`() = runTest {
        val vehicleRepository = FakeVehicleRepository()
        val viewModel = createViewModel(vehicleRepository = vehicleRepository)

        viewModel.updateQuery("A12")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertTrue(vehicleRepository.searchKeywords.isEmpty())
        assertEquals(SearchResultState.AwaitingInput, viewModel.uiState.value.resultState)
    }

    @Test
    fun `四位有效字符会归一化后查询并显示候选`() = runTest {
        val candidate = VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆")
        val vehicleRepository = FakeVehicleRepository(searchResult = listOf(candidate))
        val viewModel = createViewModel(vehicleRepository = vehicleRepository)

        viewModel.updateQuery(" 新a-123 ")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(listOf("新A123"), vehicleRepository.searchKeywords)
        assertEquals(listOf(candidate), viewModel.uiState.value.candidates)
        assertEquals(SearchResultState.Idle, viewModel.uiState.value.resultState)
    }

    @Test
    fun `无匹配时显示空结果状态`() = runTest {
        val viewModel = createViewModel(vehicleRepository = FakeVehicleRepository())

        viewModel.updateQuery("新A999")
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

        viewModel.updateQuery("新A123")
        viewModel.onVoicePermissionDenied()

        assertEquals("新A123", viewModel.uiState.value.query)
        assertEquals(VoiceInputFailure.PermissionDenied, viewModel.uiState.value.voiceFailure)
    }

    private fun createViewModel(
        vehicleRepository: FakeVehicleRepository = FakeVehicleRepository(),
        historyRepository: FakeSearchHistoryRepository = FakeSearchHistoryRepository(),
    ): SearchViewModel = SearchViewModel(
        vehicleRepository = vehicleRepository,
        historyRepository = historyRepository,
        sessionProvider = FakeAuthSessionProvider(),
        voiceRecognizer = FakeVoiceRecognizer(),
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
    override fun start(
        onResult: (String) -> Unit,
        onFailure: (VoiceInputFailure) -> Unit,
    ) = Unit

    override fun release() = Unit
}
