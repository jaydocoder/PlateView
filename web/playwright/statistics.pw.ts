import { test, expect, type Page } from "@playwright/test";

const summary = { range: "TODAY", summary: { totalQueries: 42, distinctPlates: 3, activeUsers: 1 }, trend: [], categories: [{ category: "RESIDENT", queryCount: 24 }, { category: "SCENIC_UNIT", queryCount: 18 }], topPlates: [{ plateNumber: "新H12345", queryCount: 24 }, { plateNumber: "新H23456", queryCount: 18 }] };
const items = Array.from({ length: 42 }, (_, index) => ({ vehicleId: index + 1, plateNumber: index % 2 ? "新H23456" : "新H12345", category: "RESIDENT", occurredAtEpochMillis: Date.UTC(2026, 9, 2, 13, 21) - index * 60000 }));
async function setup(page: Page, username = "普通管理员", role = "ADMIN") {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 3, username, role } }));
  await page.route("**/statistics?*", route => route.fulfill({ json: summary }));
  await page.route("**/statistics/events?*", route => {
    const params = new URL(route.request().url()).searchParams;
    const query = params.get("query"); const offset = Number(params.get("offset"));
    const matching = query ? items.filter(item => item.plateNumber.includes(query)) : items;
    return route.fulfill({ json: { items: matching.slice(offset, offset + 20), total: matching.length } });
  });
  await page.route("**/statistics/events", async route => route.fulfill({ json: { acceptedEventIds: route.request().postDataJSON().events.map((item: { eventId: string }) => item.eventId) } }));
  await page.route("**/vehicles/1", route => route.fulfill({ json: { id: 1, plateNumber: "新H12345", category: "RESIDENT", status: "ACTIVE" } }));
  await page.goto("statistics");
  await expect(page.locator(".statistics-history-row").first()).toBeVisible();
}

test("统计条件与布局匹配应用，搜索仅过滤历史并支持清空", async ({ page }) => {
  await setup(page);
  await expect(page.getByRole("button", { name: "全员统计", exact: true })).toHaveCount(0);
  await expect(page.locator(".statistics-column")).toHaveCount(6);
  await expect(page.getByLabel("喀旅公司车辆：0次")).toBeVisible();
  await expect(page.locator(".statistics-history-row").first()).toContainText("2026/10/02 21:21");
  for (const [label, range] of [["近7天", "SEVEN_DAYS"], ["近30天", "THIRTY_DAYS"], ["全部时间", "ALL_TIME"], ["今天", "TODAY"]]) {
    const request = page.waitForRequest(req => new URL(req.url()).pathname === "/statistics" && new URL(req.url()).searchParams.get("range") === range);
    await page.getByRole("button", { name: label, exact: true }).click(); await request;
    await expect(page.locator(".statistics-history-row").first()).toBeVisible();
  }
  const categoryRequest = page.waitForRequest(req => new URL(req.url()).pathname === "/statistics" && new URL(req.url()).searchParams.get("category") === "RESIDENT");
  await page.getByLabel("车辆类别").selectOption("RESIDENT"); await categoryRequest;
  await expect(page.locator(".statistics-overview")).toHaveCount(0);
  await page.getByLabel("车辆类别").selectOption("");
  await expect(page.locator(".statistics-overview")).toBeVisible();
  await page.getByRole("textbox", { name: "搜索历史车牌" }).fill("23456");
  await expect(page.locator(".statistics-overview")).toHaveCount(0);
  await expect(page.locator(".statistics-history-row").first()).toContainText("新H23456");
  await page.getByRole("button", { name: "清空历史车牌搜索" }).click();
  await expect(page.locator(".statistics-overview")).toBeVisible();
  await page.getByRole("textbox", { name: "搜索历史车牌" }).fill("不存在");
  await expect(page.getByText("未找到匹配的查询记录")).toBeVisible();
});

test("下滑分页、记录详情和排行详情导航", async ({ page }) => {
  await setup(page);
  await page.locator(".statistics-sentinel").scrollIntoViewIfNeeded();
  await expect(page.locator(".statistics-history-row")).toHaveCount(40);
  await page.locator(".statistics-sentinel").scrollIntoViewIfNeeded();
  await expect(page.locator(".statistics-history-row")).toHaveCount(42);
  await page.locator(".statistics-history-row").first().click();
  await expect(page).toHaveURL(/vehicle\/1$/);
  await expect(page.locator(".vehicle-detail-cover")).toBeVisible();
  await page.goBack();
  await expect(page.locator(".statistics-ranking")).toBeVisible();
  await page.locator(".statistics-ranking button").first().click();
  await expect(page).toHaveURL(/vehicle\/1$/);
});

test("只有主管理员可切换全员，普通用户不显示范围控件", async ({ page }) => {
  await setup(page, "admin");
  const request = page.waitForRequest(req => new URL(req.url()).pathname === "/statistics" && new URL(req.url()).searchParams.get("scope") === "ALL");
  await page.getByRole("button", { name: "全员统计" }).click(); await request;
  await expect(page.getByRole("button", { name: "全员统计" })).toHaveAttribute("aria-pressed", "true");
  await setup(page, "查询人员", "USER");
  await expect(page.getByRole("group", { name: "统计范围" })).toHaveCount(0);
});

