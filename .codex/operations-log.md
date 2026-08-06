## Android 开发 Skills 配置

时间：2026-08-05

### 工具链记录

- `sequential-thinking` 和 `shrimp-task-manager` 未在当前环境提供，已以人工结构化分析和本地检查替代。
- `desktop-commander` 未在当前环境提供，已以受限的本地终端只读检查替代。
- GitHub API 匿名树查询返回 HTTP 403；已改用 Git 远程引用和稀疏检出，成功获得所需上游目录清单。
- 上游安装器首次通过 `python` 调用失败，因为系统未提供该命令别名；将使用同一脚本的 `python3` 入口重试。

## 编码前检查 - Android 开发 Skills 配置

时间：2026-08-05

- 已查阅上下文摘要文件：`.codex/context-summary-android-development-skills.md`
- 将使用以下可复用组件：
  - Android 官方 Skills：导航、测试、性能、性能分析和 Compose 指引。
  - Firebase 官方 Skills：Firebase 基础入口与产品级 Agent Skills。
  - Compose 增强 Skill：现代 Compose 状态、Material 3 和性能实践。
- 将遵循命名约定：保留上游目录名，项目总 Skill 使用 `android-development`。
- 将遵循代码风格：仅编写简体中文 Markdown 说明，不创建业务代码。
- 确认不重复造轮子：项目内未发现 `.agents/skills/`、`SKILL.md` 或既有 Android 配置。

## 编码后声明 - Android 开发 Skills 配置

时间：2026-08-05

### 1. 复用了以下既有组件

- Android 官方 Skills：Compose、导航、测试、R8 和 Perfetto 的上游内容。
- Google 与 Firebase 官方 Skills：Firebase 基础入口、Authentication 与 Firestore 的上游内容。
- Compose 增强 Skill：现代 Compose 的上游内容和引用资料。

### 2. 遵循的项目约定

- 文件组织：所有项目 Skills 位于 `.agents/skills/`，工作记录位于 `.codex/`。
- 命名约定：上游目录名保持不变，项目总 Skill 采用 `android-development` 与 `firebase-android`。
- 语言：新建说明文件和 Skill 使用简体中文。

### 3. 对比的相似实现

- Android `navigation-3`：沿用入口文件加引用资料目录的组织方式。
- Android `testing-setup`：沿用测试分层和按需配置的实践。
- `modern-jetpack-compose`：沿用按功能加载参考资料的 Compose 工作流。

### 4. 未重复造轮子的证明

- 已检查项目根目录和 `.agents/skills/` 的既有内容，安装前不存在同名能力。
- 项目总 Skill 仅负责聚合与路由，不复制上游实现或 Android SDK 文档。

## GitHub 连接修复尝试

时间：2026-08-06

- 已确认 ShellCrash 生成配置中的 `DOMAIN,ssh.github.com,DIRECT` 是 SSH 连接中断的直接规则来源。
- 已在 `/home/neo/.codex/config.toml` 的 GitHub MCP 配置中加入 HTTP、HTTPS 和本地排除代理变量，并将其传入 Docker 容器。
- 配置文件检查通过；当前任务绑定的 GitHub MCP 进程未热重载，新变量将在重连 MCP 或重启 Codex 后生效。
- ShellCrash 持久化规则位于仅管理员可写的 `/etc/ShellCrash`；当前环境未配置图形化 `sudo` 认证程序，未对其进行修改或重启服务。

## 编码前检查 - 数据库迁移与基础数据模型

时间：2026-08-06

- 已查阅上下文摘要文件：`.codex/context-summary-database-foundation.md`。
- 将使用以下既有组件：`Application.module()` 作为 Ktor 启动入口；`ApplicationTest` 作为接口测试结构；`001_extensions.sql` 安装 `pg_trgm`；`docs/07-数据库设计.md` 约束数据模型。
- 将遵循命名约定：Kotlin 包名维持现有服务端包名；SQL 使用小写下划线；Flyway 使用版本化迁移命名。
- 将遵循代码风格：Kotlin 官方格式，说明、测试名称和注释使用简体中文。
- 确认不重复造轮子：项目目前没有迁移框架、数据库访问层或重复的数据模型；采用成熟的 Flyway 和 PostgreSQL 官方能力。
- 工具可用性：当前环境未提供 `sequential-thinking`、`shrimp-task-manager`、`desktop-commander` 或 Context7；已以项目文件、设计文档、Ktor 既有测试和本地 Docker 验证替代，并记录限制。

