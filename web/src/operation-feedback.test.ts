import { afterEach, describe, expect, it, vi } from "vitest";
import { createAdminUser, getClientPolicy, testClientEndpoint, updateClientLimits, verifyRebuild } from "./api";
import { dismissFeedback, getFeedback, notify, operationLabel, subscribeFeedback } from "./state/feedback";

afterEach(() => { dismissFeedback(); vi.unstubAllGlobals(); });
const respond = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
describe("统一操作反馈", () => {
  it("保存成功及失败保留具体原因", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond({})));
    await createAdminUser({});
    expect(getFeedback()).toMatchObject({ tone: "success", message: "账号创建成功" });
    vi.stubGlobal("fetch", vi.fn(async () => respond({ message: "版本冲突" }, 409)));
    await expect(createAdminUser({})).rejects.toThrow("版本冲突");
    expect(getFeedback()).toMatchObject({ tone: "error" });
    expect(getFeedback()?.message).toContain("版本冲突");
  });
  it("连接测试的业务失败不显示成功", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond({ success: false, message: "无法连接目标服务" })));
    await testClientEndpoint("api", "http://localhost/");
    expect(getFeedback()).toMatchObject({ tone: "error", message: "无法连接目标服务" });
  });
  it("网络失败可见且读取不打扰", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new Error("网络连接中断"); }));
    await expect(createAdminUser({})).rejects.toThrow();
    expect(getFeedback()?.message).toContain("网络连接中断");
    dismissFeedback();
    vi.stubGlobal("fetch", vi.fn(async () => respond({})));
    await getClientPolicy();
    expect(getFeedback()).toBeNull();
    expect(operationLabel("/auth/web-refresh", "POST")).toBeNull();
    expect(operationLabel("/statistics/events", "POST")).toBeNull();
  });
  it("重构返回失败状态不显示成功", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond({ status: "FAILED" })));
    await verifyRebuild("test");
    expect(getFeedback()).toMatchObject({ tone: "error" });
  });
  it("会话重试仅发一次最终结果", async () => {
    const listener = vi.fn(); const unsubscribe = subscribeFeedback(listener);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(respond({}, 401)).mockResolvedValueOnce(respond({ accessToken: "test" })).mockResolvedValueOnce(respond({})));
    await updateClientLimits({ vehicleResultLimit: 10, workOrderResultLimit: 10, wechatMessageResultLimit: 10 });
    expect(listener).toHaveBeenCalledTimes(1);
    unsubscribe();
  });
  it("关闭提示清理存储并通知订阅者", () => {
    const listener = vi.fn(); const unsubscribe = subscribeFeedback(listener);
    notify("success", "已保存"); dismissFeedback();
    expect(listener).toHaveBeenLastCalledWith(null);
    expect(getFeedback()).toBeNull(); unsubscribe();
  });
});
