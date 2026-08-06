# 阶段 6 验证报告

生成时间：2026-08-06（Asia/Shanghai）

## 需求与交付物映射

| 需求 | 交付物 | 验证结果 |
| --- | --- | --- |
| 统一车牌归一化 | `vehicle/PlateNormalizer.kt`、`PlateNormalizerTest.kt` | 通过 |
| 大小写不敏感与四字符触发 | `VehicleQueryService.kt`、API 边界验证 | 通过 |
| 候选查询与 20 条上限 | `VehicleQueryService.kt`、`VehicleQueryFeature.kt` | 通过 |
| 精确、前缀、包含排序 | `VehicleQueryService.kt` 的 SQL 排序 | 通过 |
| 分类详情 | `VehicleQueryService.kt` 的村民与长期车辆联合查询 | 通过 |
| 查询审计 | `VehicleQueryFeature.kt` 与 `AuditLogWriter` 集成 | 通过 |
| 接口契约与规则同步 | `docs/01`、`02`、`03`、`05`、`08`、`09`、ADR-002 与计划 | 通过 |

## 本地验证结果

| 检查项 | 方法 | 结果 |
| --- | --- | --- |
| 服务端自动测试 | `server/gradlew --no-daemon test` | 通过，包含健康检查、错误响应、导入解析和车牌归一化测试。 |
| API 镜像构建 | Docker 主机网络构建 | 通过。 |
| 数据源准备 | 隔离 Docker Compose 中预览并发布两份真实工作簿 | 通过，村民与四类长期车辆均进入隔离查询库。 |
| 未登录访问 | 未携带令牌调用搜索接口 | 通过，返回 HTTP 401。 |
| 短关键字 | 输入归一化后少于 4 个字符 | 通过，返回 `SEARCH_KEYWORD_TOO_SHORT`。 |
| 大小写搜索与排序 | 以小写完整车牌搜索 | 通过，返回精确匹配的第一条候选。 |
| 候选隐私边界 | 检查候选 JSON 字段 | 通过，仅有 ID、车牌、类别代码和类别名称。 |
| 详情分类资料 | 查询村民与驻景区单位车辆详情 | 通过，分别返回正确的分类资料块。 |
| 空结果与不存在详情 | 搜索无匹配值和请求不存在车辆 ID | 通过，空候选列表与 `VEHICLE_NOT_FOUND` 均正确返回。 |
| 查询审计 | 查询 `audit_logs` 的成功与失败详情事件 | 通过，不读取或输出敏感字段。 |
| 20 条上限 | 隔离库插入 25 条虚构车辆后搜索 | 通过，接口严格返回 20 条。 |
| 响应时间 | 连续 20 次已认证搜索请求 | 通过，全部低于 1 秒。 |
| 工作区空白检查 | `git diff --cached --check` 与 `git diff --check` | 通过，暂存区与工作区均无空白错误。 |

## 已知限制与补偿

- Testcontainers 与本机 Docker API 版本不兼容，因此接口集成验证使用独立 Docker Compose 项目、全新数据卷和 API/SQL 断言替代。
- 第六阶段提供服务端查询能力；Android 搜索框的 250 毫秒防抖、语音输入、候选 UI 和历史记录将在第七阶段实现。
- 真实工作簿只在隔离容器中以无输出方式验证，验证完成后删除容器和数据卷。

## 技术维度评分

| 维度 | 分数 | 结论 |
| --- | ---: | --- |
| 代码质量 | 96/100 | 领域归一化、查询服务、路由 DTO 与审计职责明确，导入和查询共用类别与检索键。 |
| 测试覆盖 | 94/100 | 单元测试和真实工作簿隔离集成验证覆盖正常、边界、权限、空结果、审计和性能；Testcontainers 受环境限制。 |
| 规范遵循 | 97/100 | 遵循 Kotlin、Ktor、PostgreSQL、既有错误响应与审计模式，候选隐私边界符合最新规则。 |

## 战略维度评分

| 维度 | 分数 | 结论 |
| --- | ---: | --- |
| 需求匹配 | 97/100 | 实现最新的 4 字符触发、大小写匹配、车牌与所属类型候选、详情和审计要求。 |
| 架构一致性 | 96/100 | 与现有 JWT、Flyway、导入发布数据模型和 `pg_trgm` 索引一致。 |
| 风险评估 | 96/100 | 处理短输入、停用车辆、隐私字段泄露、排序稳定性和查询上限风险。 |

## 结论

- **综合评分：** 96/100
- **建议：** 通过
- **决策依据：** 自动测试、真实工作簿隔离查询、权限、短输入、大小写、候选隐私、五类详情、审计、上限和性能断言均已完成；可以进入第七阶段 Android 普通用户流程。

## 可重复验证步骤

```bash
cd /home/neo/project/AiProject/codex-ui/PlateView/server
./gradlew --no-daemon test

cd /home/neo/project/AiProject/codex-ui/PlateView
docker build --network host \
  --build-arg HTTP_PROXY=http://127.0.0.1:7890 \
  --build-arg HTTPS_PROXY=http://127.0.0.1:7890 \
  --build-arg NO_PROXY=localhost,127.0.0.0/8,::1 \
  -t plateview-api ./server

# 使用独立 Compose 项目名、端口和仅用于验证的本地环境变量启动。
# 导入两份工作簿并发布后，执行已认证搜索、详情和审计断言。
```
