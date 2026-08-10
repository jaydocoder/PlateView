## 项目上下文摘要（导入批次重新发布）

生成时间：2026-08-08

### 相似实现分析

- `server/src/main/kotlin/com/jaydocoder/plateview/server/imports/ImportWorkflowService.kt`：发布与回滚均在同一数据库事务中执行，重新发布必须复用既有行锁、来源版本校验和发布逻辑。
- `server/src/main/resources/db/migration/V4__support_split_rows_and_import_effects.sql`：`import_effects.import_row_id` 有唯一约束，回滚后的重新发布必须在事务内清理旧效果记录。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/admin/AdminWorkspaceScreen.kt`：导入批次状态控制发布按钮，已回滚批次需要明确显示重新发布动作。

### 测试策略

- 状态决策抽取为无副作用函数并添加 JVM 单元测试。
- 服务端事务路径复用原有发布、回滚和来源变更校验。
- 验证运行服务端 JVM 测试、Android JVM 测试、Lint 与调试 APK 构建。

### 关键风险

- 效果记录清理与重新发布必须是同一事务，避免发布校验失败后丢失回滚依据。
- 已回滚批次仍必须经过原有车牌和版本校验，不能绕过正式库变化检测。
