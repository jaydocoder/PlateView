# 项目上下文摘要（第八阶段管理员维护）

生成时间：2026-08-06

## 1. 相似实现分析

- **实现 1：** `server/src/main/kotlin/com/jaydocoder/plateview/server/imports/ImportPreviewFeature.kt`
  - 模式：`authenticate("access-token")` 下按 `/admin` 路由组织，先调用 `requireAdministrator()`，再通过服务层返回 DTO，并用 `AuditLogWriter` 写入操作审计。
  - 可复用：管理员鉴权、批次预览、行处置、发布、回滚和统一错误响应。
  - 约束：导入的原始单元格和个人信息不能进入普通日志、Android 历史或测试夹具。

- **实现 2：** `server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleQueryService.kt`
  - 模式：服务层负责参数校验、参数化 SQL、结果映射；路由只处理鉴权、请求和响应。
  - 可复用：`normalizePlate`、`VehicleCategory`、村民与长期车辆资料的联合查询结构。
  - 约束：车牌使用规范化键，车辆更新需通过 `version` 防止编辑覆盖。

- **实现 3：** `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchViewModel.kt`
  - 模式：Hilt ViewModel 私有 `MutableStateFlow`、公开 `StateFlow`，仓库与会话提供者经构造函数注入，界面只发送事件。
  - 可复用：HTTP 401 时退出会话、服务不可用状态、一次性导航事件、测试假仓库。
  - 约束：Composable 使用 `collectAsStateWithLifecycle()`，不得直接调用网络或数据库。

- **实现 4：** `android/app/src/main/kotlin/com/jaydocoder/plateview/core/navigation/AuthenticatedNavigation.kt`
  - 模式：序列化类型安全目的地，仅传递标识而非完整业务数据。
  - 可复用：基于会话角色的受保护导航图和 `NavHost` 结构。
  - 约束：普通用户不得出现管理员入口；服务端仍是权限最终判定点。

## 2. 项目约定

- **命名约定：** Kotlin 类型使用 PascalCase，函数和变量使用英文 camelCase，用户可见文本、注释、测试名和文档使用简体中文。
- **文件组织：** Android 按 `feature`、`domain`、`data`、`core` 分层；服务端按业务特性分包，Flyway 迁移位于 `server/src/main/resources/db/migration`。
- **状态与导航：** ViewModel 以不可变 `UiState` 输出状态，Navigation Compose 使用类型安全对象；界面状态、领域模型和网络 DTO 分离。
- **错误处理：** 服务端通过 `ApiErrorResponse` 和 `StatusPages` 统一返回参数、找不到、冲突、权限和服务异常；客户端映射为明确可操作状态。

## 3. 可复用组件清单

- `server/.../auth/AuthFeature.kt`：JWT 登录态与 `requireAdministrator()`。
- `server/.../infrastructure/database/AuditLogWriter.kt`：操作审计。
- `server/.../imports/ImportWorkflowService.kt`：导入预览、行处置、发布与回滚。
- `server/.../vehicle/PlateNormalizer.kt` 与 `VehicleCategory.kt`：车牌和类别标准。
- `android/.../data/network/NetworkModule.kt`：Retrofit 客户端。
- `android/.../feature/auth/AuthRuntime.kt`：`AuthSessionProvider`。
- `android/.../PlateViewTheme.kt`、`PlateViewTokens.kt`：Material 3 主题与尺寸。

## 4. 测试策略

- **服务端：** Kotlin Test + Ktor 测试宿主；新服务的请求校验和映射使用纯 Kotlin 单元测试，完整 PostgreSQL 管理流程以 Docker Compose 隔离库验证。
- **Android：** JUnit4 + `kotlinx-coroutines-test` 验证管理员 ViewModel 的加载、角色拒绝、表单校验和冲突状态；Compose 仪器化测试验证工作台、表单和失败提示的语义交互。
- **验证：** Android 运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug` 和 `assembleDebugAndroidTest`；服务端运行 `test`、Docker Compose 构建与管理员 API 冒烟。

## 5. 依赖和集成点

- **服务端输入：** JWT 管理员令牌、JSON 车辆和账号请求、Excel Multipart 文件、批次标识和乐观锁版本号。
- **服务端输出：** 车辆、账号、导入批次和审计摘要；冲突返回 HTTP 409，校验失败返回 HTTP 400，普通用户访问返回 HTTP 403。
- **Android 集成：** Retrofit 向 `/admin/*` 发送 Bearer 令牌；系统文件选择器提供 Excel 内容；管理导航只在会话角色为 `ADMIN` 时注册。
- **数据约束：** 账号名唯一，角色仅 `ADMIN` 或 `USER`，账号仅 `ACTIVE` 或 `DISABLED`；车辆类别固定五类，所有修改带 `version`，删除为逻辑停用以保留导入和审计可追溯性。

## 6. 风险与充分性检查

- **并发：** 车辆与账号写入按版本条件更新，不匹配时返回冲突并要求刷新。
- **边界：** 按类别要求村民姓名和身份证号，长期车辆禁止同时保存村民资料；动态属性只允许预定义字段。
- **性能：** 管理列表提供固定最大页大小，导入详情继续使用既有分页参数。
- **验证限制：** 当前 Android 12 真机锁屏，设备测试 APK 只能编译，解锁后于第九阶段执行。

充分性结论：接口输入输出、既有模式、可复用组件、测试路径、数据库约束和主要风险均已确认，可以开始实施。
