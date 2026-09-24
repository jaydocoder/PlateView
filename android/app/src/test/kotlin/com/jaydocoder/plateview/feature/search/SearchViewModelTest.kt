package com.jaydocoder.plateview.feature.search

import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.history.SearchHistoryRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import com.jaydocoder.plateview.domain.vehicle.CachedVehicleDetail
import com.jaydocoder.plateview.domain.vehicle.CatalogSyncResult
import com.jaydocoder.plateview.domain.vehicle.VehicleCatalogPage
import com.jaydocoder.plateview.domain.vehicle.VehicleCatalogChangePage
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.domain.vehicle.VehicleFullCatalogPage
import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import com.jaydocoder.plateview.domain.workorder.CachedWorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import com.jaydocoder.plateview.domain.workorder.WorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import com.jaydocoder.plateview.domain.workorder.WorkOrderSyncResult
import com.jaydocoder.plateview.domain.workorder.WechatMessage
import com.jaydocoder.plateview.domain.workorder.WechatMessagePage
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import com.jaydocoder.plateview.feature.consistency.CatalogConsistencyStateProvider
import com.jaydocoder.plateview.feature.consistency.CatalogFreshness
import com.jaydocoder.plateview.feature.consistency.CatalogKind
import com.jaydocoder.plateview.feature.consistency.CatalogSyncStatus
import com.jaydocoder.plateview.data.network.AppErrorKind
import com.jaydocoder.plateview.data.network.ClientRuntimePolicy
import com.jaydocoder.plateview.data.network.ClientRuntimePolicyProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
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
    fun `首个有效字符会归一化后查询本地缓存并显示候选`() = runTest {
        val candidate = VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆")
        val vehicleRepository = FakeVehicleRepository()
        val vehicleCacheRepository = FakeVehicleCacheRepository(localCandidates = listOf(candidate))
        val viewModel = createViewModel(
            vehicleRepository = vehicleRepository,
            vehicleCacheRepository = vehicleCacheRepository,
        )

        viewModel.updateQuery(" 新 ")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(listOf("新"), vehicleCacheRepository.searchKeywords)
        assertTrue(vehicleRepository.searchKeywords.isEmpty())
        assertEquals(listOf(candidate), viewModel.uiState.value.candidates)
        assertEquals(SearchResultState.Idle, viewModel.uiState.value.resultState)
    }

    @Test
    fun `本地候选展示后不使用远程搜索刷新`() = runTest {
        val cached = VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆")
        val vehicleRepository = FakeVehicleRepository(searchResult = listOf(cached))
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
    fun `本地缓存查询失败时显示可重试错误状态`() = runTest {
        val viewModel = createViewModel(
            vehicleCacheRepository = FakeVehicleCacheRepository(searchFailure = IllegalStateException()),
        )

        viewModel.updateQuery("新A999")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(
            AppErrorKind.Unexpected,
            (viewModel.uiState.value.resultState as SearchResultState.Error).error.kind,
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
    fun `获得权限后车辆无结果仍显示微信车单候选`() = runTest {
        val workOrder = sampleWorkOrder()
        val viewModel = createViewModel(
            workOrderRepository = FakeWorkOrderRepository(localResults = listOf(workOrder)),
            sessionProvider = FakeAuthSessionProvider(wechatAccessEnabled = true),
        )

        viewModel.updateQuery("0919011")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(listOf(workOrder), viewModel.uiState.value.workOrderCandidates)
        assertEquals(SearchResultState.Idle, viewModel.uiState.value.resultState)
    }

    @Test
    fun `选择微信车单时把当前查询词传入详情导航`() = runTest {
        val workOrder = sampleWorkOrder()
        val viewModel = createViewModel()
        val event = async { viewModel.events.first() }
        runCurrent()

        viewModel.updateQuery("H27274")
        viewModel.selectWorkOrder(workOrder)
        advanceUntilIdle()

        assertEquals(SearchEvent.OpenWorkOrder(workOrder.id, "H27274"), event.await())
    }

    @Test
    fun `三类首页候选各自最多显示八条`() = runTest {
        val vehicles = (1L..12L).map { VehicleCandidate(it, "新A${it.toString().padStart(5, '0')}", "RESIDENT", "村民车辆") }
        val workOrders = (1L..12L).map { sampleWorkOrder().copy(id = it, orderNumber = "0920${it.toString().padStart(3, '0')}") }
        val messages = (1L..12L).map { sampleWechatMessage(it) }
        val viewModel = createViewModel(
            vehicleCacheRepository = FakeVehicleCacheRepository(localCandidates = vehicles),
            workOrderRepository = FakeWorkOrderRepository(localResults = workOrders, localMessages = messages),
            sessionProvider = FakeAuthSessionProvider(wechatAccessEnabled = true),
        )

        viewModel.updateQuery("新")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(8, viewModel.uiState.value.candidates.size)
        assertEquals(8, viewModel.uiState.value.workOrderCandidates.size)
        assertEquals(8, viewModel.uiState.value.wechatMessages.size)
    }

    @Test
    fun `本地匹配车辆不等待后台目录同步即可显示`() = runTest {
        val cached = VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆")
        val remoteGate = CompletableDeferred<Unit>()
        val workOrderRepository = FakeWorkOrderRepository(synchronizeGate = remoteGate)
        val viewModel = createViewModel(
            vehicleCacheRepository = FakeVehicleCacheRepository(localCandidates = listOf(cached)),
            workOrderRepository = workOrderRepository,
            sessionProvider = FakeAuthSessionProvider(wechatAccessEnabled = true),
        )

        viewModel.updateQuery("新A")
        advanceTimeBy(250)
        runCurrent()

        assertEquals(listOf(cached), viewModel.uiState.value.candidates)
        assertEquals(0, workOrderRepository.homeSearchCalls)

        remoteGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `本地微信车单查询失败不影响本地聊天记录显示`() = runTest {
        val message = sampleWechatMessage(41)
        val viewModel = createViewModel(
            workOrderRepository = FakeWorkOrderRepository(
                localMessages = listOf(message),
                localWorkOrderFailure = IllegalStateException("本地车单查询失败"),
            ),
            sessionProvider = FakeAuthSessionProvider(wechatAccessEnabled = true),
        )

        viewModel.updateQuery("测试")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.workOrderSectionState is SearchSectionState.Error)
        assertEquals(SearchSectionState.Success, viewModel.uiState.value.wechatMessageSectionState)
        assertEquals(listOf(message), viewModel.uiState.value.wechatMessages)
    }

    @Test
    fun `缓存维护会取消进行中的搜索并丢弃旧响应`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val candidate = VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆")
        val policy = FakeRuntimePolicyProvider()
        val viewModel = createViewModel(
            vehicleCacheRepository = FakeVehicleCacheRepository(localCandidates = listOf(candidate), searchGate = gate),
            runtimePolicyProvider = policy,
        )

        viewModel.updateQuery("新A")
        advanceTimeBy(250)
        runCurrent()
        policy.maintenance.value = true
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.candidates.isEmpty())
        assertEquals(SearchResultState.Idle, viewModel.uiState.value.resultState)
    }

    @Test
    fun `三类限制为零时不发起远程查询`() = runTest {
        val vehicleRepository = FakeVehicleRepository()
        val workOrderRepository = FakeWorkOrderRepository()
        val policy = FakeRuntimePolicyProvider(
            ClientRuntimePolicy(vehicleResultLimit = 0, workOrderResultLimit = 0, wechatMessageResultLimit = 0),
        )
        val viewModel = createViewModel(
            vehicleRepository = vehicleRepository,
            workOrderRepository = workOrderRepository,
            sessionProvider = FakeAuthSessionProvider(wechatAccessEnabled = true),
            runtimePolicyProvider = policy,
        )

        viewModel.updateQuery("测试")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertTrue(vehicleRepository.searchKeywords.isEmpty())
        assertEquals(0, workOrderRepository.homeSearchCalls)
    }

    @Test
    fun `输入搜索词时三类候选均不调用远端搜索`() = runTest {
        val vehicleRepository = FakeVehicleRepository(searchResult = listOf(VehicleCandidate(1, "新A00001", "RESIDENT", "村民车辆")))
        val workOrderRepository = FakeWorkOrderRepository(
            remoteResults = listOf(sampleWorkOrder()),
            remoteMessages = listOf(sampleWechatMessage(1)),
        )
        val viewModel = createViewModel(
            vehicleRepository = vehicleRepository,
            vehicleCacheRepository = FakeVehicleCacheRepository(localCandidates = listOf(VehicleCandidate(2, "新A00002", "RESIDENT", "村民车辆"))),
            workOrderRepository = workOrderRepository,
            sessionProvider = FakeAuthSessionProvider(wechatAccessEnabled = true),
        )

        viewModel.updateQuery("新A")
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(0, vehicleRepository.searchKeywords.size)
        assertEquals(0, workOrderRepository.remoteWorkOrderSearchCalls)
        assertEquals(0, workOrderRepository.remoteMessageSearchCalls)
        assertEquals(0, workOrderRepository.homeSearchCalls)
    }

    @Test
    fun `无微信权限时微信目录失败不污染已确认车辆状态`() = runTest {
        val now = System.currentTimeMillis()
        val consistency = FakeCatalogConsistencyStateProvider(
            mapOf(
                CatalogKind.VEHICLE to CatalogFreshness(
                    kind = CatalogKind.VEHICLE,
                    appliedRevision = 44,
                    observedServerRevision = 44,
                    lastConfirmedAtEpochMillis = now,
                    status = CatalogSyncStatus.CONFIRMED,
                ),
                CatalogKind.WORK_ORDER to CatalogFreshness(CatalogKind.WORK_ORDER, status = CatalogSyncStatus.FAILED),
                CatalogKind.WECHAT_MESSAGE to CatalogFreshness(CatalogKind.WECHAT_MESSAGE, status = CatalogSyncStatus.FAILED),
                CatalogKind.ATTACHMENT to CatalogFreshness(CatalogKind.ATTACHMENT, status = CatalogSyncStatus.FAILED),
            ),
        )
        val viewModel = createViewModel(
            sessionProvider = FakeAuthSessionProvider(wechatAccessEnabled = false),
            consistencyStateProvider = consistency,
        )

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dataConfirmed)
        assertTrue(viewModel.uiState.value.freshnessLabel.startsWith("数据已确认"))
    }

    private fun createViewModel(
        vehicleRepository: FakeVehicleRepository = FakeVehicleRepository(),
        vehicleCacheRepository: VehicleCacheRepository = FakeVehicleCacheRepository(),
        historyRepository: FakeSearchHistoryRepository = FakeSearchHistoryRepository(),
        workOrderRepository: WorkOrderRepository = FakeWorkOrderRepository(),
        sessionProvider: AuthSessionProvider = FakeAuthSessionProvider(),
        runtimePolicyProvider: ClientRuntimePolicyProvider = FakeRuntimePolicyProvider(),
        consistencyStateProvider: CatalogConsistencyStateProvider = FakeCatalogConsistencyStateProvider(),
    ): SearchViewModel = SearchViewModel(
        vehicleRepository = vehicleRepository,
        vehicleCacheRepository = vehicleCacheRepository,
        historyRepository = historyRepository,
        sessionProvider = sessionProvider,
        workOrderRepository = workOrderRepository,
        runtimePolicyRepository = runtimePolicyProvider,
        consistencyStateProvider = consistencyStateProvider,
    )
}

