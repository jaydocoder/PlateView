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

    @Test
    fun 浅色主题所有容器层与页面背景一致() {
        var background: Color? = null
        var surface: Color? = null
        var surfaceBright: Color? = null
        var surfaceDim: Color? = null
        var surfaceContainerLowest: Color? = null
        var surfaceContainerLow: Color? = null
        var surfaceContainer: Color? = null
        var surfaceContainerHigh: Color? = null
        var surfaceContainerHighest: Color? = null
        var surfaceTint: Color? = null

        composeRule.setContent {
            PlateViewTheme(useDarkTheme = false) {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    background = colors.background
                    surface = colors.surface
                    surfaceBright = colors.surfaceBright
                    surfaceDim = colors.surfaceDim
                    surfaceContainerLowest = colors.surfaceContainerLowest
                    surfaceContainerLow = colors.surfaceContainerLow
                    surfaceContainer = colors.surfaceContainer
                    surfaceContainerHigh = colors.surfaceContainerHigh
                    surfaceContainerHighest = colors.surfaceContainerHighest
                    surfaceTint = colors.surfaceTint
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(Color(0xFFEAF3EE), background)
            assertEquals(Color(0xFFEEF7F2), surface)
            assertEquals(Color(0xFFF9FFFB), surfaceBright)
            assertEquals(Color(0xFFD8E6DD), surfaceDim)
            assertEquals(Color(0xFFEAF3EE), surfaceContainerLowest)
            assertEquals(Color(0xFFEEF7F2), surfaceContainerLow)
            assertEquals(Color(0xFFE6F0E9), surfaceContainer)
            assertEquals(Color(0xFFDFEBE3), surfaceContainerHigh)
            assertEquals(Color(0xFFD6E5DC), surfaceContainerHighest)
            assertEquals(Color.Transparent, surfaceTint)
        }
    }
}
