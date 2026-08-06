package com.jaydocoder.plateview.server.admin

import com.jaydocoder.plateview.server.vehicle.VehicleCategory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonObject

class AdminManagementValidationTest {
    @Test
    fun `村民车辆缺少核验资料时拒绝保存`() {
        val command = AdminVehicleCommand(
            plateNumber = "新A12345",
            category = VehicleCategory.RESIDENT,
            vehicleType = null,
            status = AdminVehicleStatus.ACTIVE,
            attributes = JsonObject(emptyMap()),
            residentProfile = null,
            longTermProfile = null,
        )

        assertFailsWith<AdminValidationException> { command.validate() }
    }

    @Test
    fun `长期车辆不能同时保存村民资料`() {
        val command = AdminVehicleCommand(
            plateNumber = "新B12345",
            category = VehicleCategory.SCENIC_UNIT,
            vehicleType = null,
            status = AdminVehicleStatus.ACTIVE,
            attributes = JsonObject(emptyMap()),
            residentProfile = AdminResidentProfile("测试姓名", "测试证件号", null, null),
            longTermProfile = null,
        )

        assertFailsWith<AdminValidationException> { command.validate() }
    }

    @Test
    fun `账号密码少于六位时拒绝创建`() {
        val command = AdminUserCreateCommand("operator", "12345", AdminRole.USER)

        assertFailsWith<AdminValidationException> { command.validate() }
    }
}
