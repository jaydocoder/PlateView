## 项目上下文摘要（车辆权限过滤修复）

生成时间：2026-09-28

### 1. 相似实现分析

- `server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleQueryService.kt`
  - 统一处理车辆搜索、目录分页、增量目录和详情权限。
- `server/src/main/kotlin/com/jaydocoder/plateview/server/client/CatalogStateService.kt`
  - 为账号返回目录修订号和策略版本，客户端据此失效本地缓存。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/data/cache/RoomVehicleCacheRepository.kt`
  - 按服务端目录版本同步车辆缓存，版本不变时直接使用本地候选。

### 2. 项目约定

- 服务端使用 Kotlin、JDBC 参数化 SQL 和 Kotlin 测试。
- 车辆访问权限统一由 `VehicleAccessScope` 和 `canViewVehicleCandidate` 判定。
- 管理员接口与普通车辆查询接口分离。

### 3. 可复用组件清单

- `VehicleAccessScope`：账号车辆权限范围。
- `canViewVehicleCandidate`：候选可见性规则，保留黑名单和严查候选。
- `CatalogConsistencyCoordinator`：客户端目录版本变化后的缓存清理与同步。

### 4. 测试策略

- 使用 `server` 模块现有 Gradle/Kotlin 测试。
- 覆盖普通其他长期车辆隐藏、风险车辆候选保留、账号权限版本变化。

### 5. 关键风险点

- 旧客户端缓存可能在权限变化后继续被本地搜索命中，因此账号版本必须参与策略版本。
- 管理工作台 `/admin/vehicles` 是管理员专用全量数据，不应与普通 `/vehicles/*` 接口混用。
