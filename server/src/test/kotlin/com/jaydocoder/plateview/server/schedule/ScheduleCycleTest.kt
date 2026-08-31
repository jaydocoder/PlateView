package com.jaydocoder.plateview.server.schedule

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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

    @Test
    fun `排班模板状态在北京时间零点后立即生效`() {
        val clock = Clock.fixed(Instant.parse("2026-08-31T16:23:00Z"), ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 9, 1), scheduleCurrentDate(clock))
    }
}
