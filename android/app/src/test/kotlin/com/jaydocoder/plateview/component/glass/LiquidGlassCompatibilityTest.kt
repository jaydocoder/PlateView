package com.jaydocoder.plateview.component.glass

import org.junit.Assert.assertFalse
import org.junit.Test

class LiquidGlassCompatibilityTest {
    @Test
    fun 所有设备均关闭实时背景采样() {
        assertFalse(supportsLiquidBackdrop(31, manufacturer = "realme", brand = "realme"))
        assertFalse(supportsLiquidBackdrop(34, manufacturer = "HONOR", brand = "HONOR"))
        assertFalse(supportsLiquidBackdrop(35, manufacturer = "Google", brand = "google"))
        assertFalse(supportsLiquidBackdrop(36, manufacturer = "HUAWEI", brand = "HUAWEI"))
    }

    @Test
    fun 荣耀与华为设备始终使用静态玻璃() {
        assertFalse(supportsLiquidBackdrop(35, manufacturer = "HONOR", brand = "HONOR"))
        assertFalse(supportsLiquidBackdrop(35, manufacturer = "HUAWEI", brand = "HUAWEI"))
    }
}
