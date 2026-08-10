## 项目上下文摘要（村民车辆候选优先排序）

生成时间：2026-08-09 00:30:00 CST

### 1. 相似实现分析

- **实现 1**：`server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleQueryService.kt`
  - 模式：使用参数化 PostgreSQL 查询完成候选筛选、排序和 20 条上限。
  - 可复用：`SEARCH_VEHICLES`、`VehicleQueryService.search`、`VehicleSearchCandidate`。
  - 注意：精确、前缀、包含匹配是既有排序主规则，不能被类别优先级覆盖。

- **实现 2**：`server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleCategory.kt`
  - 模式：车辆类别以枚举的稳定名称持久化，并提供展示名称。
  - 可复用：`VehicleCategory.RESIDENT`。
  - 注意：数据库 `category` 保存的是枚举名称，排序参数必须使用 `name`。

- **实现 3**：`server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleQueryFeature.kt`
  - 模式：受 JWT 保护的 `/vehicles/search` 直接调用查询服务并映射响应。
  - 可复用：既有响应 DTO 与目录版本接口。
  - 注意：接口契约无需变化，排序调整只影响候选数组顺序。

### 2. 项目约定

- Kotlin 标识符使用英文驼峰，用户可见文本和测试名称使用简体中文。
- SQL 使用多行常量和预编译参数绑定。
- 服务端单元测试使用 `kotlin.test`，位于 `server/src/test/kotlin`。

### 3. 可复用组件清单

- `VehicleCategory.RESIDENT`：村民车辆类别的唯一领域标识。
- `VehicleQueryService.search`：候选搜索入口。
- `SEARCH_VEHICLES`：候选搜索排序的唯一 SQL 实现。

### 4. 测试策略

- 增加类别优先级单元测试，确保村民类别使用最高排序值。
- 运行 `server/gradlew test`，覆盖既有车牌归一化、导入、管理员和应用测试。
- 由于现有服务端测试未提供 PostgreSQL 容器基础设施，SQL 的最终参数绑定由全量编译和既有服务端测试验证。

### 5. 依赖和集成点

- 输入：`GET /vehicles/search?keyword=`。
- 数据源：PostgreSQL `vehicles.category`、`vehicles.normalized_plate`。
- 输出：不变的 `VehicleSearchCandidateResponse` 列表。
- 配置：不新增环境变量、数据库迁移或客户端改动。

### 6. 技术选型理由

- 使用 SQL `CASE` 作为第二排序键，可在返回前完成优先级排列并保持 20 条上限正确。
- 类别优先级位于精确/前缀/包含匹配之后，避免一个模糊村民记录压过精确匹配的其他车辆。

### 7. 关键风险点

- 必须调整预编译参数下标，避免把类别或 `LIMIT` 绑定到错误位置。
- Android 完整目录快照的全量排序不受此接口影响；本次仅改变在线候选返回顺序。
