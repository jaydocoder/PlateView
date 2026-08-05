---
name: firebase-android
description: Android 项目的 Firebase 能力入口，覆盖 Authentication、Cloud Firestore、Cloud Storage、Cloud Messaging 和 Analytics。
metadata:
  scope: project
  language: zh-CN
---

# Firebase Android 开发

在 Android 项目中使用 Firebase 时，先读取本项目的 `firebase-basics`，并按功能读取 `firebase-auth-basics` 或 `firebase-firestore`。对于 Cloud Storage、Cloud Messaging 和 Analytics，先检查当前 Firebase Android BoM、Google Services 插件版本和 Firebase 官方 Android 文档，再实施。

## 功能路由

- **Firebase Authentication**：读取 `firebase-auth-basics`，使用 Kotlin 协程封装异步结果，并提供可替换的数据源接口。
- **Cloud Firestore**：读取 `firebase-firestore`，先设计集合、文档和索引，再实现仓库与 Flow 数据流。
- **Cloud Storage**：使用 Firebase Android SDK 的 Storage API；上传、下载和进度状态由数据层负责，UI 仅观察状态。
- **Firebase Cloud Messaging**：使用 Firebase Messaging；令牌管理、通知接收和深链接路由与界面层分离，并提供仪器化测试方案。
- **Firebase Analytics**：使用 Firebase Analytics；事件名称和参数集中定义，避免在 Composable 中直接散落事件上报调用。

## 实施约束

1. 不在源码、资源或日志中写入密钥、令牌或生产配置。
2. 所有 Firebase 调用置于数据层或专用服务层，并通过接口向领域层暴露。
3. 使用 Kotlin Coroutine 与 Flow 处理异步数据和生命周期。
4. 使用 Hilt 提供 Firebase 客户端、仓库和测试替身。
5. 对认证、离线、权限拒绝、网络失败和消息跳转分别定义可观察的界面状态。
6. 使用 Firebase Emulator Suite 或可控测试替身验证关键数据路径，避免测试直接依赖生产项目。
