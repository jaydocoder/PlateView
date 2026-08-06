## 项目上下文摘要（Excel 导入、发布与回滚）

生成时间：2026-08-06

### 1. 相似实现分析

- **实现 1**：`server/src/main/kotlin/com/jaydocoder/plateview/server/Application.kt`
  - 模式：在 `Application.module()` 中按基础设施、鉴权、功能路由顺序装配。
  - 可复用：`configureImportPreviewFeature()` 的功能注册位置。
  - 注意：数据库未配置时功能路由不应注册，以保持现有健康检查测试可执行。

- **实现 2**：`server/src/main/kotlin/com/jaydocoder/plateview/server/auth/AuthFeature.kt`
  - 模式：使用 `authenticate("access-token")` 包装 JWT 受保护路由，统一返回 `ApiErrorResponse`。
  - 可复用：`JWTPrincipal` 的用户标识和角色声明、初始管理员与会话校验。
  - 注意：管理员接口必须在服务端校验角色，不能仅依赖 Android 客户端隐藏入口。

- **实现 3**：`server/src/main/kotlin/com/jaydocoder/plateview/server/infrastructure/database/AuditLogWriter.kt`
  - 模式：通过应用属性注入 `AuditLogWriter`，以 `AuditEvent` 记录成功、失败和拒绝结果。
  - 可复用：管理员导入预览、行处置、发布和回滚审计。
  - 注意：审计元数据仅保存统计和批次标识，不能写入身份证号、联系方式或原始 Excel 内容。

### 2. 项目约定

- **命名约定**：Kotlin 类和函数使用英文驼峰；SQL 使用小写下划线；用户可见文案、注释和文档使用简体中文。
- **文件组织**：服务端功能位于 `server/src/main/kotlin/com/jaydocoder/plateview/server/`；迁移位于 `server/src/main/resources/db/migration/`；Ktor 测试位于 `server/src/test/kotlin/`。
- **错误处理**：通过 `ApiErrorResponse` 和请求标识返回客户端可处理的错误，不暴露内部异常。
- **数据演进**：既有 V1 至 V3 不修改，新增结构使用后续 Flyway 迁移。

### 3. 可复用组件清单

- `DataSourceKey`：取得 PostgreSQL 数据源。
- `AuditLogWriterKey`、`AuditEvent`：记录管理员操作审计。
- `ApiErrorResponse`：统一错误响应。
- `docs/04-Excel导入与数据质量规范.md`、`docs/ADR/ADR-003-Excel分阶段导入.md`：解析规则、状态机和回滚边界。

### 4. 测试策略

- **测试框架**：Kotlin Test 与 Ktor `testApplication`。
- **新增测试**：使用运行时生成的无真实个人信息工作簿覆盖分类、单位继承、多车牌拆分、警告和异常。
- **集成验证**：通过 Docker Compose 的本地 PostgreSQL、真实 Excel 上传和数据库统计断言验证迁移与预览；终端不打印行数据或个人字段。
- **覆盖范围**：正常解析、边界车牌、跨行单位、多车牌、身份证和联系方式告警、非法文件、批次状态冲突、发布与回滚事务。

### 5. 依赖和集成点

- **外部依赖**：Apache POI 解析 Excel；Ktor Multipart、JWT 和 Kotlinx Serialization；PostgreSQL JSONB 与 Flyway。
- **内部依赖**：导入行写入 `import_rows`，发布写入 `vehicles`、`resident_profiles` 和 `long_term_profiles`，回滚依赖新增的导入效果快照。
- **配置来源**：数据库和 JWT 配置来自 `server/src/main/resources/application.conf` 的环境变量覆盖。

### 6. 技术选型理由

- 采用 Apache POI：支持两份来源工作簿的 `.xlsx` 解析和数据格式化。
- 采用预览批次与行级决议：阻止未校验的表格直接进入正式查询库。
- 采用效果快照和版本校验：只恢复本批次的更新，检测后续修改，避免回滚污染其他批次数据。

### 7. 关键风险点

- **解析边界**：工作表名可能变化，解析器以字段表头辅助识别并为无法识别的工作簿返回错误。
- **并发问题**：预览后可能有其他导入或编辑变更车辆；发布使用行锁和批次状态校验，回滚比较发布版本。
- **数据质量**：多车牌、首行空单位、无效车牌为异常；格式不正确的身份证和联系方式为需确认警告。
- **隐私边界**：真实 Excel 仅用于本地无输出验证，不能提交到 Git、写入日志或放入测试夹具。
