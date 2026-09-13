package com.jaydocoder.plateview.component.glass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassCompatibilityTest {
    @Test
    fun 仅Android15启用实时背景采样() {
        assertFalse(supportsLiquidBackdrop(31))
        assertFalse(supportsLiquidBackdrop(34))
        assertTrue(supportsLiquidBackdrop(35))
        assertFalse(supportsLiquidBackdrop(36))
    }
}
