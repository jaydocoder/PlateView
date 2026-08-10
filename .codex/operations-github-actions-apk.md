## 编码前检查 - GitHub Actions APK 自动构建

时间：2026-08-08

- 已查阅上下文摘要：`.codex/context-summary-github-actions-apk.md`。
- 将复用 `android/app/build.gradle.kts` 的签名读取与 API 地址注入机制。
- 将复用 `android/scripts/create-release-signing.sh` 的 PKCS12 签名约定。
- 将执行既有 Android 单元测试、Lint 和调试 APK 构建任务。
- 确认不会提交本机密钥、签名属性、用户未提交的包装器与审查文件。
