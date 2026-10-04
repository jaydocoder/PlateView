import { test, expect } from "@playwright/test";

for (const primary of [false, true]) {
  test(primary ? "主管理员可见微信重构" : "普通管理员看不到微信重构入口及内容", async ({ page }) => {
    await page.route("**/auth/web-refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ accessToken: "test-token" }) }));
    await page.route("**/auth/profile", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id: 1, username: primary ? "admin" : "manager", role: "ADMIN", isPrimaryAdministrator: primary }) }));
    // 概览标识与会话不一致时，也不能扩大普通管理员的可见范围。
    await page.route("**/admin/dashboard-summary", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ isPrimaryAdministrator: true }) }));
    let rebuildRequests = 0;
    await page.route("**/admin/wechat-sync/**", route => {
      rebuildRequests++;
      return route.fulfill({ status: 200, contentType: "application/json", body: route.request().url().endsWith("/backups") ? "[]" : "null" });
    });
    await page.goto("admin");
    await expect(page.locator(".admin-module-grid")).toBeVisible();
    const tab = page.locator(".admin-tabs").getByRole("button", { name: "微信同步", exact: true });
    if (primary) {
      await expect(tab).toBeVisible();
      await expect(page.locator(".admin-module-grid").getByRole("button", { name: /微信同步/ })).toBeVisible();
      await tab.click();
      await expect(page.getByRole("button", { name: "预览重构范围" })).toBeVisible();
      await expect.poll(() => rebuildRequests).toBeGreaterThanOrEqual(2);
    } else {
      await expect(tab).toHaveCount(0);
      await expect(page.getByRole("button", { name: /微信同步/ })).toHaveCount(0);
      await expect(page.locator(".rebuild-panel")).toHaveCount(0);
      expect(rebuildRequests).toBe(0);
    }
  });
}

test("非管理员即使有主管理员标识也不显示管理工作台", async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ accessToken: "test-token" }) }));
  await page.route("**/auth/profile", route => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id: 1, username: "admin", role: "USER", isPrimaryAdministrator: true }) }));
  let adminRequests = 0;
  await page.route("http://127.0.0.1:4175/admin/**", route => { adminRequests++; return route.fulfill({ status: 403 }); });
  await page.goto("admin");
  await expect(page.locator(".app-shell")).toBeVisible();
  await expect(page.locator(".admin-page")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "管理", exact: true })).toHaveCount(0);
  await expect(page.locator(".rebuild-panel")).toHaveCount(0);
  expect(adminRequests).toBe(0);
});
