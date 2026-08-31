package com.jaydocoder.plateview.server.schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleCycleTest {
    @Test
    fun `十五天模板在第十六天回到第一天`() {
        val start = LocalDate.of(2026, 8, 2)
        assertEquals(1, scheduleCycleDay(start, start, 15))
        assertEquals(15, scheduleCycleDay(start, start.plusDays(14), 15))
        assertEquals(1, scheduleCycleDay(start, start.plusDays(15), 15))
    }
}
