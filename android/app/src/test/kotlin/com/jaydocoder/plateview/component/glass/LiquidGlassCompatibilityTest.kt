package com.jaydocoder.plateview.component.glass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

class LiquidGlassCompatibilityTest {
    @Test
    fun 非Android15设备关闭实时背景采样() {
        assertFalse(supportsLiquidBackdrop(31, manufacturer = "realme", brand = "realme"))
        assertFalse(supportsLiquidBackdrop(34, manufacturer = "HONOR", brand = "HONOR"))
        assertFalse(supportsLiquidBackdrop(36, manufacturer = "HUAWEI", brand = "HUAWEI"))
    }

    @Test
    fun Android15安全设备启用实时玻璃而高风险厂商降级() {
        org.junit.Assert.assertTrue(supportsLiquidBackdrop(35, manufacturer = "Google", brand = "google"))
        assertFalse(supportsLiquidBackdrop(35, manufacturer = "HONOR", brand = "HONOR"))
        assertFalse(supportsLiquidBackdrop(35, manufacturer = "HUAWEI", brand = "HUAWEI"))
        assertFalse(supportsLiquidBackdrop(35, manufacturer = "realme", brand = "realme"))
    }

    @Test
    fun 材质参数符合FlClash层级差异() {
        val regular = glassStyle(GlassMaterial.Regular)
        val pill = glassStyle(GlassMaterial.Pill)
        val menu = glassStyle(GlassMaterial.Menu)
        val alert = glassStyle(GlassMaterial.Alert)
        val lens = glassStyle(GlassMaterial.Lens)

        assertTrue(pill.blurRadius < regular.blurRadius)
        assertTrue(menu.blurRadius < alert.blurRadius)
        assertTrue(lens.refractionHeight < 0.dp)
        assertTrue(lens.rimStrength > regular.rimStrength)
        assertTrue(alert.hairlineAlpha > regular.hairlineAlpha * 0.5f)
    }

    @Test
    fun 详情材质保持头部分组字段与附件的层级差异() {
        val header = glassStyle(GlassMaterial.Elevated)
        val block = glassStyle(GlassMaterial.Surface, elevated = true)
        val inset = glassStyle(GlassMaterial.Regular)
        val attachment = glassStyle(GlassMaterial.Regular)

        assertTrue(header.blurRadius >= inset.blurRadius)
        assertTrue(block.shadowAlpha >= inset.shadowAlpha)
        assertTrue(inset.tintAlpha >= attachment.tintAlpha)
        assertTrue(glassStyle(GlassMaterial.Tinted).saturation < header.saturation)
    }

    @Test
    fun 三四级页面材质具备工具栏菜单和弹窗层级() {
        val toolbar = glassStyle(GlassMaterial.Toolbar)
        val menu = glassStyle(GlassMaterial.Menu)
        val alert = glassStyle(GlassMaterial.Alert)

        assertTrue(toolbar.blurRadius < alert.blurRadius)
        assertTrue(menu.blurRadius < alert.blurRadius)
        assertTrue(alert.shadowAlpha > toolbar.shadowAlpha)
        assertTrue(GlassInteractiveState.entries.size >= 5)
        assertTrue(GlassInteractiveState.entries.contains(GlassInteractiveState.Pressed))
    }
}
