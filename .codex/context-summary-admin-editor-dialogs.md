## 项目上下文摘要（管理员编辑弹框优化）

生成时间：2026-08-10 12:25 CST

### 1. 相似实现分析

- **实现 1**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/admin/AdminWorkspaceViewModel.kt`
  - 模式：ViewModel 以 `AdminUiState` 和 `StateFlow` 管理管理员页面及编辑状态。
  - 可复用：`update`、`launchAdminAction`、`VehicleEditorState` 与 `UserEditorState`。
  - 注意：`editVehicle` 错误使用全局 `isLoading`，会遮蔽车辆列表。

- **实现 2**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/admin/AdminWorkspaceScreen.kt`
  - 模式：Compose Material 3 页面与对话框，表单通过上行回调修改不可变编辑状态。
  - 可复用：`EditorTextField`、`ChoiceField`、`EditorSectionHeading`、`PlateViewDimensions`。
  - 注意：现有车辆和账号编辑弹框均为未分层的 `AlertDialog`，保存操作不固定。

- **实现 3**：`android/app/src/androidTest/kotlin/com/jaydocoder/plateview/AdminWorkspaceScreenTest.kt`
  - 模式：Compose 仪器化测试以语义文本与测试标签验证管理界面。
  - 可复用：`createAndroidComposeRule`、`AdminWorkspaceScreen` 注入回调。
  - 注意：保留既有车辆列表与导入操作测试标签。

### 2. 项目约定

- Kotlin、Hilt、StateFlow、Compose Material 3；页面仅渲染状态并通过回调发出动作。
- 所有颜色与圆角从 `MaterialTheme` 和 `PlateViewDimensions` 取得。
- 面向景区数据管理员，优先保证字段扫描、核对和保存操作效率。

### 3. 测试策略

- 单元测试：编辑现有车辆时只启用局部编辑加载状态，不触发页面级同步。
- Compose 测试：编辑加载提示、车辆编辑分区、账号编辑分区和停用确认的关键语义。
- 构建：Android 单元测试、Lint、调试 APK；真机运行管理员弹框测试。

### 4. 依赖与风险

- 详情接口在服务器内部约 5–6ms，主要用户等待来自公网往返；本次不改服务端协议。
- 编辑加载状态必须在成功、失败和取消路径复位，避免弹框永久停留。
- 字段、校验、保存命令与版本冲突处理保持不变。
