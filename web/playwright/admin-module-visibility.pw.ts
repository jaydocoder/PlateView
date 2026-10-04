import { test, expect } from "@playwright/test";

const baseModules = ["车辆档案", "账号管理", "导入中心", "审计日志"];
const primaryModules = ["排班规划", "微信同步", "数据访问控制"];

for (const profile of [
  { username: "普通核验员", role: "USER", isPrimaryAdministrator: false },
  { username: "其他管理员", role: "ADMIN", isPrimaryAdministrator: true },
  { username: "admin", role: "ADMIN" },
  { username: "admin", role: "USER", isPrimaryAdministrator: true },
]) {
  test(`工作台模块分级可见：${profile.username}，${profile.role}`, async ({ page }) => {
    await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
    await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, ...profile } }));
    let requests = 0;
    await page.route("http://127.0.0.1:4175/admin/**", route => {
      requests++;
      // 概览返回的标识不能改变会话中用户名及角色定义的权限。
      return route.fulfill({ json: { isPrimaryAdministrator: true } });
    });
    await page.goto("admin");
    await expect(page.locator(".app-shell")).toBeVisible();
    const isAdmin = profile.role === "ADMIN";
    const isPrimary = isAdmin && profile.username === "admin";
    await expect(page.locator(".bottom-nav button").filter({ hasText: /^管理$/ })).toHaveCount(isAdmin ? 1 : 0);
    if (!isAdmin) {
      await expect(page.locator(".admin-page")).toHaveCount(0);
      expect(requests).toBe(0);
      return;
    }
    const grid = page.locator(".admin-module-grid");
    await expect(grid).toBeVisible();
    await expect(grid.locator(".admin-module-card strong")).toHaveText(isPrimary ? [...baseModules, ...primaryModules] : baseModules);
    await expect(page.locator(".admin-tabs").getByRole("button", { name: "微信同步", exact: true })).toHaveCount(isPrimary ? 1 : 0);
    for (const title of primaryModules) {
      await expect(grid.getByRole("button", { name: new RegExp(title) })).toHaveCount(isPrimary ? 1 : 0);
    }
  });
}
