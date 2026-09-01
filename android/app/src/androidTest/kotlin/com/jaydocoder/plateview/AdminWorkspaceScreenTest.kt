package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.jaydocoder.plateview.domain.admin.ImportBatchStats
import com.jaydocoder.plateview.domain.admin.ManagedAuditEntry
import com.jaydocoder.plateview.domain.admin.ManagedAuditSummary
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedImportRow
import com.jaydocoder.plateview.domain.admin.ManagedImportRowDetail
import com.jaydocoder.plateview.domain.admin.ManagedUser
import com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary
import com.jaydocoder.plateview.feature.admin.AdminTab
import com.jaydocoder.plateview.feature.admin.AdminUiState
import com.jaydocoder.plateview.feature.admin.VehicleEditorState
import com.jaydocoder.plateview.feature.admin.AdminWorkspaceScreen
import com.jaydocoder.plateview.feature.admin.toEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdminWorkspaceScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 车辆档案显示真实总数并接受车牌检索输入() {
        var query: String? = null
        val vehicle = ManagedVehicleSummary(101, "新A12345", "RESIDENT", "村民车辆", "ACTIVE", 0, "小型汽车")

        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(
                        tab = AdminTab.Vehicles,
                        vehicles = listOf(vehicle),
                        vehicleTotalCount = 1165,
                        isLoading = false,
                    ),
                    onNavigateUp = {},
                    onTabSelected = {},
                    onRefresh = {},
                    onVehicleSearchQueryChanged = { query = it },
                    onLoadMoreVehicles = {},
                    onCreateVehicle = {},
                    onEditVehicle = {},
                    onVehicleEditorChanged = {},
                    onDismissVehicleEditor = {},
                    onSaveVehicle = {},
                    onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {},
                    onConfirmVehicleDeactivation = {},
                    onCreateUser = {},
                    onEditUser = {},
                    onUserEditorChanged = {},
                    onDismissUserEditor = {},
                    onSaveUser = {},
                    onChooseImport = {},
                    onOpenImportBatch = {},
                    onDismissImportBatch = {},
                    onImportResolution = { _, _ -> },
                    onPublishImport = {},
                    onRollbackImport = {},
                )
            }
        }

        composeRule.onNodeWithText("1165 条档案").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_vehicle_search").performTextInput("新A1")

        composeRule.runOnIdle {
            assertEquals("新A1", query)
        }
    }

    @Test
    fun 车辆页展示新增入口和车辆编辑命令() {
        var createCalled = false
        val vehicle = ManagedVehicleSummary(101, "新A12345", "RESIDENT", "村民车辆", "ACTIVE", 0, "小型汽车")

        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(tab = AdminTab.Vehicles, vehicles = listOf(vehicle), isLoading = false),
                    onNavigateUp = {},
                    onTabSelected = {},
                    onRefresh = {},
                    onCreateVehicle = { createCalled = true },
                    onEditVehicle = {},
                    onVehicleEditorChanged = {},
                    onDismissVehicleEditor = {},
                    onSaveVehicle = {},
                    onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {},
                    onConfirmVehicleDeactivation = {},
                    onCreateUser = {},
                    onEditUser = {},
                    onUserEditorChanged = {},
                    onDismissUserEditor = {},
                    onSaveUser = {},
                    onChooseImport = {},
                    onOpenImportBatch = {},
                    onDismissImportBatch = {},
                    onImportResolution = { _, _ -> },
                    onPublishImport = {},
                    onRollbackImport = {},
                )
            }
        }

        composeRule.onNodeWithText("新A12345").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_new_vehicle").performClick()

        assertEquals(true, createCalled)
    }

    @Test
    fun 账号页展示角色和状态() {
        val user = ManagedUser(11, "operator", "USER", "ACTIVE", 0, null, null)

        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(tab = AdminTab.Users, users = listOf(user), isLoading = false),
                    onNavigateUp = {},
                    onTabSelected = {},
                    onRefresh = {},
                    onCreateVehicle = {},
                    onEditVehicle = {},
                    onVehicleEditorChanged = {},
                    onDismissVehicleEditor = {},
                    onSaveVehicle = {},
                    onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {},
                    onConfirmVehicleDeactivation = {},
                    onCreateUser = {},
                    onEditUser = {},
                    onUserEditorChanged = {},
                    onDismissUserEditor = {},
                    onSaveUser = {},
                    onChooseImport = {},
                    onOpenImportBatch = {},
                    onDismissImportBatch = {},
                    onImportResolution = { _, _ -> },
                    onPublishImport = {},
                    onRollbackImport = {},
                )
            }
        }

        composeRule.onAllNodesWithText("operator").assertCountEquals(1)
        composeRule.onAllNodesWithText("核验员").assertCountEquals(1)
        composeRule.onAllNodesWithText("正常").assertCountEquals(1)
    }

    @Test
    fun admin编辑账号时可显示资料与头像操作() {
        val user = ManagedUser(11, "operator", "USER", "ACTIVE", 0, null, null)

        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(
                        tab = AdminTab.Users,
                        users = listOf(user),
                        isLoading = false,
                        userEditor = user.toEditor(canEditProfile = true),
                    ),
                    onNavigateUp = {}, onTabSelected = {}, onRefresh = {}, onCreateVehicle = {}, onEditVehicle = {},
                    onVehicleEditorChanged = {}, onDismissVehicleEditor = {}, onSaveVehicle = {}, onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {}, onConfirmVehicleDeactivation = {}, onCreateUser = {}, onEditUser = {},
                    onUserEditorChanged = {}, onDismissUserEditor = {}, onSaveUser = {}, onChooseImport = {},
                    onOpenImportBatch = {}, onDismissImportBatch = {}, onImportResolution = { _, _ -> },
                    onPublishImport = {}, onRollbackImport = {},
                )
            }
        }

        composeRule.onNodeWithText("账号资料").assertIsDisplayed()
        composeRule.onNodeWithText("新登录密码").assertIsDisplayed()
        composeRule.onNodeWithText("更换头像").assertIsDisplayed()
        composeRule.onNodeWithText("显示排班功能").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_schedule_access_switch").assertIsDisplayed()
    }

    @Test
    fun 非admin管理员编辑账号时不显示资料与头像操作() {
        val user = ManagedUser(11, "operator", "USER", "ACTIVE", 0, null, null)

        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(
                        tab = AdminTab.Users,
                        users = listOf(user),
                        isLoading = false,
                        userEditor = user.toEditor(canEditProfile = false),
                    ),
                    onNavigateUp = {}, onTabSelected = {}, onRefresh = {}, onCreateVehicle = {}, onEditVehicle = {},
                    onVehicleEditorChanged = {}, onDismissVehicleEditor = {}, onSaveVehicle = {}, onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {}, onConfirmVehicleDeactivation = {}, onCreateUser = {}, onEditUser = {},
                    onUserEditorChanged = {}, onDismissUserEditor = {}, onSaveUser = {}, onChooseImport = {},
                    onOpenImportBatch = {}, onDismissImportBatch = {}, onImportResolution = { _, _ -> },
                    onPublishImport = {}, onRollbackImport = {},
                )
            }
        }

        composeRule.onAllNodesWithText("账号资料").assertCountEquals(0)
        composeRule.onAllNodesWithText("新登录密码").assertCountEquals(0)
        composeRule.onAllNodesWithText("更换头像").assertCountEquals(0)
        composeRule.onAllNodesWithText("显示排班功能").assertCountEquals(0)
    }

    @Test
    fun admin账号资料不显示排班入口开关() {
        val user = ManagedUser(1, "admin", "ADMIN", "ACTIVE", 0, null, null)

        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(
                        tab = AdminTab.Users,
                        users = listOf(user),
                        isLoading = false,
                        userEditor = user.toEditor(canEditProfile = true),
                    ),
                    onNavigateUp = {}, onTabSelected = {}, onRefresh = {}, onCreateVehicle = {}, onEditVehicle = {},
                    onVehicleEditorChanged = {}, onDismissVehicleEditor = {}, onSaveVehicle = {}, onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {}, onConfirmVehicleDeactivation = {}, onCreateUser = {}, onEditUser = {},
                    onUserEditorChanged = {}, onDismissUserEditor = {}, onSaveUser = {}, onChooseImport = {},
                    onOpenImportBatch = {}, onDismissImportBatch = {}, onImportResolution = { _, _ -> },
                    onPublishImport = {}, onRollbackImport = {},
                )
            }
        }

        composeRule.onAllNodesWithText("显示排班功能").assertCountEquals(0)
    }

    @Test
    fun 编辑车辆时显示局部读取提示而非整页同步() {
        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(tab = AdminTab.Vehicles, isLoading = false, isVehicleEditorLoading = true),
                    onNavigateUp = {}, onTabSelected = {}, onRefresh = {}, onCreateVehicle = {}, onEditVehicle = {},
                    onVehicleEditorChanged = {}, onDismissVehicleEditor = {}, onSaveVehicle = {}, onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {}, onConfirmVehicleDeactivation = {}, onCreateUser = {}, onEditUser = {},
                    onUserEditorChanged = {}, onDismissUserEditor = {}, onSaveUser = {}, onChooseImport = {},
                    onOpenImportBatch = {}, onDismissImportBatch = {}, onImportResolution = { _, _ -> },
                    onPublishImport = {}, onRollbackImport = {},
                )
            }
        }

        composeRule.onNodeWithText("正在读取车辆档案").assertIsDisplayed()
        composeRule.onAllNodesWithText("正在同步管理数据...").assertCountEquals(0)
    }

    @Test
    fun 编辑弹框按资料分区并提供固定保存操作() {
        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(
                        tab = AdminTab.Vehicles,
                        isLoading = false,
                        vehicleEditor = VehicleEditorState(id = 101, plateNumber = "新A12345"),
                    ),
                    onNavigateUp = {}, onTabSelected = {}, onRefresh = {}, onCreateVehicle = {}, onEditVehicle = {},
                    onVehicleEditorChanged = {}, onDismissVehicleEditor = {}, onSaveVehicle = {}, onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {}, onConfirmVehicleDeactivation = {}, onCreateUser = {}, onEditUser = {},
                    onUserEditorChanged = {}, onDismissUserEditor = {}, onSaveUser = {}, onChooseImport = {},
                    onOpenImportBatch = {}, onDismissImportBatch = {}, onImportResolution = { _, _ -> },
                    onPublishImport = {}, onRollbackImport = {},
                )
            }
        }

        composeRule.onNodeWithText("车辆信息").assertIsDisplayed()
        composeRule.onNodeWithText("保存档案").assertIsDisplayed()
    }

    @Test
    fun 放大字体时底部取消和保存操作完整可见() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                PlateViewTheme {
                    AdminWorkspaceScreen(
                        uiState = AdminUiState(
                            tab = AdminTab.Vehicles,
                            isLoading = false,
                            vehicleEditor = VehicleEditorState(id = 101, plateNumber = "新A12345"),
                        ),
                        onNavigateUp = {}, onTabSelected = {}, onRefresh = {}, onCreateVehicle = {}, onEditVehicle = {},
                        onVehicleEditorChanged = {}, onDismissVehicleEditor = {}, onSaveVehicle = {}, onDeactivateVehicle = {},
                        onDismissVehicleDeactivation = {}, onConfirmVehicleDeactivation = {}, onCreateUser = {}, onEditUser = {},
                        onUserEditorChanged = {}, onDismissUserEditor = {}, onSaveUser = {}, onChooseImport = {},
                        onOpenImportBatch = {}, onDismissImportBatch = {}, onImportResolution = { _, _ -> },
                        onPublishImport = {}, onRollbackImport = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("admin_bottom_actions").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
        composeRule.onNodeWithText("保存档案").assertIsDisplayed()
    }

    @Test
    fun 受限管理员新增档案时只展示其他长期通行车辆() {
        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(
                        tab = AdminTab.Vehicles,
                        isLoading = false,
                        creatableVehicleCategories = listOf("OTHER_LONG_TERM"),
                        canChangeVehicleCategory = false,
                        vehicleEditor = VehicleEditorState(category = "OTHER_LONG_TERM"),
                    ),
                    onNavigateUp = {}, onTabSelected = {}, onRefresh = {}, onCreateVehicle = {}, onEditVehicle = {},
                    onVehicleEditorChanged = {}, onDismissVehicleEditor = {}, onSaveVehicle = {}, onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {}, onConfirmVehicleDeactivation = {}, onCreateUser = {}, onEditUser = {},
                    onUserEditorChanged = {}, onDismissUserEditor = {}, onSaveUser = {}, onChooseImport = {},
                    onOpenImportBatch = {}, onDismissImportBatch = {}, onImportResolution = { _, _ -> },
                    onPublishImport = {}, onRollbackImport = {},
                )
            }
        }

        composeRule.onNodeWithText("其他长期通行车辆").assertIsDisplayed()
        composeRule.onAllNodesWithText("村民车辆").assertCountEquals(0)
    }

    @Test
    fun 已撤销导入预览中点击重新发布会调用发布命令() {
        var publishCalled = 0
        val batch = ManagedImportBatch(
            id = 2,
            sourceFileName = "导入数据.xlsx",
            status = "ROLLED_BACK",
            stats = ImportBatchStats(
                totalRows = 390,
                newRows = 385,
                updateRows = 0,
                duplicateRows = 2,
                errorRows = 2,
                warningRows = 0,
                publishableRows = 385,
                pendingReviewRows = 0,
            ),
            createdAt = null,
            publishedAt = null,
            rollbackAt = null,
            rows = emptyList(),
        )

        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(tab = AdminTab.Imports, isLoading = false, selectedImportBatch = batch),
                    onNavigateUp = {},
                    onTabSelected = {},
                    onRefresh = {},
                    onCreateVehicle = {},
                    onEditVehicle = {},
                    onVehicleEditorChanged = {},
                    onDismissVehicleEditor = {},
                    onSaveVehicle = {},
                    onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {},
                    onConfirmVehicleDeactivation = {},
                    onCreateUser = {},
                    onEditUser = {},
                    onUserEditorChanged = {},
                    onDismissUserEditor = {},
                    onSaveUser = {},
                    onChooseImport = {},
                    onOpenImportBatch = {},
                    onDismissImportBatch = {},
                    onImportResolution = { _, _ -> },
                    onPublishImport = { publishCalled += 1 },
                    onRollbackImport = {},
                )
            }
        }

        composeRule.onNodeWithText("该批次已经撤销，可重新发布").assertIsDisplayed()
        composeRule.onNodeWithText("重新发布数据").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_publish_import").performClick()

        composeRule.runOnIdle {
            assertEquals(1, publishCalled)
        }
    }

    @Test
    fun 待失效记录详情展示确认失效操作() {
        val row = ManagedImportRow(
            id = 202,
            sourceSheetName = "系统差异检测",
            sourceRowNumber = 0,
            sourceItemIndex = 0,
            plateNumber = "新A12346",
            category = "SCENIC_UNIT",
            primarySubject = "测试单位",
            resultStatus = "VALID",
            plannedAction = "DEACTIVATE",
            resolution = "PENDING",
            errorMessage = null,
            warningMessage = "本次导入中未出现该车牌",
        )
        var resolution: String? = null

        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(
                        tab = AdminTab.Imports,
                        isLoading = false,
                        selectedImportRowDetail = ManagedImportRowDetail(row, emptyList(), emptyList()),
                    ),
                    onNavigateUp = {}, onTabSelected = {}, onRefresh = {}, onCreateVehicle = {}, onEditVehicle = {},
                    onVehicleEditorChanged = {}, onDismissVehicleEditor = {}, onSaveVehicle = {}, onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {}, onConfirmVehicleDeactivation = {}, onCreateUser = {}, onEditUser = {},
                    onUserEditorChanged = {}, onDismissUserEditor = {}, onSaveUser = {}, onChooseImport = {},
                    onOpenImportBatch = {}, onDismissImportBatch = {}, onImportResolution = { _, value -> resolution = value },
                    onPublishImport = {}, onRollbackImport = {},
                )
            }
        }

        composeRule.onNodeWithText("确认失效").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_import_confirm_202").performClick()
        composeRule.runOnIdle { assertEquals("PUBLISH", resolution) }
    }

    @Test
    fun 审计页显示筛选范围汇总和异常状态() {
        val entry = ManagedAuditEntry(
            id = 301,
            actorUsername = "admin",
            actionType = "VEHICLE_UPDATE",
            targetType = "VEHICLE",
            targetId = 101,
            resultStatus = "FAILURE",
            createdAt = "2026-08-09T10:00:00Z",
        )

        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(
                        tab = AdminTab.Audit,
                        isLoading = false,
                        auditEntries = listOf(entry),
                        auditTotalCount = 1,
                        auditSummary = ManagedAuditSummary(1, 0, 1, 1),
                    ),
                    onNavigateUp = {},
                    onTabSelected = {},
                    onRefresh = {},
                    onCreateVehicle = {},
                    onEditVehicle = {},
                    onVehicleEditorChanged = {},
                    onDismissVehicleEditor = {},
                    onSaveVehicle = {},
                    onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {},
                    onConfirmVehicleDeactivation = {},
                    onCreateUser = {},
                    onEditUser = {},
                    onUserEditorChanged = {},
                    onDismissUserEditor = {},
                    onSaveUser = {},
                    onChooseImport = {},
                    onOpenImportBatch = {},
                    onDismissImportBatch = {},
                    onImportResolution = { _, _ -> },
                    onPublishImport = {},
                    onRollbackImport = {},
                )
            }
        }

        composeRule.onNodeWithText("近30天").assertIsDisplayed()
        composeRule.onNodeWithText("FAILURE").assertIsDisplayed()
    }

    @Test
    fun 仅主管理员在工作台看见排班规划入口() {
        var isPrimaryAdministrator by mutableStateOf(false)
        composeRule.setContent {
            PlateViewTheme {
                AdminWorkspaceScreen(
                    uiState = AdminUiState(isLoading = false, isPrimaryAdministrator = isPrimaryAdministrator),
                    onNavigateUp = {}, onTabSelected = {}, onRefresh = {}, onCreateVehicle = {}, onEditVehicle = {},
                    onVehicleEditorChanged = {}, onDismissVehicleEditor = {}, onSaveVehicle = {}, onDeactivateVehicle = {},
                    onDismissVehicleDeactivation = {}, onConfirmVehicleDeactivation = {}, onCreateUser = {}, onEditUser = {},
                    onUserEditorChanged = {}, onDismissUserEditor = {}, onSaveUser = {}, onChooseImport = {},
                    onOpenImportBatch = {}, onDismissImportBatch = {}, onImportResolution = { _, _ -> },
                    onPublishImport = {}, onRollbackImport = {},
                )
            }
        }

        composeRule.onAllNodesWithText("排班规划").assertCountEquals(0)

        composeRule.runOnUiThread { isPrimaryAdministrator = true }
        composeRule.onNodeWithText("排班规划").assertIsDisplayed()
    }
}
