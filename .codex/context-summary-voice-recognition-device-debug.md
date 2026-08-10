## 项目上下文摘要（真机语音识别故障修复）

生成时间：2026-08-10

### 1. 相似实现分析

- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchViewModel.kt`
  - 通过 `StateFlow` 保存界面状态，通过 `SearchEvent` 发送一次性导航和系统语音事件。
  - 直接识别失败时当前依赖无重放的共享流触发系统识别界面。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchScreen.kt`
  - 使用 `rememberLauncherForActivityResult` 接收权限和系统识别 Activity 结果。
  - 已具备系统识别 Intent 构造与结果回填逻辑。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/admin/AdminWorkspaceViewModel.kt`
  - 使用不可变 `AdminUiState` 保存可恢复的分页和筛选命令状态。
  - 证明项目以状态而不是易丢失回调维护跨重组界面行为。

### 2. 真机证据

- 真机为 Android 12，当前已安装 PlateView `0.3.4`。
- Google `RecognitionService` 与 `ACTION_RECOGNIZE_SPEECH` Activity 均可查询到。
- ADB 可启动 Google `TranscriptionActivity`，但 Activity 随即返回。
- 在 PlateView 点击麦克风后仍显示“当前设备未提供语音识别服务”，系统界面未成为前台。

### 3. 项目约定与复用

- ViewModel 持有 `StateFlow`，路由层处理 Android `ActivityResult`。
- 使用 Kotlin、Compose、Hilt 和 Material 3；不引入第三方云语音 SDK。
- 测试以 `SearchViewModelTest` 的假识别器覆盖状态转换。

### 4. 修复策略

- 将“启动系统识别”从无重放共享流事件改为 `SearchUiState` 的一次性消费命令。
- 路由以 `LaunchedEffect` 观察该状态启动系统 Activity，并在启动、取消、失败和回填后确认消费。
- 单元测试覆盖命令设置、消费和系统结果回填。

### 5. 风险与验证

- Google 系统界面可自行快速结束，应用必须显示取消或无结果而非错误的服务不可用提示。
- 使用真机 `adb` 触发麦克风入口验证系统 Activity 是否成为前台，并读取实际回调状态。
