import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "admin", role: "ADMIN" } }));
});

test("概览不被未使用的统计请求阻塞", async ({ page }) => {
  let summaryCalls = 0;
  await page.route("**/admin/dashboard-summary", async route => { summaryCalls++; await new Promise(resolve => setTimeout(resolve, 2500)); await route.fulfill({ json: {} }).catch(() => undefined); });
  await page.goto("admin");
  await expect(page.locator(".admin-tabs")).toBeVisible();
  await expect(page.locator(".admin-module-grid")).toBeVisible();
  expect(summaryCalls).toBe(0);
});

test("车辆首屏不进行二次重复查询", async ({ page }) => {
  let calls = 0;
  await page.route("http://127.0.0.1:4175/admin/**", route => {
    if (new URL(route.request().url()).pathname === "/admin/vehicles") { calls++; return route.fulfill({ json: { items: [], total: 0 } }); }
    return route.fulfill({ json: { creatableCategories: ["OTHER_LONG_TERM"], canChangeVehicleCategory: false } });
  });
  await page.goto("admin");
  await expect(page.locator(".admin-tabs")).toBeVisible();
  await page.locator(".admin-tabs").getByRole("button", { name: "车辆档案", exact: true }).click();
  await expect(page.getByPlaceholder("按车牌号检索车辆档案")).toBeVisible();
  await page.waitForTimeout(700);
  expect(calls).toBe(1);
});

test("离开七个读取模块都会取消旧请求", async ({ page }) => {
  await page.addInitScript(() => {
    const state = window as typeof window & { requestStarts: string[]; requestAborts: string[] };
    state.requestStarts = []; state.requestAborts = [];
    const native = window.fetch.bind(window);
    window.fetch = (input, init) => {
      const path = typeof input === "string" ? input : input instanceof URL ? input.pathname : input.url;
      state.requestStarts.push(path);
      init?.signal?.addEventListener("abort", () => state.requestAborts.push(path));
      return native(input, init);
    };
  });
  await page.route("http://127.0.0.1:4175/admin/**", async route => { await new Promise(resolve => setTimeout(resolve, 3000)); await route.fulfill({ json: { items: [], total: 0 } }).catch(() => undefined); });
  await page.goto("admin");
  for (const [label, path] of [["车辆档案", "/admin/vehicles?"], ["账号管理", "/admin/users?"], ["导入中心", "/admin/imports?"], ["审计日志", "/admin/audit?"], ["排班规划", "/admin/schedules/configuration"], ["微信同步", "/admin/wechat-sync/rebuild/current"], ["数据访问控制", "/admin/client-policy"]]) {
    await page.locator(".admin-tabs").getByRole("button", { name: label, exact: true }).click();
    await expect.poll(() => page.evaluate(prefix => (window as any).requestStarts.some((item: string) => item.startsWith(prefix)), path)).toBe(true);
    await page.locator(".admin-tabs").getByRole("button", { name: "概览", exact: true }).click();
    await expect.poll(() => page.evaluate(prefix => (window as any).requestAborts.some((item: string) => item.startsWith(prefix)), path)).toBe(true);
    await expect(page.locator(".admin-module-grid")).toBeVisible();
  }
});
