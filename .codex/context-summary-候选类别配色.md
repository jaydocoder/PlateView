## 项目上下文摘要（候选类别配色）

生成时间：2026-08-10 23:45:00 CST

### 1. 相似实现分析

- **实现 1**：`android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchScreen.kt`
  - 模式：`VehicleCandidateRow` 使用 `VehicleCandidate.category` 和 `categoryLabel` 呈现候选车辆类别，点击卡片进入原有详情流程。
  - 可复用：候选列表稳定键、`ElevatedCard`、`PlateViewDimensions` 与类别文本。
  - 注意：只允许调整类别文本视觉，不得影响车牌、点击、排序和查询结果。

- **实现 2**：`android/app/src/main/kotlin/com/jaydocoder/plateview/PlateViewTheme.kt`
  - 模式：湖水蓝 `primary`、松林绿 `secondary`、暮紫 `tertiary` 和中性前景色由 Material 3 主题集中提供。
  - 可复用：颜色角色而非硬编码颜色。
  - 注意：浅色和深色主题均需保持可读性，不能引入高饱和警示色。

- **实现 3**：`server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleCategory.kt`
  - 模式：车辆类别为 `RESIDENT`、`SCENIC_UNIT`、`SCENIC_ENTERPRISE`、`CADRE`、`KANAS_TOURISM_DEVELOPMENT` 五个固定值。
  - 可复用：已有稳定类别编码。
  - 注意：客户端仅按字符串做展示映射，不能新建服务端协议或改变类别语义。

- **实现 4**：`android/app/src/androidTest/kotlin/com/jaydocoder/plateview/VehicleQueryScreenTest.kt`
  - 模式：Compose 测试使用候选夹具与语义文本断言验证查询结果展示和点击。
  - 可复用：`SearchScreen` 测试装配、`VehicleCandidate` 夹具和类别文本断言。
  - 注意：测试需覆盖五类候选，确保各颜色映射分支均能进入界面。

### 2. 项目约定

- Compose 界面使用 Material 3 主题颜色，不在功能组件中硬编码色值。
- 候选视觉映射属于展示层，不添加 ViewModel 状态或服务端数据字段。
- 用户可见文本、测试名称、文档与提交信息使用简体中文。

### 3. 可复用组件清单

- `VehicleCandidateRow`：候选卡片布局、点击回调与类别文案位置。
- `PlateViewTheme`：主题颜色角色和深浅主题适配。
- `VehicleCategory`：五类固定类别编码。
- `VehicleQueryScreenTest`：候选 UI 回归测试模式。

### 4. 测试策略

- Compose：一次装配五类候选，确认每个类别文案存在；保留村民候选点击详情的回归测试。
- 构建：运行调试单元测试、调试 Lint、调试与仪器化测试 APK、发布单元测试、发布 Lint 和正式签名构建。
- 真机：从官方 `v0.3.8` 启动后验证 `v0.3.9` 更新入口、弹框和“稍后处理”状态。

### 5. 依赖和集成点

- 输入：`VehicleCandidate.category`、`VehicleCandidate.categoryLabel`。
- 输出：候选卡片类别文本的主题前景色。
- 发布：将远端回滚后的 `main` 更新至 `v0.3.9 / versionCode 12`，通过 GitHub Actions 生成正式 APK。

### 6. 技术选型理由

- 使用现有主题角色可避免色差突兀，并自动适应深色模式。
- 仅通过私有 Compose 辅助函数映射五个既有类别，不引入依赖、配置或数据同步。
- 村民车辆使用松林绿，景区单位使用湖水蓝，景区企业使用暮紫，干部车辆使用中性青灰，旅游发展公司使用湖水蓝，能在紧凑列表中形成克制但可扫描的区分。

### 7. 关键风险点

- 未知类别必须回退到 `onSurface`，保证不会因新增数据造成低对比文本。
- 网络可达性仍决定 GitHub Release 更新检查能否在真机完成；类别配色本身不涉及服务端。
