## 项目上下文摘要（导入差异核对与全量失效同步）

生成时间：2026-08-16

### 1. 相似实现分析

- **实现1**：`server/src/main/kotlin/com/jaydocoder/plateview/server/imports/ImportWorkflowService.kt`
  - 模式：在单一 PostgreSQL 事务中完成导入预览、发布、回滚与乐观锁校验。
  - 可复用：`ExistingVehicle` 快照、`replaceProfile`、`insertEffect`、`prepareImportPublish`。
  - 约束：发布前必须校验车辆版本；回滚依赖 `import_effects` 的前态快照。
- **实现2**：`server/src/main/kotlin/com/jaydocoder/plateview/server/imports/ImportPreviewFeature.kt`
  - 模式：Ktor 路由将内部导入视图映射为序列化响应，统一使用管理员鉴权和审计。
  - 可复用：批次分页、处置接口与 `ImportBatchView.toResponse()` 映射。
  - 约束：接口契约变更需要同步 Android DTO 与仓储映射。
- **实现3**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/admin/AdminWorkspaceScreen.kt`
  - 模式：`ViewModel` 持有不可变界面状态，Compose 仅接收状态和回调；`LazyColumn` 配合 `snapshotFlow` 做分页。
  - 可复用：`FilterChip`、`AlertDialog`、稳定列表键、加载提示与现有管理端视觉令牌。
  - 约束：差异详情按需请求，不能在组合阶段发起网络调用。

### 2. 项目约定

- **命名约定**：Kotlin 类型使用帕斯卡命名，函数与字段使用驼峰命名，SQL 使用小写下划线。
- **文件组织**：服务端导入逻辑位于 `server/.../imports`；Android 管理端按数据、领域、展示层分目录。
- **代码风格**：Kotlin 官方格式；业务文本、注释、测试名称和文档均使用简体中文。
- **状态管理**：Android 使用 `StateFlow`、不可变 `AdminUiState` 与上行回调的单向数据流。

### 3. 可复用组件清单

- `ImportWorkflowService.replaceProfile`：按车辆类别写入村民或长期车辆档案。
- `ImportWorkflowService.insertEffect`：记录发布前态，供批次回滚。
- `NetworkAdminRepository`：Retrofit DTO 到领域模型的唯一映射边界。
- `AdminWorkspaceViewModel.loadImportBatch`：批次列表的分页与状态合并。
- `AdminWorkspaceScreen`：管理端 Material 3 弹框、筛选与懒加载模式。

### 4. 测试策略

- **服务端**：Kotlin Test；既有 `ExcelImportParserTest` 与 `ImportPublishLifecycleTest` 使用中文场景名。
- **Android 单元测试**：JUnit/Kotlin Test，使用伪 `AdminRepository` 检查 ViewModel 状态。
- **Android 界面测试**：Compose 仪器化测试，使用语义文本与 `testTag` 断言交互。
- **覆盖要求**：新增、更新、完全一致隐藏、待失效、恢复、异常、并发冲突、回滚、筛选和分页。

### 5. 依赖和集成点

- **数据库**：PostgreSQL/Flyway；迁移目录为 `server/src/main/resources/db/migration`。
- **服务端**：Ktor、kotlinx.serialization、JDBC、Apache POI。
- **客户端**：Retrofit、Gson、Hilt、Kotlin 协程、Material 3 Compose。
- **接口顺序**：先部署服务端和 Flyway，再发布 Android APK；旧客户端可忽略新增响应字段。

### 6. 技术选型理由

- 在 `import_rows` 持久化正式库对照快照，保证管理员看到的是上传时的稳定基线，不受随后编辑影响。
- 以系统生成的 `DEACTIVATE` 导入行复用现有处置、发布和回滚链路，不新建平行工作流。
- 详情接口按需返回字段级差异，避免列表分页传输完整身份信息与大型 JSON。

### 7. 关键风险点

- 局部 Excel 可能误伤未包含类别，故仅比较本次成功识别的类别。
- 解析异常与缺失判定可能同时出现；按已确认需求仍生成待失效项并要求逐条确认。
- 同一失效车牌存在多条历史记录时不能自动恢复，必须作为异常阻止发布。
- 发布与回滚必须在事务与乐观锁校验内完成，避免覆盖预览后的人工修改。