private class FakeCatalogConsistencyStateProvider(
    initial: Map<CatalogKind, CatalogFreshness> = CatalogKind.entries.associateWith(::CatalogFreshness),
) : CatalogConsistencyStateProvider {
    override val freshness = MutableStateFlow(initial)
}

private class FakeRuntimePolicyProvider(initial: ClientRuntimePolicy = ClientRuntimePolicy()) : ClientRuntimePolicyProvider {
    override val policy = MutableStateFlow(initial)
    val maintenance = MutableStateFlow(false)
    override val cacheMaintenanceActive = maintenance
}

private class FakeWorkOrderRepository(
    private val localResults: List<WorkOrder> = emptyList(),
    private val localMessages: List<WechatMessage> = emptyList(),
    private val remoteResults: List<WorkOrder> = emptyList(),
    private val remoteMessages: List<WechatMessage> = emptyList(),
    private val synchronizeGate: CompletableDeferred<Unit>? = null,
    private val localWorkOrderFailure: Throwable? = null,
    private val localMessageFailure: Throwable? = null,
    private val remoteWorkOrderFailed: Boolean = false,
    private val remoteWechatFailed: Boolean = false,
) : WorkOrderRepository {
    var homeSearchCalls = 0
    var remoteWorkOrderSearchCalls = 0
    var remoteMessageSearchCalls = 0
    override suspend fun searchCached(userId: Long, keyword: String): List<WorkOrder> {
        localWorkOrderFailure?.let { throw it }
        return localResults
    }
    override suspend fun searchRemote(accessToken: String, keyword: String): List<WorkOrder> {
        remoteWorkOrderSearchCalls += 1
        return remoteResults
    }
    override suspend fun searchMessagesCached(userId: Long, keyword: String): List<WechatMessage> {
        localMessageFailure?.let { throw it }
        return localMessages
    }
    override suspend fun searchMessagesRemote(accessToken: String, keyword: String, offset: Int): WechatMessagePage {
        remoteMessageSearchCalls += 1
        return WechatMessagePage(remoteMessages, null)
    }
    override suspend fun searchHomeRemote(accessToken: String, userId: Long, keyword: String): com.jaydocoder.plateview.domain.workorder.WorkOrderHomeSearchResult {
        homeSearchCalls += 1
        synchronizeGate?.await()
        return com.jaydocoder.plateview.domain.workorder.WorkOrderHomeSearchResult(
            workOrders = remoteResults,
            wechatMessages = remoteMessages,
            workOrderHasMore = false,
            wechatMessageHasMore = false,
            catalogVersion = 1,
            workOrderFailed = remoteWorkOrderFailed,
            wechatMessageFailed = remoteWechatFailed,
        )
    }
    override suspend fun getCachedWechatMessage(userId: Long, messageId: Long): com.jaydocoder.plateview.domain.workorder.WechatMessage? = null
    override suspend fun refreshWechatMessage(accessToken: String, userId: Long, messageId: Long): com.jaydocoder.plateview.domain.workorder.WechatMessage =
        error("本测试不读取微信聊天详情")
    override suspend fun synchronize(
        accessToken: String,
        userId: Long,
        forceVersionCheck: Boolean,
        targetWorkOrderRevision: Long?,
        targetMessageRevision: Long?,
    ): WorkOrderSyncResult {
        synchronizeGate?.await()
        return WorkOrderSyncResult(false, 0, 0)
    }
    override suspend fun getCachedWorkOrder(userId: Long, recordId: Long): WorkOrder? = null
    override suspend fun refreshWorkOrder(accessToken: String, userId: Long, recordId: Long): WorkOrder = error("本测试不读取车单详情")
    override suspend fun getHistory(accessToken: String, recordId: Long): List<WorkOrder> = emptyList()
    override suspend fun image(
        accessToken: String,
        userId: Long,
        recordId: Long,
        image: WorkOrderImage,
        variant: String,
    ): CachedWorkOrderImage = error("本测试不读取车单图片")
    override suspend fun attachment(
        accessToken: String,
        userId: Long,
        messageId: Long,
        attachment: com.jaydocoder.plateview.domain.workorder.WorkOrderAttachment,
        variant: String,
    ): CachedWorkOrderImage = error("本测试不读取微信附件")
    override suspend fun clear(userId: Long?) = Unit
}

