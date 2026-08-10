## 项目上下文摘要（GitHub Actions APK 自动构建）

生成时间：2026-08-08

### 1. 相似实现分析

- **实现 1**：`android/app/build.gradle.kts`
  - 模式：正式构建通过 `android/keystore.properties` 配置签名，缺少配置时主动终止。
  - 可复用：`assembleRelease`、`assembleDebug`、`:app:testDebugUnitTest`、`:app:lintDebug` Gradle 任务。
  - 约束：正式构建必须提供四个签名属性，且 API 地址由 `plateviewApiBaseUrl` Gradle 属性注入。

- **实现 2**：`android/scripts/create-release-signing.sh`
  - 模式：本机密钥存放在仓库外，签名属性文件不纳入版本控制。
  - 可复用：密钥库格式为 PKCS12，别名默认是 `plateview`。
  - 约束：不能将 `.jks`、`keystore.properties` 或密码写入仓库、日志和构建产物之外的位置。

- **实现 3**：`docs/18-测试执行报告.md`
  - 模式：Android 验证基线为 JVM 单元测试、Lint 和 APK 构建。
  - 可复用：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
  - 约束：调试构建使用明确定义的正式 API 地址，避免 CI 默认模拟器地址。

### 2. 项目约定

- **构建根目录**：`android/`。
- **Java 版本**：17。
- **正式 API 地址**：`https://api.chenxiruyu.dpdns.org/`。
- **提交信息与工作流说明**：使用简体中文。

### 3. 测试策略

- 推送 `main`：执行 JVM 单元测试、Lint、调试 APK 构建并上传调试 APK。
- 推送 `v*` 标签：在上述验证通过后还原 GitHub Secrets 中的签名材料，构建并上传正式 APK，创建 GitHub Release。
- 本地验证：YAML 语法检查、Gradle 配置检查和既有 Android 验证任务。

### 4. 依赖和集成点

- GitHub Actions：`actions/checkout`、`actions/setup-java`、`gradle/actions/setup-gradle`、`actions/upload-artifact`、`softprops/action-gh-release`。
- GitHub Secrets：`PLATEVIEW_KEYSTORE_BASE64`、`PLATEVIEW_STORE_PASSWORD`、`PLATEVIEW_KEY_ALIAS`、`PLATEVIEW_KEY_PASSWORD`。
- GitHub 标签：`v*` 触发正式发布。

### 5. 风险与处理

- Secrets 未配置时，正式发布任务必须失败，不能生成未签名 APK 冒充正式版本。
- GitHub Actions 不能替代真机验证；CI 只负责可重复的构建与静态验证。
- 本工作树存在用户未提交的包装器和审查文件，提交时只能暂存工作流文件。
