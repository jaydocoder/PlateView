## 项目上下文摘要（生产服务器部署）

生成时间：2026-08-06

### 1. 相似实现分析

- **实现 1**：`compose.yaml`
  - 模式：以 PostgreSQL 与 Ktor API 组成开发环境，API 启动时执行 Flyway 迁移。
  - 可复用：镜像构建、环境变量名、数据库健康检查与持久卷。
  - 约束：当前将 PostgreSQL 和 API 映射至主机端口，只可用于本地开发，不能作为公网生产编排。

- **实现 2**：`server/Dockerfile`
  - 模式：多阶段构建，使用 JDK 17 构建 Ktor 发行包，JRE 17 运行。
  - 可复用：现有镜像构建流程，不新增应用运行时或构建系统。
  - 约束：服务器只有约 1.6GiB 内存且无交换空间，构建前必须补足内存缓冲或采用预构建镜像。

- **实现 3**：`android/app/build.gradle.kts` 与 `data/network/NetworkModule.kt`
  - 模式：构建期 Gradle 属性写入 `BuildConfig.API_BASE_URL`，Retrofit 仅从该常量读取地址。
  - 可复用：`plateviewApiBaseUrl` 属性，不修改网络模块。
  - 约束：正式构建必须指定 `https://api.plateview.top/`，并保留尾部斜杠。

### 2. 项目约定

- **部署目录**：生产配置必须版本化保存，真实 `.env` 仅保存在服务器。
- **服务边界**：PostgreSQL、Ktor API 与反向代理使用 Docker Compose；Flyway 随 API 启动执行。
- **数据边界**：真实 Excel、数据库卷、备份、签名密钥、密码和令牌不进入 Git、日志或部署报告。
- **文档语言**：文档、操作记录、测试结论和 Git 提交信息均使用简体中文。

### 3. 可复用组件清单

- `compose.yaml`：数据库、API、环境变量和健康检查的来源。
- `server/Dockerfile`：Ktor 正式运行镜像。
- `.env.example`：生产环境变量键名来源。
- `docs/12-部署运行手册.md`：启动、备份、恢复与 Android 构建流程。
- `android/app/build.gradle.kts`：正式 API 地址注入点。

### 4. 测试策略

- 本地：执行 `docker compose -f compose.production.yaml config` 验证编排与变量引用；构建 Ktor 镜像。
- 服务器：检查 Docker Compose 配置、容器状态、健康端点、日志、数据库卷和端口监听。
- 公网：验证 `https://api.plateview.top/health` 经过 Cloudflare 到达反向代理，并检查证书。
- Android：以正式 HTTPS 地址构建调试 APK，在 Android 12 真机验证登录和查询。

### 5. 依赖和集成点

- **外部依赖**：Cloudflare DNS/代理、阿里云安全组、Docker、Docker Compose、GitHub、Let’s Encrypt、Android Gradle。本机需为服务器提供 Git 归档与镜像归档。
- **服务器**：SSH 别名 `aliyun`，Docker 与 Compose 可用，已有 `mysql80` 容器必须隔离。GitHub HTTPS 拉取已在 20 秒内超时，源码采用本机 Git 归档经 SSH 上传。
- **域名**：`api.plateview.top` 已解析至 Cloudflare；当前 HTTP 返回 `521`，等待原站 80/443 服务。
- **配置来源**：服务器 `/opt/plateview/.env`（计划路径，未提交）。

### 6. 技术选型理由

- 使用 Caddy 作为 Compose 服务提供 HTTPS 和反向代理，避免新增独立系统服务，配置与应用一同版本化。
- 使用独立生产 Compose 文件，避免开发环境的 PostgreSQL/API 端口暴露规则进入生产。
- 使用既有 Dockerfile、Flyway 和 Gradle API 地址属性，保持构建与运行路径一致。

### 7. 关键风险点

- 阿里云安全组若未开放 80/443，Cloudflare 会持续返回 `521`。
- Cloudflare SSL 模式需使用“完全（严格）”，否则可能出现回源 TLS 或重定向问题。
- 服务器无交换空间，镜像构建可能因内存不足失败。
- 服务器 GitHub HTTPS 出站请求当前超时，部署不能依赖服务器自行拉取代码。
- 服务器 Docker Registry 出站请求当前超时，部署不能依赖服务器自行拉取 Caddy 镜像。
- Cloudflare 橙云代理当前对源站返回 `525`，但服务器公网地址直连的 Let’s Encrypt TLS 和健康端点已通过。正式真机网络验收依赖 Cloudflare 回源规则生效或 API 记录切换为灰云。
- 生产密钥和真实 Excel 必须避免进入 Git、命令输出和操作记录。

### 8. 充分性检查

- [x] 可定义部署输入输出：输入为 Git 提交、服务器 `.env` 和域名；输出为 HTTPS 健康端点与正式 Android API 地址。
- [x] 理解技术选型：现有 Docker Compose、Ktor、PostgreSQL 与构建期 API 地址注入是唯一正式路径。
- [x] 已识别主要风险：Cloudflare 回源、阿里云安全组、内存、GitHub 拉取和生产密钥。
- [x] 知道验证方式：Compose 配置检查、镜像构建、SSH 容器检查、HTTPS 健康检查和 Android 构建。

### 9. Android 正式地址构建阻断

- **反馈循环**：`android/gradlew :app:assembleDebug -PplateviewApiBaseUrl=https://api.plateview.top/` 能稳定复现 Compose 编译失败。
- **相似实现**：`AdminWorkspaceScreen.kt` 的 `EditorTextField` 已使用 Material 3 的 `focusedContainerColor`、`unfocusedContainerColor`；`PlateViewTheme.kt`、`SearchScreen.kt` 和 `VehicleDetailScreen.kt` 已导入 `androidx.compose.ui.unit.sp`。
- **根因**：美化提交遗漏 `sp` 导入，并将已不适用的 `containerColor` 传给当前 Material 3 的 `TextFieldDefaults.colors`。
- **修复与验证**：补齐单位导入，并拆分为当前 API 的 focused、unfocused、disabled 容器颜色；相同的正式地址 APK 构建命令是本缺陷的回归验证。