private class FakeVehicleRepository(
    private val searchResult: List<VehicleCandidate> = emptyList(),
    private val searchFailure: Throwable? = null,
    private val searchGate: CompletableDeferred<Unit>? = null,
) : VehicleRepository {
    val searchKeywords = mutableListOf<String>()

    override suspend fun search(accessToken: String, keyword: String): List<VehicleCandidate> {
        searchKeywords += keyword
        searchGate?.await()
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

    override suspend fun getCatalogChanges(
        accessToken: String,
        afterRevision: Long,
        afterId: Long,
        targetRevision: Long,
        limit: Int,
    ): VehicleCatalogChangePage = VehicleCatalogChangePage(
        catalogVersion = targetRevision,
        nextRevision = targetRevision,
        nextId = afterId,
        hasMore = false,
        fullSyncRequired = false,
        changes = emptyList(),
    )
}

private class FakeVehicleCacheRepository(
    private val localCandidates: List<VehicleCandidate> = emptyList(),
    private val searchFailure: Throwable? = null,
    private val searchGate: CompletableDeferred<Unit>? = null,
) : VehicleCacheRepository {
    val searchKeywords = mutableListOf<String>()

    override suspend fun search(userId: Long, normalizedKeyword: String): List<VehicleCandidate> {
        searchKeywords += normalizedKeyword
        searchGate?.await()
        searchFailure?.let { throw it }
        return localCandidates
    }

    override suspend fun synchronizeCatalog(
        accessToken: String,
        userId: Long,
        forceVersionCheck: Boolean,
        targetRevision: Long?,
    ): CatalogSyncResult = CatalogSyncResult(refreshed = false, appliedRevision = 0)

    override suspend fun getDetail(userId: Long, vehicleId: Long): CachedVehicleDetail? = null

    override suspend fun clearSnapshot(userId: Long) = Unit
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

private class FakeAuthSessionProvider(wechatAccessEnabled: Boolean = false) : AuthSessionProvider {
    override val session = MutableStateFlow(
        AuthSession(
            accessToken = "测试令牌",
            refreshToken = "测试刷新令牌",
            username = "guard-a",
            role = "USER",
            wechatWorkOrderAccessEnabled = wechatAccessEnabled,
        ),
    )

    override suspend fun logout() = Unit
}

private fun sampleWorkOrder() = WorkOrder(
    id = 31,
    orderNumber = "0919011",
    rawPlate = "新 H27274",
    normalizedPlate = "新H27274",
    vehicleType = "轻型多用途货车",
    declaredPeople = 2,
    rawValidTime = "9.20-9.23",
    location = "禾木",
    verificationMethod = "核实免门票",
    reason = "设备调试",
    remarks = "早八晚九",
    status = "ACTIVE",
    parseQuality = "COMPLETE",
    catalogRevision = 1,
    rawContent = "【单号】0919011",
    sentAt = "2026-09-20T01:00:00Z",
    sourceKey = "20546602068@chatroom",
    sourceName = "2026车单子接收群",
    senderUsername = "wxid_test",
    senderDisplay = "测试发送者",
    senderGroupNickname = null,
    people = emptyList(),
    images = emptyList(),
)

private fun sampleWechatMessage(id: Long) = WechatMessage(
    id = id,
    businessType = "GENERAL_MESSAGE",
    rawContent = "新A00001测试消息$id",
    matchedSnippet = "新A00001测试消息$id",
    sentAt = "2026-09-21T01:00:00Z",
    sourceKey = "31463879194@chatroom",
    sourceName = "贾登峪车道口",
    senderUsername = "wxid_test_$id",
    senderDisplay = "测试发送者$id",
    senderGroupNickname = null,
    displayName = "测试发送者$id",
    plateNumbers = listOf("新A00001"),
    attachments = emptyList(),
)
