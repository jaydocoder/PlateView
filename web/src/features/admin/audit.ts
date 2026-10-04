const actions: Record<string, string> = {
  ADMIN_ACCESS: "尝试访问管理功能", AUDIT_LIST: "查看审计日志", IMPORT_LIST: "查看导入批次", IMPORT_PREVIEW: "预览导入数据", IMPORT_VIEW: "查看导入批次", IMPORT_VIEW_DETAIL: "查看导入差异", IMPORT_RESOLUTION: "确认导入差异", IMPORT_PUBLISH: "正式发布导入", IMPORT_ROLLBACK: "撤销导入发布", LOGIN: "登录应用", LOGOUT: "退出登录", USER_LIST: "查看账号列表", USER_CREATE: "创建账号", USER_UPDATE: "更新账号资料", USER_UPDATE_POLICY: "更新升级策略", USER_DATA_ACCESS_UPDATE: "更新数据访问权限", USER_AVATAR_UPDATE: "更新账号头像", USER_AVATAR_DELETE: "删除账号头像", VEHICLE_LIST: "查看车辆档案", VEHICLE_CREATE: "新增车辆档案", VEHICLE_VIEW: "查看车辆详情", VEHICLE_DETAIL_VIEW: "查看车辆详情", VEHICLE_UPDATE: "更新车辆档案", VEHICLE_STATUS_ACTIVE: "设为启用", VEHICLE_STATUS_STRICT_CHECK: "标记严查", VEHICLE_STATUS_BLACKLISTED: "设为拉黑", VEHICLE_STATUS_INACTIVE: "设为失效", VEHICLE_STATUS_DELETED: "删除车辆档案", SCHEDULE_ADMIN_ACCESS: "尝试管理排班", SCHEDULE_CONFIGURATION_UPDATE: "更新排班配置", SCHEDULE_TEMPLATE_APPLY: "应用排班模板", SCHEDULE_TEMPLATE_CREATE: "创建排班模板", SCHEDULE_TEMPLATE_DELETE: "删除排班模板", SCHEDULE_TEMPLATE_UPDATE: "更新排班模板", CLIENT_POLICY_UPDATE: "更新数据访问控制", CLIENT_POLICY_LIMITS_UPDATE: "更新返回数量", CLIENT_POLICY_API_ENDPOINT_UPDATE: "更新服务地址", CLIENT_POLICY_UPDATE_ENDPOINT_UPDATE: "更新升级地址",
};
const targets: Record<string, string> = { AUTH: "认证", AUDIT: "审计日志", IMPORT_BATCH: "导入批次", IMPORT_ROW: "导入记录", SCHEDULE: "排班", SCHEDULE_TEMPLATE: "排班模板", SESSION: "会话", USER: "账号", VEHICLE: "车辆档案", CLIENT_POLICY: "数据访问控制" };
export const auditActionLabel = (value: string) => actions[value] || "其他操作";
export const auditTargetLabel = (value: string) => targets[value] || "管理对象";
export const auditResultLabel = (value: string) => ({ SUCCESS: "成功", FAILURE: "失败", DENIED: "已拒绝" }[value] || "异常");
export const auditTime = (value: string) => { const date = new Date(value); return Number.isNaN(date.getTime()) ? "时间未知" : new Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23" }).format(date); };
export type AuditEntry = { id: number; actorUsername: string; actionType: string; targetType: string; targetId: number | null; resultStatus: string; createdAt: string };
export type AuditPageData = { items: AuditEntry[]; total: number; summary: { total: number; successCount: number; abnormalCount: number; activeActorCount: number }; actors: { id: number; username: string }[]; actionTypes: string[] };
const record = (value: unknown): Record<string, unknown> => value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
const list = (value: unknown) => Array.isArray(value) ? value : [];
const count = (value: unknown) => typeof value === "number" && Number.isFinite(value) ? Math.max(0, value) : 0;
export function adaptAudit(value: unknown): AuditPageData {
  const data = record(value), summary = record(data.summary);
  return {
    items: list(data.items).map(record).filter(item => typeof item.id === "number").map(item => ({ id: Number(item.id), actorUsername: typeof item.actorUsername === "string" ? item.actorUsername : "系统", actionType: String(item.actionType || ""), targetType: String(item.targetType || ""), targetId: typeof item.targetId === "number" ? item.targetId : null, resultStatus: String(item.resultStatus || ""), createdAt: String(item.createdAt || "") })),
    total: count(data.total), summary: { total: count(summary.total), successCount: count(summary.successCount), abnormalCount: count(summary.abnormalCount), activeActorCount: count(summary.activeActorCount) },
    actors: list(data.actors).map(record).filter(actor => typeof actor.id === "number").map(actor => ({ id: Number(actor.id), username: typeof actor.username === "string" ? actor.username : "系统" })), actionTypes: list(data.actionTypes).filter((type): type is string => typeof type === "string"),
  };
}
