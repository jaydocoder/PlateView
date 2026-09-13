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
            val expected = Color(0xFFF4F7F5)
            assertEquals(expected, background)
            assertEquals(expected, surface)
            assertEquals(expected, surfaceBright)
            assertEquals(expected, surfaceDim)
            assertEquals(expected, surfaceContainerLowest)
            assertEquals(expected, surfaceContainerLow)
            assertEquals(expected, surfaceContainer)
            assertEquals(expected, surfaceContainerHigh)
            assertEquals(expected, surfaceContainerHighest)
            assertEquals(Color.Transparent, surfaceTint)
        }
    }
}
