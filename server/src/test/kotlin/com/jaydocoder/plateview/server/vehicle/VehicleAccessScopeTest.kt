package com.jaydocoder.plateview.server.vehicle

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