## 验证异常 - 数据库迁移容器测试

时间：2026-08-06

- 首次执行 `server/gradlew --no-daemon -p server test` 失败。
- 原因：Testcontainers `1.20.6` 默认使用 Docker API `1.32`，而本机 Docker `29.7.1` 的最低支持版本为 `1.40`，守护进程返回 HTTP 400。
- 补救：在项目 `tasks.test` 中为测试进程设置 `DOCKER_API_VERSION=1.40`，不修改 Docker 守护进程、不要求全局环境变量。
- 结果：该环境变量未被当前 Testcontainers 客户端采用，第二次测试仍以 API `1.32` 连接。
- 最终方案：移除不兼容的 Testcontainers 测试依赖和测试源码；数据库迁移通过 Docker Compose 的全新数据库卷、API 启动迁移和 SQL 结构断言执行本地集成验证。Ktor 自动健康检查测试继续保留。

## 验证异常 - 服务端基础组件编译

时间：2026-08-06

- 首次编译统一错误响应组件失败，原因是 `StatusPages` 处理器作用域不提供直接的 `log` 属性。
- 补救：改用 `call.application.log` 获取 Ktor 应用日志器；错误响应契约和日志脱敏边界不变。
- 第二次编译显示日志器位于 `Application.environment`，最终改用 `call.application.environment.log`。

## 编码后声明 - 数据库迁移与基础数据模型

时间：2026-08-06

### 1. 复用的既有组件

- `Application.module()`：在应用启动时组织迁移、连接池、请求标识和错误响应插件。
- `ApplicationTest`：沿用 Ktor `testApplication` 结构，新增统一错误响应测试。
- `001_extensions.sql` 与 `docs/07-数据库设计.md`：沿用 `pg_trgm` 扩展与核心表、索引、乐观锁约束。

### 2. 遵循的项目约定

- Kotlin 代码维持 `com.jaydocoder.plateview.server` 包结构；SQL 表和字段使用小写下划线命名。
- Flyway 迁移使用 `V1__create_core_schema.sql` 版本化命名，不通过人工或 Docker 初始化脚本演进业务结构。
- 错误响应和日志使用简体中文；日志不包含数据库密码、身份证号、联系方式、密码或令牌。

### 3. 对比的既有实现

- `Application.kt`：保留其单一模块入口，仅扩展启动期基础设施配置。
- `ApplicationTest.kt`：沿用现有宿主测试方式，补充请求标识与错误码断言。
- `001_extensions.sql`：保留 Docker 首次初始化扩展，业务表结构改由 Flyway 管理，避免职责重叠。

### 4. 未重复造轮子的证明

- 项目内不存在迁移框架、连接池、审计写入器或统一错误模型；使用 Flyway、Hikari、PostgreSQL `pg_trgm` 与 Ktor 官方插件组合实现。
- 未引入自定义数据库迁移执行器、车牌模糊算法或容器编排替代方案。

## 编码前检查 - 登录、角色与权限

时间：2026-08-06

- 已查阅上下文摘要文件：`.codex/context-summary-authentication-and-authorization.md`。
- 将复用 Ktor 模块入口、Flyway 数据库运行时、审计写入器、请求标识和统一错误响应。
- Android 将遵循 `feature/auth`、MVVM、StateFlow、Hilt 和 Compose 单向数据流约定。
- 确认不重复造轮子：项目中没有既有认证、令牌、导航、网络客户端或登录态存储实现；使用 Ktor Authentication/JWT、BCrypt、DataStore 和 Navigation Compose。
- 当前环境未提供 `sequential-thinking`、`shrimp-task-manager`、`desktop-commander` 或 Context7；已以项目文档、现有实现、专项 Android Skills 和本地验证替代，并记录限制。

## 编码前检查 - Excel 导入与数据治理

时间：2026-08-06

