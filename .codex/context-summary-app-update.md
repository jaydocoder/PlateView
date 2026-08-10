## 项目上下文摘要（GitHub 发行版应用更新）

生成时间：2026-08-09 22:05:00 CST

### 1. 相似实现分析

- **实现 1**：`android/app/src/main/kotlin/com/jaydocoder/plateview/data/network/NetworkModule.kt`
  - 模式：Hilt 单例模块提供 Retrofit。
  - 可复用：Retrofit、Gson、Hilt `SingletonComponent`。
  - 注意：GitHub API 基础地址与业务 API 不同，必须使用限定符提供第二个客户端。

- **实现 2**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/auth/AppSessionViewModel.kt`
  - 模式：Hilt ViewModel 通过不可变 `StateFlow` 向 Compose 传递状态，事件由方法向上回调。
  - 可复用：`@HiltViewModel`、`viewModelScope`、`collectAsStateWithLifecycle`。
  - 注意：更新检查不能直接在 Composable 主体执行。

- **实现 3**：`android/app/src/main/kotlin/com/jaydocoder/plateview/MainActivity.kt`
  - 模式：Activity 承担 Android 平台入口，Compose 仅承载应用根界面。
  - 可复用：`@AndroidEntryPoint`、Activity 生命周期。
  - 注意：APK 安装 Intent 与 FileProvider 必须留在 Android 平台层，不进入 Composable 或仓库。

- **实现 4**：`android/app/src/main/kotlin/com/jaydocoder/plateview/PlateViewTheme.kt`
  - 模式：所有界面颜色、圆角和文本由 MaterialTheme 提供。
  - 可复用：主色、次色、第三色和 Material 3 形状。
  - 注意：更新弹窗不得新增硬编码色彩。

### 2. 项目约定

- Android 使用 Kotlin、Hilt、Compose Material 3、MVVM 与单向数据流。
- 数据层位于 `data/`，领域接口和模型位于 `domain/`，界面状态位于 `feature/`。
- Compose 使用 `collectAsStateWithLifecycle`，副作用从 ViewModel 或 Activity 生命周期触发。
- 用户可见文本和测试名称使用简体中文。

### 3. 可复用组件清单

- `NetworkModule`：Retrofit 与 Gson 配置模式。
- `AppSessionViewModel`：Hilt ViewModel 与 StateFlow 模式。
- `PlateViewTheme`、`PlateViewDimensions`：更新弹窗视觉令牌。
- `MainActivity`：平台安装 Intent 的唯一入口。

### 4. 测试策略

- JVM：版本号比较、发现高版本、忽略相同或较低版本、下载进度状态。
- 构建：调试单元测试、Lint、调试 APK、正式 APK、仪器化测试 APK。
- 真机：按用户当前指示不执行；需由用户验证系统安装来源授权与覆盖安装。

### 5. 依赖和集成点

- 输入：公开 GitHub API `repos/jaydocoder/PlateView/releases/latest`。
- 输出：`app-release.apk` 的浏览器下载地址。
- 平台：`REQUEST_INSTALL_PACKAGES`、FileProvider、`ACTION_VIEW` 安装 Intent。
- 配置：仅新增 GitHub API Retrofit 客户端，不新增服务器、数据库或密钥。

### 6. 技术选型理由

- GitHub Release 是现有正式 APK 发布渠道，读取最新发行版无需维护额外服务端状态。
- 流式下载写入应用私有缓存目录，无需存储读写权限，并能在弹窗中显示下载进度。
- 安装最终交给 Android 系统确认，符合 Android 12+ 的应用安装流程。

### 7. 关键风险点

- 已安装的旧版本不含更新模块，需要手动安装本次包含更新能力的正式 APK 一次。
- 发行版标签必须递增并包含 `app-release.apk`，否则不会发现更新。
- Android 系统可能要求用户允许“安装未知应用”，应用只能引导用户授权，不能静默安装。
