package com.jaydocoder.plateview.feature.update

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithText("v0.3.3").assertExists()
        composeRule.onNodeWithText("立即更新").performClick()

        composeRule.runOnIdle { assertTrue(downloadRequested) }
    }
}
