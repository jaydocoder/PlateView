## 项目上下文摘要（启动图标替换）

生成时间：2026-08-07

### 1. 相似实现分析

- **实现1**：`android/app/src/main/AndroidManifest.xml`
  - 模式：应用图标和圆形图标统一引用 `@mipmap/ic_plateview_launcher`。
  - 可复用：现有清单与资源名称，无需修改应用入口。
  - 注意：替换资源必须继续保持同名，避免启动器找不到图标。

- **实现2**：`android/app/src/main/res/mipmap-anydpi-v26/ic_plateview_launcher.xml`
  - 模式：Android 自适应图标由背景色与 `@drawable/ic_plateview_launcher_foreground` 前景组合。
  - 可复用：现有自适应图标定义与安全区域裁切机制。
  - 注意：前景图需要透明通道，避免品红背景在系统启动器中显示。

- **实现3**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchScreen.kt`
  - 模式：首页顶栏通过 `painterResource(R.drawable.ic_plateview_launcher_foreground)` 复用同一前景资源。
  - 可复用：现有资源引用与圆形容器。
  - 注意：PNG 资源名称需与原矢量资源一致，才能同时更新首页视觉。

### 2. 项目约定

- Android 模块使用 Kotlin、Compose 和 Material 3；本次仅替换资源，不调整界面状态或业务逻辑。
- 启动器图标资源采用 `mipmap-anydpi-v26` 自适应定义与 `drawable` 前景资源分离。
- 验证使用 Gradle 静态检查与调试包构建，并在已连接 Android 12 真机上覆盖安装。

### 3. 测试策略

- 验证输出为带透明通道的 PNG，四角透明且主体边缘无品红色溢出。
- 执行 `:app:lintDebug` 与 `:app:assembleDebug`。
- 覆盖安装到已连接真机，确认系统可接受 APK。

### 4. 依赖与集成点

- 输入：用户提供的 PNG 图像。
- 图像处理：项目配置的 `codex-imagegen` 环境中的 `remove_chroma_key.py`。
- 输出：`android/app/src/main/res/drawable-nodpi/ic_plateview_launcher_foreground.png`。
- 集成点：自适应图标 XML、`AndroidManifest.xml` 和 `SearchScreen.kt` 的既有资源引用。

### 5. 风险与处理

- 风险：图标资源前景过大或边缘残留品红色。
- 处理：使用边界自动取色、柔化遮罩与去溢色，并在生成后检查透明通道、像素颜色和视觉效果。

### 6. 充分性检查

- 是：已识别三个相似实现及其资源复用关系。
- 是：输入、输出、资源名称和系统集成点明确。
- 是：现有 Gradle 构建与真机安装验证路径明确。
