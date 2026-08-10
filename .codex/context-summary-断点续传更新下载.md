## 项目上下文摘要（断点续传更新下载）

生成时间：2026-08-11 00:35:00 CST

### 1. 相似实现分析

- **实现1**: `android/app/src/main/kotlin/com/jaydocoder/plateview/data/update/GitHubUpdateRepository.kt`
  - 模式：仓库实现网络请求与文件落盘，`AppUpdateRepository` 仅暴露下载契约。
  - 可复用：现有专用 `OkHttpClient`、更新目录、安装包与临时文件命名、进度回调。
  - 需注意：现有实现每次删除临时文件，必须改为保留并在成功后原子改名。

- **实现2**: `android/app/src/main/kotlin/com/jaydocoder/plateview/data/admin/AdminImportFileReader.kt`
  - 模式：在 `Dispatchers.IO` 中按固定缓冲区顺序读写流，并显式处理文件读取失败。
  - 可复用：缓冲读写循环与 I/O 调度边界。
  - 需注意：下载失败时不能清除部分文件，否则无法继续下载。

- **实现3**: `android/app/src/main/kotlin/com/jaydocoder/plateview/data/cache/VehicleCacheSync.kt`
  - 模式：后台 I/O 失败由调用层决定重试，数据组件本身保留可恢复状态。
  - 可复用：失败后不破坏已有状态的恢复原则。
  - 需注意：下载器应保留 `.part` 文件，供用户下一次主动下载恢复。

- **实现4**: `android/app/src/test/kotlin/com/jaydocoder/plateview/feature/update/AppUpdateViewModelTest.kt`
  - 模式：JUnit4 + `runTest` + 测试替身验证更新状态。
  - 可复用：中文测试名称、协程测试与断言风格。
  - 需注意：HTTP 范围请求属于仓库级行为，应使用本地 HTTP 测试服务器而非 UI 测试。

### 2. 项目约定

- **命名约定**：Kotlin 类和函数使用英文驼峰；用户文案、测试名、注释、文档与提交信息使用简体中文。
- **文件组织**：更新网络与下载实现位于 `data/update`，更新状态位于 `feature/update`，领域契约位于 `domain/update`。
- **代码风格**：Kotlin 官方格式；I/O 在 `Dispatchers.IO`；使用 `check`、`require` 与 `runCatching` 处理异常。

### 3. 可复用组件清单

- `AppUpdateRepository`：保持 ViewModel 与下载实现解耦。
- `GitHubUpdateRepository`：保留 GitHub Release 查询、下载目录和专用 HTTP 客户端。
- `UpdateDownloadProgress`：继续用总字节数与已下载字节驱动现有进度界面。
- `AppUpdateViewModelTest`：保持更新状态机测试方式一致。

### 4. 测试策略

- **测试框架**：JUnit4、`kotlinx-coroutines-test`、OkHttp `MockWebServer`。
- **测试模式**：本地 HTTP 集成测试验证请求头、响应码、文件内容与进度；既有 ViewModel 测试回归。
- **覆盖要求**：`206 Partial Content` 续传、服务端忽略 `Range` 后全量回退、`416 Range Not Satisfiable` 后重试、失败后保留部分文件。

### 5. 依赖和集成点

- **外部依赖**：现有 OkHttp `4.12.0`；新增同版本测试专用 `mockwebserver`。
- **内部依赖**：下载器由 `GitHubUpdateRepository` 调用，进度继续传给 `AppUpdateViewModel` 和 Compose 对话框。
- **配置来源**：更新 API 与 APK 地址仍来自 GitHub Release；不改变服务端、域名或安装流程。

### 6. 技术选型理由

- **方案**：HTTP `Range` 请求加持久化 `.part` 临时文件。
- **优势**：无需服务端改动；GitHub Release 支持范围请求；网络中断后可减少重复流量。
- **风险与降级**：服务器若返回 `200` 则安全覆盖为完整文件；返回 `416` 则删除无效断点后仅重试一次完整下载；缓存目录可能被 Android 系统清理，届时自动从头下载。

### 7. 关键风险点

- **一致性**：只在接收完成且长度校验通过后将 `.part` 改名为 APK。
- **边界条件**：空临时文件、未知响应总长度、错误 `Content-Range`、服务器不支持续传与异常 HTTP 状态。
- **性能**：使用现有 8KB 流缓冲，不将 APK 整体读入内存。
- **工具限制**：当前 GitHub CLI 不支持 `search` 子命令，Context7 不可用；使用现有 OkHttp 依赖与本地协议集成测试替代外部资料查询。
