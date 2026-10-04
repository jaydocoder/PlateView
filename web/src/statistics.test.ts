import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getStatistics, getStatisticsHistory } from "./api";
import { flushQueryEvents, pendingQueryEvents, recordVehicleQuery } from "./features/statistics/queryEvents";

beforeEach(() => {
  const storage = new Map<string, string>();
  vi.stubGlobal("localStorage", { getItem: (key: string) => storage.get(key) ?? null, setItem: (key: string, value: string) => storage.set(key, value) });
});
afterEach(() => vi.unstubAllGlobals());
describe("统计协议与查询记录", () => {
  it("使用后端支持的范围并省略空类别", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response("{}")); vi.stubGlobal("fetch", fetcher);
    await getStatistics({ range: "ALL_TIME", category: "", scope: "ME" });
    expect(fetcher.mock.calls[0][0]).toBe("/statistics?range=ALL_TIME&scope=ME");
  });
  it("历史按车牌规范化搜索并传递分页与取消信号", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response("{}")); vi.stubGlobal("fetch", fetcher);
    const signal = new AbortController().signal;
    await getStatisticsHistory({ range: "SEVEN_DAYS", category: "RESIDENT", scope: "ALL" }, " 新H · 12345 ", 20, signal);
    const url = new URL(fetcher.mock.calls[0][0], "http://localhost");
    expect(Object.fromEntries(url.searchParams)).toEqual({ range: "SEVEN_DAYS", scope: "ALL", category: "RESIDENT", query: "新H12345", limit: "20", offset: "20" });
    expect(fetcher.mock.calls[0][1].signal).toBeInstanceOf(AbortSignal);
    expect(fetcher.mock.calls[0][1].signal.aborted).toBe(false);
  });
  it("上报失败保留账号隔离事件，成功重试移除已确认记录", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("断网")));
    await expect(recordVehicleQuery(7, 12)).rejects.toThrow("断网");
    expect(pendingQueryEvents(7)).toHaveLength(1); expect(pendingQueryEvents(8)).toHaveLength(0);
    const event = pendingQueryEvents(7)[0];
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ acceptedEventIds: [event.eventId] }))));
    await flushQueryEvents(7); expect(pendingQueryEvents(7)).toHaveLength(0);
  });
  it("手机局域网 HTTP 没有 randomUUID 时仍可产生有效事件标识", async () => {
    vi.stubGlobal("crypto", { getRandomValues: (bytes: Uint8Array) => bytes.fill(1) });
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("断网")));
    await expect(recordVehicleQuery(9, 13)).rejects.toThrow("断网");
    expect(pendingQueryEvents(9)[0].eventId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
});