test("失败重试、无数据和多尺寸无溢出", async ({ page }) => {
  await setup(page);
  for (const width of [320, 375, 390, 430, 768, 1280, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: test.info().outputPath("统计首屏.png") });
  await page.screenshot({ path: test.info().outputPath("统计页面.png"), fullPage: true });
  await page.route("**/statistics?*", route => route.fulfill({ status: 500, json: { message: "统计服务暂不可用" } }));
  await page.getByRole("button", { name: "近7天" }).click();
  await expect(page.getByRole("alert")).toContainText("统计服务暂不可用");
  await page.route("**/statistics?*", route => route.fulfill({ json: { ...summary, summary: { totalQueries: 0, distinctPlates: 0, activeUsers: 0 } } }));
  await page.getByRole("button", { name: "重新加载" }).click();
  await expect(page.getByText("当前条件下还没有查询记录")).toBeVisible();
});

test("分页失败保留已有记录且可重试", async ({ page }) => {
  await setup(page);
  await page.route("**/statistics/events?*", route => {
    const offset = Number(new URL(route.request().url()).searchParams.get("offset"));
    return offset ? route.fulfill({ status: 500, json: { message: "下一页加载失败" } }) : route.fulfill({ json: { items: items.slice(0, 20), total: 42 } });
  });
  await page.locator(".statistics-sentinel").scrollIntoViewIfNeeded();
  await expect(page.getByRole("alert")).toContainText("下一页加载失败");
  await expect(page.locator(".statistics-history-row")).toHaveCount(20);
  let releaseTail!: () => void;
  const tailReady = new Promise<void>(resolve => { releaseTail = resolve; });
  await page.route("**/statistics/events?*", async route => { const offset = Number(new URL(route.request().url()).searchParams.get("offset")); if (offset >= 40) await tailReady; return route.fulfill({ json: { items: items.slice(offset, offset + 20), total: 42 } }); });
  await page.getByRole("button", { name: "重试", exact: true }).click();
  await expect(page.locator(".statistics-history-row")).toHaveCount(40);
  releaseTail();
  await page.locator(".statistics-sentinel").scrollIntoViewIfNeeded();
  await expect(page.locator(".statistics-history-row")).toHaveCount(42);
});

test("历史输入防抖且慢请求不能覆盖新条件", async ({ page }) => {
  await setup(page);
  const historyQueries: string[] = [];
  page.on("request", request => { const url = new URL(request.url()); if (url.pathname === "/statistics/events" && url.searchParams.has("query")) historyQueries.push(url.searchParams.get("query")!); });
  const input = page.getByRole("textbox", { name: "搜索历史车牌" });
  await input.fill("1"); await input.fill("12"); await input.fill("12345");
  await expect(page.locator(".statistics-history-row").first()).toContainText("新H12345");
  await expect.poll(() => historyQueries).toEqual(["12345"]);
  await page.route("**/statistics/events?*", async route => {
    const query = new URL(route.request().url()).searchParams.get("query");
    if (query === "123") await new Promise(resolve => setTimeout(resolve, 900));
    await route.fulfill({ json: { items: query === "23456" ? [items[1]] : [items[0]], total: 1 } }).catch(() => undefined);
  });
  await input.fill("123");
  await expect.poll(() => historyQueries.includes("123")).toBe(true);
  await input.fill("23456");
  await expect(page.locator(".statistics-history-row")).toHaveCount(1);
  await expect(page.locator(".statistics-history-row").first()).toContainText("新H23456");
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("打开详情上报查询事件且刷新统计使用真实接口结果", async ({ page }) => {
  await setup(page);
  const upload = page.waitForRequest(request => new URL(request.url()).pathname === "/statistics/events" && request.method() === "POST");
  await page.locator(".statistics-ranking button").first().click();
  const events = (await upload).postDataJSON().events;
  expect(events).toHaveLength(1); expect(events[0].vehicleId).toBe(1);
  expect(events[0].eventId).toMatch(/^[0-9a-f-]{36}$/);
  await page.goBack();
  await expect(page.locator(".statistics-history-row").first()).toBeVisible();
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem("plateview-query-events:3") || "[]").length)).toBe(0);
});

test("统计搜索聚焦后仅显示外层边框，输入框无内部圆角矩形", async ({ page }) => {
  await setup(page);
  const input = page.getByRole("textbox", { name: "搜索历史车牌" });
  await input.focus();
  await expect(input).toHaveCSS("outline-style", "none");
  await expect(input).toHaveCSS("border-radius", "0px");
  await expect(input).toHaveCSS("box-shadow", "none");
  await expect(page.locator(".statistics-search")).toHaveCSS("outline-style", "solid");
  await input.fill("12345");
  await expect(page.locator(".statistics-history-row").first()).toContainText("新H12345");
  await page.locator(".statistics-search").screenshot({ path: test.info().outputPath("统计搜索框.png") });
});
