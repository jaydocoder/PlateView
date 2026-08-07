## 项目上下文摘要（车辆档案懒加载与检索）

生成时间：2026-08-07 18:06 CST

### 相似实现分析

- `server/.../AdminManagementFeature.kt`：管理员车辆列表已接受 `keyword`、`limit`、`offset`，但默认仅返回 100 条且响应未包含总数。
- `server/.../AdminManagementService.kt`：车辆按状态、规范化车牌与标识排序，数据库查询已有稳定分页边界。
- `android/.../AdminWorkspaceViewModel.kt`：管理员界面由 `StateFlow<AdminUiState>` 驱动，仓库调用集中在 ViewModel。
- `android/.../SearchViewModel.kt`：首页车牌查询已使用归一化、250 毫秒防抖与 `collectLatest`。
- `android/.../AdminWorkspaceScreenTest.kt`：真机 Compose 测试以 `testTag` 和回调断言验证管理界面交互。

### 设计方案

- 车辆档案新增总数、车牌筛选、100 条分页和滚动接近底部时的下一页加载。
- 每次检索替换当前列表；只有滚动加载才追加，避免重复与请求竞态。
- 首页统一车牌最小匹配长度改为 3 个规范化字符。
- 全局使用冰川青绿、路牌黄、霜白和林墨的 Material 3 配色；车辆档案以总量和检索条组织信息。

### 验证策略

1. 服务端测试与编译验证分页响应契约。
2. ViewModel 单元测试验证搜索重置与下一页追加。
3. Compose 真机测试验证总数、筛选输入与加载更多事件。
4. 构建正式 API 地址 APK，并以 Android 12 真机检查滚动与首页三字符匹配。

### 风险

- 分页响应契约改变后，Android 客户端与服务器必须一起部署。
- 滚动事件必须受加载状态与总数保护，防止重复页请求。

### 本轮补充需求

- 访问令牌与刷新令牌均按 30 天配置，即 `ACCESS_TOKEN_MINUTES=43200`、`REFRESH_TOKEN_DAYS=30`；需重启生产 API 后生效。
