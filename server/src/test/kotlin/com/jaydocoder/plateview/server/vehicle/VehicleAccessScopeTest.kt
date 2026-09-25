package com.jaydocoder.plateview.server.vehicle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleAccessScopeTest {
    @Test
    fun `四种数据访问组合使用不同目录版本标识`() {
        assertEquals(3L, VehicleAccessScope.fullAccess().versionBits)
        assertEquals(2L, VehicleAccessScope(otherLongTermAccessEnabled = true, residentRemarksAccessEnabled = false).versionBits)
        assertEquals(1L, VehicleAccessScope(otherLongTermAccessEnabled = false, residentRemarksAccessEnabled = true).versionBits)
        assertEquals(0L, VehicleAccessScope(otherLongTermAccessEnabled = false, residentRemarksAccessEnabled = false).versionBits)
    }

    @Test
    fun `禁止村民备注时车辆响应不保留备注`() {
        val vehicle = VehicleDetail(
            id = 1,
            plateNumber = "新A12345",
            normalizedPlate = "新A12345",
            category = VehicleCategory.RESIDENT,
            vehicleType = null,
            status = "ACTIVE",
            attributes = kotlinx.serialization.json.JsonObject(emptyMap()),
            residentProfile = ResidentVehicleProfile("测试姓名", "测试证件", null, "仅管理员可见备注"),
            longTermProfile = null,
        )

        val filtered = vehicle.filteredFor(
            VehicleAccessScope(otherLongTermAccessEnabled = true, residentRemarksAccessEnabled = false),
        )

        assertEquals(null, filtered.residentProfile?.remarks)
        assertEquals("测试姓名", filtered.residentProfile?.ownerName)
    }

    @Test
    fun `无其他长期车辆权限时仅风险状态可作为候选显示`() {
        val scope = VehicleAccessScope(otherLongTermAccessEnabled = false, residentRemarksAccessEnabled = false)

        assertFalse(canViewVehicleCandidate(VehicleCategory.OTHER_LONG_TERM, "ACTIVE", scope))
        assertTrue(canViewVehicleCandidate(VehicleCategory.OTHER_LONG_TERM, "BLACKLISTED", scope))
        assertTrue(canViewVehicleCandidate(VehicleCategory.OTHER_LONG_TERM, "STRICT_CHECK", scope))
        assertFalse(canViewVehicleDetail(VehicleCategory.OTHER_LONG_TERM, scope))
        assertTrue(canViewVehicleDetail(VehicleCategory.RESIDENT, scope))
    }

    @Test
    fun `无权限风险车辆进入目录时移除全部详情字段`() {
        val vehicle = VehicleDetail(
            id = 2,
            plateNumber = "新H12345",
            normalizedPlate = "新H12345",
            category = VehicleCategory.OTHER_LONG_TERM,
            vehicleType = "大型汽车",
            status = "BLACKLISTED",
            attributes = kotlinx.serialization.json.buildJsonObject {
                put("plateColor", kotlinx.serialization.json.JsonPrimitive("黄色"))
            },
            residentProfile = null,
            longTermProfile = LongTermVehicleProfile("测试单位", "测试人员", "喀纳斯", "内部备注"),
        )

        val visible = vehicle.visibleInCatalogFor(
            VehicleAccessScope(otherLongTermAccessEnabled = false, residentRemarksAccessEnabled = false),
        )

        assertFalse(visible.detailAccessible)
        assertNull(visible.vehicleType)
        assertTrue(visible.attributes.isEmpty())
        assertNull(visible.longTermProfile)
    }
}
