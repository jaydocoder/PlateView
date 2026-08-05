## Android 开发 Skills 配置

时间：2026-08-05

### 工具链记录

- `sequential-thinking` 和 `shrimp-task-manager` 未在当前环境提供，已以人工结构化分析和本地检查替代。
- `desktop-commander` 未在当前环境提供，已以受限的本地终端只读检查替代。
- GitHub API 匿名树查询返回 HTTP 403；已改用 Git 远程引用和稀疏检出，成功获得所需上游目录清单。
- 上游安装器首次通过 `python` 调用失败，因为系统未提供该命令别名；将使用同一脚本的 `python3` 入口重试。

## 编码前检查 - Android 开发 Skills 配置

时间：2026-08-05

- 已查阅上下文摘要文件：`.codex/context-summary-android-development-skills.md`
- 将使用以下可复用组件：
  - Android 官方 Skills：导航、测试、性能、性能分析和 Compose 指引。
  - Firebase 官方 Skills：Firebase 基础入口与产品级 Agent Skills。
  - Compose 增强 Skill：现代 Compose 状态、Material 3 和性能实践。
- 将遵循命名约定：保留上游目录名，项目总 Skill 使用 `android-development`。
- 将遵循代码风格：仅编写简体中文 Markdown 说明，不创建业务代码。
- 确认不重复造轮子：项目内未发现 `.agents/skills/`、`SKILL.md` 或既有 Android 配置。

## 编码后声明 - Android 开发 Skills 配置

时间：2026-08-05

### 1. 复用了以下既有组件

- Android 官方 Skills：Compose、导航、测试、R8 和 Perfetto 的上游内容。
- Google 与 Firebase 官方 Skills：Firebase 基础入口、Authentication 与 Firestore 的上游内容。
- Compose 增强 Skill：现代 Compose 的上游内容和引用资料。

### 2. 遵循的项目约定

- 文件组织：所有项目 Skills 位于 `.agents/skills/`，工作记录位于 `.codex/`。
- 命名约定：上游目录名保持不变，项目总 Skill 采用 `android-development` 与 `firebase-android`。
- 语言：新建说明文件和 Skill 使用简体中文。

### 3. 对比的相似实现

- Android `navigation-3`：沿用入口文件加引用资料目录的组织方式。
- Android `testing-setup`：沿用测试分层和按需配置的实践。
- `modern-jetpack-compose`：沿用按功能加载参考资料的 Compose 工作流。

### 4. 未重复造轮子的证明

- 已检查项目根目录和 `.agents/skills/` 的既有内容，安装前不存在同名能力。
- 项目总 Skill 仅负责聚合与路由，不复制上游实现或 Android SDK 文档。

## GitHub 连接修复尝试

时间：2026-08-06

- 已确认 ShellCrash 生成配置中的 `DOMAIN,ssh.github.com,DIRECT` 是 SSH 连接中断的直接规则来源。
- 已在 `/home/neo/.codex/config.toml` 的 GitHub MCP 配置中加入 HTTP、HTTPS 和本地排除代理变量，并将其传入 Docker 容器。
- 配置文件检查通过；当前任务绑定的 GitHub MCP 进程未热重载，新变量将在重连 MCP 或重启 Codex 后生效。
- ShellCrash 持久化规则位于仅管理员可写的 `/etc/ShellCrash`；当前环境未配置图形化 `sudo` 认证程序，未对其进行修改或重启服务。
