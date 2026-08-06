# 项目上下文摘要（第九阶段测试、性能、构建与交付）

生成时间：2026-08-06 21:10 CST

## 1. 相似实现分析

- **实现 1**：`server/src/test/kotlin/com/jaydocoder/plateview/server/ApplicationTest.kt`
  - 模式：使用 Ktor `testApplication` 验证 HTTP 状态、响应内容与请求标识。
  - 可复用：`module()` 和项目既有 Ktor 测试运行方式。
  - 约束：服务端涉及 PostgreSQL 的完整场景必须使用隔离 Docker Compose 数据库，不能用内存替代。

- **实现 2**：`server/src/test/kotlin/com/jaydocoder/plateview/server/imports/ExcelImportParserTest.kt`
  - 模式：Kotlin Test 生成内存工作簿，覆盖正常、边界和无效输入。
  - 可复用：`ExcelImportParser`、导入状态机、导入异常模型。
  - 约束：真实 Excel 只能在隔离环境上传验收，原始个人信息不得写入日志、报告或 Git。

- **实现 3**：`android/app/src/androidTest/kotlin/com/jaydocoder/plateview/VehicleQueryScreenTest.kt`
  - 模式：Compose `createAndroidComposeRule` 通过语义标签和可见文本验证界面行为。
  - 可复用：`SearchScreen`、`VehicleDetailScreen`、`PlateViewTheme`、`search_input` 与候选项标签。
  - 约束：仪器化测试只能在已解锁且允许测试 APK 安装的 Android 12 设备上完成。

- **实现 4**：`android/app/src/androidTest/kotlin/com/jaydocoder/plateview/SearchHistoryDaoTest.kt`
  - 模式：Room 内存数据库在真实 SQLite 引擎上验证 DAO、账号隔离和排序。
  - 可复用：`SearchHistoryDatabase`、`SearchHistoryDao`、`SearchHistoryEntity`。
  - 约束：必须通过 `connectedDebugAndroidTest` 实际运行，不可仅以编译替代。

- **实现 5**：`server/src/main/kotlin/com/jaydocoder/plateview/server/admin/AdminManagementFeature.kt`
  - 模式：管理员接口统一位于 JWT 保护的 `/admin` 路由，并调用 `requireAdministrator()` 和审计写入器。
  - 可复用：管理员登录、车辆管理、账号管理、导入列表和审计列表接口。
  - 约束：端到端测试必须验证普通用户访问管理员接口返回 `403`，并验证审计记录。

## 2. 项目约定

- **命名约定**：Kotlin 标识符使用英文驼峰或帕斯卡命名；包按 `feature`、`domain`、`data`、`core` 与服务端功能域组织；SQL 使用小写下划线。
- **文件组织**：Android 测试分别位于 `src/test` 与 `src/androidTest`；服务端测试位于 `server/src/test`；交付文档位于 `docs/`；过程证据位于 `.codex/`。
- **测试风格**：测试名称和说明使用中文；断言采用 Kotlin Test、JUnit 与 Compose 语义节点；端到端场景使用独立 Docker Compose 项目和 HTTP 断言。
- **数据边界**：真实 Excel、密钥、环境变量、APK 与构建输出不得提交；日志、报告和测试夹具不得记载身份证号、联系方式、密码或令牌。

## 3. 可复用组件清单

- `compose.yaml`：API、PostgreSQL、健康检查和持久卷的标准本地编排。
- `server/src/main/kotlin/com/jaydocoder/plateview/server/Application.kt`：Flyway 迁移、初始管理员和 Ktor 功能装配入口。
- `server/src/main/kotlin/com/jaydocoder/plateview/server/imports/ImportWorkflowService.kt`：预览、行处置、发布和回滚状态机。
- `server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleQueryFeature.kt`：已认证搜索、详情和查询审计接口。
- `android/app/src/androidTest/kotlin/com/jaydocoder/plateview/*.kt`：Compose 与 Room 仪器化测试模式。

## 4. 测试策略

