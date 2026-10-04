import { describe, expect, it } from "vitest";
import { adaptAudit, auditActionLabel, auditResultLabel, auditTargetLabel, auditTime } from "./features/admin/audit";
import { visibleAdminModules } from "./features/admin/modules";

describe("管理模块与审计适配", () => {
  it("七卡片与导航共用唯一名称，普通管理员只有四项", () => {
    expect(visibleAdminModules(true).map(item => item.title)).toEqual(["车辆档案", "账号管理", "导入中心", "审计日志", "排班规划", "微信同步", "数据访问控制"]);
    expect(visibleAdminModules(false)).toHaveLength(4);
  });
  it("空响应及异常字段不会直接渲染未知对象", () => {
    expect(adaptAudit(null).items).toEqual([]);
    expect(adaptAudit({ items: [null, { id: 1, actorUsername: null, targetId: null }], summary: { total: -1 }, actors: [null], actionTypes: [null, "LOGIN"] })).toMatchObject({ items: [{ id: 1, actorUsername: "系统", targetId: null }], actors: [], actionTypes: ["LOGIN"], summary: { total: 0 } });
  });
  it("中文动作、目标和失败及拒绝状态", () => {
    expect(auditActionLabel("VEHICLE_UPDATE")).toBe("更新车辆档案");
    expect(auditActionLabel("UNKNOWN")).toBe("其他操作");
    expect(auditTargetLabel("USER")).toBe("账号");
    expect(auditResultLabel("FAILURE")).toBe("失败");
    expect(auditResultLabel("DENIED")).toBe("已拒绝");
    expect(auditTime("2026-10-02T00:00:05Z")).toContain("08:00:05");
    expect(auditTime("错误时间")).toBe("时间未知");
  });
});
