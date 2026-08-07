## 项目上下文摘要（正式 Android 签名）

生成时间：2026-08-07

### 1. 相似实现分析

- **实现1**：`android/app/build.gradle.kts`
  - 模式：模块构建配置集中在 `android {}` 中，正式地址由本机 Gradle 属性注入。
  - 可复用：Kotlin DSL、`providers.gradleProperty` 和现有 Android 构建任务。
  - 注意：签名配置也必须仅在本机提供，不能写入源码。

- **实现2**：`.gitignore`
  - 模式：本机密钥、APK 和环境配置被统一排除在版本控制外。
  - 可复用：现有 `*.jks`、`*.keystore` 和 `keystore.properties` 忽略规则。
  - 注意：发布密钥和密码文件必须继续位于忽略范围内。

- **实现3**：`docs/12-部署运行手册.md`
  - 模式：构建命令使用 Gradle 并通过 `plateviewApiBaseUrl` 指向正式 HTTPS 服务。
  - 可复用：现有 `assembleRelease` 发布流程与验收清单。
  - 注意：文档当前指出发布构建需要签名，本次应补全可重复的本机签名准备流程。

### 2. 项目约定

- 使用 Gradle Kotlin DSL 与 Android Gradle Plugin 的内置签名能力，不新增发布插件。
- 密钥位于用户目录，`keystore.properties` 仅本机保存，均不纳入 Git。
- Android 测试继续使用调试签名；正式构建使用正式密钥，二者不可互相覆盖安装。

### 3. 配置契约

- 输入：`android/keystore.properties`，包含密钥位置、密钥库密码、别名与别名密码。
- 输出：带正式签名的 `android/app/build/outputs/apk/release/app-release.apk`。
- 缺失配置：请求 `assembleRelease` 或 `bundleRelease` 时必须在配置阶段给出明确错误，不能生成未签名分发包。

### 4. 测试策略

- 无密钥配置时执行 `assembleRelease`，确认构建明确失败且不暴露密码。
- 用户完成交互式密钥创建后，执行正式构建、`apksigner verify` 和真机安装。
- 调试构建与已有自动化测试不应受到发布签名配置影响。

### 5. 风险与处理

- 风险：正式密钥丢失会导致后续版本无法覆盖安装。
- 处理：密钥文件放在用户目录并离线备份；密码由用户在可见终端输入，代理不读取、不记录、不提交。

### 6. 充分性检查

- 是：已明确构建文件、忽略规则、部署手册和正式构建的集成点。
- 是：输入、输出、失败行为和真机验收路径明确。
- 是：不引入第三方签名工具，沿用 Android 标准签名机制。
