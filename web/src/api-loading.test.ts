import { afterEach, describe, expect, it, vi } from "vitest";
import { getAdminSummary, getAdminVehicles, getAdminUsers, getImports, getAudit, getClientPolicy, getScheduleConfiguration, getScheduleTemplates, getRebuild, getBackups, createAdminUser } from "./api";

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

function pendingFetch() {
  return vi.fn((_path: string, init: RequestInit) => new Promise<Response>((_resolve, reject) => {
    const abort = () => reject(new DOMException("请求已取消", "AbortError"));
    if (init.signal?.aborted) abort();
    else init.signal?.addEventListener("abort", abort, { once: true });
  }));
}

describe("管理读取的等待边界", () => {
  it("所有管理读取接口在挂起时结束等待并允许重试", async () => {
    vi.useFakeTimers(); vi.stubGlobal("fetch", pendingFetch());
    const calls = [() => getAdminSummary(), () => getAdminVehicles(), () => getAdminUsers(), () => getImports(), () => getAudit(), () => getClientPolicy(), () => getScheduleConfiguration(), () => getScheduleTemplates(), () => getRebuild(), () => getBackups()];
    const result = Promise.all(calls.map(call => call().catch(error => error)));
    await vi.advanceTimersByTimeAsync(15_000);
    for (const error of await result) expect(error).toMatchObject({ code: "REQUEST_TIMEOUT", message: "请求超时，请检查网络后重试" });
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ items: [] }), { status: 200 })));
    await expect(getAdminUsers()).resolves.toEqual({ items: [] });
    expect(vi.getTimerCount()).toBe(0);
  });
  it("模块切换可立即取消读取，不伪装成超时", async () => {
    vi.useFakeTimers(); vi.stubGlobal("fetch", pendingFetch());
    const controller = new AbortController();
    const result = getAdminUsers(controller.signal).catch(error => error);
    controller.abort();
    expect(await result).toMatchObject({ name: "AbortError" });
    expect(vi.getTimerCount()).toBe(0);
  });
  it("管理写操作不受十五秒读取期限影响", async () => {
    vi.useFakeTimers();
    let signal: AbortSignal | null | undefined;
    vi.stubGlobal("fetch", vi.fn(async (_path: string, init: RequestInit) => { signal = init.signal; return new Response("{}", { status: 200 }); }));
    await createAdminUser({});
    expect(signal).toBeUndefined();
    expect(vi.getTimerCount()).toBe(0);
  });
});
