import { test, expect } from "@playwright/test";

test("概览七张卡片都有同名页签并激活对应内容", async ({ page }, info) => {
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "admin", role: "ADMIN" } }));
  await page.route("http://127.0.0.1:4175/admin/**", route => route.fulfill({ json: { items: [], templates: [], participants: [], candidates: [], cycleDays: 7, summary: {}, actors: [], actionTypes: [] } }));
  await page.route("**/admin/vehicles/creation-capabilities", route => route.fulfill({ json: { creatableCategories: ["OTHER_LONG_TERM"], canChangeVehicleCategory: false } }));
  await page.route("**/admin/wechat-sync/rebuild/backups", route => route.fulfill({ json: [] }));
  await page.goto("admin");
  await expect(page.locator(".admin-module-grid")).toBeVisible();
  await page.screenshot({ path: info.outputPath("管理概览.png"), fullPage: true });
  for (const label of ["车辆档案", "账号管理", "导入中心", "审计日志", "排班规划", "微信同步", "数据访问控制"]) {
    const tab = page.locator(".admin-tabs").getByRole("button", { name: label, exact: true });
    await expect(tab).toHaveCount(1);
    await page.locator(".admin-module-card").filter({ has: page.getByText(label, { exact: true }) }).click();
    await expect(tab).toHaveClass(/selected/);
    if (label === "排班规划") await expect(page.getByRole("heading", { name: "排班规划", exact: true })).toBeVisible();
    if (label === "数据访问控制") await expect(page.getByRole("heading", { name: "数据访问控制", exact: true })).toBeVisible();
    if (label === "审计日志") await expect(page.locator(".audit-page")).toBeVisible();
    await page.locator(".admin-tabs").getByRole("button", { name: "概览", exact: true }).click();
    await expect(page.locator(".admin-module-grid")).toBeVisible();
  }
  expect(errors).toEqual([]);
});
