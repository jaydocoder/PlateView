package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import com.jaydocoder.plateview.domain.workorder.WorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrderPerson
import com.jaydocoder.plateview.feature.auth.AvatarCacheEntry
import com.jaydocoder.plateview.feature.search.SearchScreen
import com.jaydocoder.plateview.feature.search.SearchUiState
import com.jaydocoder.plateview.feature.workorder.WorkOrderDetailScreen
import com.jaydocoder.plateview.feature.workorder.WorkOrderDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkOrderScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 首页车单候选突出单号车牌并保留通行摘要() {
        val workOrder = sampleWorkOrder()
        var selectedId: Long? = null
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(query = "0916", workOrderCandidates = listOf(workOrder)),
                    onQueryChanged = {},
                    onCandidateSelected = {},
                    onWorkOrderSelected = { selectedId = it.id },
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = {},
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithText("0916024").assertIsDisplayed()
        composeRule.onNodeWithText("新H·B8V00").assertIsDisplayed()
        composeRule.onAllNodesWithText("新H·E6Q96").assertCountEquals(0)
        composeRule.onAllNodesWithText("新H·C5J11").assertCountEquals(0)
        composeRule.onAllNodesWithText("四驱皮卡").assertCountEquals(0)
        composeRule.onNodeWithText("9.20-9.23 · 禾木 · 2026车单子接收群 · 早八晚九，中午两点到四点").assertIsDisplayed()
        composeRule.onAllNodesWithText("核实免门票").assertCountEquals(0)
        composeRule.onNodeWithTag("work_order_801").performClick()
        composeRule.runOnIdle { assertEquals(801L, selectedId) }
    }

    @Test
    fun 按车牌搜索时首页车单候选只显示匹配车牌() {
        val workOrder = sampleWorkOrder()
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(query = "E6Q96", workOrderCandidates = listOf(workOrder)),
                    onQueryChanged = {},
                    onCandidateSelected = {},
                    onWorkOrderSelected = {},
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = {},
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithText("新H·E6Q96").assertIsDisplayed()
        composeRule.onAllNodesWithText("新H·B8V00").assertCountEquals(0)
        composeRule.onAllNodesWithText("新H·C5J11").assertCountEquals(0)
    }

    @Test
    fun 车单详情使用景区横幅和分组完整展示关键信息() {
        val workOrder = sampleWorkOrder()
        composeRule.setContent {
            PlateViewTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState(
                        isLoading = false,
                        record = workOrder,
                        history = listOf(workOrder),
                    ),
                    onNavigateUp = {},
                    onRetry = {},
                    onOpenImage = {},
                    onLoadOriginal = {},
                    onCloseImage = {},
                )
            }
        }

        composeRule.onNodeWithTag("work_order_header").assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_identity_glass_panel").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("景区道路背景").assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_header_plate_row").assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_detail_list").performScrollToIndex(1)
        composeRule.onNodeWithText("微信原始内容").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_raw_content").assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_detail_list").performScrollToIndex(2)
        composeRule.onNodeWithText("微信来源").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_detail_list").performScrollToIndex(3)
        composeRule.onNodeWithText("通行信息").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_passage_plate_text").assertIsDisplayed()
        composeRule.onNodeWithText("新H·B8V00、新H·E6Q96、新H·C5J11").assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_field_人数").assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_field_时间").assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_detail_list").performScrollToIndex(4)
        composeRule.onNodeWithText("人员信息").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_people_text").assertIsDisplayed()
        composeRule.onNodeWithText("65432119760417201X", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_detail_list").performScrollToIndex(5)
        composeRule.onNodeWithText("事由与备注").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_detail_list").performScrollToIndex(6)
        composeRule.onNodeWithText("相关图片").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun 按单号进入详情时顶部实体车牌可单行左滑查看() {
        val workOrder = sampleWorkOrder()
        composeRule.setContent {
            PlateViewTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState(
                        isLoading = false,
                        sourceQuery = "0916024",
                        record = workOrder,
                    ),
                    onNavigateUp = {},
                    onRetry = {},
                    onOpenImage = {},
                    onLoadOriginal = {},
                    onCloseImage = {},
                )
            }
        }

        composeRule.onNodeWithText("新H·B8V00").assertIsDisplayed()
        composeRule.onNodeWithTag("work_order_header_plate_row").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("新H·C5J11").assertIsDisplayed()
    }

    @Test
    fun 按车牌进入详情时顶部只显示命中车牌但通行信息保留全部() {
        val workOrder = sampleWorkOrder()
        composeRule.setContent {
            PlateViewTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState(
                        isLoading = false,
                        sourceQuery = "E6Q96",
                        record = workOrder,
                    ),
                    onNavigateUp = {},
                    onRetry = {},
                    onOpenImage = {},
                    onLoadOriginal = {},
                    onCloseImage = {},
                )
            }
        }

        composeRule.onNodeWithText("新H·E6Q96").assertIsDisplayed()
        composeRule.onAllNodesWithText("新H·B8V00").assertCountEquals(0)
        composeRule.onAllNodesWithText("新H·C5J11").assertCountEquals(0)
        composeRule.onNodeWithTag("work_order_detail_list").performScrollToIndex(3)
        composeRule.onNodeWithText("新H·B8V00、新H·E6Q96、新H·C5J11").assertIsDisplayed()
    }

    @Test
    fun 单车牌车单横幅使用一点八比一紧凑比例() {
        val workOrder = sampleWorkOrder().copy(
            rawPlate = "新A54X27",
            rawContent = "【单号】0920014\n【车号】新A54X27\n【地点】喀纳斯",
        )
        composeRule.setContent {
            PlateViewTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState(
                        isLoading = false,
                        sourceQuery = "0920014",
                        record = workOrder,
                    ),
                    onNavigateUp = {},
                    onRetry = {},
                    onOpenImage = {},
                    onLoadOriginal = {},
                    onCloseImage = {},
                )
            }
        }

        val bounds = composeRule.onNodeWithTag("work_order_header").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width / bounds.height in 1.75f..1.85f)
    }

    @Test
    fun 首页车单候选同时显示通行有效过期和已失效状态() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)
        val active = sampleWorkOrder().copy(
            rawValidTime = today.asWorkOrderDate(),
            location = "喀纳斯",
            remarks = null,
            sentAt = today.atStartOfDay(zoneId).toInstant().toString(),
        )
        val expired = active.copy(
            id = 803,
            orderNumber = "0916022",
            rawValidTime = yesterday.asWorkOrderDate(),
            sentAt = yesterday.atStartOfDay(zoneId).toInstant().toString(),
        )
        val inactive = active.copy(id = 802, orderNumber = "0916023", status = "VOID")
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(workOrderCandidates = listOf(active, expired, inactive)),
                    onQueryChanged = {},
                    onCandidateSelected = {},
                    onWorkOrderSelected = {},
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = {},
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithText("通行时间有效").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("通行时间已过期").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("已失效").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun 车单详情横幅显示通行时间状态() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val yesterday = LocalDate.now(zoneId).minusDays(1)
        composeRule.setContent {
            PlateViewTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState(
                        isLoading = false,
                        sourceQuery = "0916024",
                        record = sampleWorkOrder().copy(
                            rawValidTime = yesterday.asWorkOrderDate(),
                            location = "喀纳斯",
                            remarks = null,
                            sentAt = yesterday.atStartOfDay(zoneId).toInstant().toString(),
                        ),
                    ),
                    onNavigateUp = {},
                    onRetry = {},
                    onOpenImage = {},
                    onLoadOriginal = {},
                    onCloseImage = {},
                )
            }
        }

        composeRule.onNodeWithText("通行时间已过期").assertIsDisplayed()
    }

    private fun sampleWorkOrder() = WorkOrder(
        id = 801,
        orderNumber = "0916024",
        rawPlate = "新H B8V00四驱皮卡",
        normalizedPlate = "新H27274",
        vehicleType = "轻型多用途货车",
        declaredPeople = 2,
        rawValidTime = "9.20-9.23",
        location = "禾木",
        verificationMethod = "核实免门票",
        reason = "为纯净水厂调试设备",
        remarks = "早八晚九，中午两点到四点，车辆不得停靠三湾",
        status = "ACTIVE",
        parseQuality = "COMPLETE",
        catalogRevision = 8,
        rawContent = "【单号】0916024\n【车号】新H B8V00、新H E6Q96、新H C5J11\n【地点】禾木",
        sentAt = "2026-09-20T01:00:00Z",
        sourceKey = "20546602068@chatroom",
        sourceName = "2026车单子接收群",
        senderUsername = "wxid-test",
        senderDisplay = "测试发送者",
        senderGroupNickname = "值班员",
        people = listOf(WorkOrderPerson("张卫华65432119760417201X", "张卫华", "65432119760417201X")),
        images = listOf(WorkOrderImage(91, null, "image/jpeg", 1024, true, true, "AVAILABLE")),
    )

    private fun LocalDate.asWorkOrderDate(): String = "$monthValue.$dayOfMonth"
}
