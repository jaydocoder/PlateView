# 项目上下文摘要（移除语音输入）

生成时间：2026-08-10

## 相似实现与复用边界

- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchScreen.kt`：搜索页以 `SearchUiState` 渲染候选、历史与查询反馈；删除语音入口后保留搜索框、候选和历史列表。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchViewModel.kt`：使用 `StateFlow`、防抖归一化与本地缓存优先的查询流；语音识别器是构造参数中的独立依赖，可删除而不影响查询。
- `android/app/src/test/kotlin/com/jaydocoder/plateview/feature/search/SearchViewModelTest.kt`：通过假仓库验证输入、候选和历史；保留手动查询用例，删除只覆盖识别回调的测试替身与用例。
- `android/app/src/androidTest/kotlin/com/jaydocoder/plateview/VehicleQueryScreenTest.kt`：直接调用无状态 `SearchScreen` 测试候选和历史交互；同步移除已失效的语音回调参数。

## 当前约定

- Kotlin、Compose、Hilt、`StateFlow` 与单向数据流。
- 搜索的可验证主路径为手动输入、250 毫秒防抖、车牌归一化、本地候选优先和远程兜底。
- JVM 测试为 JUnit4 与协程测试；界面行为测试使用 Compose 仪器化测试。

## 删除范围

- 删除 `VoiceRecognizer.kt`、录音权限、系统识别 Intent 查询、语音状态、按钮、识别状态条、文案与对应测试。
- 保留 `SearchEvent.OpenVehicle`、前台目录同步、手动搜索、候选、详情导航和历史记录。
- 更新当前产品文档与版本号；历史报告保留事实记录。

## 验收标准

- 搜索页不展示麦克风或语音状态。
- 清单不声明录音权限或系统语音识别查询。
- 项目中不再引用 `VoiceRecognizer`、`VoiceInputFailure`、`RECORD_AUDIO`、`RECOGNIZE_SPEECH` 或 `search_voice_`。
- 搜索 ViewModel 单元测试、Compose 仪器化测试、Lint 与正式签名构建通过。
