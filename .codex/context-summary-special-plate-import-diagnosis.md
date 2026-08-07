## 项目上下文摘要（特殊车牌 Excel 导入诊断）

生成时间：2026-08-07

### 1. 相似实现分析

- **实现1**：`server/src/main/kotlin/com/jaydocoder/plateview/server/imports/ExcelImportParser.kt`
  - 模式：每行先按分隔符拆分，再以 `PLATE_PATTERN` 抽取候选车牌；没有候选即生成异常导入行。
  - 可复用：`ExcelImportParser` 及现有内存工作簿测试夹具。
  - 注意：模式当前只匹配普通车牌结构，未列出警车和应急车后缀。

- **实现2**：`server/src/main/kotlin/com/jaydocoder/plateview/server/vehicle/PlateNormalizer.kt`
  - 模式：统一大写、移除空白和无语义分隔符，保留汉字、字母与数字。
  - 可复用：特殊后缀在归一化层不会丢失，可作为查询键保存。
  - 注意：归一化成功不等于导入解析规则接受该格式。

- **实现3**：`server/src/test/kotlin/com/jaydocoder/plateview/server/imports/ExcelImportParserTest.kt`
  - 模式：使用 Apache POI 内存工作簿构建单元测试，覆盖普通车牌、多车牌与异常行。
  - 可复用：工作簿夹具和断言风格。
  - 注意：缺少警车、应急车后缀的回归用例。

### 2. 导入链路

Excel 工作表 -> 表头识别与车辆分类 -> 单元格读取 -> 车牌候选提取 -> 行级校验 -> 批次预览 -> 管理员处置 -> 事务发布 -> `vehicles` 查询。

### 3. 测试策略

- 用匿名化的警车与应急车格式构造最小 Excel 工作簿。
- 断言当前解析器产生异常行，作为可重复的红色信号。
- 不修改正式导入代码；临时诊断测试完成后删除。

### 4. 初步风险

- 若只扩展正则而不保留完整特殊后缀，会造成不同车辆使用同一归一化车牌键，违反唯一性约束。
- 发布前需要增加警车、应急车、普通车牌和多车牌单元格的回归覆盖。

### 5. 分页集成分析

- **服务端分页**：`ImportWorkflowService.getBatch(batchId, limit, offset)` 已提供 `1..500` 的分页契约，统计中的 `totalRows` 可作为客户端结束条件。
- **当前缺口**：`AdminApi.getImportBatch` 没有 `limit`、`offset` 查询参数；`AdminWorkspaceViewModel` 仅加载一次；`ImportBatchDialog` 已使用 `LazyColumn`，但没有底部加载事件。
- **复用模式**：车辆档案已经使用 `StateFlow`、页加载状态、`offset`、稳定键和 `snapshotFlow` 近底加载。导入预览将沿用同一模式，不引入分页库。
- **接口约束**：导入预览创建批次时当前直接返回全部行，修复后改为返回首个分页窗口，避免大工作簿一次性传输到移动端。

### 6. 本次验收

- 匿名化警车和应急车后缀在同一单元格可解析为完整、可发布的独立车牌。
- 普通车牌和既有多车牌拆分测试继续通过。
- 导入预览客户端能以页大小和偏移量请求、追加并去重行记录，到达总行数后停止。
- 本轮不部署容器、不上传服务器、不安装新 APK。
