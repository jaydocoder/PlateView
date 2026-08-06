# PlateView

PlateView 是用于车辆归属与通行信息核验的 Android 应用及其自建后端。

## 工程结构

- `android/`：Android 12 及以上的 Jetpack Compose 客户端。
- `server/`：Kotlin/Ktor REST API。
- `infra/`：PostgreSQL 初始化脚本。
- `compose.yaml`：本地后端与数据库编排。

## 当前状态

已建立 Android、Ktor、PostgreSQL 与 Docker Compose 工程骨架。车辆、账号、Excel 导入和语音查询功能将按 `task_plan.md` 后续阶段实现。
