## 项目上下文摘要（雪山启动图标替换）

生成时间：2026-08-08

### 相似实现分析

- `android/app/src/main/AndroidManifest.xml`
  - 应用图标与圆形图标统一引用 `@mipmap/ic_plateview_launcher`。
  - 保持入口名称不变即可同时覆盖系统启动器的两种图标引用。

- `android/app/src/main/res/mipmap-anydpi-v26/ic_plateview_launcher.xml`
  - 使用 Android 自适应图标：背景色与 `@drawable/ic_plateview_launcher_foreground` 前景资源组合。
  - 系统负责不同启动器的圆形或圆角矩形裁切，不另建传统图标资源。

- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchScreen.kt`
  - 首页顶栏通过 `painterResource` 复用同名的前景位图，并在圆形容器内显示。
  - 替换同名文件会同步更新启动器和首页标识。

### 项目约定

- 图标前景放在 `drawable-nodpi`，自适应图标定义保留在 `mipmap-anydpi-v26`。
- 本任务只替换用户提供的位图，不变更 Kotlin、清单、导航或主题代码。
- 正式构建依赖既有的 `android/keystore.properties` 签名配置。

### 测试策略

- 验证资源为有效的正方形 PNG，且资源名称保持不变。
- 执行 `:app:lintRelease` 与 `:app:assembleRelease`。
- 使用 Android 构建工具验证 APK 签名，并检查 APK 内含新的图标资源。

### 依赖与集成点

- 输入：用户提供的 1254 x 1254 PNG。
- 输出：`android/app/src/main/res/drawable-nodpi/ic_plateview_launcher_foreground.png`。
- 集成点：自适应图标 XML、应用清单与首页顶栏。

### 风险与处理

- 不同厂商启动器的裁切形状不同；使用现有自适应图标而不是单独的固定图标，可由系统正确处理。
- 原图为不透明方图，前景层将覆盖默认背景色；这是保留用户完整配色的预期效果。

### 充分性检查

- 是：已识别清单、自适应图标定义和首页复用三个集成点。
- 是：输入输出资源名称、构建命令和签名验证路径明确。
- 是：无需新增运行时依赖或修改应用行为。