- **服务端**：执行 `server/gradlew test`，再以临时 Compose 项目创建全新 PostgreSQL 卷，验证迁移、真实工作簿导入、搜索、详情、管理员操作、发布回滚与审计。
- **Android JVM 与静态检查**：执行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`。
- **Android 设备**：已连接 Android 12 真机 `83bdbca2`，但当前锁屏且系统焦点为通知栏；设备解锁并确认测试 APK 安装后执行 `:app:connectedDebugAndroidTest`。
- **性能**：在隔离 API 环境对已认证搜索执行固定次数计时并计算 P95；真机通过 ADB 的启动耗时和进程内存指标采集。若成功采集 Perfetto Trace，另按专项 Skill 创建同目录证据链。
- **签名**：只在发现未提交的发布密钥和本机签名配置后执行正式签名构建；不会生成或伪造发布身份。

## 5. 依赖和集成点

- **外部依赖**：Docker Compose、PostgreSQL 17、Ktor、Flyway、Apache POI、Android Gradle Plugin、Compose、Room、Hilt。
- **输入**：两份未提交的真实 `.xlsx` 工作簿、隔离环境的虚构密码和 JWT 密钥、已连接 Android 12 真机。
- **输出**：测试报告、性能报告、操作手册、发布说明、回滚演练记录、调试 APK 和正式签名 APK（仅在本机密钥存在时）。
- **环境约束**：缺少 `sequential-thinking`、`shrimp-task-manager`、`desktop-commander`、Context7 和 GitHub MCP；用已安装 Android Skills、项目既有实现、Gradle、Docker Compose、ADB 和 Git 本地检查替代。

## 6. 技术选型理由

- 使用现有 Gradle、Compose、Ktor 和 Docker Compose 流程，保证验收与正式运行路径一致。
- 使用隔离 Compose 项目名和独立卷，避免真实开发库、源 Excel 和其他项目数据受影响。
- 使用现有真机测试而非模拟器，直接验证 Android 12 的运行时权限、安装和 SQLite 行为。

## 7. 关键风险点

- 真机仍锁定，仪器化测试与运行时权限验收不能绕过系统锁屏或安装确认。
- 当前未发现发布密钥或正式签名配置；这会阻止“正式签名 APK”验收，除非本机已存在可用密钥。
- 真实数据含个人信息，验收脚本与报告必须只输出计数、类别和匿名化结果。
- 本机 Docker 29 与旧 Testcontainers 不兼容，迁移测试固定采用 Docker Compose 全新卷。

## 9. 阶段执行发现

- 2026-08-06 的隔离 Docker Compose 验收已通过：4 条迁移、9 张业务表、两份真实工作簿、五类车辆、预览发布回滚、查询详情、权限、管理员 CRUD、审计和最终数据恢复均有命令断言。
- 已认证查询连续 30 次的 P95 为 `0.005884` 秒。
- 账号停用值必须是 `DISABLED`，而车辆逻辑停用值是 `INACTIVE`。Android `USER_STATUSES` 和 OpenAPI 契约均符合该规则；首次验收命令误用车辆值被服务端 400 拒绝，按正确契约重试后通过。

## 10. 语音结果测试补充

- **相似实现**：`SearchViewModelTest.kt` 已通过 `FakeVoiceRecognizer` 注入 `SearchViewModel`，并覆盖录音权限拒绝后保留手动输入。
- **复用接口**：`VoiceRecognizer.start(onResult, onFailure)` 是语音平台适配器的唯一回调契约；`SearchViewModel.updateQuery` 负责将识别文本进入归一化、防抖和搜索路径。
- **缺口与策略**：真机已验证麦克风授权和语音入口，但未采集现场口述音频。新增 JVM 测试主动触发假识别器的成功回调，断言识别结果回填并以既有车牌归一化规则发起查询。

## 8. 充分性检查

- [x] 可以定义验收输入输出：测试输入为隔离 Compose 服务、真实工作簿和 Android 12 真机；输出为通过的命令结果和脱敏报告。
- [x] 理解技术选型：已有 Gradle 与 Compose/Docker 实现是唯一可复用的正式运行路径。
- [x] 已识别主要风险：锁屏、安装确认、发布密钥、个人信息和 Docker API 兼容性。
- [x] 知道验证方式：服务端/Android Gradle 命令、隔离 HTTP 测试、ADB 与签名配置检查。
