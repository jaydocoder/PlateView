## 项目上下文摘要（Android 加密车辆缓存）

生成时间：2026-08-08

### 相似实现分析

- `android/app/src/main/kotlin/com/jaydocoder/plateview/data/history/SearchHistoryDatabase.kt`
  - 使用 Room 实体、DAO 和独立 Hilt 模块提供本机数据源。
  - 本次沿用该分层方式，新增独立车辆缓存数据库，不改动搜索历史数据库。

- `android/app/src/main/kotlin/com/jaydocoder/plateview/data/vehicle/NetworkVehicleRepository.kt`
  - 将 Retrofit DTO 映射为领域模型，并通过 `VehicleRepository` 向 ViewModel 提供数据。
  - 本次扩展目录版本与分页 API，并以该仓库作为目录同步的远程数据源。

- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchViewModel.kt`
  - 使用 StateFlow、250 毫秒防抖和 `collectLatest` 管理候选结果。
  - 本次在防抖后先执行本地 Room 模糊查询，再按需发起目录同步和远程兜底。

- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/vehicle/VehicleDetailViewModel.kt`
  - 通过不可变界面状态表达加载、数据和错误。
  - 本次先发布未过期的本地详情，再后台核验目录版本并按需刷新。

- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/auth/AuthRuntime.kt`
  - 由认证仓库集中管理会话与退出。
  - 本次在退出前按用户名删除该账号的候选与详情缓存。

### 项目约定

- Kotlin、Hilt、Room、Retrofit、Compose、ViewModel 与 StateFlow 为既有技术栈。
- Room 本机数据源放在 `data` 层；ViewModel 仅调用领域仓库接口。
- Compose 副作用由 `LaunchedEffect` 或 `DisposableEffect` 承载，网络与数据库操作不在可组合函数主体执行。

### 可复用组件

- `PlateQueryNormalizer`：统一候选查询关键字。
- `VehicleRepository`：车辆远程查询与详情读取入口。
- `AuthSessionProvider`：当前账号与令牌来源。
- `MainDispatcherRule`、`FakeVehicleRepository`：现有 ViewModel 单元测试风格。

### 测试策略

- JVM：缓存优先搜索、远程兜底、详情缓存回退、版本变化清理与退出清理。
- 仪器化：加密 Room 的账号隔离、模糊查询、目录替换和详情上限。
- 构建：静态检查、调试单元测试、服务端测试和正式签名 APK 校验。

### 依赖与集成点

- 新依赖：SQLCipher Room 支持与 WorkManager。
- 服务端接口：`/vehicles/catalog/version`、`/vehicles/catalog`，以及已有搜索与详情接口。
- 失效依据：服务端 `catalogVersion`；版本变化时事务性替换候选并清除详情。

### 风险与处理

- SQLCipher 密码必须不以明文写入偏好设置；随机数据库密码用 Android Keystore AES-GCM 密钥加密后保存。
- 目录同步失败不得清除已有快照；仅在完整分页获取成功后替换本地目录。
- WorkManager 周期任务最短为 15 分钟，系统可能延后执行；以前台与搜索触发的版本检查补足。

### 充分性检查

- 是：接口输入输出、目录版本和分页约束已明确。
- 是：已确定 Room、Hilt、认证退出、ViewModel 与 Compose 生命周期的复用路径。
- 是：已有 JVM 与仪器化测试框架可覆盖核心行为。
