## 项目上下文摘要（首字符实时匹配与首页视觉改造）

生成时间：2026-08-07 19:12 CST

### 相似实现分析

- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchViewModel.kt`：已有归一化、250 毫秒防抖和 `collectLatest`，适合将查询门槛降为首个有效字符。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchScreen.kt`：已有 Material 3 `LazyColumn`、稳定候选键和语音输入入口，适合重构视觉而不改变数据流。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/PlateViewTheme.kt`：集中定义全局色彩、字体和形状令牌，适合让登录、首页、详情和管理台统一更新。
- `android/app/src/test/kotlin/com/jaydocoder/plateview/feature/search/SearchViewModelTest.kt`：已有归一化、短输入、语音输入与候选断言模式。

### 项目约定与复用

- 展示层保持 `StateFlow` 到无状态 Compose 回调的数据流，不在 Composable 内触发网络请求。
- 车牌搜索沿用客户端与服务端相同的归一化和候选数量上限 20。
- 视觉继续使用 Material 3 色彩角色与项目尺寸令牌，不引入第三方设计依赖。

### 实施与验证策略

- 将客户端和服务端最小查询长度同步为 1，空归一化结果不请求服务。
- 用湖水蓝、松林绿、日照金、暮紫与云雾白建立更丰富的主题；首页强化实时候选层级与圆角触感。
- 运行 Android JVM 测试、Ktor 测试、Android 12 真机 Compose 测试、Lint、正式地址 APK 构建及生产健康检查。
- 图像生成接口认证通过，但返回体缺少脚本要求的 Base64 图像字段；不修改生图脚本，不将不存在的图像引用到应用中。
