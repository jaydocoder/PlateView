import { afterEach, describe, expect, it, vi } from "vitest";
import { searchHome } from "./api";

afterEach(() => vi.unstubAllGlobals());

describe("微信首页查询权限隔离", () => {
  it("明确无微信权限时返回空分组而不拒绝普通车辆查询", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "WORK_ORDER_PERMISSION_DENIED", message: "当前账号没有微信车单访问权限" }), { status: 403 })));
    await expect(searchHome("123")).resolves.toEqual({ workOrderCandidates: [], wechatMessages: [] });
  });

  it.each([
    [403, "OTHER_PERMISSION_DENIED"],
    [500, "INTERNAL_SERVER_ERROR"],
  ])("其他错误不能被当作缺少微信权限：状态 %s", async (status, code) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code, message: "实际接口错误" }), { status })));
    await expect(searchHome("123")).rejects.toMatchObject({ status, code, message: "实际接口错误" });
  });

  it("网络异常仍可被界面发现", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("网络不可达")));
    await expect(searchHome("123")).rejects.toThrow("网络不可达");
  });
});
