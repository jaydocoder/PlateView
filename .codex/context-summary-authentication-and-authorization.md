## 项目上下文摘要（登录、角色与权限）

生成时间：2026-08-06

### 1. 相似实现分析

- **实现 1：** `server/src/main/kotlin/com/jaydocoder/plateview/server/Application.kt`
  - 模式：所有 Ktor 插件和路由均从 `Application.module()` 统一安装。
  - 可复用：内容协商、请求标识、状态页错误响应和健康检查路由。
  - 注意：认证插件必须在路由注册前安装，受保护路由使用统一鉴权入口。

- **实现 2：** `server/src/main/kotlin/com/jaydocoder/plateview/server/infrastructure/database/DatabaseMigrator.kt`
  - 模式：Flyway 在数据库配置存在时执行迁移，Hikari 数据源与应用生命周期绑定。
  - 可复用：`DatabaseSettings`、应用属性和连接池。
  - 注意：会话持久化必须新增版本化迁移，不能修改已发布的 V1 迁移。

- **实现 3：** `server/src/main/kotlin/com/jaydocoder/plateview/server/infrastructure/database/AuditLogWriter.kt`
  - 模式：审计写入器通过应用属性注册，使用参数化 SQL 和结构化 JSON 元数据。
  - 可复用：`AuditEvent`、`AuditLogWriterKey` 和 JDBC 写入器。
  - 注意：审计元数据不得包含密码、令牌、身份证号或联系方式。

- **实现 4：** `android/app/src/main/kotlin/com/jaydocoder/plateview/PlateViewApp.kt`
  - 模式：根 Composable 负责主题和顶层界面；当前尚无导航与状态管理。
  - 可复用：Material 3 主题和 Hilt Application。
  - 注意：登录界面必须由不可变状态渲染，不能在 Composable 中直接调用网络。

- **实现 5：** `server/src/test/kotlin/com/jaydocoder/plateview/server/ApplicationTest.kt`
  - 模式：使用 `testApplication` 验证真实 Ktor 路由的状态码、响应体和请求标识。
  - 可复用：现有测试宿主和 Kotlin 断言。
  - 注意：认证测试使用虚构账号和密码，不能读取真实部署变量。

### 2. 项目约定

- **服务端：** 账号密码、刷新令牌和角色均由 PostgreSQL 管理；数据库结构只通过 Flyway 新增迁移演进。
- **Android：** Kotlin、Compose、Material 3、MVVM、StateFlow、Hilt 和类型安全导航；按 `feature/auth`、`data`、`domain` 组织。
- **错误处理：** 统一响应包含错误码、中文提示和请求标识；不得将密码或令牌写入日志或错误响应。
- **审计：** 登录成功、登录失败、刷新、退出和权限拒绝必须记录最小必要元数据。

### 3. 可复用组件清单

- `Application.module()`：插件和路由总入口。
- `DatabaseSettings`、`AuditLogWriterKey`、`AuditEvent`：服务端数据库和审计基础设施。
- `configureRequestContext()`、`configureErrorHandling()`：请求标识与异常响应。
- `PlateViewTheme`、`PlateViewApplication`：Android 根主题和 Hilt 启动入口。

### 4. 测试策略

- 服务端：登录成功/失败、刷新、退出、未认证、普通用户访问管理员接口、管理员访问管理员接口、禁用账号和审计写入。
- Android：认证 ViewModel 覆盖加载、成功、失败和退出；Compose 测试覆盖输入、提交和错误状态；导航测试验证登录态切换。
- 集成：Docker Compose 使用虚构的初始管理员密码启动，验证登录、刷新和退出；测试结束后清理卷。

### 5. 依赖和集成点

- **服务端依赖：** Ktor Authentication/JWT、BCrypt、Flyway、Hikari、PostgreSQL。
- **Android 依赖：** Navigation Compose、Lifecycle ViewModel、DataStore、Retrofit/OkHttp、Hilt。
- **环境变量：** 初始管理员密码、JWT 签名密钥、访问令牌和刷新令牌有效期必须由未提交部署变量提供。
- **API：** 现有契约仅有 `/auth/login` 摘要；本阶段补齐刷新和退出路径及请求响应模型。

### 6. 技术选型理由

- 采用短期访问令牌加可撤销刷新会话，支持登录态保存、刷新和显式退出。
- 密码采用 BCrypt 哈希，刷新令牌仅保存哈希，数据库泄露时不能直接复用令牌。
- 角色以 JWT 声明和数据库当前状态双重校验，确保停用或角色变更即时生效。
- Android 使用仓库接口和 Hilt 绑定，使网络实现可被测试替身替换。

### 7. 关键风险点

- `users` 表没有会话表，必须新增 V2 迁移，且不得修改 V1。
- 初始管理员密码和 JWT 密钥未提供，必须通过 `.env` 配置；开发默认值只能用于明确的本地开发模式，不能进入生产镜像。
- API 契约缺少刷新、退出和错误细节，必须先补齐文档再实现客户端 DTO。
- 当前 Android 无模拟器会话，Compose 仪器化测试需在后续可用设备上执行；本地单元与构建验证不可省略。

### 8. 上下文充分性检查

- 是：已分析 Ktor 模块入口、数据库运行时、审计写入器、Android 根界面和 Ktor 测试五类实现。
- 是：已明确会话迁移、认证插件、API 契约、Android ViewModel 和导航的集成点。
- 是：已确认项目内不存在既有认证、令牌、导航或网络客户端实现，采用成熟框架而非重复造轮子。
- 是：已定义服务端、Android 单元、Compose 与 Docker Compose 集成验证路径。
