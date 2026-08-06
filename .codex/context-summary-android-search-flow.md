# 项目上下文摘要（Android 普通用户查询流程）

生成时间：2026-08-06 13:46:59 CST

## 1. 相似实现分析

- **实现 1：** `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/auth/AuthRuntime.kt:22`
  - 模式：Hilt 单例仓库通过 DataStore 以 `Flow` 暴露会话，Retrofit 接口作为构造函数依赖。
  - 可复用：`AuthSession`、`AuthRepository.session`、`AuthRepository.logout()`。
  - 注意：车辆接口必须复用同一个 Retrofit 实例；详情与搜索请求只在内存中读取访问令牌。

- **实现 2：** `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/auth/LoginViewModel.kt:13`
  - 模式：`@HiltViewModel` 私有 `MutableStateFlow`、公开只读 `StateFlow`，事件由界面调用方法上送。
  - 可复用：`viewModelScope`、`update`、`collectAsStateWithLifecycle()` 配套方式。
  - 注意：网络失败必须转为明确界面状态，不能在 Compose 内直接请求。

- **实现 3：** `server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleQueryFeature.kt:113`
  - 模式：候选 DTO 与详情 DTO 分离，搜索只返回车辆标识、车牌、类别代码和类别名称。
  - 可复用：`GET /vehicles/search`、`GET /vehicles/{vehicleId}` 的稳定字段契约。
  - 注意：候选、历史和日志均不得保存身份证号、联系方式或完整详情。

- **实现 4：** `android/app/src/androidTest/kotlin/com/jaydocoder/plateview/PlateViewAppTest.kt:10`
  - 模式：现有仪器化测试使用 Compose 测试规则。
  - 可复用：`createAndroidComposeRule` 与语义断言。
  - 注意：现有测试绑定真实应用根节点且断言已过期，需要改为可控状态的界面与 Room 测试。

## 2. 项目约定

- **命名约定：** Kotlin 标识符使用英文驼峰和 PascalCase；包按 `feature`、`domain`、`data` 分层；用户可见文本、注释、测试名称和文档使用简体中文。
- **文件组织：** 现有认证位于 `feature/auth`；第七阶段按车辆领域、历史数据源、搜索特性、详情特性和导航特性拆分。
- **状态与导航：** Compose 使用 Material 3、`collectAsStateWithLifecycle()`；Navigation Compose 2.8 使用 Kotlin Serialization 类型安全路由，只传递车辆 ID。
- **数据边界：** Room 只保存账号、车辆 ID、车牌、类别和时间；车辆详情每次从服务端加载。

## 3. 可复用组件清单

- `feature/auth/AuthRepository.kt`：登录会话、退出和访问令牌来源。
- `PlateViewTheme.kt`：冷杉深绿、核验青、通行琥珀和异常红的 Material 3 主题。
- `docs/08-API契约.yaml`：候选与详情字段、错误状态和四字符查询阈值。
- `docs/ADR/ADR-004-本机搜索历史与车辆数据缓存.md`：历史字段与禁止缓存详情的边界。

## 4. 测试策略

- **测试框架：** JUnit 4、`kotlinx-coroutines-test`、Compose 仪器化测试、Room 内存数据库测试。
- **单元测试：** 车牌归一化、搜索 ViewModel 的四字符门槛、成功、空结果、失败、历史保存和语音回退。
- **仪器化测试：** 搜索状态、候选点击、详情展示、错误重试入口、账号隔离 Room 历史。
- **真机验证：** 使用已连接的 Android 设备构建、安装，并在 `adb reverse tcp:8080 tcp:8080` 后执行端到端手工冒烟验证。

## 5. 依赖和集成点

- **外部依赖：** Retrofit/Gson、Room、Hilt、DataStore、Navigation Compose、Android `SpeechRecognizer`、Kotlin Serialization。
- **内部依赖：** 搜索与详情仓库从 `AuthSessionProvider` 取得令牌；搜索特性写入搜索历史；导航仅传递 `vehicleId`。
- **配置来源：** `BuildConfig.API_BASE_URL` 由 `plateviewApiBaseUrl` Gradle 属性提供；真机使用 `http://127.0.0.1:8080/`。

## 6. 技术选型理由

- **Room：** Android 官方持久化层，适合按账号隔离的少量历史数据，并可进行设备 SQLite 验证。
- **类型安全导航：** 避免字符串路由错误，只传递车辆 ID，符合详情实时加载要求。
- **系统语音识别：** 不引入云端语音服务，识别结果回填可编辑输入框，失败时保留手动查询。

## 7. 关键风险点与充分性检查

- **并发：** 输入流使用 250 毫秒防抖和 `collectLatest` 取消过期查询。
- **边界：** 归一化少于 4 个有效字符时不触发网络；候选数量由服务端限制为 20。
- **隐私：** 身份证号和联系方式只在详情内存状态展示，不进入候选、Room、日志或测试夹具。
- **验证：** 已识别 JUnit、Compose 和真机验证路径；将用假的仓库与语音适配器测试 ViewModel。

充分性结论：接口契约、现有实现模式、可复用组件、命名与格式、测试方式、重复实现检查和集成点均已确认，可进入编码。

## 8. 收尾审查补充

- 已复核 `SearchScreen.kt`、`SearchViewModel.kt`、`VehicleDetailScreen.kt`、`AuthenticatedNavigation.kt`、`LoginScreen.kt`、`AuthRuntime.kt` 和服务端车辆查询契约，确认复用既有 Hilt、DataStore、Material 3、生命周期感知状态订阅和类型安全导航模式。
- Compose 审查确认：状态由 ViewModel 下传，副作用仅用于收集导航事件，列表提供稳定键，`Scaffold` 内边距已应用，语义文本或图标说明覆盖主要交互。
- Lint 收尾发现启动图标位于 `mipmap-anydpi-v26`，但最低 SDK 为 31。迁移到无版本目录后 AAPT 无法解析自适应图标资源；保留 Android 所需目录，并在模块 `app/lint.xml` 忽略仅由资源目录触发的 `ObsoleteSdkInt` 检查。本模块没有任何 `SDK_INT` 分支，后续新增条件 API 代码时须人工复核最低版本约束。
- 已确认真机序列号为 `83bdbca2`，Android 12（API 31）。设备处于锁屏休眠状态，`connectedDebugAndroidTest` 的安装确认无法完成；不会尝试绕过锁屏。
- 仪器化层新增内存 Room 测试，直接覆盖账号隔离、最近优先排序、单条删除和清空边界；与 Compose 界面测试共同构成普通用户搜索流程的设备验证集。
