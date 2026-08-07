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
