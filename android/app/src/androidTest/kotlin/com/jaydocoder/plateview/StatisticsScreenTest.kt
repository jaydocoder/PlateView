package com.jaydocoder.plateview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jaydocoder.plateview.data.statistics.VehicleCategoryPoint
import com.jaydocoder.plateview.data.statistics.VehicleQueryHistoryItem
import com.jaydocoder.plateview.data.statistics.VehicleStatistics
import com.jaydocoder.plateview.feature.statistics.StatisticsRange
import com.jaydocoder.plateview.feature.statistics.StatisticsScope
import com.jaydocoder.plateview.feature.statistics.StatisticsScreen
import com.jaydocoder.plateview.feature.statistics.StatisticsUiState
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 全部类别显示占比图并可通过下拉菜单选择类别() {
        var selected: String? = null
        composeRule.setContent {
            PlateViewTheme {
                StatisticsScreen(
                    state = state(category = null),
                    onRange = {},
                    onCategory = { selected = it },
                    onScope = {},
                )
            }
        }

        composeRule.onNodeWithText("类别占比").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics_category_selector").performClick()
        composeRule.onNodeWithTag("statistics_category_option_RESIDENT").performClick()

        composeRule.runOnIdle { org.junit.Assert.assertEquals("RESIDENT", selected) }
    }

    @Test
    fun 具体类别隐藏占比图并展示筛选后的查询记录() {
        composeRule.setContent {
            PlateViewTheme {
                StatisticsScreen(
                    state = state(category = "RESIDENT"),
                    onRange = {},
                    onCategory = {},
                    onScope = {},
                )
            }
        }

        composeRule.onNodeWithText("查询记录").assertIsDisplayed()
        composeRule.onNodeWithText("新A·12345").assertIsDisplayed()
        composeRule.onAllNodesWithText("类别占比").assertCountEquals(0)
    }

    private fun state(category: String?) = StatisticsUiState(
        range = StatisticsRange.TODAY,
        category = category,
        scope = StatisticsScope.ME,
        statistics = VehicleStatistics(
            totalQueries = 1,
            distinctPlates = 1,
            activeUsers = 1,
            trend = emptyList(),
            categories = listOf(VehicleCategoryPoint("RESIDENT", 1)),
        ),
        history = if (category == null) emptyList() else listOf(
            VehicleQueryHistoryItem(1, "新A12345", "RESIDENT", 1_000L),
        ),
        loading = false,
    )
}
