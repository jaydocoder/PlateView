## 项目上下文摘要（车辆查询服务）

生成时间：2026-08-06

### 1. 相似实现分析

- **实现 1**：`server/src/main/kotlin/com/jaydocoder/plateview/server/Application.kt`
  - 模式：在 `Application.module()` 中按数据库、请求上下文、错误处理、认证和功能路由顺序装配。
  - 可复用：新增 `configureVehicleQueryFeature()` 与导入功能并列注册。
  - 注意：未配置数据库时功能模块不注册，不能破坏现有健康检查测试。

- **实现 2**：`server/src/main/kotlin/com/jaydocoder/plateview/server/auth/AuthFeature.kt`
  - 模式：通过 `authenticate("access-token")` 保护普通用户可用接口；仅管理员接口再调用 `requireAdministrator()`。
  - 可复用：查询和详情使用 JWT 中的用户标识作为审计操作人。
  - 注意：车辆查询不要求管理员，已登录普通用户和管理员均可使用。

- **实现 3**：`server/src/main/kotlin/com/jaydocoder/plateview/server/infrastructure/database/AuditLogWriter.kt`
  - 模式：以 `AuditEvent` 统一写入追加式审计，元数据为 JSON 对象。
  - 可复用：详情查询记录规范化车牌、结果和设备类别。
  - 注意：元数据不得写入身份证号、联系方式、密码、令牌或完整 User-Agent。

- **实现 4**：`server/src/main/kotlin/com/jaydocoder/plateview/server/imports/ExcelImportParser.kt`
  - 模式：当前 `normalizePlate` 已用于导入时建立 `normalized_plate`。
  - 可复用：将其迁移为车辆领域工具，确保导入和查询键规则完全一致。
  - 注意：查询必须过滤无效字符，并对少于四个有效字符返回可处理的客户端错误。

### 2. 项目约定

- **命名约定**：Kotlin 类型和函数使用英文驼峰；SQL 使用小写下划线；文案、注释、测试名称和文档使用简体中文。
- **路由约定**：Ktor 路由使用 `authenticate("access-token")`、`ApiErrorResponse`、请求标识和 `StatusPages`。
- **数据约定**：仅查询 `ACTIVE` 车辆；候选最多 20 条且仅含车牌与车辆所属类型，固定精确、前缀、包含排序；详情使用车辆 ID 查询分类档案。
- **审计约定**：详情查询成功与未命中均留痕，日志与审计不包含身份证号、联系方式或完整设备标识。

### 3. 可复用组件清单

- `DataSourceKey`：取得 PostgreSQL 数据源。
- `AuditLogWriterKey`、`AuditEvent`：写入详情查询审计。
- `ApiErrorResponse`：统一响应格式。
- `ImportCategory`：既有的五类车辆类别代码，可迁移为车辆领域类别。
- `pg_trgm` GIN 索引：已有 `vehicles.normalized_plate` 包含式检索索引。

### 4. 测试策略

- **单元测试**：归一化、分类显示名、两字符边界。
- **接口与数据库验证**：隔离 Docker Compose 中导入真实工作簿后验证搜索排序、20 条上限、详情字段、未登录、短关键字、空结果和审计记录。
- **隐私约束**：终端仅断言状态、数量、类别和字段是否存在，不输出任何真实车牌、姓名、身份证号或联系方式。

### 5. 依赖和集成点

- **外部依赖**：Ktor JWT、PostgreSQL JDBC、PostgreSQL `pg_trgm`、Kotlinx Serialization。
- **内部依赖**：`vehicles`、`resident_profiles`、`long_term_profiles` 和 `audit_logs`；导入发布后的正式车辆直接可供查询。
- **配置来源**：数据库与 JWT 环境变量沿用 `application.conf` 和 Docker Compose。

### 6. 技术选型理由

- 使用 PostgreSQL `LIKE` 加现有 `pg_trgm` 索引：满足包含匹配，避免维护额外搜索服务。
- 使用单条带分类档案的详情查询：减少网络往返，并保持资料读取的一致性。
- 使用稳定的车辆 ID 查询详情：候选车牌可能被管理员后续修正，ID 更适合作为导航参数。

### 7. 关键风险点

- **性能**：包含式 `LIKE` 需要保留三元索引；结果限制为 20 条且使用稳定排序。
- **边界输入**：无效字符和不足四个有效字符不得访问数据库；不存在或停用的车辆返回 404。
- **隐私**：候选 DTO 不能出现身份证号或联系方式；详情数据不得进入错误消息、审计元数据或应用日志。
- **一致性**：导入和查询必须共用同一归一化函数，避免同一车牌无法被检索。
