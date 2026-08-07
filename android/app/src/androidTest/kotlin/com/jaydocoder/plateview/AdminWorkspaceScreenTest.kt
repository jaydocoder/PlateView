package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jaydocoder.plateview.domain.admin.ImportBatchStats
import com.jaydocoder.plateview.domain.admin.ManagedImportBatch
import com.jaydocoder.plateview.domain.admin.ManagedUser
import com.jaydocoder.plateview.domain.admin.ManagedVehicleSummary
import com.jaydocoder.plateview.feature.admin.AdminTab
import com.jaydocoder.plateview.feature.admin.AdminUiState
import com.jaydocoder.plateview.feature.admin.AdminWorkspaceScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdminWorkspaceScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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

        composeRule.onNodeWithText("operator").assertIsDisplayed()
        composeRule.onNodeWithText("核验员 · 正常").assertIsDisplayed()
    }

    @Test
    fun 导入预览中点击正式发布会调用发布命令() {
        var publishCalled = 0
        val batch = ManagedImportBatch(
            id = 2,
            sourceFileName = "导入数据.xlsx",
            status = "VALIDATED",
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

        composeRule.onNodeWithTag("admin_publish_import").performClick()

        composeRule.runOnIdle {
            assertEquals(1, publishCalled)
        }
    }
}
