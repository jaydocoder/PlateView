## 项目上下文摘要（生产真机登录 TLS 诊断）

生成时间：2026-08-07 00:59:21 CST

### 1. 相似实现分析

- **实现 1**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/auth/AuthRuntime.kt`
  - 模式：`AuthRepository` 经注入的 Retrofit `AuthApi` 请求 `POST /auth/login`，成功后将会话写入 DataStore。
  - 可复用：`AuthApi`、`LoginRequest`、`AuthRepository` 与 `AuthSessionProvider`。
  - 约束：网络异常在仓库层原样抛出，不能在 Composable 中直接访问网络。

- **实现 2**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/auth/LoginViewModel.kt`
  - 模式：私有 `MutableStateFlow` 与公开 `StateFlow`，在 `viewModelScope` 中调用仓库并更新不可变状态。
  - 可复用：登录状态更新、加载状态和错误状态的单向数据流结构。
  - 约束：当前将所有失败折叠为同一条文案，真机排障前无法区分 HTTP、DNS 与 TLS 异常。

- **实现 3**：`android/app/src/main/kotlin/com/jaydocoder/plateview/data/network/NetworkModule.kt`
  - 模式：构建期 `BuildConfig.API_BASE_URL` 是 Retrofit 唯一的地址来源。
  - 可复用：`plateviewApiBaseUrl` Gradle 属性和既有 Retrofit 实例。
  - 约束：地址必须为 HTTPS 且以斜杠结尾；已安装 APK 的 DEX 字符串确认该值为正式域名地址。

- **实现 4**：`compose.production.yaml` 与 `deploy/Caddyfile`
  - 模式：Caddy 对外监听 80/443，反向代理到内部网络中的 Ktor API；数据库和 API 不映射公网端口。
  - 可复用：既有 Caddy 自动证书、Docker 网络和服务健康检查。
  - 约束：生产入口的 TLS 问题必须在反向代理和 DNS 层定位，不能通过修改 Android 业务逻辑规避。

### 2. 项目约定

- **命名约定**：Kotlin 标识符使用英文驼峰和 PascalCase；用户可见文案、文档、注释和测试名称使用简体中文。
- **文件组织**：认证特性位于 `feature/auth`，网络层位于 `data/network`，生产编排位于项目根目录与 `deploy`。
- **导入顺序**：沿用 Kotlin 官方格式化规则和现有 Android 导入顺序。
- **代码风格**：Compose 只渲染状态与上送事件，ViewModel 调用仓库，仓库调用 Retrofit。

### 3. 可复用组件清单

- `AuthRepository`：真实登录请求和本机会话写入。
- `LoginViewModel`：登录状态、加载状态和错误状态。
- `NetworkModule`：生产 API 基地址的唯一注入点。
- `AuthFeature.kt`：服务端登录路由与 BCrypt 校验。
- `Caddyfile`：生产 HTTPS 反向代理配置。

### 4. 测试策略

- **测试框架**：JVM 测试使用 JUnit 与协程测试工具；仪器化测试使用 AndroidX 与 Compose 测试框架。
- **现有模式**：`SearchViewModelTest.kt` 使用 Fake 仓库和 `StateFlow` 断言；`VehicleQueryScreenTest.kt` 使用 Compose 语义节点断言；`AdminWorkspaceViewModelTest.kt` 验证 HTTP 失败分支。
- **本次反馈环**：ADB 在已连接 Android 12 真机上启动应用、输入有效凭据、提交登录，然后读取界面语义树；当前每次都显示同一失败文案，且服务器未收到 `/auth/login`。
- **待补测试**：TLS 入口恢复后，用同一 ADB 流程验证登录成功，再执行已存在的车牌查询和详情人工验收；若客户端错误分类改动，则补充 `LoginViewModel` 单元测试。

### 5. 依赖和集成点

```text
Android LoginScreen -> LoginViewModel -> AuthRepository -> Retrofit
-> https://api.plateview.top/auth/login -> Caddy -> Ktor API -> PostgreSQL
```

