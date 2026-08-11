package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jaydocoder.plateview.domain.history.SearchHistoryItem
import com.jaydocoder.plateview.domain.vehicle.ResidentProfile
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
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
                    onOpenAdmin = null,
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithTag("search_input").assertIsDisplayed()
        composeRule.onNodeWithText("新A12345").assertIsDisplayed()
        composeRule.onNodeWithText("村民车辆").assertIsDisplayed()
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
                    onOpenAdmin = null,
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithTag("search_clear_action").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals("", query.value) }
        composeRule.onAllNodesWithTag("search_clear_action").assertCountEquals(0)
    }

    @Test
    fun 候选列表展示五类车辆类别() {
        val candidates = listOf(
            VehicleCandidate(101, "新A12345", "RESIDENT", "村民车辆"),
            VehicleCandidate(102, "新A12346", "SCENIC_UNIT", "驻景区单位车辆"),
            VehicleCandidate(103, "新A12347", "SCENIC_ENTERPRISE", "驻景区企业车辆"),
            VehicleCandidate(104, "新A12348", "CADRE", "干部车辆"),
            VehicleCandidate(105, "新A12349", "KANAS_TOURISM_DEVELOPMENT", "喀纳斯旅游发展股份有限公司车辆"),
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
                    onOpenAdmin = null,
                    onLogout = {},
                )
            }
        }

        candidates.forEach { candidate ->
            composeRule.onNodeWithText(candidate.categoryLabel).assertIsDisplayed()
        }
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
        composeRule.onNodeWithText("测试姓名").assertIsDisplayed()
        composeRule.onNodeWithText("测试证件号").assertIsDisplayed()
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
                    onOpenAdmin = null,
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("历史查询").assertIsDisplayed()
        composeRule.onNodeWithText("清空").performClick()

        assertEquals(true, clearCalled)
    }
}
