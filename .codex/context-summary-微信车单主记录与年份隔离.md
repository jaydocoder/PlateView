## 项目上下文摘要（微信车单主记录与年份隔离）

生成时间：2026-09-26

### 1. 相似实现分析

- `server/src/main/kotlin/com/jaydocoder/plateview/server/workorder/WorkOrderParser.kt`
  - 已区分结构化车单、简短附件车单、通行消息和普通聊天，并统一提取标准化单号与车牌。
- `server/src/main/kotlin/com/jaydocoder/plateview/server/workorder/WorkOrderService.kt`
  - 消息入库、车单目录、聊天目录、附件双归属和目录修订均集中在同一服务内，可在事务中重算主记录。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/domain/workorder/WorkOrderDisplayFormatter.kt`
  - 已有车单候选按查询词选择匹配车牌的实现，可直接复用于聊天候选。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/data/workorder/WorkOrderCache.kt`
  - Room按账号隔离车单与聊天目录，并在单事务内应用增量更新和墓碑。

### 2. 项目约定

- 服务端使用Flyway顺序迁移，当前最新版本为V48，本任务新增V49。
- 服务端以`catalog_revision`和变更日志驱动Android增量同步。
- Android使用Kotlin、Room、Compose和JUnit4，数据库当前版本为6。
- 车牌查询统一使用`PlateQueryNormalizer`消除空格、中点和大小写差异。

### 3. 可复用组件

- `WorkOrderParser`：提取单号、车牌、结构化字段和解析质量。
- `WorkOrderService.nextCatalogRevision`：生成目录修订。
- `linked_record_id`与`linked_message_id`：同一附件分别归属车单和聊天，不复制文件。
- `RoomWorkOrderRepository.applyCatalogSync`：原子应用车单和聊天变化。

### 4. 测试策略

- 服务端：解析器单元测试、SQL迁移与本地PostgreSQL行为验证。
- Android：格式化单元测试、Room迁移仪器测试、Compose候选展示测试。
- 真机：搜索示例单号和多车牌消息，检查日志与附件复用。

### 5. 关键风险

- 主记录切换必须生成车单墓碑和聊天消息更新，否则客户端会保留重复候选。
- 单号分组必须包含来源和北京时间年份，禁止跨年、跨群误合并。
- 附件重定向必须保留`linked_message_id`，才能在车单和聊天两处复用。
- 旧Room缓存必须通过6到7迁移保留，不能清库规避迁移。

### 6. 其他长期风险车辆权限例外

- `server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleQueryService.kt`
  - 当前搜索、普通目录、全量目录和增量详情都复用“无权限则排除全部其他长期车辆”的条件；详情接口也使用同一条件。
  - 本需求必须拆分“候选可见”和“详情可见”：无其他长期车辆权限时，仅拉黑、严查状态可进入候选和目录，详情接口仍不可访问。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/data/cache/RoomVehicleCacheRepository.kt`
  - 首页始终先查Room，目录记录当前保存完整详情；风险候选若要离线可见，服务端必须下发脱敏目录记录，客户端必须持久化详情可访问标记。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchViewModel.kt`
  - 所有车辆候选点击统一经过`selectCandidate`，适合在保存历史和发送导航事件之前拦截不可访问候选。
- `android/app/src/main/kotlin/com/jaydocoder/plateview/feature/search/SearchScreen.kt`
  - 候选卡片已有拉黑、严查视觉状态；不可访问候选继续显示风险状态，但隐藏详情箭头且点击不执行回调，不增加权限提示。
- `server/src/test/kotlin/com/jaydocoder/plateview/server/vehicle/VehicleAccessScopeTest.kt`
  - 现有测试覆盖权限版本位和村民备注脱敏；新增候选可见与详情可见规则测试可沿用该纯单元测试模式。

#### 协议与集成点

- 服务端候选及目录详情新增`detailAccessible`布尔字段；无权限风险车辆为`false`，其他车辆为`true`。
- 服务端完整目录对无权限风险车辆只保留编号、车牌、类别和状态，车辆类型、属性、人员及单位详情均不下发。
- 风险状态恢复为普通状态后，增量目录通过既有`REVOKE`墓碑移除无权限用户的本地候选。
- Android车辆缓存数据库从8升级到9，旧记录默认可访问；新同步记录按服务端字段覆盖。
- 详情接口继续使用严格权限过滤，客户端拦截只负责交互，不能替代服务端边界。

#### 充分性检查

- 接口契约明确：候选及目录返回`detailAccessible`，详情接口响应规则不变。
- 技术选型明确：复用现有目录同步和墓碑，不建立第二套风险车辆接口。
- 风险已识别：防止完整详情泄露、状态恢复后残留候选、旧缓存绕过点击限制。
- 验证方式明确：服务端规则与数据库行为、Android ViewModel、Room 8到9迁移、Compose点击和真机定向测试。
