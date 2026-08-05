## 项目上下文摘要（Android 开发 Skills 配置）

生成时间：2026-08-05

### 1. 相似实现分析

- **实现 1**：`https://github.com/android/skills/navigation/navigation-3/SKILL.md`
  - 模式：以独立 Skill 目录承载入口文件和按需加载的引用资料。
  - 可复用：导航、Hilt、ViewModel、多返回栈和深链接配方。
  - 注意：该 Skill 面向 Navigation 3，同时保留 Navigation Compose 类型安全迁移资料。

- **实现 2**：`https://github.com/android/skills/testing/testing-setup/SKILL.md`
  - 模式：先分析当前测试栈，再按单元、Compose UI 与仪器化测试分层配置。
  - 可复用：Hilt 测试、Compose Test、Robolectric、Room 内存数据库测试准则。
  - 注意：当前项目尚无 Android 工程，Skill 仅提供未来实施规范，不修改构建文件。

- **实现 3**：`https://github.com/anhvt52/jetpack-compose-skills/modern-jetpack-compose/SKILL.md`
  - 模式：按功能按需读取状态、副作用、重组、导航、设计和性能参考资料。
  - 可复用：Material 3、MVVM、StateFlow 和单向数据流实践。
  - 注意：避免引入未经项目确认的第三方依赖。

### 2. 项目约定

- **Skills 位置**：本任务采用项目本地 `.agents/skills/`，不写入全局 Skills 目录。
- **命名约定**：保留上游 Skill 目录名；项目总 Skill 使用 `android-development`。
- **文件组织**：每个 Skill 目录以 `SKILL.md` 为入口，并完整保留上游引用资料。
- **代码风格**：本次只配置 Markdown 与 Skills，不创建 Android 业务代码。

### 3. 可复用组件清单

- Android 官方 Skills：Compose 自适应与 Material 样式、Navigation 3、测试、R8、Perfetto。
- Google Skills：`firebase-basics`，作为 Firebase 官方接入入口。
- Firebase Agent Skills：认证、Firestore、Storage、Cloud Messaging、Analytics 的产品级资料。
- Compose 增强 Skill：`modern-jetpack-compose`。

### 4. 测试策略

- **验证方式**：检查每个预期目录的 `SKILL.md`、引用文件数量、来源清单和本地 Skills 发现命令输出。
- **未来测试框架**：根据 Android 官方 `testing-setup`，采用 Kotlin 单元测试、Compose UI 测试和 Android 仪器化测试。
- **当前限制**：项目尚无 Gradle 工程或测试源码，因此不运行 Android 构建或测试命令。

### 5. 依赖和集成点

- **外部来源**：`android/skills`、`google/skills`、`firebase/agent-skills`、`anhvt52/jetpack-compose-skills`。
- **安装器**：`npx skills` 支持项目级安装；Android 与 Compose 来源使用其上游目录内容进行确定性复制。
- **Codex 发现位置**：当前会话提供的项目约定目录为 `.agents/skills/`；新 Skills 将在下一轮任务中自动发现。

### 6. 技术选型理由

- 使用项目目录避免污染用户全局配置，并可随项目一同版本化。
- 使用上游完整目录而非仅复制入口文件，以保留 Skill 所需参考资料。
- Firebase 采用官方基础入口与 Firebase 官方 Agent Skills 的产品级资料，覆盖目标产品范围。

### 7. 关键风险点

- GitHub API 匿名请求返回 HTTP 403，已改用 Git 稀疏检出读取来源，安装前将记录该替代措施。
- Android 官方仓库没有单一的通用 Compose Skill，使用其 `adaptive` 与 `theming/styles` 组合，并由指定 Compose 增强 Skill 补齐状态和性能实践。
- Skill 目录不等于 Firebase 项目初始化；不会执行登录、创建云项目或写入任何应用配置。

### 8. 上下文充分性检查

- 是：已识别三项相似实现和来源路径。
- 是：理解 Skill 的目录入口与引用文件模式。
- 是：可复用上游 Skill 目录及 `npx skills` 项目级安装器。
- 是：本任务文件使用 Markdown，遵循中文说明和项目本地目录约定。
- 是：可通过文件清单、元数据和本地 Skills 列表验证。
- 是：已确认项目内不存在重复的 Skills 安装。
- 是：已确认集成点为 `.agents/skills/` 和 Codex 的下一轮 Skill 发现流程。
