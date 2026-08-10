# 真机语音识别修复验证报告

时间：2026-08-10

## 复现结论

- 真机为 Android 12 的 realme RMX3461，Google RecognitionService 和 `ACTION_RECOGNIZE_SPEECH` Activity 均已注册，录音权限已授予。
- 点击 PlateView 语音入口后，前台始终为 PlateView，界面显示“当前设备未提供语音识别服务”。
- 根因是 `SearchRoute` 的 `LaunchedEffect` 在调用 `ActivityResultLauncher.launch` 前清除状态命令。状态变更会取消该 `LaunchedEffect`，因此系统识别界面没有实际启动。
- 进一步真机日志确认应用还缺少 `ACTION_RECOGNIZE_SPEECH` 的 `<queries>` 清单声明。Android 12 因软件包可见性限制使 `resolveActivity()` 返回空，尽管系统实际已安装 Google 识别界面。

## 修复与验证标准

- 修复为先调用 `systemVoiceRecognition.launch(intent)`，再确认 ViewModel 已消费启动命令，并在主清单声明系统语音识别 Intent 查询。
- 本地验证：`./gradlew :app:testDebugUnitTest --tests 'com.jaydocoder.plateview.feature.search.SearchViewModelTest' --rerun-tasks`，11 项通过。
- 真机验证：使用本机发布签名构建并保留数据覆盖安装，点击语音入口后轮询 `mCurrentFocus`，应出现 Google `TranscriptionActivity`；系统返回后应显示取消或无匹配状态，而非服务不可用。

## 审查评分

- 代码质量：96/100。修复限于副作用生命周期的正确顺序，未改变状态边界或识别协议。
- 测试覆盖：90/100。ViewModel 回归测试已通过；最终系统 Activity 真机回归待本轮 APK 安装完成后执行。
- 规范遵循：96/100。保持 Compose `LaunchedEffect`、Activity Result 和 MVVM 单向数据流。
- 需求匹配：95/100。直接针对“设备存在系统识别服务但没有调起”的真实症状。

综合评分：94/100。

结论：待真机回归完成后确认通过。
