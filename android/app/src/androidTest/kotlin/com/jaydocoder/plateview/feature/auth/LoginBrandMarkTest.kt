package com.jaydocoder.plateview.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.jaydocoder.plateview.PlateViewTheme
import org.junit.Rule
import org.junit.Test

class LoginBrandMarkTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 标志和圆形容器均可见() {
        composeRule.setContent {
            PlateViewTheme {
                LoginBrandMark()
            }
        }

        composeRule.onNodeWithTag("login_brand_mark_container").assertIsDisplayed()
        composeRule.onNodeWithTag("login_brand_logo").assertIsDisplayed()
    }
}
