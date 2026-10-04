import { describe, expect, it } from "vitest";
import { canAccessPrimaryAdminModules, canOpenCandidate, rebuildStateLabel, selectWechatMessageCandidatePlate } from "./domain";
import { adaptAdminList, adaptAttachments, errorMessage } from "./adapters";
import { normalizeAdminVehicleStatus } from "./api";

describe("网页端业务纯函数", () => {
  it("只有 admin 管理员可见主管理员模块", () => {
    expect(canAccessPrimaryAdminModules({ username: "admin", role: "ADMIN" })).toBe(true);
    expect(canAccessPrimaryAdminModules({ username: "其他管理员", role: "ADMIN" })).toBe(false);
    expect(canAccessPrimaryAdminModules({ username: "admin", role: "USER" })).toBe(false);
    expect(canAccessPrimaryAdminModules({ username: "普通用户", role: "USER" })).toBe(false);
  });
  it("受限候选不允许打开详情", () => {
    expect(canOpenCandidate({ detailAccessible: false })).toBe(false);
    expect(canOpenCandidate({ detailAccessible: true })).toBe(true);
    expect(canOpenCandidate({})).toBe(true);
  });

  it("按搜索词选择消息车牌并在无匹配时回退", () => {
    const plates = ["新H29102", "新H26927"];
    expect(selectWechatMessageCandidatePlate(plates, "26927")).toBe("新H26927");
    expect(selectWechatMessageCandidatePlate(plates, " h29102 ")).toBe("新H29102");
    expect(selectWechatMessageCandidatePlate(plates, "不存在")).toBe("新H29102");
  });

  it("映射重构状态", () => {
    expect(rebuildStateLabel("REBUILDING")).toBe("等待全量同步");
    expect(rebuildStateLabel(undefined)).toBe("未开始");
  });

  it("将未知管理响应安全转换为列表", () => {
    expect(adaptAdminList({ items: [{ id: 1 }, null], total: 2 }).items).toEqual([{ id: 1 }, {}]);
    expect(adaptAdminList(null).items).toEqual([]);
  });

  it("统一附件类型和缺省文件名", () => {
    expect(adaptAttachments([{ id: 7, attachmentKind: "PDF" }])[0]).toMatchObject({ id: 7, kind: "PDF", fileName: "PDF附件 1" });
  });

  it("保留结构化错误消息并提供降级文案", () => {
    expect(errorMessage(new Error("服务不可用"), "失败")).toBe("服务不可用");
    expect(errorMessage({ message: "未知" }, "失败")).toBe("失败");
  });

  it("规范化车辆状态枚举并拒绝界面文案", () => {
    expect(normalizeAdminVehicleStatus(" inactive ")).toBe("INACTIVE");
    expect(normalizeAdminVehicleStatus("STRICT_CHECK")).toBe("STRICT_CHECK");
    expect(() => normalizeAdminVehicleStatus("失效")).toThrow("车辆状态值无效");
  });
});
