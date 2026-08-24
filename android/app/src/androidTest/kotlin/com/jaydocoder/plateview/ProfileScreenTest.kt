package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jaydocoder.plateview.feature.auth.AvatarCacheEntry
import com.jaydocoder.plateview.feature.profile.ProfileScreen
import com.jaydocoder.plateview.feature.profile.ProfileUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 账号资料入口进入分离的用户名和密码编辑页面() {
        composeRule.setContent {
            PlateViewTheme {
                ProfileScreen(
                    state = adminState(),
                    onNavigateUp = {},
                    onOpenAdmin = {},
                    onCheckForUpdate = {},
                    onLogout = {},
                    onUploadAvatar = {},
                    onDeleteAvatar = {},
                    onSaveProfile = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("编辑账号资料").performClick()

        composeRule.onAllNodesWithText("账号资料").assertCountEquals(1)
        composeRule.onAllNodesWithText("用户名").assertCountEquals(1)
        composeRule.onAllNodesWithText("登录密码").assertCountEquals(1)
    }

    @Test
    fun 退出登录显示独立确认文案() {
        var logoutCalls = 0
        composeRule.setContent {
            PlateViewTheme {
                ProfileScreen(
                    state = adminState(),
                    onNavigateUp = {},
                    onOpenAdmin = {},
                    onCheckForUpdate = {},
                    onLogout = { logoutCalls++ },
                    onUploadAvatar = {},
                    onDeleteAvatar = {},
                    onSaveProfile = { _, _, _ -> },
                )
            }
        }
        composeRule.onNodeWithTag("profile_logout_action").performScrollTo().performClick()
        composeRule.onAllNodesWithText("退出当前账号？").assertCountEquals(1)
        composeRule.onNodeWithTag("profile_confirm_logout").performClick()
        composeRule.runOnIdle { assertEquals(1, logoutCalls) }
    }

    @Test
    fun 系统更新始终显示且触发手动检查() {
        var updateChecks = 0
        composeRule.setContent {
            PlateViewTheme {
                ProfileScreen(
                    state = adminState(),
                    onNavigateUp = {},
                    onOpenAdmin = {},
                    onCheckForUpdate = { updateChecks++ },
                    onLogout = {},
                    onUploadAvatar = {},
                    onDeleteAvatar = {},
                    onSaveProfile = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("系统更新").performClick()
        composeRule.runOnIdle { assertEquals(1, updateChecks) }
    }

    @Test
    fun 我的页面使用单一操作列表且不显示分组说明文字() {
        composeRule.setContent {
            PlateViewTheme {
                ProfileScreen(
                    state = adminState(),
                    onNavigateUp = {},
                    onOpenAdmin = {},
                    onCheckForUpdate = {},
                    onLogout = {},
                    onUploadAvatar = {},
                    onDeleteAvatar = {},
                    onSaveProfile = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("账号与安全").assertExists()
        composeRule.onAllNodesWithText("账户操作").assertCountEquals(0)
        composeRule.onAllNodesWithText("检查并下载最新版本").assertCountEquals(0)
        composeRule.onAllNodesWithText("切换账号").assertCountEquals(0)
    }

    private fun adminState() = ProfileUiState(
        username = "admin",
        roleLabel = "管理员",
        avatar = AvatarCacheEntry(null, null, 0L),
    )
}
