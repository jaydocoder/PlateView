## 项目上下文摘要（数据库字段设计）

生成时间：2026-08-16 20:51:17 CST

### 1. 相似实现分析

- **实现 1：** `docs/07-数据库设计.md`
  - 模式：按核心表、约束索引、乐观锁和迁移边界说明数据库设计。
  - 可复用：既有表职责、业务规则和迁移说明。
  - 需补足：尚未覆盖字段类型、可空性、主外键和完整索引清单。

- **实现 2：** `docs/03-数据字典与分类规范.md`
  - 模式：使用“字段、类型、说明”表格呈现可读的数据字典。
  - 可复用：中文字段说明和车辆分类枚举术语。
  - 需注意：该文档只描述车辆业务字段，不能替代实际 PostgreSQL DDL。

- **实现 3：** `server/src/main/resources/db/migration/V1__create_core_schema.sql`
  - 模式：以 PostgreSQL DDL 创建主表、约束、索引和更新时间触发器。
  - 可复用：字段默认值、检查约束、外键关系和索引定义。
  - 需注意：后续迁移会扩展导入流程，因此不能只依据 V1。

- **实现 4：** `server/src/main/resources/db/migration/V2__create_refresh_sessions.sql`、`V3__extend_import_workflow.sql`、`V4__support_split_rows_and_import_effects.sql`、`V5__add_vehicle_catalog_version.sql`、`V13__add_audit_operations_indexes.sql`
  - 模式：通过版本化增量迁移演进表字段、外键和索引。
  - 可复用：刷新会话、导入回滚、目录版本和审计索引的最终定义。
  - 需注意：文档必须反映所有已执行迁移，而非某一个版本快照。

### 2. 项目约定

- SQL 表名、列名和索引名使用小写下划线。
- 结构变更只能通过 `server/src/main/resources/db/migration/` 中的 Flyway 迁移完成。
- 数据库设计文档使用 Markdown 表格，字段说明使用简体中文。
- `flyway_schema_history` 为 Flyway 管理表，不作为业务功能表编辑。

### 3. 事实来源与集成点

- 服务器数据库：七牛云 `plateview` 数据库的 `public` Schema。
- 实际结构核验：使用 PostgreSQL `\\d+` 读取字段、可空性、默认值、索引、检查约束与外键。
- 运行时迁移：Ktor API 启动时通过 Flyway 执行。
- 业务模型：车辆主档案、两类分类档案、导入工作流、账户会话、审计和 Android 目录版本同步。

### 4. 文档验证策略

- 对照全部已提交迁移脚本，确认每个业务表均有文档项。
- 使用服务器真实 `public` Schema 的表清单、字段和索引结果复核文档。
- 以 Markdown 表格行数与表清单交叉检查，确保覆盖 10 张业务/运行表和 1 张 Flyway 内部表。

### 5. 风险点

- 业务数据含身份证号等敏感字段；文档只描述字段用途，绝不写入真实记录或环境变量。
- 手工通过 Navicat 修改结构会使实际库与 Flyway 迁移脱节，禁止作为结构演进方式。
- 迁移新增字段或索引后，必须同步更新本文件。

### 6. 充分性检查

- 是：已分析至少三个既有实现，且覆盖概要文档、数据字典和全部结构迁移。
- 是：已明确数据库最终结构、关系、配置来源和运行时迁移入口。
- 是：已识别使用 PostgreSQL 实际元数据进行验证的方法。
