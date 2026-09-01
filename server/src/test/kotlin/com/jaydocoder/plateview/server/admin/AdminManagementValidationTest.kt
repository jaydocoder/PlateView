package com.jaydocoder.plateview.server.admin

import com.jaydocoder.plateview.server.vehicle.VehicleCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonObject

class AdminManagementValidationTest {
    @Test
    fun `管理员修改任意用户资料字段都会触发会话失效`() {
        val existing = AdminUserRecord(
            id = 2,
            username = "operator",
            role = AdminRole.USER,
            status = AdminUserStatus.ACTIVE,
            version = 1,
            createdAt = null,
            updatedAt = null,
            avatarVersion = 0,
            hasAvatar = false,
            realName = "操作员",
            scheduleAccessEnabled = true,
        )
        val unchanged = AdminUserUpdateCommand(AdminRole.USER, AdminUserStatus.ACTIVE)
        val roleChanged = AdminUserUpdateCommand(AdminRole.ADMIN, AdminUserStatus.ACTIVE)
        val statusChanged = AdminUserUpdateCommand(AdminRole.USER, AdminUserStatus.DISABLED)
        val avatarIndependentProfileChanged = AdminUserUpdateCommand(
            AdminRole.USER,
            AdminUserStatus.ACTIVE,
            username = "新用户名",
            realName = "新姓名",
            scheduleAccessEnabled = false,
        )

        assertEquals(false, hasUserInfoChanged(existing, unchanged))
        assertEquals(true, hasUserInfoChanged(existing, roleChanged))
        assertEquals(true, hasUserInfoChanged(existing, statusChanged))
        assertEquals(true, hasUserInfoChanged(existing, avatarIndependentProfileChanged))
    }

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
    fun `仅admin账号可修改其他账号的排班入口许可`() {
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
    }

    @Test
    fun `普通管理员可以修改普通账号角色和状态`() {
        AdminUserProfilePolicy.requireModificationAllowed(
            canManageOtherUserProfiles = false,
            profileChangeRequested = false,
        )
        AdminUserProfilePolicy.requireTargetModificationAllowed(
            targetUsername = "operator",
            canManageOtherUserProfiles = false,
            modificationRequested = true,
        )
    }

    @Test
    fun `普通管理员不能修改admin账号角色和状态`() {
        assertFailsWith<AdminPermissionException> {
            AdminUserProfilePolicy.requireTargetModificationAllowed(
                targetUsername = "admin",
                canManageOtherUserProfiles = false,
                modificationRequested = true,
            )
        }

        AdminUserProfilePolicy.requireTargetModificationAllowed(
            targetUsername = "admin",
            canManageOtherUserProfiles = true,
            modificationRequested = true,
        )
    }

    @Test
    fun `账号密码少于六位时拒绝创建`() {
        val command = AdminUserCreateCommand("operator", "12345", AdminRole.USER)

        assertFailsWith<AdminValidationException> { command.validate() }
    }

    @Test
    fun `新建账号资料字段默认为空且排班入口关闭`() {
        val command = AdminUserCreateCommand("operator", "123456", AdminRole.USER)

        assertEquals(null, command.realName)
        assertEquals(false, command.scheduleAccessEnabled)
    }
}
