# ADR-005：Android 采用特性分包、MVVM 与单向数据流

- **状态：** 已接受
- **日期：** 2026-08-06

## 背景

App 包含登录、查询、车辆详情、历史记录、车辆管理、账号管理和导入等多项功能，需要避免界面、业务和数据访问相互耦合。

## 决策

Android 使用 Kotlin、Jetpack Compose、Material 3、MVVM、Clean Architecture、Hilt、Navigation Compose、Coroutines 和 Flow。代码按特性组织；每个屏幕的 ViewModel 以 `StateFlow` 输出不可变界面状态，界面只发送事件。Room 仅为搜索历史提供本地数据源。

## 后果

- 界面状态可独立测试，导航与数据访问边界明确。
- 需要为每项特性建立展示、领域和数据层契约。
- 禁止在 Composable 中直接调用 API、数据库或承载业务规则。
