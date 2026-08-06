package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class PlateViewAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 启动时显示应用名称和准备状态() {
        composeRule.setContent {
            PlateViewApp()
        }

        composeRule.onNodeWithText("PlateView").assertIsDisplayed()
        composeRule.onNodeWithText("车辆核验服务正在准备").assertIsDisplayed()
    }
}
