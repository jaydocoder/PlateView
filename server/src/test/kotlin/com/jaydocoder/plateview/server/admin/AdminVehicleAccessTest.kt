package com.jaydocoder.plateview.server.admin

import com.jaydocoder.plateview.server.vehicle.VehicleAccessScope
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminVehicleAccessTest {
    @Test
    fun `管理端无村民备注权限时详情清空备注`() {
        val vehicle = AdminVehicleRecord(
            id = 1,
            plateNumber = "新A12345",
            normalizedPlate = "新A12345",
            category = com.jaydocoder.plateview.server.vehicle.VehicleCategory.RESIDENT,
            status = AdminVehicleStatus.ACTIVE,
            version = 1,
            vehicleType = "小型汽车",
            attributes = JsonObject(mapOf("plateColor" to kotlinx.serialization.json.JsonPrimitive("蓝色"))),
            residentProfile = AdminResidentProfile("测试人员", "证件", "电话", "内部备注"),
            longTermProfile = null,
        )

        val visible = vehicle.visibleTo(
            VehicleAccessScope(otherLongTermAccessEnabled = true, residentRemarksAccessEnabled = false),
        )

        assertEquals("测试人员", visible.residentProfile?.ownerName)
        assertNull(visible.residentProfile?.remarks)
    }
}
