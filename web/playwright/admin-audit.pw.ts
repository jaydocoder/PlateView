import { test, expect, type Page } from "@playwright/test";

async function setup(page: Page) {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "普通管理员", role: "ADMIN" } }));
  await page.route("**/admin/dashboard-summary", route => route.fulfill({ json: {} }));
}
async function openAudit(page: Page) {
  await page.goto("admin");
  await expect(page.locator(".admin-module-grid")).toBeVisible();
  await page.locator(".admin-module-card").filter({ hasText: "审计日志" }).click();
}
const entry = (id: number, resultStatus = "SUCCESS") => ({ id, actorUsername: "测试人员", actionType: "VEHICLE_UPDATE", targetType: "VEHICLE", targetId: id, resultStatus, createdAt: "2026-10-02T00:00:05Z" });
const response = (items: ReturnType<typeof entry>[], total = items.length) => ({ items, total, summary: { total, successCount: total - 1, abnormalCount: 1, activeActorCount: 2 }, actors: [{ id: 2, username: "测试人员" }], actionTypes: ["VEHICLE_UPDATE", "LOGIN"] });

test("审计布局、中文、北京时间与全部筛选", async ({ page }, info) => {
  await setup(page);
  const queries: URL[] = [];
  await page.route("**/admin/audit?*", route => { const url = new URL(route.request().url()); queries.push(url); const items = url.searchParams.get("result") === "ABNORMAL" ? [entry(2, "DENIED")] : [entry(1), entry(2, "DENIED")]; return route.fulfill({ json: response(items) }); });
  await openAudit(page);
  await expect(page.locator(".audit-entry")).toHaveCount(2);
  expect(queries[0].searchParams.get("range")).toBe("24h");
  expect(queries[0].searchParams.has("actorId")).toBe(false);
  expect(queries[0].searchParams.has("actionType")).toBe(false);
  expect(queries[0].searchParams.has("result")).toBe(false);
  await expect(page.locator(".audit-summary>div")).toHaveCount(4);
  await expect(page.locator(".audit-entry").first()).toContainText("更新车辆档案");
  await expect(page.locator(".audit-entry").first()).toContainText("08:00:05");
  await expect(page.locator(".audit-entry").nth(1).locator(".audit-result")).toHaveText("已拒绝");
  await expect(page.locator(".audit-entry").nth(1).locator(".audit-result")).toHaveClass(/failure/);
  for (const [label, range] of [["近7天", "7d"], ["近30天", "30d"], ["全部时间", "all"]]) {
    await page.getByRole("button", { name: label, exact: true }).click();
    await expect.poll(() => queries.at(-1)?.searchParams.get("range")).toBe(range);
  }
  await page.getByLabel("操作人", { exact: true }).selectOption("2");
  await expect.poll(() => queries.at(-1)?.searchParams.get("actorId")).toBe("2");
  await page.getByLabel("操作类型", { exact: true }).selectOption("VEHICLE_UPDATE");
  await expect.poll(() => queries.at(-1)?.searchParams.get("actionType")).toBe("VEHICLE_UPDATE");
  await page.locator('[aria-label="审计结果"]').getByRole("button", { name: "异常", exact: true }).click();
  await expect.poll(() => queries.at(-1)?.searchParams.get("result")).toBe("ABNORMAL");
  await expect(page.getByLabel("操作人", { exact: true })).toHaveValue("2");
  await expect(page.getByLabel("操作类型", { exact: true })).toHaveValue("VEHICLE_UPDATE");
  await expect(page.locator(".audit-entries")).toHaveAttribute("aria-busy", "false");
  const count = queries.length;
  await page.getByRole("button", { name: "刷新审计日志" }).click();
  await expect.poll(() => queries.length).toBeGreaterThan(count);
  for (const width of [320, 375, 390, 430, 768, 1280, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    if (width === 390) await page.screenshot({ path: info.outputPath("审计日志.png"), fullPage: true });
  }
});

test("审计下滑加载下一页并去重，失败重试保留已有记录", async ({ page }) => {
  await setup(page);
  let failed = false;
  const offsets: number[] = [];
  await page.route("**/admin/audit?*", route => { const offset = Number(new URL(route.request().url()).searchParams.get("offset")); offsets.push(offset); if (offset && !failed) { failed = true; return route.fulfill({ status: 503, json: { message: "加载暂时失败" } }); } return route.fulfill({ json: response(offset ? [entry(50), entry(51, "FAILURE")] : Array.from({ length: 50 }, (_, index) => entry(index + 1)), 51) }); });
  await openAudit(page);
  await expect(page.locator(".audit-entry")).toHaveCount(50);
  await page.locator(".audit-sentinel").scrollIntoViewIfNeeded();
  await expect(page.getByRole("alert")).toContainText("加载暂时失败");
  await expect(page.locator(".audit-entry")).toHaveCount(50);
  await page.getByRole("button", { name: "重试", exact: true }).click();
  await expect(page.locator(".audit-entry")).toHaveCount(51);
  expect(offsets.filter(offset => offset > 0)).toEqual([50, 50]);
  expect(offsets[0]).toBe(0);
  await expect(page.locator(".audit-entry").last()).toContainText("失败");
});

test("初次失败可重试，空结果可刷新", async ({ page }) => {
  await setup(page);
  let requests = 0;
  let fail = true;
  await page.route("**/admin/audit?*", route => { requests++; return fail ? route.fulfill({ status: 503, json: { message: "服务暂不可用" } }) : route.fulfill({ json: response([], 0) }); });
  await openAudit(page);
  await expect(page.getByRole("alert")).toContainText("服务暂不可用");
  fail = false;
  await page.getByRole("button", { name: "重试", exact: true }).click();
  await expect(page.getByRole("heading", { name: "暂无审计记录" })).toBeVisible();
  const count = requests;
  await page.getByRole("button", { name: "刷新审计日志" }).click();
  await expect.poll(() => requests).toBe(count + 1);
});

test("切换筛选不会被旧响应覆盖", async ({ page }) => {
  await setup(page);
  await page.route("**/admin/audit?*", async route => { const old = new URL(route.request().url()).searchParams.get("range") === "24h"; if (old) await new Promise(resolve => setTimeout(resolve, 500)); return route.fulfill({ json: response([entry(old ? 1 : 2)]) }); });
  await openAudit(page);
  await page.getByRole("button", { name: "近7天", exact: true }).click();
  await expect(page.locator(".audit-entry")).toContainText("#2");
  await page.waitForTimeout(600);
  await expect(page.locator(".audit-entry")).toContainText("#2");
  await expect(page.locator(".audit-entry")).toHaveCount(1);
});
