package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlateViewThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 浅色主题使用森林冰湖绿而非蓝色主色() {
        var primary: Color? = null
        var secondary: Color? = null

        composeRule.setContent {
            PlateViewTheme(useDarkTheme = false) {
                val currentPrimary = MaterialTheme.colorScheme.primary
                val currentSecondary = MaterialTheme.colorScheme.secondary
                SideEffect {
                    primary = currentPrimary
                    secondary = currentSecondary
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(Color(0xFF245B48), primary)
            assertEquals(Color(0xFF3B8878), secondary)
        }
    }
}
