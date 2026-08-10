## 项目上下文摘要（更新入口自主选择）

生成时间：2026-08-10 13:18:36 CST

### 1. 相似实现分析

- **实现 1**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/update/AppUpdateViewModel.kt`
  - 模式：Hilt ViewModel 以不可变 `AppUpdateUiState` 和 `StateFlow` 提供更新状态，界面只调用事件方法。
  - 可复用：`MutableStateFlow.update`、下载状态和 15 分钟检查节流。
  - 注意：下载进行中不能关闭弹框，避免用户误以为下载已取消。

- **实现 2**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchScreen.kt`
  - 模式：`Scaffold` 的 `TopAppBar.actions` 承载页面级操作，图标按钮有明确的无障碍说明。
  - 可复用：Material 3 顶栏、`IconButton`、主题颜色和 `PlateViewDimensions`。
  - 注意：更新入口应放入既有顶栏动作区，不能覆盖固定搜索框或历史记录。

- **实现 3**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/vehicle/VehicleDetailScreen.kt`
  - 模式：详情页通过路由参数向屏幕传入导航回调，屏幕本身不持有导航控制器。
  - 可复用：路由到屏幕的回调透传方式。
  - 注意：更新入口需跨搜索、详情和管理页保持可达。

- **实现 4**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/update/AppUpdateDialog.kt`
  - 模式：更新对话框已完整覆盖下载、重试和安装就绪状态。
  - 可复用：现有弹框和 `UpdateDownloadState`。
  - 注意：本次不改变下载与安装流程，只让用户主动打开弹框。

### 2. 项目约定

- Android 使用 Kotlin、Hilt、Compose Material 3、MVVM 和单向数据流。
- 可组合界面通过参数接收事件回调，路由层负责 ViewModel 和导航组装。
- 颜色和圆角使用 `MaterialTheme` 与 `PlateViewDimensions`，不新增硬编码配色。
- 用户可见文字、测试名称、注释和提交信息均使用简体中文。

### 3. 可复用组件清单

- `AppUpdateDialog`：版本说明、下载进度、重试和系统安装入口。
- `AppUpdateViewModel`：静默检查与下载状态机。
- `AuthenticatedNavigation`：跨页面回调的统一分发点。
- `PlateViewTheme`、`PlateViewDimensions`：颜色、形状和间距令牌。

### 4. 测试策略

- JVM：验证静默发现更新时不显示弹框；点击入口显示弹框；稍后处理仅隐藏弹框并保留更新入口。
- Compose：验证更新入口可见、具备无障碍描述且可点击。
- 构建：串行运行调试单元测试、Lint、调试 APK；不与其他 Gradle 构建并发执行。

### 5. 依赖和集成点

- 输入：`MainActivity.onResume()` 触发的 GitHub Release 静默检查。
- 状态：`AppUpdateUiState.update` 表示有可用版本，`isUpdateDialogVisible` 仅表示用户已主动打开详情。
- 已登录入口：`AuthenticatedNavigation` 传入各业务页顶栏。
- 未登录入口：`PlateViewApp` 在登录页右上角展示同一入口。

### 6. 技术选型理由

- 保留 GitHub Release 更新源，避免新增服务端、配置与维护成本。
- 将“可用更新”和“弹框可见性”拆开，符合单向数据流，也能让“稍后处理”不丢失版本提示。
- 使用顶栏图标加状态点，提示可见但不会中断车牌核验工作流。

### 7. 关键风险点

- GitHub 网络不可用时检查失败必须静默保留已有更新状态，不能影响正常使用。
- 当新版本撤回或本机已升级后，需要清除旧入口和下载状态。
- 更新下载过程中不允许关闭弹框，防止下载任务状态与界面认知不一致。