- 使用表格分析 Skill 和只读工作簿扫描定位两份正式 Excel，忽略 Office 临时文件。
- 将复用 `import_batches`、`import_rows`、`vehicles`、`resident_profiles`、`long_term_profiles` 表及现有审计写入器。
- 导入实现遵循 `docs/04-Excel导入与数据质量规范.md`、`docs/07-数据库设计.md` 和 ADR-003；不得将源表数据写入 Git、日志或测试夹具。

## 编码前检查 - Excel 导入、发布与回滚

时间：2026-08-06

- 已查阅上下文摘要文件：`.codex/context-summary-excel-import-workflow.md`。
- 将使用以下可复用组件：`Application.module()` 的功能装配方式、`AuthFeature.kt` 的 JWT 路由保护、`AuditLogWriter.kt` 的审计事件、`DataSourceKey` 的数据库访问和既有 Flyway 迁移目录。
- 将遵循命名约定：Kotlin 标识符使用英文驼峰，SQL 使用小写下划线，用户可见文案、注释、测试名称和文档使用简体中文。
- 将遵循代码风格：保持 Ktor 路由与服务职责分离，所有 SQL 使用参数绑定，结构变更使用新的 Flyway 迁移。
- 确认不重复造轮子：已检查现有导入雏形、认证、审计和数据库基础设施；新增代码只补齐 Excel 解析、批次工作流与效果快照，不另建认证或持久化框架。
- 工具可用性：当前环境未提供 `sequential-thinking`、`shrimp-task-manager`、`desktop-commander`、Context7 或 GitHub MCP；以项目文档、现有实现、依赖源码检索、Gradle 自动测试和 Docker Compose 本地验证替代。

## 验证异常 - Ktor Multipart 读取

时间：2026-08-06

- 首次隔离环境上传真实工作簿返回 500，原因是 Ktor `readByteArray(count)` 读取固定字节数，小于上限的上传流会在末尾触发读取异常。
- 已通过本地 Ktor 依赖字节码确认可用 API，改为 `readRemaining(上限).readByteArray()`，保留 10MB 上限后重新构建验证。
- 修复后，村民车辆真实工作簿已在隔离 PostgreSQL 中完成预览、发布、回滚，且回滚后车辆数为零。

## 验证异常 - 长期车辆字段长度

时间：2026-08-06

- 长期车辆工作簿首次发布时，PostgreSQL 报告 `long_term_profiles` 的 `VARCHAR(255)` 字段超长。
- 已在预览解析阶段增加姓名、身份证号、联系方式、车辆类型、单位名称和通行人员的数据库长度校验，将超长行标记为异常而非让整批发布失败。
- 新增自动化边界测试；修复后长期车辆四种分类均完成隔离环境的预览、发布与回滚验证。

## 编码后声明 - Excel 导入、发布与回滚

时间：2026-08-06

### 1. 复用的既有组件

- `Application.module()`：注册导入功能而不改变服务端启动顺序。
- `authenticate("access-token")` 与 `requireAdministrator()`：复用 JWT 会话和管理员角色校验。
- `DataSourceKey`、Flyway 迁移目录和 `AuditLogWriter`：复用数据库连接、结构演进与管理员审计能力。

### 2. 遵循的项目约定

- 导入能力放置在 `server/.../imports/`，数据库变化采用 `V3`、`V4` 版本化迁移。
- 错误经 `ApiErrorResponse` 返回，导入文件、批次不存在和状态冲突拥有稳定错误码。
- 所有新增用户文案、注释、测试名称、文档和提交信息均使用简体中文；日志、审计元数据和测试夹具不包含身份证号、联系方式或真实 Excel 行值。

### 3. 对比的相似实现

- `AuthFeature.kt`：导入路由沿用受保护路由与统一权限拒绝模式，差异是按批次记录审计。
- `AuditLogWriter.kt`：导入只记录批次标识和统计，不记录原始字段，满足敏感信息最小化。
- `ApplicationTest.kt`：新增解析测试沿用 Kotlin Test 组织方式，使用运行时生成工作簿覆盖正常、边界和异常流程。

### 4. 未重复造轮子的证明

- 使用 Apache POI 解析工作簿、Ktor Multipart 和认证插件处理上传与鉴权、PostgreSQL JSONB 保存原始和解析结果、Flyway 管理结构演进。
- 未新增自定义表格格式、认证逻辑、事务框架或回滚存储；发布效果快照基于既有 PostgreSQL 表和标准 JSONB 实现。
