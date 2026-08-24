package com.jaydocoder.plateview.feature.update

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jaydocoder.plateview.BuildConfig
import com.jaydocoder.plateview.PlateViewTheme
import com.jaydocoder.plateview.domain.update.AppUpdate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppUpdateDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<UpdateTestActivity>()

    @Test
    fun 新版本弹窗显示版本并触发下载() {
        var downloadRequested = false
        composeRule.setContent {
            PlateViewTheme {
                AppUpdateDialog(
                    update = AppUpdate("0.3.3", "修复查询排序", "https://example.com/app-release.apk"),
                    downloadState = UpdateDownloadState.Idle,
                    onDownload = { downloadRequested = true },
                    onInstall = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("发现新版本").assertExists()
        composeRule.onNodeWithText("当前版本 v${BuildConfig.VERSION_NAME}").assertExists()
        composeRule.onNodeWithText("最新版本 v0.3.3").assertExists()
        composeRule.onNodeWithText("v0.3.3").assertExists()
        composeRule.onNodeWithText("立即更新").performClick()

        composeRule.runOnIdle { assertTrue(downloadRequested) }
    }

    @Test
    fun 新版本入口显示并可由用户主动打开更新详情() {
        var openRequested = false
        composeRule.setContent {
            PlateViewTheme {
                UpdateAvailableAction(onClick = { openRequested = true })
            }
        }

        composeRule.onNodeWithTag("update_available_action").performClick()
        composeRule.onNodeWithContentDescription("发现新版本，查看更新").assertExists()

        composeRule.runOnIdle { assertTrue(openRequested) }
    }

    @Test
    fun 手动检查无新版本时显示已是最新版本() {
        composeRule.setContent {
            PlateViewTheme {
                UpdateCheckDialog(
                    state = ManualUpdateCheckState.Latest,
                    onRetry = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("已是最新版本").assertExists()
        composeRule.onNodeWithText("当前版本 v${BuildConfig.VERSION_NAME} 已是最新版本。").assertExists()
    }
}
