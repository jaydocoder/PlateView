import { test, expect } from "@playwright/test";

const cases = [
  { role: "USER", schedule: false, primary: false, labels: ["首页", "统计", "我的"] },
  { role: "USER", schedule: true, primary: false, labels: ["首页", "排班", "统计", "我的"] },
  { role: "USER", schedule: true, primary: true, labels: ["首页", "排班", "统计", "我的"] },
  { role: "ADMIN", schedule: true, primary: false, labels: ["首页", "排班", "统计", "管理", "我的"] },
  { role: "ADMIN", schedule: true, primary: true, labels: ["首页", "排班", "统计", "管理", "我的"] },
];

for (const scenario of cases) {
  test(`底部导航按角色显示管理：${scenario.role}，排班${scenario.schedule}，主管理员${scenario.primary}`, async ({ page }, testInfo) => {
    await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test-token" } }));
    await page.route("**/auth/profile", route => route.fulfill({ json: {
      id: 1, username: "任意测试账号", role: scenario.role,
      scheduleEnabled: scenario.schedule, isPrimaryAdministrator: scenario.primary,
    } }));
    await page.goto("search");
    await expect(page.locator(".app-shell")).toBeVisible();
    const dock = page.locator(".bottom-nav");
    await expect(dock.locator("button")).toHaveText(scenario.labels);
    await expect(dock.locator("button").filter({ hasText: /^管理$/ })).toHaveCount(scenario.role === "ADMIN" ? 1 : 0);
    await expect(page.locator(".sidebar .nav-item")).toHaveText(scenario.labels);
    if (testInfo.project.name === "移动端") {
      await expect(dock).toBeVisible();
      const widths = await dock.getByRole("button").evaluateAll(buttons => buttons.map(button => button.getBoundingClientRect().width));
      expect(Math.max(...widths) - Math.min(...widths)).toBeLessThan(2);
      await page.screenshot({ path: testInfo.outputPath("底部导航.png") });
      await dock.getByRole("button", { name: "我的", exact: true }).click();
      await expect(page).toHaveURL(/\/profile$/);
      await expect(dock.getByRole("button")).toHaveText(scenario.labels);
    }
  });
}
