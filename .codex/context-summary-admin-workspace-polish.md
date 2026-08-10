## 项目上下文摘要（管理员工作台视觉优化与发行）

生成时间：2026-08-09

### 相似实现分析

- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/admin/AdminWorkspaceScreen.kt`
  - 单一 Compose 工作台承载概览、车辆、账号、导入、审计和编辑对话框。
  - 已使用 `LazyColumn`、稳定键、Material 3 主题令牌和 `PlateViewDimensions`。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/PlateViewTheme.kt`
  - 已提供湖水蓝、松林绿、日照金组成的主题色和统一圆角。
  - 管理页应继续使用 `MaterialTheme.colorScheme`，不能新增硬编码颜色。
- `android/app/src/androidTest/kotlin/com/jaydocoder/plateview/AdminWorkspaceScreenTest.kt`
  - 通过文本与测试标签验证车辆检索、账号显示、导入发布操作。
  - 保持现有测试标签与关键文案可访问。

### 设计决策

- 受众：景区数据管理员；页面唯一目标：快速分辨当前模块、记录状态和下一步操作。
- 方向：景区运营控制台。用主题中的湖水蓝强调当前操作、松林绿表示有效状态、日照金提示待处理；信息采用清晰标题、辅助说明、状态胶囊和低干扰列表卡片。
- 标志性元素：每个管理模块使用带图标和统计值的无边框“运营标题带”，将任务量和主操作固定在首屏。

### 测试策略

- Android JVM 单元测试、Lint、调试及签名正式 APK 构建。
- Android 12 真机验证管理员概览、车辆档案、编辑对话框和导入预览。
- GitHub Actions 推送主分支后构建；推送 `v0.3.2` 标签后创建 GitHub 发行版并验证发行资产下载。

### 风险

- GitHub Packages 不支持面向终端用户的 APK 直接分发，必须使用 GitHub Releases。
- 只调整展示层，不能改变管理员权限、导入发布和车辆分页语义。
