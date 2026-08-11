## 项目上下文摘要（首页搜索清空）

生成时间：2026-08-12 00:20:00 CST

### 1. 相似实现分析

- **实现1**: `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchScreen.kt`
  - 模式：`SearchBar` 为无状态 Compose 组件，文本变化通过 `onQueryChanged` 上送。
  - 可复用：`TextField`、主题色、`IconButton`、稳定测试标签和已有搜索图标。
  - 需注意：清空必须仅在非空输入时显示，避免空白状态出现无效操作。

- **实现2**: `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchViewModel.kt`
  - 模式：`updateQuery` 同时更新输入 Flow 与 `SearchUiState`；防抖查询消费该 Flow。
  - 可复用：直接传入空字符串即可保持单向数据流。
  - 需注意：不得绕过 ViewModel 修改候选或反馈状态，空输入已有 `Idle` 状态处理。

- **实现3**: `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/update/UpdateAvailableAction.kt`
  - 模式：使用 Material 3 `IconButton`、图标内容描述和 `testTag` 提供可访问、可测试的图标操作。
  - 可复用：标准最小触控区域、语义描述和稳定测试定位方式。
  - 需注意：清空图标应采用同一主题令牌而非硬编码颜色。

- **实现4**: `android/app/src/androidTest/kotlin/com/jaydocoder/plateview/VehicleQueryScreenTest.kt`
  - 模式：以 `createAndroidComposeRule` 渲染 `SearchScreen` 并验证回调。
  - 可复用：现有主题、测试夹具、`testTag` 与点击断言。
  - 需注意：测试使用 Compose 状态保存查询文本，才能验证点击后的重新组合与按钮隐藏。

### 2. 项目约定

- **命名约定**：组件使用 PascalCase，回调使用动词；测试标签以功能名小写下划线命名。
- **代码风格**：Compose 界面只渲染状态并派发事件；使用 Material 3 组件和 `MaterialTheme` 颜色令牌。
- **可访问性**：图标按钮提供中文 `contentDescription`，使用 Material `IconButton` 保持 48dp 触控区域。

### 3. 可复用组件清单

- `SearchScreen` 和 `SearchBar`：新增末尾操作位。
- `SearchViewModel.updateQuery`：作为清空后的唯一状态更新入口。
- `VehicleQueryScreenTest`：增加首页清空输入的 Compose 回归用例。
- `PlateViewTheme`：使用现有 `onSurfaceVariant` 主题色。

### 4. 测试策略

- **测试框架**：JUnit4 与 Compose 仪器化测试。
- **测试场景**：非空输入显示清空按钮；点击回调得到空字符串；重组后清空按钮隐藏；空输入不显示按钮。
- **发布验证**：调试与正式 JVM 测试、Lint、正式签名构建、仪器化测试 APK 编译与版本签名校验。

### 5. 依赖和集成点

- **外部依赖**：既有 Compose Material 3 和扩展图标库，无新增依赖。
- **内部依赖**：`SearchScreen` -> `onQueryChanged` -> `SearchViewModel.updateQuery` -> 查询 Flow。
- **配置来源**：仅升级 Android 版本号与 README 发布说明；不改变服务端接口、域名或更新下载模块。

### 6. 技术选型理由

- **方案**：在现有 `TextField.trailingIcon` 使用条件化 `IconButton`。
- **优势**：无新增状态、无新增网络调用、符合 Android 原生输入框交互，响应即时。
- **风险**：按钮需要在空输入后隐藏；通过 Compose 回归测试锁定该状态转换。

### 7. 关键风险点

- **状态一致性**：只调用 `onQueryChanged("")`，确保 ViewModel 防抖、候选和反馈状态统一更新。
- **无障碍**：图标必须拥有明确中文内容描述。
- **兼容性**：使用项目已有 Material 3 API，最低 Android 12 不受影响。
- **资料来源**：通过 GitHub API 搜索公开 Kotlin Compose 示例确认末尾图标模式；Context7 与 sequential-thinking、shrimp-task-manager 工具未在本会话提供，已以项目既有模式、自动化测试和本地构建替代。
