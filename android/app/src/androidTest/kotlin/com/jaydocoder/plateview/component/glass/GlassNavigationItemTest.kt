package com.jaydocoder.plateview.component.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jaydocoder.plateview.PlateViewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlassNavigationItemTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 选中和未选中时图标始终居中且位置不变() {
        composeRule.setContent {
            val selected = remember { mutableStateOf(true) }
            PlateViewTheme {
                Row(Modifier.fillMaxWidth()) {
                    GlassNavigationItem(
                        label = "首页",
                        icon = Icons.Outlined.Home,
                        selected = selected.value,
                        onClick = { selected.value = !selected.value },
                    )
                }
            }
        }

        val iconTag = "glass_navigation_icon_首页"
        val containerTag = "glass_navigation_icon_container_首页"
        val selectedCenter = composeRule.onNodeWithTag(iconTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        val selectedContainerCenter = composeRule.onNodeWithTag(containerTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        assertCentersMatch(selectedContainerCenter, selectedCenter, tolerance = 0.75f)

        composeRule.onNodeWithTag("glass_navigation_item_首页").performClick()
        val unselectedCenter = composeRule.onNodeWithTag(iconTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        assertCentersMatch(selectedCenter, unselectedCenter)

        composeRule.onNodeWithTag("glass_navigation_item_首页").performClick()
        val restoredCenter = composeRule.onNodeWithTag(iconTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center
        assertCentersMatch(selectedCenter, restoredCenter)
    }

    private fun assertCentersMatch(expected: Offset, actual: Offset, tolerance: Float = 0.01f) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
    }
}
