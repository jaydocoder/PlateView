package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import com.jaydocoder.plateview.domain.workorder.CachedWorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrderAttachment
import com.jaydocoder.plateview.domain.workorder.WorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrderPerson
import com.jaydocoder.plateview.domain.workorder.WorkOrderVehicle
import com.jaydocoder.plateview.domain.workorder.WechatMessage
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.feature.auth.AvatarCacheEntry
import com.jaydocoder.plateview.feature.auth.WechatSyncHealth
import com.jaydocoder.plateview.feature.search.SearchScreen
import com.jaydocoder.plateview.feature.search.SearchUiState
import com.jaydocoder.plateview.feature.workorder.WorkOrderDetailScreen
import com.jaydocoder.plateview.feature.workorder.WorkOrderDetailUiState
import com.jaydocoder.plateview.feature.workorder.WechatMessageDetailScreen
import com.jaydocoder.plateview.feature.workorder.WechatMessageDetailUiState
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WorkOrderScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 首页车辆分区使用匹配车辆标题() {
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "新H",
                        candidates = listOf(VehicleCandidate(1, "新H12345", "RESIDENT", "村民车辆")),
                    ),
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

        composeRule.onNodeWithText("匹配车辆").assertIsDisplayed()
        composeRule.onAllNodesWithText("实时匹配").assertCountEquals(0)
    }

    @Test
    fun 首页删除全局确认框但保留车辆候选核验标签() {
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "新H",
                        candidates = listOf(VehicleCandidate(1, "新H12345", "RESIDENT", "村民车辆")),
                        dataConfirmed = true,
                    ),
                    onQueryChanged = {}, onCandidateSelected = {}, onHistorySelected = {},
                    onDeleteHistory = {}, onClearHistory = {}, onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L), onOpenProfile = {},
                )
            }
        }

        composeRule.onAllNodesWithText("核验就绪").assertCountEquals(1)
        composeRule.onAllNodesWithText("数据已确认", substring = true).assertCountEquals(0)
    }

    @Test
    fun 微信分类标题正确显示五种电脑同步状态() {
        val expectedLabels = linkedMapOf(
            "ONLINE_HEALTHY" to "电脑在线，微信记录同步正常",
            "ONLINE_SYNCING" to "电脑在线，微信记录同步中",
            "ONLINE_ERROR" to "电脑在线，微信同步异常",
            "OFFLINE" to "电脑离线",
            "UNKNOWN" to "微信同步状态暂不可用",
        )

        var currentState by mutableStateOf(expectedLabels.keys.first())
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "0916",
                        workOrderCandidates = listOf(sampleWorkOrder()),
                        wechatSyncHealth = WechatSyncHealth(currentState, "2026-09-24T14:20:00Z", "2026-09-24T14:20:05Z"),
                    ),
                    onQueryChanged = {}, onCandidateSelected = {}, onWorkOrderSelected = {},
                    onHistorySelected = {}, onDeleteHistory = {}, onClearHistory = {}, onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L), onOpenProfile = {},
                )
            }
        }

        expectedLabels.forEach { (state, expected) ->
            composeRule.runOnUiThread { currentState = state }
            composeRule.onNodeWithText(expected).assertIsDisplayed()
        }
    }

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
        composeRule.onAllNodesWithText("数据已确认", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("核验就绪").assertCountEquals(0)
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
        composeRule.onNodeWithText("相关附件").performScrollTo().assertIsDisplayed()
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
    fun 首页多车型车单按展示车牌判定当前时段() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val now = java.time.ZonedDateTime.now(zoneId)
        assumeTrue(!now.toLocalTime().isBefore(LocalTime.of(8, 0)) && now.toLocalTime().isBefore(LocalTime.of(21, 0)))
        val workOrder = sampleWorkOrder().copy(
            orderNumber = "0920028",
            rawPlate = "新H9078E、新AK8F44、新H8931B、新H30765",
            rawValidTime = now.toLocalDate().asWorkOrderDate(),
            location = "喀纳斯",
            remarks = "轻型早八晚九，重型早八晚十二，不得停靠三湾",
            sentAt = now.toLocalDate().atStartOfDay(zoneId).toInstant().toString(),
            vehicles = listOf(
                WorkOrderVehicle("工程保障车辆1 新H9078E", "新H9078E", "新H9078E", "工程保障车辆1"),
                WorkOrderVehicle("重型半挂牵引车 新H30765", "新H30765", "新H30765", "重型半挂牵引车"),
            ),
        )
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(query = "0920", workOrderCandidates = listOf(workOrder)),
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

        composeRule.onNodeWithText("新H·9078E").assertIsDisplayed()
        composeRule.onNodeWithText("不在通行时间").assertIsDisplayed()
    }

    @Test
    fun 首页重点发送者使用三个独立强调样式() {
        val messages = listOf(
            sampleWechatMessage(901, "wxid_b0rmsm0lwqjk22", "孙主任"),
            sampleWechatMessage(902, "xurujun9599", "徐站"),
            sampleWechatMessage(903, "wxid_2493514935112", "三叔"),
        )
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(query = "通行", wechatMessages = messages),
                    onQueryChanged = {},
                    onCandidateSelected = {},
                    onWorkOrderSelected = {},
                    onWechatMessageSelected = {},
                    onHistorySelected = {},
                    onDeleteHistory = {},
                    onClearHistory = {},
                    onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithTag("important_sender_director", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("important_sender_station_master", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("important_sender_uncle", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun 首页重点发送者车单显示发送者称呼和强调样式() {
        val workOrders = listOf(
            sampleWorkOrder().copy(id = 911, orderNumber = "0923001", senderUsername = "wxid_b0rmsm0lwqjk22", senderDisplay = "孙主任", senderGroupNickname = null),
            sampleWorkOrder().copy(id = 912, orderNumber = "0923002", senderUsername = "xurujun9599", senderDisplay = "徐站", senderGroupNickname = null),
            sampleWorkOrder().copy(id = 913, orderNumber = "0923003", senderUsername = "wxid_2493514935112", senderDisplay = "三叔", senderGroupNickname = null),
        )
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(query = "0923", workOrderCandidates = workOrders),
                    onQueryChanged = {}, onCandidateSelected = {}, onWorkOrderSelected = {},
                    onHistorySelected = {}, onDeleteHistory = {}, onClearHistory = {}, onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L), onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithTag("important_work_order_sender_director", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("important_work_order_sender_station_master", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("important_work_order_sender_uncle", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun 首页微信记录在配置称呼为空时仍显示发送者() {
        val message = sampleWechatMessage(904, "wxid_2493514935112", "三叔").copy(
            displayName = "",
            senderGroupNickname = "",
        )
        composeRule.setContent {
            PlateViewTheme {
                SearchScreen(
                    uiState = SearchUiState(query = "通行", wechatMessages = listOf(message)),
                    onQueryChanged = {}, onCandidateSelected = {}, onWorkOrderSelected = {}, onWechatMessageSelected = {},
                    onHistorySelected = {}, onDeleteHistory = {}, onClearHistory = {}, onRetry = {},
                    avatar = AvatarCacheEntry(null, null, 0L), onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithText("三叔").assertIsDisplayed()
    }

    @Test
    fun 微信图片消息隐藏占位正文并显示发送者和缩略图() {
        val preview = java.io.File.createTempFile("wechat-image", ".webp", composeRule.activity.cacheDir)
        val attachment = WorkOrderAttachment(81, "IMAGE", null, null, "image/jpeg", 1024, true, true, "AVAILABLE", null)
        val message = sampleWechatMessage(905, "wxid_2493514935112", "三叔").copy(
            rawContent = "[图片] local_id=1188",
            matchedSnippet = "[图片] local_id=1188",
            displayName = "",
            senderGroupNickname = "",
            attachments = listOf(attachment),
        )
        var opened = false
        composeRule.setContent {
            PlateViewTheme {
                WechatMessageDetailScreen(
                    state = WechatMessageDetailUiState(
                        isLoading = false,
                        message = message,
                        attachmentFiles = mapOf(81L to CachedWorkOrderImage(preview, "preview")),
                    ),
                    onNavigateUp = {}, onRetry = {}, onOpenAttachment = { opened = true },
                    onLoadOriginal = {}, onCloseAttachment = {},
                )
            }
        }

        composeRule.onAllNodesWithText("[图片] local_id=1188").assertCountEquals(0)
        composeRule.onAllNodesWithText("数据已确认", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("微信图片缩略图").assertCountEquals(2)
        composeRule.onNodeWithText("发送者：三叔").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("相关附件").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("微信图片缩略图")[0].performClick()
        composeRule.runOnIdle { assertTrue(opened) }
        preview.delete()
    }

    @Test
    fun 微信PDF消息隐藏占位正文并显示首页缩略图() {
        val preview = java.io.File.createTempFile("wechat-pdf", ".png", composeRule.activity.cacheDir)
        val attachment = WorkOrderAttachment(82, "PDF", "车辆申请.pdf", null, "application/pdf", 2048, true, true, "AVAILABLE", 2)
        val message = sampleWechatMessage(906, "xurujun9599", "徐如军").copy(
            rawContent = "[文件] 车辆申请.pdf (612.8 KB, pdf)",
            matchedSnippet = "[文件] 车辆申请.pdf (612.8 KB, pdf)",
            displayName = "徐站",
            attachments = listOf(attachment),
        )
        composeRule.setContent {
            PlateViewTheme {
                WechatMessageDetailScreen(
                    state = WechatMessageDetailUiState(
                        isLoading = false,
                        message = message,
                        attachmentFiles = mapOf(82L to CachedWorkOrderImage(preview, "preview")),
                    ),
                    onNavigateUp = {}, onRetry = {}, onOpenAttachment = {}, onLoadOriginal = {}, onCloseAttachment = {},
                )
            }
        }

        composeRule.onAllNodesWithText("[文件] 车辆申请.pdf (612.8 KB, pdf)").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("PDF首页缩略图").assertCountEquals(2)
        composeRule.onNodeWithText("发送者：徐站").performScrollTo().assertIsDisplayed()
        preview.delete()
    }

    @Test
    fun 微信附件弹窗使用顶部关闭入口且不显示底部关闭按钮() {
        val preview = java.io.File.createTempFile("wechat-image-dialog", ".png", composeRule.activity.cacheDir)
        val attachment = WorkOrderAttachment(83, "IMAGE", "通行说明.png", null, "image/png", 1024, true, true, "AVAILABLE", null)
        val message = sampleWechatMessage(907, "wxid_2493514935112", "三叔").copy(attachments = listOf(attachment))
        composeRule.setContent {
            PlateViewTheme {
                WechatMessageDetailScreen(
                    state = WechatMessageDetailUiState(
                        isLoading = false,
                        message = message,
                        attachmentFiles = mapOf(83L to CachedWorkOrderImage(preview, "original")),
                        selectedAttachment = attachment,
                    ),
                    onNavigateUp = {}, onRetry = {}, onOpenAttachment = {}, onLoadOriginal = {}, onCloseAttachment = {},
                )
            }
        }

        composeRule.onNodeWithTag("attachment_viewer_dialog").assertIsDisplayed()
        composeRule.onAllNodesWithText("通行说明.png").assertCountEquals(2)
        composeRule.onNodeWithContentDescription("关闭附件预览").assertIsDisplayed()
        composeRule.onAllNodesWithText("关闭").assertCountEquals(0)
        preview.delete()
    }

    @Test
    fun 车单详情同步使用缩短后的非通行时段文案() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val now = java.time.ZonedDateTime.now(zoneId)
        assumeTrue(!now.toLocalTime().isBefore(LocalTime.of(8, 0)) && now.toLocalTime().isBefore(LocalTime.of(21, 0)))
        val tomorrow = now.toLocalDate().plusDays(1)
        composeRule.setContent {
            PlateViewTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState(
                        isLoading = false,
                        sourceQuery = "0920028",
                        record = sampleWorkOrder().copy(
                            orderNumber = "0920028",
                            rawValidTime = "${now.monthValue}.${now.dayOfMonth}-${tomorrow.monthValue}.${tomorrow.dayOfMonth}",
                            location = "喀纳斯",
                            remarks = "早八晚九",
                            sentAt = now.toLocalDate().atStartOfDay(zoneId).toInstant().toString(),
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

        composeRule.onNodeWithText("不在通行时间").assertIsDisplayed()
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

    private fun sampleWechatMessage(id: Long, senderUsername: String, displayName: String) = WechatMessage(
        id = id,
        businessType = "GENERAL_MESSAGE",
        rawContent = "测试微信聊天内容",
        matchedSnippet = "测试微信聊天内容",
        sentAt = "2026-09-21T01:00:00Z",
        sourceKey = "31463879194@chatroom",
        sourceName = "贾登峪车道口",
        senderUsername = senderUsername,
        senderDisplay = displayName,
        senderGroupNickname = null,
        displayName = displayName,
        plateNumbers = emptyList(),
        attachments = emptyList(),
    )

    private fun LocalDate.asWorkOrderDate(): String = "$monthValue.$dayOfMonth"
}
