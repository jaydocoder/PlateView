package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.vehicle.LongTermProfile
import com.jaydocoder.plateview.domain.vehicle.ResidentProfile
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.feature.auth.AvatarCacheEntry
import com.jaydocoder.plateview.feature.search.SearchScreen
import com.jaydocoder.plateview.feature.search.SearchUiState
import com.jaydocoder.plateview.feature.vehicle.VehicleDetailContent
import com.jaydocoder.plateview.feature.vehicle.VehicleDetailScreen
import com.jaydocoder.plateview.feature.vehicle.VehicleDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VehicleQueryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 搜索框不显示占位说明文字() {
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(),
                    onQueryChanged = {},
                    onCandidateSelected = {},
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = {},
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onAllNodesWithText("车牌、姓名、单位或备注").assertCountEquals(0)
    }

    @Test
    fun 候选仅展示车牌和车辆所属类型并支持点击进入详情() {
        val candidate = VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆")
        var selectedVehicleId: Long? = null

        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(candidates = listOf(candidate)),
                    onQueryChanged = {},
                    onCandidateSelected = { selectedVehicleId = it.id },
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = {},
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onAllNodesWithTag("search_input").assertCountEquals(1)
        composeRule.onAllNodesWithTag("vehicle_plate_badge", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("新A·12345").assertCountEquals(1)
        composeRule.onAllNodesWithText("村民车辆").assertCountEquals(1)
        composeRule.onNodeWithTag("candidate_101").performClick()

        assertEquals(101L, selectedVehicleId)
    }

    @Test
    fun 搜索输入不为空时可快速清空并隐藏清空按钮() {
        val query = mutableStateOf("新A12345")

        composeRule.setContent {
            val currentQuery = remember { query }
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(query = currentQuery.value),
                    onQueryChanged = { currentQuery.value = it },
                    onCandidateSelected = {},
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = {},
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithTag("search_clear_action").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals("", query.value) }
        composeRule.onAllNodesWithTag("search_clear_action").assertCountEquals(0)
    }

    @Test
    fun 候选列表展示六类车辆类别与其他长期车辆单位名称() {
        val candidates = listOf(
            VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆"),
            VehicleCandidate(102, "新A12346", "SCENIC_UNIT", "驻景区单位车辆"),
            VehicleCandidate(103, "新A12347", "SCENIC_ENTERPRISE", "驻景区企业车辆"),
            VehicleCandidate(104, "新A12348", "CADRE", "干部车辆"),
            VehicleCandidate(105, "新A12349", "KANAS_TOURISM_DEVELOPMENT", "喀旅公司车辆"),
            VehicleCandidate(106, "新A12350", "OTHER_LONG_TERM", "其他长期通行车辆", "内部维护单位"),
        )

        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(candidates = candidates),
                    onQueryChanged = {},
                    onCandidateSelected = {},
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = {},
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        candidates.forEach { candidate ->
            composeRule.onNodeWithText(candidate.categoryLabel).assertIsDisplayed()
        }
        composeRule.onNodeWithText("单位名称：内部维护单位").assertIsDisplayed()
    }

    @Test
    fun 停用车辆仍可展示并给出拉黑状态提示() {
        val candidate = VehicleCandidate(107, "新A12351", "CADRE", "干部车辆", status = "INACTIVE")

        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(candidates = listOf(candidate)),
                    onQueryChanged = {},
                    onCandidateSelected = {},
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = {},
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithTag("candidate_107").assertIsDisplayed()
        composeRule.onNodeWithText("已拉黑 / 已停用").assertIsDisplayed()
    }

    @Test
    fun 详情页显示村民核验字段() {
        val vehicle = VehicleDetail(
            id = 101,
            plateNumber = "新A12345",
            normalizedPlate = "新A12345",
            category = "RESIDENT",
            categoryLabel = "村民车辆",
            vehicleType = "小型汽车",
            status = "ACTIVE",
            attributes = emptyList(),
            residentProfile = ResidentProfile(
                ownerName = "测试姓名",
                identityCardNumber = "测试证件号",
                contactPhone = "测试联系方式",
                remarks = "测试备注",
            ),
            longTermProfile = null,
        )

        composeRule.setContent {
            PlateViewTheme {
                VehicleDetailScreen(
                    uiState = VehicleDetailUiState(VehicleDetailContent.Data(vehicle)),
                    onNavigateUp = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("村民核验信息").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("景区道路插画").assertIsDisplayed()
        composeRule.onAllNodesWithTag("vehicle_plate_badge", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithText("测试姓名").assertIsDisplayed()
        composeRule.onNodeWithText("测试证件号").assertIsDisplayed()
    }

    @Test
    fun 详情页对缺失村民核验字段显示未填写() {
        val vehicle = VehicleDetail(
            id = 102,
            plateNumber = "新A12346",
            normalizedPlate = "新A12346",
            category = "RESIDENT",
            categoryLabel = "村民车辆",
            vehicleType = null,
            status = "ACTIVE",
            attributes = emptyList(),
            residentProfile = ResidentProfile(
                ownerName = null,
                identityCardNumber = null,
                contactPhone = null,
                remarks = null,
            ),
            longTermProfile = null,
        )

        composeRule.setContent {
            PlateViewTheme {
                VehicleDetailScreen(
                    uiState = VehicleDetailUiState(VehicleDetailContent.Data(vehicle)),
                    onNavigateUp = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onAllNodesWithText("未填写").assertCountEquals(2)
    }

    @Test
    fun 详情页完整显示长期车辆长字段() {
        val longRemarks = "黄色皮卡，车身喷燃气设备标识，需保持长期通行资格"
        val passageDetails = "车辆用途：应急抢险车辆为燃气用户检查、维护燃气设备；通行区域：喀纳斯、禾木、白哈巴"
        val vehicle = VehicleDetail(
            id = 108,
            plateNumber = "新HC0C21",
            normalizedPlate = "新HC0C21",
            category = "SCENIC_ENTERPRISE",
            categoryLabel = "驻景区企业车辆",
            vehicleType = "皮卡",
            status = "ACTIVE",
            attributes = emptyList(),
            residentProfile = null,
            longTermProfile = LongTermProfile(
                organizationName = "测试单位",
                passHolder = "测试通行人员",
                passageDetails = passageDetails,
                remarks = longRemarks,
            ),
        )

        composeRule.setContent {
            PlateViewTheme {
                VehicleDetailScreen(
                    uiState = VehicleDetailUiState(VehicleDetailContent.Data(vehicle)),
                    onNavigateUp = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText(passageDetails).assertIsDisplayed()
        composeRule.onNodeWithText(longRemarks).assertIsDisplayed()
    }

    @Test
    fun 停用车辆详情显示通行资格限制() {
        val vehicle = VehicleDetail(
            id = 103,
            plateNumber = "新A12347",
            normalizedPlate = "新A12347",
            category = "CADRE",
            categoryLabel = "干部车辆",
            vehicleType = null,
            status = "INACTIVE",
            attributes = emptyList(),
            residentProfile = null,
            longTermProfile = null,
        )

        composeRule.setContent {
            PlateViewTheme {
                VehicleDetailScreen(
                    uiState = VehicleDetailUiState(VehicleDetailContent.Data(vehicle)),
                    onNavigateUp = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("该车辆档案已停用，不具备有效通行核验资格").assertIsDisplayed()
    }

    @Test
    fun 历史记录显示后支持清空命令() {
        var clearCalled = false
        val history = SearchHistoryItem(
            id = 1,
            vehicleId = 101,
            plateNumber = "新A12345",
            category = "RESIDENT",
            categoryLabel = "村民车辆",
            searchedAtEpochMillis = 0L,
        )

        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(history = listOf(history)),
                    onQueryChanged = {},
                    onCandidateSelected = {},
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = { clearCalled = true },
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithText("历史查询").assertIsDisplayed()
        composeRule.onNodeWithText("清空").performClick()

        assertEquals(true, clearCalled)
    }
}