- **外部依赖**：Retrofit、OkHttp、Caddy、Let’s Encrypt、Cloudflare DNS/代理和阿里云网络。
- **内部依赖**：`BuildConfig.API_BASE_URL`、`AuthApi`、`AuthFeature.kt`、生产 Docker Compose 网络。
- **配置来源**：Android Gradle 属性、服务器 `/opt/plateview/.env`、项目 `Caddyfile` 和 Cloudflare DNS 记录。

### 6. 技术选型理由

- **为什么使用真实真机反馈环**：故障只在真机调用生产地址时出现，单纯的本地单元测试无法覆盖 DNS、TLS 与公网反向代理。
- **优势**：ADB 可无人值守地重复提交登录，界面语义树能断言用户可见症状；服务器日志和抓包可界定请求是否进入 HTTP 层。
- **风险**：TLS 报文及生产日志可能含敏感请求内容，因此仅记录握手状态、HTTP 状态和脱敏结论，不保存负载或凭据。

### 7. 关键风险点

- **网络入口**：Cloudflare 当前返回 HTTP `525`，表明其到源站的 TLS 握手失败。
- **边界条件**：服务端直连本机健康检查正常不等于公网回源正常，必须以外部请求和真机流程验收。
- **性能影响**：调试日志只临时启用并在定位后清除，避免长期增加日志与存储开销。
- **安全考虑**：不在命令、日志、文档或测试夹具中记录密码、令牌、身份证号或车辆资料。

### 8. 上下文充分性检查

- [x] 已定位 Android 到服务器的完整请求链路及输入输出契约。
- [x] 已确认正式 APK 使用正式 HTTPS 地址，不是模拟器默认地址。
- [x] 已确认管理员账号存在且处于启用状态。
- [x] 已建立可重复的真机失败反馈环并知道恢复后的验证步骤。
- [x] 已识别主要风险为 Cloudflare 到 Caddy 的 TLS 握手，不进入业务代码路径。

### 9. 诊断结论更新

- 公共 DNS 已统一解析到生产服务器 IP，手机没有 VPN 或系统 HTTP 代理，服务器内部 HTTPS 健康检查正常。
- 同一 Android 12 真机的 Chrome 可访问 `https://api.plateview.top/health` 并收到 HTTP 200，说明域名、端口、服务器证书名称和手机网络基本可用。
- 使用同一 `BuildConfig.API_BASE_URL` 的 Android 仪器化 OkHttp 探针，无论默认 TLS 配置还是仅 TLS 1.2 配置，都会在 `ConscryptEngineSocket.startHandshake()` 收到 `SocketException: Connection reset`。
- Caddy 对失败连接已选中 `api.plateview.top` 的有效证书，随后报告对端在 HTTP 前复位；服务端不会记录 `/auth/login`，因此账号、密码、数据库和角色校验均不是本次失败原因。
- 当前证据只能确认 Android 原生 TLS 客户端与当前 Caddy 证书回包或中间网络路径存在兼容性问题；尚未能区分复位由终端还是链路中间设备发出，不能将证书链长度作为已证实根因。

### 10. 外部资料核验

- Let’s Encrypt 官方链路说明确认：当前 `YE1` ECDSA 终端证书的默认链为“终端证书 -> YE1 -> Root YE -> ISRG Root X2 -> ISRG Root X1”。
- 官方同时明确：终止于 `ISRG Root X1` 的链兼容性最高；不能简单选择最短链，因为最短的 Gen Y 链会终止于尚未进入主流信任库的 `Root YE`。
- Let’s Encrypt 的 RSA 中间证书 R10 至 R14 直接链向 `ISRG Root X1`。Caddy 官方支持在 `tls` 指令中使用 `key_type rsa2048`，这是当前最小、可回滚且最符合 Android 12 兼容目标的修复候选。

### 11. Cronet 传输替换预研

