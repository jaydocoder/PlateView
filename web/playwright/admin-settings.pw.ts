import { test, expect } from "@playwright/test";

test("专属模块进入自己的配置页并使用对应接口", async ({ page }) => {
  await page.route("**/auth/web-refresh", route => route.fulfill({ json: { accessToken: "test" } }));
  await page.route("**/auth/profile", route => route.fulfill({ json: { id: 1, username: "admin", role: "ADMIN" } }));
  const policy = { revision: 1, vehicleResultLimit: 8, workOrderResultLimit: 8, wechatMessageResultLimit: 8, apiBaseUrl: "https://api.example.test/", updateBaseUrl: "https://update.example.test/", clientCount: 3, appliedClientCount: 2 };
  const person = { id: 2, username: "测试人员", realName: "", status: "ACTIVE" };
  const writes: Array<{ path: string; body: any }> = [];
  let rejectLimits = true;
  await page.route("http://127.0.0.1:4175/admin/**", route => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    if (method !== "GET") {
      const body = route.request().postDataJSON();
      writes.push({ path, body });
      if (path.endsWith("/api-endpoint/test")) return route.fulfill({ json: { success: true, message: "连接正常" } });
      if (path.endsWith("/api-endpoint")) { policy.apiBaseUrl = body.baseUrl; return route.fulfill({ json: policy }); }
      if (path.endsWith("/limits")) {
        if (rejectLimits) { rejectLimits = false; return route.fulfill({ status: 503, json: { message: "暂时无法保存" } }); }
        Object.assign(policy, body, { revision: 2 });
        return route.fulfill({ json: policy });
      }
      if (path.endsWith("/configuration")) return route.fulfill({ json: { cycleDays: body.cycleDays, participants: [person], candidates: [person] } });
      return route.fulfill({ json: {} });
    }
    if (path.endsWith("/client-policy")) return route.fulfill({ json: policy });
    if (path.endsWith("/configuration")) return route.fulfill({ json: { cycleDays: 7, participants: [person], candidates: [person] } });
    if (path.endsWith("/templates")) return route.fulfill({ json: { items: [{ id: 1, name: "测试模板", cycleDays: 7, versionNumber: 1, status: "ACTIVE" }] } });
    if (path.endsWith("/preview")) return route.fulfill({ json: { weekStart: "2026-10-02", shifts: [{ date: "2026-10-02", shiftType: "MORNING", persons: [person] }] } });
    return route.fulfill({ json: {} });
  });
  await page.goto("admin");
  await expect(page.locator(".admin-module-grid")).toBeVisible();
  await page.locator(".admin-module-card").filter({ hasText: "数据访问控制" }).click();
  const policyPage = page.getByRole("region", { name: "数据访问控制", exact: true });
  await policyPage.getByLabel("车辆档案", { exact: true }).fill("15");
  await policyPage.getByRole("button", { name: "保存数量", exact: true }).click();
  await expect(policyPage.getByRole("status")).toContainText("暂时无法保存");
  await expect(policyPage.getByLabel("车辆档案", { exact: true })).toHaveValue("15");
  await policyPage.getByRole("button", { name: "保存数量", exact: true }).click();
  await expect(policyPage.getByRole("status")).toContainText("已保存");
  expect(writes.at(-1)).toEqual({ path: "/admin/client-policy/limits", body: { vehicleResultLimit: 15, workOrderResultLimit: 8, wechatMessageResultLimit: 8 } });
  await policyPage.getByLabel("服务地址", { exact: true }).fill("https://new.example.test/");
  await policyPage.locator(".admin-settings-section").filter({ has: page.getByLabel("服务地址", { exact: true }) }).getByRole("button", { name: "测试连接", exact: true }).click();
  await expect(policyPage.getByRole("status")).toContainText("连接正常");
  page.on("dialog", dialog => dialog.accept());
  await policyPage.getByRole("button", { name: "保存服务地址", exact: true }).click();
  await expect(policyPage.getByRole("status")).toContainText("已保存");
  expect(writes.at(-1)).toEqual({ path: "/admin/client-policy/api-endpoint", body: { baseUrl: "https://new.example.test/" } });
  await page.locator(".admin-tabs").getByRole("button", { name: "排班规划", exact: true }).click();
  const planning = page.getByRole("region", { name: "排班规划", exact: true });
  await planning.getByLabel("轮班周期").fill("5");
  await planning.getByRole("button", { name: "保存排班配置", exact: true }).click();
  await expect(planning.getByRole("status")).toContainText("排班配置已保存");
  expect(writes.at(-1)).toEqual({ path: "/admin/schedules/configuration", body: { cycleDays: 5, participantIds: [2] } });
  await planning.getByRole("button", { name: "预览 测试模板", exact: true }).click();
  await expect(planning.getByRole("heading", { name: "排班预览" })).toBeVisible();
  await expect(planning).toContainText("早班");
  await planning.getByRole("button", { name: "应用 测试模板", exact: true }).click();
  await expect(planning.getByRole("status")).toContainText("模板已应用");
  expect(writes.at(-1)?.path).toBe("/admin/schedules/applications");
});
