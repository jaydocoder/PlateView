package com.jaydocoder.plateview.server.admin

import com.jaydocoder.plateview.server.vehicle.VehicleCategory
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `其他长期通行车辆必须填写单位名称`() {
        val command = AdminVehicleCommand(
            plateNumber = "新C12345",
            category = VehicleCategory.OTHER_LONG_TERM,
            vehicleType = null,
            status = AdminVehicleStatus.ACTIVE,
            attributes = JsonObject(emptyMap()),
            residentProfile = null,
            longTermProfile = null,
        )

        assertFailsWith<AdminValidationException> { command.validate() }
    }

    @Test
    fun `长期车辆通行人员不限制长度`() {
        val command = AdminVehicleCommand(
            plateNumber = "新C12346",
            category = VehicleCategory.SCENIC_UNIT,
            vehicleType = null,
            status = AdminVehicleStatus.ACTIVE,
            attributes = JsonObject(emptyMap()),
            residentProfile = null,
            longTermProfile = AdminLongTermProfile(
                organizationName = "测试单位",
                passHolder = "甲".repeat(256),
                passageDetails = null,
                remarks = null,
            ),
        )

        command.validate()
    }

    @Test
    fun `喀旅公司车辆使用简化显示名称`() {
        assertEquals("喀旅公司车辆", VehicleCategory.KANAS_TOURISM_DEVELOPMENT.displayName)
    }

    @Test
    fun `其他管理员只能新增其他长期通行车辆`() {
        val capabilities = AdminVehicleCreationPolicy.capabilities(isPrimaryAdministrator = false)

        assertEquals(listOf(VehicleCategory.OTHER_LONG_TERM), capabilities.creatableCategories)
        assertFailsWith<AdminValidationException> {
            AdminVehicleCreationPolicy.requireCreationAllowed(false, VehicleCategory.RESIDENT)
        }
    }

    @Test
    fun `其他管理员不能修改已有车辆类别`() {
        assertFailsWith<AdminValidationException> {
            AdminVehicleCreationPolicy.requireUpdateAllowed(
                isPrimaryAdministrator = false,
                originalCategory = VehicleCategory.OTHER_LONG_TERM,
                requestedCategory = VehicleCategory.SCENIC_UNIT,
            )
        }
    }

    @Test
    fun `管理员账号可创建并修改全部车辆类别`() {
        VehicleCategory.entries.forEach { category ->
            AdminVehicleCreationPolicy.requireCreationAllowed(true, category)
            AdminVehicleCreationPolicy.requireUpdateAllowed(true, VehicleCategory.RESIDENT, category)
        }
    }

    @Test
    fun `仅admin账号可修改其他账号的用户名或密码`() {
        assertFailsWith<AdminPermissionException> {
            AdminUserProfilePolicy.requireModificationAllowed(
                canManageOtherUserProfiles = false,
                profileChangeRequested = true,
            )
        }

        AdminUserProfilePolicy.requireModificationAllowed(
            canManageOtherUserProfiles = true,
            profileChangeRequested = true,
        )
        AdminUserProfilePolicy.requireModificationAllowed(
            canManageOtherUserProfiles = false,
            profileChangeRequested = false,
        )
    }

    @Test
    fun `账号密码少于六位时拒绝创建`() {
        val command = AdminUserCreateCommand("operator", "12345", AdminRole.USER)

        assertFailsWith<AdminValidationException> { command.validate() }
    }
}
