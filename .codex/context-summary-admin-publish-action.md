## 项目上下文摘要（管理员正式发布按钮）

生成时间：2026-08-07 17:20 CST

### 相似实现分析

- `AdminWorkspaceScreen.kt`：管理员页面以不可变 `AdminUiState` 渲染，并将用户事件作为回调上送。
- `AdminWorkspaceViewModel.kt`：`publishImport()` 读取当前批次和会话令牌，再调用 `AdminRepository.publishImport()`。
- `ImportPreviewFeature.kt`：服务器已注册 `POST /admin/imports/{batchId}/publish`，并在成功后写入发布审计。
- `AdminWorkspaceScreenTest.kt`：既有真机 Compose 测试用 `testTag` 查找可交互节点并验证回调。

### 故障证据

- 真机显示“正式发布数据”节点为已启用且可点击。
- 触发点击后批次仍为 `VALIDATED`，未出现 `IMPORT_PUBLISH` 审计记录。
- “关闭预览”按钮能响应同一 ADB 点击方式，排除设备触控与通用输入问题。

### 修复与验证

- 移除确认按钮槽位中的额外 `Row`，直接渲染发布或回滚按钮。
- 使用显式回调调用和 `admin_publish_import` 测试标签。
- 增加 Compose 仪器化测试，断言可发布批次点击后调用发布回调。
- 验证顺序：JVM 测试、构建测试 APK、仪器化回归、构建调试 APK、安装真机并人工确认发布动作。
