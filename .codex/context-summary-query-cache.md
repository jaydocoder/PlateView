## 项目上下文摘要（查询缓存）

### 相似实现

- `VehicleQueryService.kt`：普通查询和详情均直接使用 JDBC，现有三元组索引只优化 PostgreSQL 内部执行。
- `SearchHistoryDatabase.kt`：项目已采用 Room、Hilt 模块和账号隔离的本地数据库模式。
- `AdminWorkspaceViewModel.kt`：已有分页、状态流与近底加载模式，可复用到目录同步。

### 决策

- Redis 为服务端可选缓存；连接失败回退 PostgreSQL。
- 数据库目录版本是唯一失效源，管理员写入和批次发布、回滚提交后递增。
- Android 缓存按用户名隔离，目录全量同步、详情按需缓存；前台与网络查询触发版本校验。
- 本轮代码不执行部署。
