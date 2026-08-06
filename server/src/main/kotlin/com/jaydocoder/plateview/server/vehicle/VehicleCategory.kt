package com.jaydocoder.plateview.server.vehicle

internal enum class VehicleCategory(
    val displayName: String,
) {
    RESIDENT("村民车辆"),
    SCENIC_UNIT("驻景区单位车辆"),
    SCENIC_ENTERPRISE("驻景区企业车辆"),
    CADRE("干部车辆"),
    KANAS_TOURISM_DEVELOPMENT("喀纳斯旅游发展股份有限公司车辆"),
}
