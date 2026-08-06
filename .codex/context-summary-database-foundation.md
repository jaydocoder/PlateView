## 项目上下文摘要（数据库迁移与基础数据模型）

生成时间：2026-08-06

### 1. 相似实现分析

- **实现 1：** `server/src/main/kotlin/com/jaydocoder/plateview/server/Application.kt`
  - 模式：Ktor 通过单一 `Application.module()` 入口安装插件和注册路由。
  - 可复用：`ContentNegotiation`、`routing` 和序列化响应模式。
  - 注意：数据库迁移应在应用启动时完成，不能分散到路由处理逻辑。

- **实现 2：** `server/src/test/kotlin/com/jaydocoder/plateview/server/ApplicationTest.kt`
  - 模式：使用 `testApplication` 启动 Ktor 模块，按接口行为断言响应。
  - 可复用：Ktor 测试宿主和 Kotlin 测试断言。
  - 注意：迁移测试需要使用独立的测试数据库，不得访问真实 Excel 数据或真实部署数据库。

- **实现 3：** `infra/postgres/init/001_extensions.sql`
  - 模式：Docker Compose 首次初始化时通过只读目录执行基础数据库脚本。
  - 可复用：`pg_trgm` 扩展创建语句。
  - 注意：业务结构改为 Flyway 版本化迁移，不继续向初始化目录添加表结构脚本。

- **实现 4：** `docs/07-数据库设计.md`
  - 模式：以车辆为核心，分离村民和长期车辆资料，导入与审计独立建表。
  - 可复用：7 张核心表、唯一约束、三元模糊索引、乐观锁和仅追加审计约束。
  - 注意：所有业务表均需创建与更新时间、创建与更新人、版本号；`audit_logs` 不提供更新或删除业务接口。

### 2. 项目约定

- **命名约定：** Kotlin 包名使用 `com.jaydocoder.plateview.server`；SQL 表和列使用小写下划线命名；迁移采用 Flyway 的版本化文件名。
- **文件组织：** 服务端代码位于 `server/src/main/kotlin/`，测试位于 `server/src/test/kotlin/`，Docker 初始化位于 `infra/postgres/init/`。
- **代码风格：** 使用 Kotlin 官方格式；说明、测试名称和注释使用简体中文。
- **配置来源：** Docker Compose 从未提交的 `.env` 读取 PostgreSQL 配置；Ktor 后续从环境变量读取 JDBC 配置。

### 3. 可复用组件清单

- `Application.module()`：Ktor 插件安装和启动入口。
- `ApplicationTest`：Ktor 接口测试结构。
- `001_extensions.sql`：`pg_trgm` 基础扩展安装。
- `docs/07-数据库设计.md`：数据表、索引、乐观锁和审计边界的唯一设计来源。

### 4. 测试策略

- 使用 Docker Compose 的全新 PostgreSQL 卷执行 Flyway 迁移，并通过 SQL 验证核心表、迁移历史和车牌三元索引。
- 使用 Ktor `testApplication` 验证健康检查和统一错误响应的状态码、错误码与请求标识。
- Testcontainers `1.20.6` 与本机 Docker `29.7.1` 的最低 API 版本不兼容，已不作为本项目当前的自动测试依赖。
- 单元与接口测试不得使用真实 Excel、身份证号、联系方式、密码或令牌。

### 5. 依赖和集成点

- **外部依赖：** Flyway PostgreSQL 支持库、PostgreSQL JDBC 驱动、Ktor 配置与日志组件。
- **内部依赖：** Ktor `Application.module()` 在服务启动时调用迁移器；Docker Compose 为 API 提供 PostgreSQL 连接信息。
- **数据库：** Docker Compose 使用 `postgres:17-alpine`，默认宿主机端口为 `5432`，持久卷为 `postgres-data`。

### 6. 技术选型理由

- 使用 Flyway 管理版本化迁移，满足数据库设计文档中“不可依赖人工数据库编辑”的约束。
- 保留 Docker 初始化脚本仅用于安装 `pg_trgm`，避免初始脚本和迁移脚本双重维护表结构。
- 使用 PostgreSQL 约束和索引保证数据一致性与后续模糊查询性能。

### 7. 关键风险点

- 初始管理员密码必须来自未提交环境变量，不能写入迁移脚本或日志。
- 初始脚本只在新卷首次启动时运行，因此业务建表必须由可重复执行的迁移负责。
- 导入数据存在重复和不规范车牌，正式唯一约束应仅覆盖已发布的有效车辆。
- 本机未确认 Android 12 至 14 测试设备；本阶段不阻塞服务端数据库测试，但须在阶段 9 补齐。

### 8. 上下文充分性检查

- 是：已分析 Ktor 启动、Ktor 测试、Docker 初始化和数据库设计四类既有实现。
- 是：数据模型、表约束、初始化边界和配置来源已明确。
- 是：可复用入口、测试结构和扩展脚本已识别。
- 是：测试方式为 PostgreSQL 容器迁移验证与 Ktor 接口测试。
