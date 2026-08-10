## 项目上下文摘要（系统语音兜底与审计运维面板）

生成时间：2026-08-09 23:40 CST

### 相似实现

- `feature/search/VoiceRecognizer.kt`：现有系统 `SpeechRecognizer` 适配器与 Hilt 绑定。
- `feature/search/SearchRoute.kt`：已有运行时权限与 Activity Result 使用模式。
- `server/admin/AdminManagementService.kt`：现有审计分页查询及参数化 SQL 模式。
- `feature/admin/AdminWorkspaceViewModel.kt`：车辆懒加载、搜索防抖和分页状态模式。
- `feature/admin/AdminWorkspaceScreen.kt`：Material 3 管理工作台、状态徽章和稳定 LazyColumn 键。

### 约定与集成点

- 语音系统界面由 `SearchRoute` 的 `ActivityResultContracts.StartActivityForResult` 启动；ViewModel 只发送事件与维护状态。
- 审计接口保持 `GET /admin/audit`，扩展可选查询参数和响应字段；服务端使用 Flyway 演进索引。
- Android 领域模型、Retrofit DTO、仓库、ViewModel 和 Compose 界面按现有 MVVM 流向依次同步。
- 审计分页每页 50 条，默认最近 30 天；异常结果为 `FAILURE` 与 `DENIED`。

### 验证策略

- 服务端审计筛选、分页、汇总与权限自动测试。
- Android ViewModel 语音兜底、审计筛选重置与追加分页单元测试。
- Compose/仪器化测试覆盖系统语音回填与异常审计视觉状态。
- Android 12 真机验证系统语音 Activity 与管理员审计筛选。
