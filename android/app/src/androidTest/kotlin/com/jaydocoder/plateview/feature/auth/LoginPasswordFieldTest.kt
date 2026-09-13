package com.jaydocoder.plateview.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.jaydocoder.plateview.PlateViewTheme
import org.junit.Rule
import org.junit.Test

class LoginPasswordFieldTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 密码默认隐藏且眼睛按钮可切换显示状态() {
        composeRule.setContent {
            PlateViewTheme {
                LoginPasswordField(
                    value = "测试密码",
                    onValueChange = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("显示密码").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("隐藏密码").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("显示密码").assertIsDisplayed()
    }
}