- **实现 1**：`android/app/src/main/kotlin/com/jaydocoder/plateview/data/network/NetworkModule.kt`
  - 现状：单例 `Retrofit` 是认证、车辆与管理 API 的共同传输入口。
  - 可复用：`Retrofit.Builder`、`BuildConfig.API_BASE_URL` 与 Hilt 单例生命周期。
  - 约束：只可替换 Retrofit 的 `callFactory`，不得改变各 API 接口、DTO 或 Repository 契约。

- **实现 2**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/auth/AuthRuntime.kt`
  - 现状：`AuthRepository` 只依赖 `AuthApi`，登录成功后才写入 DataStore。
  - 可复用：现有认证接口和会话保存路径。
  - 约束：传输实现必须保持 Retrofit 协程调用语义，不能把 Cronet 调用放入 ViewModel 或 Composable。

- **实现 3**：`android/app/src/main/kotlin/com/jaydocoder/plateview/data/vehicle/NetworkVehicleRepository.kt`
  - 现状：仓库只依赖 API 接口，并负责 DTO 到领域模型映射。
  - 可复用：所有业务仓库均可在不改动的情况下使用统一传输层。
  - 约束：包含 Multipart 上传的管理员 API 也必须支持请求体和取消语义。

- **测试模式**：`android/app/src/androidTest/kotlin/com/jaydocoder/plateview/VehicleQueryScreenTest.kt` 使用 AndroidX JUnit；`SearchViewModelTest.kt` 以假仓库隔离网络。TLS 兼容性属于真实设备和生产域名的环境行为，回归测试应位于 `androidTest`，并且不使用账号、令牌或真实车辆数据。

- **官方资料**：Android 开发者文档“Cronet 入门”规定，依赖 `com.google.android.gms:play-services-cronet:18.0.1` 后，必须在创建 `CronetEngine` 前调用 `CronetProviderInstaller.installProvider(context)`。文档没有提供 Retrofit 的官方 `Call.Factory` 适配器，因此在探针通过前不引入或实现生产适配层。

### 12. 充分性检查（Cronet 探针）

- [x] 可清楚定义输入输出：输入为正式匿名 `GET /health`，输出为 HTTP 200 或 Cronet 异常。
- [x] 已确认技术选型：Cronet 使用 Chromium 网络栈，目标是验证与同机 Chrome 一致的 TLS 路径。
- [x] 已识别主要风险：Google Play 服务的 Cronet 提供者不可用、真机网络路径仍注入复位、Gradle JVM 内存不足。
- [x] 已确认验证方式：以 `ProductionApiTlsConnectivityTest` 在已连接 Android 12 真机上执行，15 秒内断言 HTTP 200。

### 13. 当前入口状态与修复边界

- Cloudflare 的 DNS 代理已生效：公网解析已返回 Cloudflare 边缘地址，响应包含 `server: cloudflare` 与 `cf-ray`，但 `/health` 返回 HTTP 525。
- Caddy、Ktor 与 PostgreSQL 在服务器内部保持健康；服务器本地经 `127.0.0.1:443` 的严格证书校验和 `/health` 均返回成功。
- 外部直连阿里云 IP 时，本机 TLS 在接收证书前被断开；真机抓包显示 TLS 回包的 TTL 与正常 ClientHello 不一致，说明公网入口路径存在篡改或异常中间设备。
- 当前 SSH TCP 端口可达，但服务器在密钥交换前关闭连接；显式代理路径则在横幅阶段超时。当前环境没有阿里云命令行控制面，无法在 SSH 不可用时直接修改源站。
- Cronet 曾在首次探针中获得 HTTP 200，但后续同一真机稳定返回 `ERR_CERT_AUTHORITY_INVALID`。这证明客户端替换不能可靠修复入口路径，因此实验性 Cronet 依赖、适配器和探针均已撤回。
- 后续首选：恢复源站 SSH 后，在 Caddy 中部署 Cloudflare Origin Certificate 并保留“完全（严格）”，然后先验证 Cloudflare `/health` 为 200，再验证 Android 原生 Retrofit/OkHttp 健康检查和登录。
